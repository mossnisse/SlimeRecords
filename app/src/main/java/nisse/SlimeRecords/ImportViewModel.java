package nisse.SlimeRecords;

import android.app.Application;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import nisse.SlimeRecords.data.*;

public class ImportViewModel extends AndroidViewModel {
    private final LocationDao locationDao;
    public enum ImportState { IDLE, LOADING, SUCCESS, ERROR }
    private final MutableLiveData<ImportState> importStatus = new MutableLiveData<>(ImportState.IDLE);
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>("");

    public ImportViewModel(@NonNull Application application) {
        super(application);
        locationDao = UserDatabase.getInstance(application).locationDao();
    }

    public void startImport(Uri zipUri) {
        if (importStatus.getValue() == ImportState.LOADING) return;

        importStatus.setValue(ImportState.LOADING);
        statusMessage.setValue("");

        final ParcelFileDescriptor pfd;
        try {
            // "r" mode is critical for the Samsung MTP bug fix
            pfd = getApplication().getContentResolver().openFileDescriptor(zipUri, "r");
            if (pfd == null) throw new IOException("Could not open File Descriptor");
        } catch (Exception e) {
            Log.e("Import", "Security Error: Denied access on main thread", e);
            statusMessage.setValue("System denied access to file.");
            importStatus.setValue(ImportState.ERROR);
            return;
        }

        UserDatabase.getDbExecutor().execute(() -> {
            File tempFile = new File(getApplication().getCacheDir(), "import_temp.zip");
            try (ParcelFileDescriptor autoClosePfd = pfd;
                 FileInputStream fis = new FileInputStream(autoClosePfd.getFileDescriptor());
                 FileOutputStream fos = new FileOutputStream(tempFile)) {

                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fos.flush();

                processZipFile(tempFile);
                importStatus.postValue(ImportState.SUCCESS);

            } catch (Exception e) {
                Log.e("Import", "Processing failed", e);
                statusMessage.postValue("Import failed: " + e.getLocalizedMessage());
                importStatus.postValue(ImportState.ERROR);
            } finally {
                if (tempFile.exists()) tempFile.delete();
            }
        });
    }

    private void processZipFile(File zipFile) throws IOException {
        File photoDir = getApplication().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (photoDir != null && !photoDir.exists()) photoDir.mkdirs();

        String csvContent = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("data.csv")) {
                    csvContent = readStreamToString(zis);
                } else if (name.startsWith("photos/") && !entry.isDirectory()) {
                    String fileName = new File(name).getName();
                    File destFile = new File(photoDir, fileName);
                    extractFile(zis, destFile);
                }
                zis.closeEntry();
            }
        }

        if (csvContent != null) {
            parseAndSaveCsv(csvContent, photoDir);
        } else {
            throw new IOException("ZIP archive is missing data.csv");
        }
    }

    private void parseAndSaveCsv(String csv, File photoDir) {
        String[] lines = csv.split("\\r?\\n");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            if (parts.length < 15) continue;

            try {
                LocationRecord record = new LocationRecord();
                record.latitude = Double.parseDouble(parts[1]);
                record.longitude = Double.parseDouble(parts[2]);
                record.accuracy = Float.parseFloat(parts[3]);
                record.altitude = Double.parseDouble(parts[4]);
                record.localTime = cleanQuotes(parts[5]);
                record.note = cleanQuotes(parts[13]);
                record.localityDescription = cleanQuotes(parts[10]);

                // Set timestamp so it shows up in HistoryActivity
                try {
                    Date date = sdf.parse(record.localTime);
                    if (date != null) record.timestamp = date.getTime();
                } catch (Exception e) {
                    record.timestamp = System.currentTimeMillis();
                }

                SpeciesAttributes attr = new SpeciesAttributes();
                attr.species = cleanQuotes(parts[6]);
                attr.substrate = cleanQuotes(parts[7]);
                attr.habitat = cleanQuotes(parts[8]);
                attr.collector = cleanQuotes(parts[9]);
                attr.isSpecimen = Boolean.parseBoolean(parts[11]);
                attr.specimenNr = cleanQuotes(parts[12]);
                record.attributes = attr;

                String photoNamesStr = cleanQuotes(parts[14]);
                List<String> photoPaths = new ArrayList<>();
                if (!photoNamesStr.isEmpty()) {
                    for (String name : photoNamesStr.split("\\|")) {
                        File pFile = new File(photoDir, name.trim());
                        if (pFile.exists()) {
                            photoPaths.add(pFile.getAbsolutePath());
                        }
                    }
                }
                locationDao.insertLocationWithPhotos(record, photoPaths);
            } catch (Exception e) {
                Log.e("Import", "Error parsing line " + i, e);
            }
        }
    }

    // Helper methods...
    private String cleanQuotes(String input) {
        if (input == null) return "";
        String s = input.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s.replace("\"\"", "\"");
    }

    private void extractFile(ZipInputStream zis, File destFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
        }
    }

    private String readStreamToString(InputStream is) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) result.write(buffer, 0, length);
        return result.toString(StandardCharsets.UTF_8.name());
    }

    // GETTERS
    public LiveData<ImportState> getImportStatus() { return importStatus; }
    public LiveData<String> getStatusMessage() { return statusMessage; }
}