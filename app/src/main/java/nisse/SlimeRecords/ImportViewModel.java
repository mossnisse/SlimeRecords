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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import nisse.SlimeRecords.data.*;

public class ImportViewModel extends AndroidViewModel {
    private final LocationDao locationDao;
    public enum ImportState { IDLE, LOADING, SUCCESS, ERROR }
    private final MutableLiveData<ImportState> importStatus = new MutableLiveData<>(ImportState.IDLE);
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>("");
    public enum DuplicateStrategy {
        SKIP,       // Don't import if it already exists
        REPLACE,    // Delete old record and photos, then insert new
        KEEP_BOTH   // Ignore the ID and insert as a brand new record
    }

    private DuplicateStrategy activeStrategy = DuplicateStrategy.SKIP;

    public ImportViewModel(@NonNull Application application) {
        super(application);
        locationDao = UserDatabase.getInstance(application).locationDao();
    }

    public void startImport(Uri zipUri, DuplicateStrategy strategy) {
        this.activeStrategy = strategy;
        if (importStatus.getValue() == ImportState.LOADING) return;

        importStatus.setValue(ImportState.LOADING);
        statusMessage.setValue("");

        // Heavy lifting on background thread
        UserDatabase.getDbExecutor().execute(() -> {
            File tempFile = new File(getApplication().getCacheDir(), "import_temp.zip");
            try (ParcelFileDescriptor pfd = getApplication().getContentResolver().openFileDescriptor(zipUri, "r");
                 FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
                 FileOutputStream fos = new FileOutputStream(tempFile)) {

                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) fos.write(buffer, 0, read);
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
        String csvContent = null;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".csv")) { // Better: handle any CSV in the root
                    csvContent = readStreamToString(zis);
                } else if (name.startsWith("photos/") && !entry.isDirectory()) {
                    File destFile = new File(photoDir, new File(name).getName());
                    extractFile(zis, destFile);
                }
                zis.closeEntry();
            }
        }

        if (csvContent != null) {
            parseAndSaveCsv(csvContent, photoDir);
        } else {
            throw new IOException("ZIP archive is missing a data.csv file.");
        }
    }

    private void parseAndSaveCsv(String csv, File photoDir) {
        ImportResult results = new ImportResult();
        // Remove BOM if present
        if (csv.startsWith("\uFEFF")) csv = csv.substring(1);

        String[] lines = csv.split("\\r?\\n");
        if (lines.length < 2) return;

        // Detect Delimiter (Comma or Semicolon)
        String headerLine = lines[0];
        String d = headerLine.contains(";") ? ";" : ",";

        // Regex to split while respecting quotes
        String regex = d + "(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)";
        String[] headers = headerLine.split(regex);
        Map<String, Integer> colMap = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            colMap.put(cleanQuotes(headers[i]).trim(), i);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

        Integer idCol = colMap.get("id");

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(regex);
            try {
                // 1. Identification
                double lat = parseDouble(parts, colMap, "decimalLatitude", 0);
                double lon = parseDouble(parts, colMap, "decimalLongitude", 0);
                String time = getString(parts, colMap, "eventDate", "");
                long exportedId = (idCol != null) ? (long) parseDouble(parts, colMap, "id", 0) : 0;

                long targetId = 0; // The ID we will use for the final record
                long existingId = 0; // The ID of the record currently in DB

                // Check if it exists by ID
                if (exportedId != 0 && locationDao.existsById(exportedId)) {
                    existingId = exportedId;
                } else {
                    // Check if it exists by Fingerprint
                    Long foundId = locationDao.findIdByFingerprint(lat, lon, time);
                    if (foundId != null) existingId = foundId;
                }

                // 2. Handle Strategy
                if (existingId != 0) {
                    if (activeStrategy == DuplicateStrategy.SKIP) {
                        results.skipped++;
                        continue;
                    } else if (activeStrategy == DuplicateStrategy.REPLACE) {
                        deleteOldRecordProperly(existingId);
                        targetId = existingId; // Reuse the ID so links remain valid
                        results.updated++;
                    } else if (activeStrategy == DuplicateStrategy.KEEP_BOTH) {
                        targetId = 0; // Let Room auto-generate a new ID
                        results.added++;
                    }
                } else {
                    // Record doesn't exist, use exported ID if available, else 0
                    targetId = (activeStrategy == DuplicateStrategy.KEEP_BOTH) ? 0 : exportedId;
                    results.added++;
                }

                // 3. Populate the Record
                ObservationRecord record = new ObservationRecord();
                record.id = targetId;

                // Primary Location Data
                record.latitude = parseDouble(parts, colMap, "decimalLatitude", 0);
                record.longitude = parseDouble(parts, colMap, "decimalLongitude", 0);
                record.accuracy = (float) parseDouble(parts, colMap, "coordinateUncertaintyInMeters", 0);
                record.altitude = parseDouble(parts, colMap, "verbatimElevation", 0);
                record.localTime = getString(parts, colMap, "eventDate", "");
                record.note = getString(parts, colMap, "occurrenceRemarks", "");

                // Geo fields
                record.countryCode = getString(parts, colMap, "countryCode", "");
                record.country = getString(parts, colMap, "country", "");
                record.province = getString(parts, colMap, "province", "");
                record.district = getString(parts, colMap, "district", "");
                record.locality = getString(parts, colMap, "locality", "");

                // Handle Timestamp for History
                try {
                    Date date = sdf.parse(record.localTime);
                    if (date != null) record.timestamp = date.getTime();
                } catch (Exception e) { record.timestamp = System.currentTimeMillis(); }

                // Attributes
                SpeciesAttributes attr = new SpeciesAttributes();
                attr.taxonName = getString(parts, colMap, "taxonName", "");
                attr.substrate = getString(parts, colMap, "Substrate", "");
                attr.habitat = getString(parts, colMap, "Habitat", "");
                attr.collector = getString(parts, colMap, "recordedBy", "");
                attr.lifeStage = getString(parts, colMap, "lifeStage", "");
                attr.sex = getString(parts, colMap, "sex", "");
                attr.activity = getString(parts, colMap, "activity", "");
                attr.samplingProtocol = getString(parts, colMap, "samplingProtocol", "");
                attr.specimenNr = getString(parts, colMap, "SpecimenNr", "");
                attr.isSpecimen = getString(parts, colMap, "isSpecimen", "false").equalsIgnoreCase("true");

                String qStr = getString(parts, colMap, "organismQuantity", "");
                if (!qStr.isEmpty()) attr.organismQuantity = Integer.parseInt(qStr);

                record.attributes = attr;

                // Photo Handling
                String photoNamesStr = getString(parts, colMap, "photos", "");
                List<String> photoPaths = new ArrayList<>();
                if (!photoNamesStr.isEmpty()) {
                    for (String name : photoNamesStr.split("\\|")) {
                        File pFile = new File(photoDir, name.trim());
                        if (pFile.exists()) photoPaths.add(pFile.getAbsolutePath());
                    }
                }

                locationDao.insertLocationWithPhotos(record, photoPaths);
            } catch (Exception e) {
                results.failed++;
                Log.e("Import", "Error parsing row " + i, e);
            }
        }
        statusMessage.postValue(results.toString());
    }

    private void deleteOldRecordProperly(long id) {
        // Get the data synchronously
        RecordWithPhotos oldRecord = locationDao.getLocationByIdSync(id);

        if (oldRecord != null) {
            // Use the same logic we put in HistoryViewModel
            if (oldRecord.photos != null) {
                for (PhotoRecord p : oldRecord.photos) {
                    // Delete the DB link first
                    locationDao.deletePhotoById(p.id);

                    // Check if the file is now orphaned
                    if (locationDao.getPhotoReferenceCount(p.filePath) == 0) {
                        FileUtils.deleteFileAtPath(p.filePath);
                    }
                }
            }
            // Delete the location itself
            locationDao.deleteLocation(oldRecord.location);
        }
    }

    // Helper: Safely get string from mapped column
    private String getString(String[] parts, Map<String, Integer> map, String key, String fallback) {
        Integer idx = map.get(key);
        if (idx == null || idx >= parts.length) return fallback;
        return cleanQuotes(parts[idx]);
    }

    // Helper: Safely get double from mapped column
    private double parseDouble(String[] parts, Map<String, Integer> map, String key, double fallback) {
        String val = getString(parts, map, key, "");
        try { return val.isEmpty() ? fallback : Double.parseDouble(val); }
        catch (Exception e) { return fallback; }
    }

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

    public LiveData<ImportState> getImportStatus() { return importStatus; }
    public LiveData<String> getStatusMessage() { return statusMessage; }
}