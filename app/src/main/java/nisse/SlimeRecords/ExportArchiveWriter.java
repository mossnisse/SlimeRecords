package nisse.SlimeRecords;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import nisse.SlimeRecords.coords.CoordSystem;
import nisse.SlimeRecords.coords.Coordinates;
import nisse.SlimeRecords.data.ObservationRecord;
import nisse.SlimeRecords.data.PhotoRecord;
import nisse.SlimeRecords.data.SpeciesAttributes;

/** Writes the portable SlimeRecords archive without depending on Android storage APIs. */
public final class ExportArchiveWriter {
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private final Supplier<String> timestampSupplier;

    ExportArchiveWriter(Supplier<String> timestampSupplier) {
        this.timestampSupplier = timestampSupplier;
    }

    public void write(OutputStream output,
                      List<RecordWithPhotos> records,
                      ExportFormat format,
                      String metadataTemplate) throws IOException {
        Map<String, String> photoArchiveNames = buildPhotoArchiveNames(records);
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            if (format == ExportFormat.ARTPORTALEN) {
                writeArtportalenCsv(zip, records);
            } else {
                writeCsv(zip, records, format == ExportFormat.EXCEL_CSV ? ";" : ",",
                        format == ExportFormat.EXCEL_CSV, photoArchiveNames);
            }
            writeReadme(zip, format, metadataTemplate);
            writePhotos(zip, photoArchiveNames);
            zip.finish();
        }
    }

    private void writeCsv(ZipOutputStream zip,
                          List<RecordWithPhotos> records,
                          String delimiter,
                          boolean includeBom,
                          Map<String, String> photoArchiveNames) throws IOException {
        zip.putNextEntry(new ZipEntry("data.csv"));
        if (includeBom) zip.write(UTF8_BOM);
        String header = String.join(delimiter,
                "ID", "decimalLatitude", "decimalLongitude", "coordinateUncertaintyInMeters",
                "verbatimElevation", "geodeticDatum", "verticalDatum", "eventDate", "taxonName",
                "organismQuantity", "lifeStage", "sex", "activity", "samplingProtocol", "Substrate",
                "Habitat", "recordedBy", "countryCode", "country", "province", "district", "locality",
                "isSpecimen", "SpecimenNr", "occurrenceRemarks", "photos") + "\n";
        zip.write(header.getBytes(StandardCharsets.UTF_8));
        for (RecordWithPhotos record : records) {
            zip.write((formatLocationAsCsv(record, delimiter, photoArchiveNames) + "\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        zip.closeEntry();
    }

    static String formatLocationAsCsv(RecordWithPhotos item, String delimiter) {
        return formatLocationAsCsv(item, delimiter, null);
    }

    private static String formatLocationAsCsv(RecordWithPhotos item,
                                              String delimiter,
                                              Map<String, String> photoArchiveNames) {
        ObservationRecord record = item.location;
        SpeciesAttributes attributes = record.attributes != null
                ? record.attributes : new SpeciesAttributes();

        StringBuilder photos = new StringBuilder();
        if (item.photos != null) {
            for (PhotoRecord photo : item.photos) {
                if (photo.filePath != null && !photo.filePath.isEmpty()) {
                    if (photos.length() > 0) photos.append('|');
                    String archiveName = photoArchiveNames != null
                            ? photoArchiveNames.get(photo.filePath) : null;
                    photos.append(archiveName != null
                            ? archiveName : new File(photo.filePath).getName());
                }
            }
        }

        boolean hasAltitude = record.hasKnownAltitude();
        List<String> columns = new ArrayList<>();
        columns.add(String.valueOf(record.id));
        columns.add(String.format(Locale.US, "%.6f", record.latitude));
        columns.add(String.format(Locale.US, "%.6f", record.longitude));
        columns.add(String.valueOf((int) Math.ceil(record.accuracy)));
        columns.add(hasAltitude ? String.valueOf((int) Math.round(record.altitude)) : "");
        columns.add("WGS84");
        columns.add(hasAltitude ? "WGS84" : "");
        columns.add(quoted(record.localTime));
        columns.add(quoted(attributes.taxonName));
        columns.add(attributes.organismQuantity != null ? String.valueOf(attributes.organismQuantity) : "");
        columns.add(quoted(attributes.lifeStage));
        columns.add(quoted(attributes.sex));
        columns.add(quoted(attributes.activity));
        columns.add(quoted(attributes.samplingProtocol));
        columns.add(quoted(attributes.substrate));
        columns.add(quoted(attributes.habitat));
        columns.add(quoted(attributes.collector));
        columns.add(quoted(record.countryCode));
        columns.add(quoted(record.country));
        columns.add(quoted(record.province));
        columns.add(quoted(record.district));
        columns.add(quoted(record.locality));
        columns.add(String.valueOf(attributes.isSpecimen));
        columns.add(quoted(attributes.specimenNr));
        columns.add(quoted(record.note));
        columns.add(quoted(photos.toString()));
        return String.join(delimiter, columns);
    }

    private void writeArtportalenCsv(ZipOutputStream zip,
                                     List<RecordWithPhotos> records) throws IOException {
        zip.putNextEntry(new ZipEntry("artportalen_import.csv"));
        zip.write(UTF8_BOM);
        String[] headers = {
                "Artnamn", "Antal", "Enhet", "Antal substrat", "Ålder-Stadium", "Kön", "Aktivitet", "Metod",
                "Lokalnamn", "Ost", "Nord", "Noggrannhet", "Diffusion", "Djup min", "Djup max", "Höjd min", "Höjd max",
                "Startdatum", "Starttid", "Slutdatum", "Sluttid", "Publik kommentar", "Intressant kommentar",
                "Privat kommentar", "Ej återfunnen", "Dölj fyndet t.o.m.", "Andrahand", "Osäker artbestämning",
                "Ospontan", "Biotop", "Biotop-beskrivning", "Art som substrat", "Art som substrat beskrivning",
                "Substrat", "Substrat-beskrivning", "Offentlig samling", "Privat samling", "Samlings-nummer",
                "Bestämningsmetod", "Artbestämd av", "Artbestämd av (fritext)", "Bestämningsår", "Beskrivning artbestämning",
                "Bekräftad av", "Bekräftad av (fritext)", "Bekräftelseår", "Länk till BOLD/GenBank",
                "Med-observatör", "Med-observatör", "Med-observatör", "Med-observatör", "Med-observatör",
                "Med-observatör", "Med-observatör", "Med-observatör", "Med-observatör", "Med-observatör",
                "Externid", "Ej funnen"
        };
        zip.write((String.join(";", headers) + "\n").getBytes(StandardCharsets.UTF_8));
        for (RecordWithPhotos record : records) {
            zip.write((formatArtportalenRow(record) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        zip.closeEntry();
    }

    static String formatArtportalenRow(RecordWithPhotos item) {
        ObservationRecord record = item.location;
        SpeciesAttributes attributes = record.attributes != null
                ? record.attributes : new SpeciesAttributes();
        String date = record.localTime.length() >= 10 ? record.localTime.substring(0, 10) : "";
        String time = record.localTime.length() >= 16 ? record.localTime.substring(11, 16) : "";
        Coordinates sweref = new Coordinates(record.latitude, record.longitude)
                .toProjected(CoordSystem.SWEREF99TM);

        List<String> columns = new ArrayList<>();
        columns.add(cleanArtportalen(attributes.taxonName));
        columns.add(attributes.organismQuantity != null ? String.valueOf(attributes.organismQuantity) : "");
        columns.add("");
        columns.add("");
        columns.add(cleanArtportalen(attributes.lifeStage));
        columns.add(cleanArtportalen(attributes.sex));
        columns.add(cleanArtportalen(attributes.activity));
        columns.add(cleanArtportalen(attributes.samplingProtocol));
        columns.add(truncate(cleanArtportalen(record.locality), 75));
        columns.add(String.format(Locale.US, "%.0f", sweref.getEast()));
        columns.add(String.format(Locale.US, "%.0f", sweref.getNorth()));
        columns.add(mapArtportalenAccuracy(record.accuracy));
        columns.add(""); columns.add(""); columns.add("");
        columns.add(record.hasKnownAltitude() ? String.valueOf(Math.round(record.altitude)) : "");
        columns.add("");
        columns.add(date);
        columns.add(time);
        columns.add(""); columns.add("");
        columns.add(truncate(cleanArtportalen(record.note), 1000));
        columns.add(""); columns.add(""); columns.add(""); columns.add(""); columns.add("");
        columns.add(""); columns.add(""); columns.add("");
        columns.add(cleanArtportalen(attributes.habitat));
        columns.add(""); columns.add(""); columns.add("");
        columns.add(cleanArtportalen(attributes.substrate));
        columns.add(""); columns.add("");
        columns.add(cleanArtportalen(attributes.specimenNr));
        while (columns.size() < 59) columns.add("");
        return String.join(";", columns);
    }

    private void writeReadme(ZipOutputStream zip,
                             ExportFormat format,
                             String metadataTemplate) throws IOException {
        zip.putNextEntry(new ZipEntry("readme.txt"));
        boolean semicolon = format != ExportFormat.STANDARD_CSV;
        String technical = "Character Encoding: UTF-8\n"
                + "Byte Order Mark (BOM): " + (semicolon ? "Present" : "Absent") + "\n"
                + "Line Endings: LF (Unix style)\n"
                + "Field Separator: " + (semicolon ? "Semicolon (;)" : "Comma (,)") + "\n"
                + "Text Fields Enclosed by: Double Quotes (\")\n"
                + "Escape Character: Double Quote (\"\")\n";
        String template = metadataTemplate == null ? "Metadata template missing." : metadataTemplate;
        String content = "Export Date: " + timestampSupplier.get() + "\n\n"
                + template.replace("[TECHNICAL_DETAILS]", technical);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static Map<String, String> buildPhotoArchiveNames(List<RecordWithPhotos> records) {
        Map<String, String> archiveNames = new LinkedHashMap<>();
        Set<String> usedNames = new HashSet<>();
        for (RecordWithPhotos record : records) {
            if (record.photos == null) continue;
            for (PhotoRecord photo : record.photos) {
                if (photo.filePath == null || photo.filePath.isEmpty()
                        || archiveNames.containsKey(photo.filePath)) {
                    continue;
                }
                String requestedName = new File(photo.filePath).getName();
                if (requestedName.isEmpty()) continue;
                archiveNames.put(photo.filePath, uniquePhotoName(requestedName, usedNames));
            }
        }
        return archiveNames;
    }

    private static String uniquePhotoName(String requestedName, Set<String> usedNames) {
        if (usedNames.add(requestedName)) return requestedName;
        String base = requestedName;
        String extension = "";
        int dot = requestedName.lastIndexOf('.');
        if (dot > 0) {
            base = requestedName.substring(0, dot);
            extension = requestedName.substring(dot);
        }
        int suffix = 1;
        String candidate;
        do {
            candidate = base + "_exported_" + suffix++ + extension;
        } while (!usedNames.add(candidate));
        return candidate;
    }

    private static void writePhotos(ZipOutputStream zip,
                                    Map<String, String> photoArchiveNames) throws IOException {
        for (Map.Entry<String, String> photo : photoArchiveNames.entrySet()) {
            File file = new File(photo.getKey());
            if (!file.isFile()) {
                throw new IOException("Photo file is missing: " + file.getName());
            }
            zip.putNextEntry(new ZipEntry("photos/" + photo.getValue()));
            try (FileInputStream input = new FileInputStream(file)) {
                FileUtils.copy(input, zip);
            } finally {
                zip.closeEntry();
            }
        }
    }

    private static String quoted(String value) {
        return "\"" + clean(value) + "\"";
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace("\"", "\"\"").replace("\n", " ");
    }

    private static String cleanArtportalen(String value) {
        return clean(value).replace(";", ",");
    }

    private static String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    static String mapArtportalenAccuracy(float accuracy) {
        int[] steps = {1, 5, 10, 25, 50, 75, 100, 125, 150, 200, 250, 300, 400, 500,
                750, 1000, 1500, 2000, 2500, 3000, 5000};
        for (int step : steps) {
            if (accuracy <= step) return step + " m";
        }
        return "5000 m";
    }
}
