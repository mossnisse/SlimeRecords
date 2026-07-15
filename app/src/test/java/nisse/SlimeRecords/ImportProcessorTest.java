package nisse.SlimeRecords;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import nisse.SlimeRecords.data.ImportRecordStore;
import nisse.SlimeRecords.data.ObservationRecord;
import nisse.SlimeRecords.data.PhotoRecord;
import nisse.SlimeRecords.data.RecordFingerprint;
import nisse.SlimeRecords.data.SpeciesAttributes;

public class ImportProcessorTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void importsBomSemicolonAndCaseInsensitiveHeaders() throws Exception {
        FakeStore store = new FakeStore();
        File csv = write("data.csv", "\uFEFFID;DECIMALLATITUDE;decimalLongitude;EVENTDATE;verbatimElevation;organismQuantity;taxonName\n"
                + "9;59.1;18.2;2026-07-14 12:30:00;0;bad;Linnaea borealis\n");
        ImportResult result = processor(store).process(csv, temporaryFolder.newFolder("photos"),
                ImportProcessor.DuplicateStrategy.SKIP);

        assertEquals(1, result.added);
        assertEquals(0, result.failed);
        ObservationRecord record = store.records.get(0);
        assertEquals(9, record.id);
        assertEquals("Linnaea borealis", record.attributes.taxonName);
        assertEquals(null, record.attributes.organismQuantity);
        // An exported "0" is a real sea-level measurement, not an unknown altitude.
        assertTrue(record.hasAltitude);
        assertEquals(0.0, record.altitude, 0.0);
        assertTrue(record.timestamp > 0);
    }

    @Test
    public void preservesQuotedNewlinesAndForwardsImportedSpecimenNumber() throws Exception {
        FakeStore store = new FakeStore();
        File csv = write("quoted_newline.csv",
                "ID,decimalLatitude,decimalLongitude,eventDate,occurrenceRemarks,isSpecimen,SpecimenNr\n"
                        + "1,59.1,18.2,2026-07-14 12:30:00,\"first line\nsecond line\",true,100\n");

        ImportResult result = processor(store).process(csv, temporaryFolder.newFolder("photos"),
                ImportProcessor.DuplicateStrategy.SKIP);

        assertEquals(1, result.added);
        assertEquals(0, result.failed);
        assertEquals("first line\nsecond line", store.records.get(0).note);
        assertEquals(Collections.singletonList(100), store.importedSpecimenNumbers);
    }

    @Test
    public void appliesAllDuplicateStrategiesByIdAndFingerprint() throws Exception {
        FakeStore store = new FakeStore();
        ObservationRecord existing = new ObservationRecord();
        existing.id = 5;
        existing.latitude = 59.12345649;
        existing.longitude = 18.2;
        existing.localTime = "2026-07-14 12:30:00";
        store.insertLocationWithPhotos(existing, Collections.emptyList());
        // No ID column value: only the rounded coordinate fingerprint can match.
        File byFingerprint = write("duplicates_fingerprint.csv", minimalCsv(
                "0,59.1234564,18.2,2026-07-14 12:30:00,updated"));
        File byId = write("duplicates_id.csv", minimalCsv(
                "5,59.1234564,18.2,2026-07-14 12:30:00,updated"));
        ImportProcessor processor = processor(store);

        ImportResult skipped = processor.process(byFingerprint, temporaryFolder.newFolder("skip"),
                ImportProcessor.DuplicateStrategy.SKIP);
        assertEquals(1, skipped.skipped);
        assertEquals(1, store.records.size());

        ImportResult replaced = processor.process(byId, temporaryFolder.newFolder("replace"),
                ImportProcessor.DuplicateStrategy.REPLACE);
        assertEquals(1, replaced.updated);
        assertEquals("updated", store.records.get(0).locality);

        ImportResult kept = processor.process(byId, temporaryFolder.newFolder("keep"),
                ImportProcessor.DuplicateStrategy.KEEP_BOTH);
        assertEquals(1, kept.added);
        assertEquals(2, store.records.size());
        assertTrue(store.records.get(1).id != 5);
    }

    @Test
    public void fingerprintMatchesExportRoundingForNegativeCoordinates() throws Exception {
        FakeStore store = new FakeStore();
        ObservationRecord existing = new ObservationRecord();
        existing.latitude = -0.0000005;
        existing.longitude = -10.0000005;
        existing.localTime = "2026-07-14 12:30:00";
        store.insertLocationWithPhotos(existing, Collections.emptyList());
        File csv = write("negative_fingerprint.csv", minimalCsv(
                "0,-0.000001,-10.000001,2026-07-14 12:30:00,duplicate"));

        ImportResult result = processor(store).process(csv,
                temporaryFolder.newFolder("negative-fingerprint-photos"),
                ImportProcessor.DuplicateStrategy.SKIP);

        assertEquals(1, result.skipped);
        assertEquals(1, store.records.size());
    }

    @Test
    public void rejectsMalformedAndOutOfRangeRequiredValuesPerRow() throws Exception {
        FakeStore store = new FakeStore();
        File csv = write("malformed_rows.csv", minimalCsv(
                "1,59.1,18.2,2026-07-14 12:30:00,valid")
                + "2,bad,18.2,2026-07-14 12:30:00,bad latitude\n"
                + "3,91,18.2,2026-07-14 12:30:00,outside latitude\n"
                + "4,59.1,Infinity,2026-07-14 12:30:00,infinite longitude\n"
                + "5,59.1,18.2,,missing date\n");

        ImportResult result = processor(store).process(csv,
                temporaryFolder.newFolder("malformed-row-photos"),
                ImportProcessor.DuplicateStrategy.SKIP);

        assertEquals(1, result.added);
        assertEquals(4, result.failed);
        assertEquals(4, result.errors.size());
        assertEquals(1, store.records.size());
        assertEquals("valid", store.records.get(0).locality);
    }

    @Test
    public void databaseFailureDoesNotRollbackOtherRowsOrReportThemAsFailed() throws Exception {
        FakeStore store = new FakeStore();
        store.failLocality = "fail";
        File csv = write("database_failure.csv", minimalCsv(
                "1,59.1,18.1,2026-07-14 10:00:00,first")
                + "2,59.2,18.2,2026-07-14 11:00:00,fail\n"
                + "3,59.3,18.3,2026-07-14 12:00:00,last\n");

        ImportResult result = processor(store).process(csv,
                temporaryFolder.newFolder("database-failure-photos"),
                ImportProcessor.DuplicateStrategy.SKIP);

        assertEquals(2, result.added);
        assertEquals(1, result.failed);
        assertEquals(2, store.records.size());
        assertEquals("first", store.records.get(0).locality);
        assertEquals("last", store.records.get(1).locality);
    }

    @Test
    public void zipImportSanitizesPhotoNamesAndAvoidsCollisions() throws Exception {
        FakeStore store = new FakeStore();
        File photoDirectory = temporaryFolder.newFolder("photo-target");
        Files.write(new File(photoDirectory, "voucher.jpg").toPath(), new byte[] {9});
        File zip = temporaryFolder.newFile("archive.zip");
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("nested/data.csv"));
            output.write((minimalCsv("1,59.1,18.2,2026-07-14 12:30:00,site")
                    .replace("locality\n", "locality,photos\n")
                    .replace("site\n", "site,voucher.jpg\n")).getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("photos/../voucher.jpg"));
            output.write(new byte[] {1, 2, 3});
            output.closeEntry();
        }

        ImportResult result = processor(store).process(zip, photoDirectory,
                ImportProcessor.DuplicateStrategy.SKIP);
        assertEquals(1, result.added);
        String importedPath = store.photoPaths.get(store.records.get(0).id).get(0);
        assertTrue(importedPath.endsWith("voucher_imported_1.jpg"));
        assertTrue(new File(importedPath).exists());
        assertEquals(3, Files.readAllBytes(new File(importedPath).toPath()).length);
    }

    @Test
    public void exportThenImportPreservesCoreRecordFields() throws Exception {
        ObservationRecord source = new ObservationRecord();
        source.id = 77;
        source.latitude = 59.3293;
        source.longitude = 18.0686;
        source.accuracy = 7.1f;
        source.altitude = 0;
        source.hasAltitude = true; // a real 0 m measurement must survive the round trip
        source.localTime = "2026-07-14 12:30:00";
        source.locality = "Park, north";
        source.note = "quoted \"note\"";
        source.attributes = new SpeciesAttributes();
        source.attributes.taxonName = "Linnaea borealis";
        source.attributes.isSpecimen = true;
        source.attributes.specimenNr = "101";
        RecordWithPhotos item = new RecordWithPhotos();
        item.location = source;
        item.photos = Collections.emptyList();
        File archive = temporaryFolder.newFile("roundtrip.zip");
        try (FileOutputStream output = new FileOutputStream(archive)) {
            new ExportArchiveWriter(() -> "fixed").write(output,
                    Collections.singletonList(item), ExportFormat.STANDARD_CSV, "[TECHNICAL_DETAILS]");
        }

        FakeStore store = new FakeStore();
        ImportResult result = processor(store).process(archive, temporaryFolder.newFolder("roundtrip-photos"),
                ImportProcessor.DuplicateStrategy.SKIP);
        assertEquals(1, result.added);
        ObservationRecord restored = store.records.get(0);
        assertEquals(source.id, restored.id);
        assertEquals(source.latitude, restored.latitude, 0.000001);
        assertEquals(source.locality, restored.locality);
        assertEquals(source.note, restored.note);
        assertEquals(source.attributes.taxonName, restored.attributes.taxonName);
        assertTrue(restored.hasAltitude);
        assertEquals(0.0, restored.altitude, 0.0);
    }

    @Test
    public void rejectsUnrecognizedCsvAndZipWithoutData() throws Exception {
        FakeStore store = new FakeStore();
        File csv = write("unknown.csv", "name,value\na,b\n");
        assertThrows(java.io.IOException.class, () -> processor(store).process(csv,
                temporaryFolder.newFolder("unknown-photos"), ImportProcessor.DuplicateStrategy.SKIP));

        File zip = temporaryFolder.newFile("missing.zip");
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("readme.txt"));
            output.write("no data".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        assertThrows(java.io.IOException.class, () -> processor(store).process(zip,
                temporaryFolder.newFolder("missing-photos"), ImportProcessor.DuplicateStrategy.SKIP));
    }

    private ImportProcessor processor(FakeStore store) {
        return new ImportProcessor(store, () -> 123456789L);
    }

    private File write(String name, String content) throws Exception {
        File file = temporaryFolder.newFile(name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String minimalCsv(String row) {
        return "ID,decimalLatitude,decimalLongitude,eventDate,locality\n" + row + "\n";
    }

    private static final class FakeStore implements ImportRecordStore {
        final List<ObservationRecord> records = new ArrayList<>();
        final Map<Long, List<String>> photoPaths = new HashMap<>();
        final List<Integer> importedSpecimenNumbers = new ArrayList<>();
        long nextId = 100;
        String failLocality;

        @Override
        public boolean existsById(long id) {
            return find(id) != null;
        }

        @Override
        public List<RecordFingerprint> loadFingerprints() {
            List<RecordFingerprint> fingerprints = new ArrayList<>();
            for (ObservationRecord record : records) {
                RecordFingerprint fingerprint = new RecordFingerprint();
                fingerprint.id = record.id;
                fingerprint.latitude = record.latitude;
                fingerprint.longitude = record.longitude;
                fingerprint.localTime = record.localTime;
                fingerprints.add(fingerprint);
            }
            return fingerprints;
        }

        @Override
        public void insertLocationWithPhotos(ObservationRecord location, List<String> paths) {
            if (location.locality.equals(failLocality)) {
                throw new IllegalStateException("simulated database failure");
            }
            if (location.id == 0) location.id = nextId++;
            records.add(location);
            photoPaths.put(location.id, new ArrayList<>(paths));
        }

        @Override
        public void insertImportedLocationWithPhotos(ObservationRecord location, List<String> paths,
                                                      Integer importedSpecimenNumber) {
            insertLocationWithPhotos(location, paths);
            if (importedSpecimenNumber != null) importedSpecimenNumbers.add(importedSpecimenNumber);
        }

        @Override
        public List<String> replaceLocationWithPhotos(long existingId,
                                                      ObservationRecord location,
                                                      List<String> paths) {
            ObservationRecord existing = find(existingId);
            records.remove(existing);
            List<String> old = photoPaths.remove(existingId);
            location.id = existingId;
            insertLocationWithPhotos(location, paths);
            return old != null ? old : Collections.emptyList();
        }

        @Override
        public int getPhotoReferenceCount(String path) {
            int count = 0;
            for (List<String> paths : photoPaths.values()) {
                for (String candidate : paths) if (candidate.equals(path)) count++;
            }
            return count;
        }

        private ObservationRecord find(long id) {
            for (ObservationRecord record : records) if (record.id == id) return record;
            return null;
        }
    }
}
