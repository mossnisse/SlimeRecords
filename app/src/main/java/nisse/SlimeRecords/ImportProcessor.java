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
import nisse.SlimeRecords.data.SpeciesAttributes;

/** Imports SlimeRecords CSV and ZIP files without depending on Android framework classes. */
public final class ImportProcessor {
    public enum DuplicateStrategy { SKIP, REPLACE, KEEP_BOTH }

    private final ImportRecordStore store;
    private final LongSupplier currentTimeMillis;

    public ImportProcessor(ImportRecordStore store) {
        this(store, System::currentTimeMillis);
    }

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
            return parseAndSave(readToString(input), photoDirectory, new HashMap<>(), strategy,
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
        try {
            try (ZipInputStream zip = new ZipInputStream(new FileInputStream(source))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name.endsWith(".csv") && !name.startsWith("photos/")) {
                        csv = readToString(zip);
                    } else if (name.startsWith("photos/") && !entry.isDirectory()) {
                        String safeName = new File(name).getName();
                        if (!safeName.isEmpty()) {
                            File staged = new File(staging, safeName);
                            try (FileOutputStream output = new FileOutputStream(staged)) {
                                copy(zip, output);
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
        String[] lines = csv.split("\\r?\\n");
        if (lines.length < 2) throw new IOException("The CSV file contains no data rows.");

        String delimiter = lines[0].contains(";") ? ";" : ",";
        String regex = delimiter + "(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)";
        String[] headers = lines[0].split(regex, -1);
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            columns.put(cleanQuotes(headers[i]).trim().toLowerCase(Locale.ROOT), i);
        }
        if (!columns.containsKey("decimallatitude")
                || !columns.containsKey("decimallongitude")
                || !columns.containsKey("eventdate")) {
            throw new IOException("Unrecognized CSV format: missing decimalLatitude, decimalLongitude or eventDate columns.");
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        dateFormat.setLenient(false);
        for (int row = 1; row < lines.length; row++) {
            String line = lines[row].trim();
            if (line.isEmpty()) continue;
            try {
                String[] parts = line.split(regex, -1);
                double latitude = getDouble(parts, columns, "decimalLatitude", 0);
                double longitude = getDouble(parts, columns, "decimalLongitude", 0);
                String localTime = getString(parts, columns, "eventDate", "");
                long exportedId = (long) getDouble(parts, columns, "id", 0);

                long existingId = 0;
                if (exportedId != 0 && store.existsById(exportedId)) {
                    existingId = exportedId;
                } else {
                    Long fingerprintId = store.findIdByFingerprint(latitude, longitude, localTime);
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
                record.accuracy = (float) getDouble(parts, columns,
                        "coordinateUncertaintyInMeters", 0);
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

                List<String> photoPaths = new ArrayList<>();
                String photoNames = getString(parts, columns, "photos", "");
                if (!photoNames.isEmpty()) {
                    for (String photoName : photoNames.split("\\|")) {
                        String path = materializePhoto(photoName.trim(), photoDirectory, stagedPhotos,
                                resolvedPhotoPaths, importedPhotos);
                        if (path != null) photoPaths.add(path);
                    }
                }

                if (replace) {
                    List<String> oldPaths = store.replaceLocationWithPhotos(existingId, record, photoPaths);
                    for (String oldPath : oldPaths) {
                        if (store.getPhotoReferenceCount(oldPath) == 0) new File(oldPath).delete();
                    }
                    result.updated++;
                } else {
                    store.insertLocationWithPhotos(record, photoPaths);
                    result.added++;
                }
            } catch (Exception ignored) {
                result.failed++;
            }
        }
        return result;
    }

    private static void populateAltitude(ObservationRecord record, String text) {
        try {
            if (!text.trim().isEmpty()) {
                record.altitude = Double.parseDouble(text.trim());
                record.hasAltitude = record.altitude != 0.0;
            }
        } catch (NumberFormatException ignored) {
            record.altitude = 0;
            record.hasAltitude = false;
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
            copy(input, output);
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
        return cleanQuotes(parts[index]);
    }

    private static double getDouble(String[] parts,
                                    Map<String, Integer> columns,
                                    String key,
                                    double fallback) {
        try {
            String value = getString(parts, columns, key, "");
            return value.isEmpty() ? fallback : Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String cleanQuotes(String input) {
        if (input == null) return "";
        String value = input.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.replace("\"\"", "\"");
    }

    private static String readToString(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static void copy(InputStream input, java.io.OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
