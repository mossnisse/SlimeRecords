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
import java.util.UUID;
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
        if (importStatus.getValue() == ImportState.LOADING) return;
        this.activeStrategy = strategy;

        importStatus.setValue(ImportState.LOADING);
        statusMessage.setValue("");

        // Heavy lifting on background thread
        UserDatabase.getDbExecutor().execute(() -> {
            File tempFile = new File(getApplication().getCacheDir(), "import_temp.zip");
            try (ParcelFileDescriptor pfd = getApplication().getContentResolver().openFileDescriptor(zipUri, "r");
                 FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
                 FileOutputStream fos = new FileOutputStream(tempFile)) {

                FileUtils.copy(fis, fos);
                fos.flush();

                processImportFile(tempFile);
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

    /** The file picker accepts both ZIP archives and bare CSV/text files. */
    private void processImportFile(File file) throws IOException {
        if (isZipFile(file)) {
            processZipFile(file);
        } else {
            processCsvFile(file);
        }
    }

    private boolean isZipFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] signature = new byte[2];
            return fis.read(signature) == 2 && signature[0] == 'P' && signature[1] == 'K';
        }
    }

    private void processCsvFile(File csvFile) throws IOException {
        File photoDir = getApplication().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (photoDir == null) throw new IOException("Photo storage is unavailable.");

        String csvContent;
        try (FileInputStream fis = new FileInputStream(csvFile)) {
            csvContent = readStreamToString(fis);
        }
        // No archive means no staged photos; rows referencing photos fall back
        // to files already present in the photo directory.
        parseAndSaveCsv(csvContent, photoDir, new HashMap<>(), new HashMap<>(), new ArrayList<>());
    }

    private void processZipFile(File zipFile) throws IOException {
        File photoDir = getApplication().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (photoDir == null) throw new IOException("Photo storage is unavailable.");
        if (!photoDir.exists() && !photoDir.mkdirs()) {
            throw new IOException("Could not create the photo directory.");
        }

        File stagingDir = new File(getApplication().getCacheDir(),
                "import_photos_" + UUID.randomUUID());
        if (!stagingDir.mkdirs()) throw new IOException("Could not prepare temporary photo storage.");

        String csvContent = null;
        Map<String, File> stagedPhotos = new HashMap<>();
        Map<String, String> resolvedPhotoPaths = new HashMap<>();
        List<File> importedPhotos = new ArrayList<>();

        try {
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    // Accept any CSV outside photos/ so nested archives still work,
                    // but never mistake a photos/*.csv entry for the data file.
                    if (name.endsWith(".csv") && !name.startsWith("photos/")) {
                        csvContent = readStreamToString(zis);
                    } else if (name.startsWith("photos/") && !entry.isDirectory()) {
                        String photoName = new File(name).getName();
                        if (!photoName.isEmpty()) {
                            File stagedFile = new File(stagingDir, photoName);
                            extractFile(zis, stagedFile);
                            stagedPhotos.put(photoName, stagedFile);
                        }
                    }
                    zis.closeEntry();
                }
            }

            if (csvContent == null) {
                throw new IOException("ZIP archive is missing a data.csv file.");
            }

            parseAndSaveCsv(csvContent, photoDir, stagedPhotos,
                    resolvedPhotoPaths, importedPhotos);
        } finally {
            cleanupUnreferencedPhotos(importedPhotos);
            deleteDirectoryContents(stagingDir);
        }
    }

    private void parseAndSaveCsv(String csv,
                                 File photoDir,
                                 Map<String, File> stagedPhotos,
                                 Map<String, String> resolvedPhotoPaths,
                                 List<File> importedPhotos) throws IOException {
        ImportResult results = new ImportResult();
        // Remove BOM if present
        if (csv.startsWith("\uFEFF")) csv = csv.substring(1);

        String[] lines = csv.split("\\r?\\n");
        if (lines.length < 2) throw new IOException("The CSV file contains no data rows.");

        // Detect Delimiter (Comma or Semicolon)
        String headerLine = lines[0];
        String d = headerLine.contains(";") ? ";" : ",";

        // Regex to split while respecting quotes
        String regex = d + "(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)";
        String[] headers = headerLine.split(regex);
        // Header names are stored lowercased so lookups are case-insensitive
        // (the app's own export writes "ID" while lookups used "id", etc.)
        Map<String, Integer> colMap = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            colMap.put(cleanQuotes(headers[i]).trim().toLowerCase(Locale.ROOT), i);
        }

        // Reject files that are clearly not a SlimeRecords export
        if (!colMap.containsKey("decimallatitude") || !colMap.containsKey("decimallongitude")
                || !colMap.containsKey("eventdate")) {
            throw new IOException("Unrecognized CSV format: missing decimalLatitude, "
                    + "decimalLongitude or eventDate columns.");
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
                boolean replaceExisting = false;
                boolean addNewRecord = false;

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
                        targetId = existingId; // Reuse the ID so links remain valid
                        replaceExisting = true;
                    } else if (activeStrategy == DuplicateStrategy.KEEP_BOTH) {
                        // Let Room auto-generate a new ID
                        addNewRecord = true;
                    }
                } else {
                    // Record doesn't exist, use exported ID if available, else 0
                    targetId = (activeStrategy == DuplicateStrategy.KEEP_BOTH) ? 0 : exportedId;
                    addNewRecord = true;
                }

                // 3. Populate the Record
                ObservationRecord record = new ObservationRecord();
                record.id = targetId;

                // Primary Location Data (lat/lon/time already parsed above)
                record.latitude = lat;
                record.longitude = lon;
                record.accuracy = (float) parseDouble(parts, colMap, "coordinateUncertaintyInMeters", 0);
                String altitudeText = getString(parts, colMap, "verbatimElevation", "").trim();
                if (!altitudeText.isEmpty()) {
                    try {
                        record.altitude = Double.parseDouble(altitudeText);
                        // Older exports wrote 0 for records without a fix, so apply
                        // the same legacy rule as hasKnownAltitude(): 0 means unknown.
                        record.hasAltitude = record.altitude != 0.0;
                    } catch (NumberFormatException ignored) {
                        record.altitude = 0;
                        record.hasAltitude = false;
                    }
                }
                record.localTime = time;
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

                String qStr = getString(parts, colMap, "organismQuantity", "").trim();
                if (!qStr.isEmpty()) {
                    try {
                        attr.organismQuantity = Integer.parseInt(qStr);
                    } catch (NumberFormatException ignored) {
                        // Import the record without a quantity rather than failing the row.
                    }
                }

                record.attributes = attr;

                // Photo Handling
                String photoNamesStr = getString(parts, colMap, "photos", "");
                List<String> photoPaths = new ArrayList<>();
                if (!photoNamesStr.isEmpty()) {
                    for (String name : photoNamesStr.split("\\|")) {
                        String resolvedPath = materializeImportedPhoto(name.trim(), photoDir,
                                stagedPhotos, resolvedPhotoPaths, importedPhotos);
                        if (resolvedPath != null) photoPaths.add(resolvedPath);
                    }
                }

                if (replaceExisting) {
                    List<String> oldPhotoPaths = locationDao.replaceLocationWithPhotos(
                            existingId, record, photoPaths);
                    deleteOrphanedPhotoPaths(oldPhotoPaths);
                    results.updated++;
                } else {
                    locationDao.insertLocationWithPhotos(record, photoPaths);
                    if (addNewRecord) results.added++;
                }
            } catch (Exception e) {
                results.failed++;
                Log.e("Import", "Error parsing row " + i, e);
            }
        }
        statusMessage.postValue(results.toString());
    }

    private String materializeImportedPhoto(String photoName,
                                            File photoDir,
                                            Map<String, File> stagedPhotos,
                                            Map<String, String> resolvedPhotoPaths,
                                            List<File> importedPhotos) throws IOException {
        if (photoName.isEmpty()) return null;

        String safeName = new File(photoName).getName();
        File stagedFile = stagedPhotos.get(safeName);
        if (stagedFile == null || !stagedFile.exists()) {
            // The archive doesn't carry this photo (e.g. the file was unreadable at
            // export time). Fall back to a matching file already on the device so a
            // REPLACE import keeps the link instead of deleting the photo as orphaned.
            File existingFile = new File(photoDir, safeName);
            return existingFile.exists() ? existingFile.getAbsolutePath() : null;
        }

        String existingPath = resolvedPhotoPaths.get(safeName);
        if (existingPath != null && new File(existingPath).exists()) return existingPath;

        File destination = createAvailablePhotoFile(photoDir, safeName);
        importedPhotos.add(destination); // Track partial files too, in case copying fails.
        try (FileInputStream in = new FileInputStream(stagedFile);
             FileOutputStream out = new FileOutputStream(destination, false)) {
            FileUtils.copy(in, out);
        }

        String path = destination.getAbsolutePath();
        resolvedPhotoPaths.put(safeName, path);
        return path;
    }

    private File createAvailablePhotoFile(File photoDir, String requestedName) throws IOException {
        String baseName = requestedName;
        String extension = "";
        int dot = requestedName.lastIndexOf('.');
        if (dot > 0) {
            baseName = requestedName.substring(0, dot);
            extension = requestedName.substring(dot);
        }

        File candidate = new File(photoDir, requestedName);
        int suffix = 1;
        while (!candidate.createNewFile()) {
            candidate = new File(photoDir, baseName + "_imported_" + suffix + extension);
            suffix++;
        }
        return candidate;
    }

    private void cleanupUnreferencedPhotos(List<File> importedPhotos) {
        for (File photo : importedPhotos) {
            try {
                if (locationDao.getPhotoReferenceCount(photo.getAbsolutePath()) == 0) {
                    FileUtils.deleteFileAtPath(photo.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.e("Import", "Could not clean up imported photo: " + photo, e);
            }
        }
    }

    private void deleteOrphanedPhotoPaths(List<String> paths) {
        for (String path : paths) {
            try {
                if (locationDao.getPhotoReferenceCount(path) == 0) {
                    FileUtils.deleteFileAtPath(path);
                }
            } catch (Exception e) {
                Log.e("Import", "Could not clean up replaced photo: " + path, e);
            }
        }
    }

    private void deleteDirectoryContents(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) FileUtils.deleteFileAtPath(file.getAbsolutePath());
        }
        FileUtils.deleteFileAtPath(directory.getAbsolutePath());
    }

    // Helper: Safely get string from mapped column (header keys are lowercased)
    private String getString(String[] parts, Map<String, Integer> map, String key, String fallback) {
        Integer idx = map.get(key.toLowerCase(Locale.ROOT));
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
            FileUtils.copy(zis, fos);
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
