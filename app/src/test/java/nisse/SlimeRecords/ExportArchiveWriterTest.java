package nisse.SlimeRecords;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import nisse.SlimeRecords.data.ObservationRecord;
import nisse.SlimeRecords.data.PhotoRecord;
import nisse.SlimeRecords.data.SpeciesAttributes;

public class ExportArchiveWriterTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void standardArchiveContainsQuotedCsvMetadataAndPhotos() throws Exception {
        File photo = temporaryFolder.newFile("voucher.jpg");
        try (FileOutputStream output = new FileOutputStream(photo)) {
            output.write(new byte[] {1, 2, 3});
        }
        RecordWithPhotos item = record();
        item.photos = Collections.singletonList(new PhotoRecord(item.location.id, photo.getAbsolutePath()));

        Map<String, byte[]> entries = writeAndRead(ExportFormat.STANDARD_CSV, item);
        String csv = new String(entries.get("data.csv"), StandardCharsets.UTF_8);
        assertFalse(startsWithBom(entries.get("data.csv")));
        assertTrue(csv.startsWith("ID,decimalLatitude"));
        assertTrue(csv.contains("\"A \"\"quoted\"\" note with a line break\""));
        assertTrue(csv.contains("\"voucher.jpg\""));
        assertArrayEquals(new byte[] {1, 2, 3}, entries.get("photos/voucher.jpg"));
        String readme = new String(entries.get("readme.txt"), StandardCharsets.UTF_8);
        assertTrue(readme.contains("Export Date: fixed-time"));
        assertTrue(readme.contains("Field Separator: Comma (,)"));
    }

    @Test
    public void excelArchiveUsesUtf8BomAndSemicolon() throws Exception {
        Map<String, byte[]> entries = writeAndRead(ExportFormat.EXCEL_CSV, record());
        byte[] csv = entries.get("data.csv");
        assertTrue(startsWithBom(csv));
        String text = new String(csv, StandardCharsets.UTF_8);
        assertTrue(text.contains("ID;decimalLatitude;decimalLongitude"));
        assertTrue(new String(entries.get("readme.txt"), StandardCharsets.UTF_8)
                .contains("Field Separator: Semicolon (;)"));
    }

    @Test
    public void artportalenArchiveHasExactColumnCountAndSanitizedText() throws Exception {
        RecordWithPhotos item = record();
        item.location.locality = String.join("", Collections.nCopies(80, "x"));
        item.location.note = "public;comment";
        item.location.accuracy = 26;
        item.location.attributes.habitat = "forest;edge";

        Map<String, byte[]> entries = writeAndRead(ExportFormat.ARTPORTALEN, item);
        byte[] csv = entries.get("artportalen_import.csv");
        assertTrue(startsWithBom(csv));
        String[] lines = new String(csv, StandardCharsets.UTF_8).substring(1).split("\n");
        String[] columns = lines[1].split(";", -1);
        assertEquals(59, columns.length);
        assertEquals(75, columns[8].length());
        assertEquals("50 m", columns[11]);
        assertEquals("public,comment", columns[21]);
        assertEquals("forest,edge", columns[30]);
    }

    @Test
    public void csvAltitudeDistinguishesUnknownFromRealSeaLevel() {
        RecordWithPhotos item = record();
        item.location.altitude = 0;
        item.location.hasAltitude = false;
        String unknown = ExportArchiveWriter.formatLocationAsCsv(item, ",");
        item.location.hasAltitude = true;
        String known = ExportArchiveWriter.formatLocationAsCsv(item, ",");
        assertTrue(unknown.contains(",,WGS84,,"));
        assertTrue(known.contains(",0,WGS84,WGS84,"));
    }

    @Test
    public void archiveRenamesDifferentPhotosWithTheSameBasename() throws Exception {
        File firstDirectory = temporaryFolder.newFolder("first");
        File secondDirectory = temporaryFolder.newFolder("second");
        File firstPhoto = new File(firstDirectory, "voucher.jpg");
        File secondPhoto = new File(secondDirectory, "voucher.jpg");
        try (FileOutputStream output = new FileOutputStream(firstPhoto)) {
            output.write(new byte[] {1});
        }
        try (FileOutputStream output = new FileOutputStream(secondPhoto)) {
            output.write(new byte[] {2});
        }
        RecordWithPhotos first = record();
        first.location.id = 1;
        first.photos = Collections.singletonList(new PhotoRecord(1, firstPhoto.getAbsolutePath()));
        RecordWithPhotos second = record();
        second.location.id = 2;
        second.photos = Collections.singletonList(new PhotoRecord(2, secondPhoto.getAbsolutePath()));

        Map<String, byte[]> entries = writeAndRead(ExportFormat.STANDARD_CSV, first, second);

        assertArrayEquals(new byte[] {1}, entries.get("photos/voucher.jpg"));
        assertArrayEquals(new byte[] {2}, entries.get("photos/voucher_exported_1.jpg"));
        String csv = new String(entries.get("data.csv"), StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"voucher.jpg\""));
        assertTrue(csv.contains("\"voucher_exported_1.jpg\""));
    }

    @Test
    public void missingPhotoFailsInsteadOfProducingIncompleteArchive() {
        RecordWithPhotos item = record();
        item.photos = Collections.singletonList(new PhotoRecord(item.location.id,
                new File(temporaryFolder.getRoot(), "missing.jpg").getAbsolutePath()));

        assertThrows(IOException.class,
                () -> writeAndRead(ExportFormat.STANDARD_CSV, item));
    }

    private Map<String, byte[]> writeAndRead(ExportFormat format,
                                             RecordWithPhotos... records) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new ExportArchiveWriter(() -> "fixed-time").write(output, Arrays.asList(records), format,
                "details:\n[TECHNICAL_DETAILS]");
        Map<String, byte[]> result = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int read;
                while ((read = zip.read(buffer)) != -1) content.write(buffer, 0, read);
                result.put(entry.getName(), content.toByteArray());
            }
        }
        return result;
    }

    private static boolean startsWithBom(byte[] bytes) {
        return bytes.length >= 3 && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF;
    }

    private static RecordWithPhotos record() {
        ObservationRecord record = new ObservationRecord();
        record.id = 12;
        record.latitude = 59.3293;
        record.longitude = 18.0686;
        record.accuracy = 4.2f;
        record.altitude = 15;
        record.hasAltitude = true;
        record.localTime = "2026-07-14 12:30:00";
        record.note = "A \"quoted\" note\nwith a line break";
        record.countryCode = "SE";
        record.country = "Sweden";
        record.province = "Uppland";
        record.district = "Uppsala";
        record.locality = "City park";
        record.attributes = new SpeciesAttributes();
        record.attributes.taxonName = "Lycopodium clavatum";
        record.attributes.organismQuantity = 2;
        record.attributes.habitat = "forest";
        record.attributes.substrate = "soil";
        record.attributes.collector = "Collector";
        record.attributes.isSpecimen = true;
        record.attributes.specimenNr = "42";
        RecordWithPhotos item = new RecordWithPhotos();
        item.location = record;
        item.photos = Collections.emptyList();
        return item;
    }
}
