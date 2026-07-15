package nisse.SlimeRecords;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import nisse.SlimeRecords.data.ImportRecordStore;
import nisse.SlimeRecords.data.ObservationRecord;
import nisse.SlimeRecords.data.RecordFingerprint;
import nisse.SlimeRecords.data.SpeciesAttributes;

/** Imports SlimeRecords CSV and ZIP files without depending on Android framework classes. */
public final class ImportProcessor {
    public enum DuplicateStrategy { SKIP, REPLACE, KEEP_BOTH }

    private static final long MAX_CSV_BYTES = 10L * 1024 * 1024;
    private static final long MAX_PHOTO_BYTES = 25L * 1024 * 1024;
    private static final long MAX_ARCHIVE_EXTRACTED_BYTES = 250L * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 5_000;

    private final ImportRecordStore store;
    private final LongSupplier currentTimeMillis;

    ImportProcessor(ImportRecordStore store, LongSupplier currentTimeMillis) {
        this.store = store;
        this.currentTimeMillis = currentTimeMillis;
    }

    public ImportResult process(File source,
                                File photoDirectory,
                                DuplicateStrategy strategy) throws IOException {
        if (photoDirectory == null) throw new IOException("Photo storage is unavailable.");
        if (isZipFile(source)) return processZip(source, photoDirectory, strategy);
        try (FileInputStream input = new FileInputStream(source)) {
            return parseAndSave(readToString(input, MAX_CSV_BYTES, null), photoDirectory, new HashMap<>(), strategy,
                    new HashMap<>(), new ArrayList<>());
        }
    }

    private boolean isZipFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] signature = new byte[2];
            return input.read(signature) == 2 && signature[0] == 'P' && signature[1] == 'K';
        }
    }

    private ImportResult processZip(File source,
                                    File photoDirectory,
                                    DuplicateStrategy strategy) throws IOException {
        if (!photoDirectory.exists() && !photoDirectory.mkdirs()) {
            throw new IOException("Could not create the photo directory.");
        }
        File parent = source.getParentFile() != null ? source.getParentFile() : photoDirectory;
        File staging = new File(parent, "import_photos_" + UUID.randomUUID());
        if (!staging.mkdirs()) throw new IOException("Could not prepare temporary photo storage.");

        String csv = null;
        Map<String, File> stagedPhotos = new HashMap<>();
        Map<String, String> resolvedPhotoPaths = new HashMap<>();
        List<File> importedPhotos = new ArrayList<>();
        ExtractionBudget extractionBudget = new ExtractionBudget();
        try {
            try (ZipInputStream zip = new ZipInputStream(new FileInputStream(source))) {
                ZipEntry entry;
                int entryCount = 0;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entryCount > MAX_ARCHIVE_ENTRIES) {
                        throw new IOException("ZIP archive contains too many entries.");
                    }
                    String name = entry.getName();
                    if (name.endsWith(".csv") && !name.startsWith("photos/")) {
                        csv = readToString(zip, MAX_CSV_BYTES, extractionBudget);
                    } else if (name.startsWith("photos/") && !entry.isDirectory()) {
                        String safeName = new File(name).getName();
                        if (!safeName.isEmpty()) {
                            File staged = new File(staging, safeName);
                            try (FileOutputStream output = new FileOutputStream(staged)) {
                                copyLimited(zip, output, MAX_PHOTO_BYTES, extractionBudget);
                            }
                            stagedPhotos.put(safeName, staged);
                        }
                    }
                    zip.closeEntry();
                }
            }
            if (csv == null) throw new IOException("ZIP archive is missing a data.csv file.");
            return parseAndSave(csv, photoDirectory, stagedPhotos, strategy,
                    resolvedPhotoPaths, importedPhotos);
        } finally {
            for (File imported : importedPhotos) {
                try {
                    if (store.getPhotoReferenceCount(imported.getAbsolutePath()) == 0) imported.delete();
                } catch (RuntimeException ignored) {
                    // Cleanup must not hide the import result or its original failure.
                }
            }
            deleteRecursively(staging);
        }
    }

    private ImportResult parseAndSave(String csv,
                                      File photoDirectory,
                                      Map<String, File> stagedPhotos,
                                      DuplicateStrategy strategy,
                                      Map<String, String> resolvedPhotoPaths,
                                      List<File> importedPhotos) throws IOException {
        ImportResult result = new ImportResult();
        if (csv.startsWith("\uFEFF")) csv = csv.substring(1);
        char delimiter = detectDelimiter(firstCsvRecord(csv));
        List<List<String>> rows = parseCsvRecords(csv, delimiter);
        if (rows.size() < 2) throw new IOException("The CSV file contains no data rows.");

        List<String> headers = rows.get(0);
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            columns.put(headers.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        if (!columns.containsKey("decimallatitude")
                || !columns.containsKey("decimallongitude")
                || !columns.containsKey("eventdate")) {
            throw new IOException("Unrecognized CSV format: missing decimalLatitude, decimalLongitude or eventDate columns.");
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        dateFormat.setLenient(false);
        Map<String, Long> fingerprints = loadFingerprints();
        List<String> replacedPaths = new ArrayList<>();
        for (int row = 1; row < rows.size(); row++) {
            List<String> csvRow = rows.get(row);
            if (csvRow.size() == 1 && csvRow.get(0).trim().isEmpty()) continue;
            try {
                String[] parts = csvRow.toArray(new String[0]);
                double latitude = getRequiredDouble(parts, columns,
                        "decimalLatitude", -90, 90);
                double longitude = getRequiredDouble(parts, columns,
                        "decimalLongitude", -180, 180);
                String localTime = getString(parts, columns, "eventDate", "");
                if (localTime.isEmpty()) {
                    throw new IllegalArgumentException("eventDate is required");
                }
                long exportedId = getOptionalLong(parts, columns, "id", 0);

                long existingId = 0;
                if (exportedId != 0 && store.existsById(exportedId)) {
                    existingId = exportedId;
                } else {
                    Long fingerprintId = fingerprints.get(
                            fingerprintKey(latitude, longitude, localTime));
                    if (fingerprintId != null) existingId = fingerprintId;
                }
                if (existingId != 0 && strategy == DuplicateStrategy.SKIP) {
                    result.skipped++;
                    continue;
                }

                boolean replace = existingId != 0 && strategy == DuplicateStrategy.REPLACE;
                ObservationRecord record = new ObservationRecord();
                if (replace) record.id = existingId;
                else if (strategy != DuplicateStrategy.KEEP_BOTH) record.id = exportedId;
                record.latitude = latitude;
                record.longitude = longitude;
                record.accuracy = (float) getOptionalDouble(parts, columns,
                        "coordinateUncertaintyInMeters", 0, 0, Double.MAX_VALUE);
                populateAltitude(record, getString(parts, columns, "verbatimElevation", ""));
                record.localTime = localTime;
                record.note = getString(parts, columns, "occurrenceRemarks", "");
                record.countryCode = getString(parts, columns, "countryCode", "");
                record.country = getString(parts, columns, "country", "");
                record.province = getString(parts, columns, "province", "");
                record.district = getString(parts, columns, "district", "");
                record.locality = getString(parts, columns, "locality", "");
                try {
                    Date parsed = dateFormat.parse(localTime);
                    record.timestamp = parsed != null ? parsed.getTime() : currentTimeMillis.getAsLong();
                } catch (Exception ignored) {
                    record.timestamp = currentTimeMillis.getAsLong();
                }
                record.attributes = parseAttributes(parts, columns);
                Integer importedSpecimenNumber = importedSpecimenNumber(record);

                List<String> photoPaths = new ArrayList<>();
                String photoNames = getString(parts, columns, "photos", "");
                if (!photoNames.isEmpty()) {
                    for (String photoName : photoNames.split("\\|")) {
                        String path = materializePhoto(photoName.trim(), photoDirectory, stagedPhotos,
                                resolvedPhotoPaths, importedPhotos);
                        if (path != null) photoPaths.add(path);
                    }
                }

                // The DAO implementations make each record-and-photos operation atomic.
                // Keeping rows in separate transactions lets a bad row fail without
                // poisoning or rolling back successful rows from the same import.
                if (replace) {
                    replacedPaths.addAll(
                            store.replaceImportedLocationWithPhotos(existingId, record, photoPaths,
                                    importedSpecimenNumber));
                    result.updated++;
                } else {
                    store.insertImportedLocationWithPhotos(record, photoPaths,
                            importedSpecimenNumber);
                    result.added++;
                }
                // Later rows in the same file must see this record as existing.
                fingerprints.put(fingerprintKey(record.latitude, record.longitude,
                        record.localTime), record.id);
            } catch (Exception exception) {
                result.failed++;
                if (result.errors.size() < ImportResult.MAX_REPORTED_ERRORS) {
                    result.errors.add("Row " + row + ": " + exception.getMessage());
                }
            }
        }
        // Replaced photo files are removed only after the transaction has
        // committed, so a rollback can never leave records pointing at
        // already-deleted files.
        for (String oldPath : replacedPaths) {
            try {
                if (store.getPhotoReferenceCount(oldPath) == 0) new File(oldPath).delete();
            } catch (RuntimeException ignored) {
                // Database changes have already committed; cleanup failure must not
                // turn a successful import into a misleading overall error.
            }
        }
        return result;
    }

    private Map<String, Long> loadFingerprints() {
        Map<String, Long> fingerprints = new HashMap<>();
        for (RecordFingerprint fingerprint : store.loadFingerprints()) {
            fingerprints.put(fingerprintKey(fingerprint.latitude, fingerprint.longitude,
                    fingerprint.localTime), fingerprint.id);
        }
        return fingerprints;
    }

    // Coordinates are matched rounded to 6 decimals because the CSV export
    // writes %.6f, so re-imported values never exactly match the stored doubles.
    private static String fingerprintKey(double latitude, double longitude, String localTime) {
        return String.format(Locale.US, "%.6f|%.6f|%s", latitude, longitude, localTime);
    }

    private static void populateAltitude(ObservationRecord record, String text) {
        try {
            if (!text.trim().isEmpty()) {
                record.altitude = Double.parseDouble(text.trim());
                // The exporter writes an elevation (including "0") only for known
                // altitudes, so any parsed value is a real measurement — a real
                // 0 m reading must survive an export/import round trip.
                record.hasAltitude = true;
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("verbatimElevation is not a valid number", exception);
        }
        if (!Double.isFinite(record.altitude)) {
            throw new IllegalArgumentException("verbatimElevation must be finite");
        }
    }

    private static SpeciesAttributes parseAttributes(String[] parts, Map<String, Integer> columns) {
        SpeciesAttributes attributes = new SpeciesAttributes();
        attributes.taxonName = getString(parts, columns, "taxonName", "");
        attributes.substrate = getString(parts, columns, "Substrate", "");
        attributes.habitat = getString(parts, columns, "Habitat", "");
        attributes.collector = getString(parts, columns, "recordedBy", "");
        attributes.lifeStage = getString(parts, columns, "lifeStage", "");
        attributes.sex = getString(parts, columns, "sex", "");
        attributes.activity = getString(parts, columns, "activity", "");
        attributes.samplingProtocol = getString(parts, columns, "samplingProtocol", "");
        attributes.specimenNr = getString(parts, columns, "SpecimenNr", "");
        attributes.isSpecimen = "true".equalsIgnoreCase(
                getString(parts, columns, "isSpecimen", "false"));
        String quantity = getString(parts, columns, "organismQuantity", "").trim();
        try {
            if (!quantity.isEmpty()) attributes.organismQuantity = Integer.parseInt(quantity);
        } catch (NumberFormatException ignored) {
            // Keep quantity absent while retaining the rest of the row.
        }
        return attributes;
    }

    private static String materializePhoto(String requestedName,
                                           File photoDirectory,
                                           Map<String, File> stagedPhotos,
                                           Map<String, String> resolvedPhotoPaths,
                                           List<File> importedPhotos) throws IOException {
        if (requestedName.isEmpty()) return null;
        String safeName = new File(requestedName).getName();
        File staged = stagedPhotos.get(safeName);
        if (staged == null || !staged.exists()) {
            File existing = new File(photoDirectory, safeName);
            return existing.exists() ? existing.getAbsolutePath() : null;
        }
        String resolved = resolvedPhotoPaths.get(safeName);
        if (resolved != null && new File(resolved).exists()) return resolved;

        File destination = createAvailablePhotoFile(photoDirectory, safeName);
        importedPhotos.add(destination);
        try (FileInputStream input = new FileInputStream(staged);
             FileOutputStream output = new FileOutputStream(destination, false)) {
            FileUtils.copy(input, output);
        }
        resolved = destination.getAbsolutePath();
        resolvedPhotoPaths.put(safeName, resolved);
        return resolved;
    }

    private static File createAvailablePhotoFile(File directory, String requestedName) throws IOException {
        String base = requestedName;
        String extension = "";
        int dot = requestedName.lastIndexOf('.');
        if (dot > 0) {
            base = requestedName.substring(0, dot);
            extension = requestedName.substring(dot);
        }
        File candidate = new File(directory, requestedName);
        int suffix = 1;
        while (!candidate.createNewFile()) {
            candidate = new File(directory, base + "_imported_" + suffix++ + extension);
        }
        return candidate;
    }

    private static String getString(String[] parts,
                                    Map<String, Integer> columns,
                                    String key,
                                    String fallback) {
        Integer index = columns.get(key.toLowerCase(Locale.ROOT));
        if (index == null || index >= parts.length) return fallback;
        return parts[index];
    }

    private static double getRequiredDouble(String[] parts,
                                            Map<String, Integer> columns,
                                            String key,
                                            double minimum,
                                            double maximum) {
        String value = getString(parts, columns, key, "").trim();
        if (value.isEmpty()) throw new IllegalArgumentException(key + " is required");
        return parseBoundedDouble(value, key, minimum, maximum);
    }

    private static double getOptionalDouble(String[] parts,
                                            Map<String, Integer> columns,
                                            String key,
                                            double fallback,
                                            double minimum,
                                            double maximum) {
        String value = getString(parts, columns, key, "").trim();
        return value.isEmpty() ? fallback : parseBoundedDouble(value, key, minimum, maximum);
    }

    private static double parseBoundedDouble(String value,
                                             String key,
                                             double minimum,
                                             double maximum) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed < minimum || parsed > maximum) {
                throw new IllegalArgumentException(key + " is outside its supported range");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " is not a valid number", exception);
        }
    }

    private static long getOptionalLong(String[] parts,
                                        Map<String, Integer> columns,
                                        String key,
                                        long fallback) {
        String value = getString(parts, columns, key, "").trim();
        if (value.isEmpty()) return fallback;
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) throw new IllegalArgumentException(key + " must not be negative");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " is not a valid whole number", exception);
        }
    }

    private static Integer importedSpecimenNumber(ObservationRecord record) {
        if (record.attributes == null || !record.attributes.isSpecimen
                || record.attributes.specimenNr == null) return null;
        try {
            int number = Integer.parseInt(record.attributes.specimenNr.trim());
            return number >= 0 ? number : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String firstCsvRecord(String csv) throws IOException {
        boolean inQuotes = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < csv.length() && csv.charAt(i + 1) == '\"') i++;
                else inQuotes = !inQuotes;
            } else if (!inQuotes && (c == '\n' || c == '\r')) {
                return csv.substring(0, i);
            }
        }
        if (inQuotes) throw new IOException("CSV has an unterminated quoted field.");
        return csv;
    }

    private static char detectDelimiter(String header) {
        int commas = 0;
        int semicolons = 0;
        boolean inQuotes = false;
        for (int i = 0; i < header.length(); i++) {
            char c = header.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < header.length() && header.charAt(i + 1) == '\"') i++;
                else inQuotes = !inQuotes;
            } else if (!inQuotes && c == ',') commas++;
            else if (!inQuotes && c == ';') semicolons++;
        }
        return semicolons > commas ? ';' : ',';
    }

    private static List<List<String>> parseCsvRecords(String csv, char delimiter) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < csv.length() && csv.charAt(i + 1) == '\"') {
                    field.append('\"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (!inQuotes && c == delimiter) {
                row.add(field.toString());
                field.setLength(0);
            } else if (!inQuotes && (c == '\n' || c == '\r')) {
                row.add(field.toString());
                rows.add(row);
                row = new ArrayList<>();
                field.setLength(0);
                if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') i++;
            } else {
                field.append(c == '\r' ? '\n' : c);
            }
        }
        if (inQuotes) throw new IOException("CSV has an unterminated quoted field.");
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static String readToString(InputStream input, long maxBytes,
                                       ExtractionBudget budget) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copyLimited(input, output, maxBytes, budget);
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static void copyLimited(InputStream input, java.io.OutputStream output,
                                    long maxBytes, ExtractionBudget budget) throws IOException {
        byte[] buffer = new byte[8192];
        long entryBytes = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (entryBytes > maxBytes - read) {
                throw new IOException("ZIP entry exceeds the supported size limit.");
            }
            if (budget != null) budget.add(read);
            output.write(buffer, 0, read);
            entryBytes += read;
        }
    }

    private static final class ExtractionBudget {
        private long extractedBytes;

        void add(int bytes) throws IOException {
            if (extractedBytes > MAX_ARCHIVE_EXTRACTED_BYTES - bytes) {
                throw new IOException("ZIP archive exceeds the supported extracted size.");
            }
            extractedBytes += bytes;
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
