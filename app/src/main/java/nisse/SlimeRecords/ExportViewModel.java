package nisse.SlimeRecords;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import nisse.SlimeRecords.coords.CoordSystem;
import nisse.SlimeRecords.data.LocationDao;
import nisse.SlimeRecords.data.ObservationRecord;
import nisse.SlimeRecords.data.PhotoRecord;
import nisse.SlimeRecords.data.SpeciesAttributes;
import nisse.SlimeRecords.data.UserDatabase;
import nisse.SlimeRecords.coords.Coordinates;

public class ExportViewModel extends AndroidViewModel {
    private final LocationDao locationDao;
    public enum ExportState { IDLE, LOADING, SUCCESS, ERROR }
    private final MutableLiveData<ExportState> exportStatus = new MutableLiveData<>(ExportState.IDLE);
    private Uri lastExportUri;

    public ExportViewModel(@NonNull Application application) {
        super(application);
        locationDao = UserDatabase.getInstance(application).locationDao();
    }

    public void startExport(String format) {
        if (exportStatus.getValue() == ExportState.LOADING) return;
        exportStatus.setValue(ExportState.LOADING);

        UserDatabase.getDbExecutor().execute(() -> {
            try {
                Context context = getApplication();
                String zipName = "SlimeRecords_" + System.currentTimeMillis() + ".zip";

                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, zipName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("Failed to create MediaStore entry");

                List<RecordWithPhotos> allData = locationDao.getAllLocationsWithPhotosSync();

                try (OutputStream os = context.getContentResolver().openOutputStream(uri);
                     ZipOutputStream zos = new ZipOutputStream(os)) {

                    // Handle Excel-specific formatting (BOM + Semicolon)
                    // Note: This check matches the string-array items you added earlier
                    if (format.contains("Excel")) {
                        writeCsv(zos, allData, ";", true);
                    } else if (format.contains("Artportalen")) {
                        writeArtportalenCsv(zos, allData);
                    } else {
                        writeCsv(zos, allData, ",", false);
                    }
                    //add metadata readme
                    writeReadme(zos, format);
                    // Write Photos (Resilient Loop)
                    for (RecordWithPhotos item : allData) {
                        if (item.photos != null) {
                            for (PhotoRecord photo : item.photos) {
                                try {
                                    addPhotoToZip(zos, photo);
                                } catch (IOException e) {
                                    Log.e("Export", "Failed to add photo: " + photo.filePath, e);
                                }
                            }
                        }
                    }
                    zos.finish();
                }

                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                context.getContentResolver().update(uri, values, null, null);

                this.lastExportUri = uri;
                exportStatus.postValue(ExportState.SUCCESS);

            } catch (Exception e) {
                Log.e("Export", "Critical Export Failure", e);
                exportStatus.postValue(ExportState.ERROR);
            }
        });
    }

    private void writeCsv(ZipOutputStream zos, List<RecordWithPhotos> allData, String d, boolean includeBom) throws IOException {
        zos.putNextEntry(new ZipEntry("data.csv"));
        if (includeBom) {
            zos.write(new byte[] {(byte)0xEF, (byte)0xBB, (byte)0xBF});
        }

        // Build Header
        String header = String.join(d, "ID", "decimalLatitude", "decimalLongitude", "coordinateUncertaintyInMeters", "verbatimElevation", "geodeticDatum", "verticalDatum",
                "eventDate", "taxonName", "organismQuantity", "lifeStage", "sex", "activity", "samplingProtocol", "Substrate", "Habitat", "recordedBy",
                "countryCode", "country", "province", "district", "locality",
                "isSpecimen", "SpecimenNr", "occurrenceRemarks", "photos") + "\n";

        zos.write(header.getBytes(StandardCharsets.UTF_8));

        // Build Rows
        for (RecordWithPhotos item : allData) {
            try {
                String row = formatLocationAsCsv(item, d) + "\n";
                zos.write(row.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                Log.e("Export", "Error formatting row for ID: " + item.location.id, e);
            }
        }
        zos.closeEntry();
    }

    private String formatLocationAsCsv(RecordWithPhotos item, String d) {
        ObservationRecord r = item.location;
        SpeciesAttributes attr = r.attributes != null ? r.attributes : new SpeciesAttributes();

        // Process Photo Names
        StringBuilder photoBuilder = new StringBuilder();
        if (item.photos != null) {
            for (PhotoRecord p : item.photos) {
                if (p.filePath != null && !p.filePath.isEmpty()) {
                    if (photoBuilder.length() > 0) photoBuilder.append("|");
                    photoBuilder.append(new File(p.filePath).getName());
                }
            }
        }

        // Build the row column by column to match the header exactly
        StringBuilder sb = new StringBuilder();
        sb.append(r.id).append(d);
        sb.append(String.format(Locale.US, "%.6f", r.latitude)).append(d);
        sb.append(String.format(Locale.US, "%.6f", r.longitude)).append(d);
        sb.append((int)Math.ceil(r.accuracy)).append(d);
        sb.append((int)Math.round(r.altitude)).append(d);
        sb.append("WGS84").append(d);
        sb.append("WGS84").append(d);
        sb.append("\"").append(clean(r.localTime)).append("\"").append(d);
        sb.append("\"").append(clean(attr.taxonName)).append("\"").append(d);
        sb.append(attr.organismQuantity != null ? attr.organismQuantity : "").append(d);
        sb.append("\"").append(clean(attr.lifeStage)).append("\"").append(d);
        sb.append("\"").append(clean(attr.sex)).append("\"").append(d);
        sb.append("\"").append(clean(attr.activity)).append("\"").append(d);
        sb.append("\"").append(clean(attr.samplingProtocol)).append("\"").append(d);
        sb.append("\"").append(clean(attr.substrate)).append("\"").append(d);
        sb.append("\"").append(clean(attr.habitat)).append("\"").append(d);
        sb.append("\"").append(clean(attr.collector)).append("\"").append(d);
        sb.append("\"").append(clean(r.countryCode)).append("\"").append(d);
        sb.append("\"").append(clean(r.country)).append("\"").append(d);
        sb.append("\"").append(clean(r.province)).append("\"").append(d);
        sb.append("\"").append(clean(r.district)).append("\"").append(d);
        sb.append("\"").append(clean(r.locality)).append("\"").append(d);
        sb.append(attr.isSpecimen).append(d);
        sb.append("\"").append(clean(attr.specimenNr)).append("\"").append(d);
        sb.append("\"").append(clean(r.note)).append("\"").append(d);

        // The final column: Photos
        sb.append("\"").append(photoBuilder.toString()).append("\"");

        return sb.toString();
    }

    private String clean(String input) {
        return (input == null) ? "" : input.replace("\"", "\"\"").replace("\n", " ");
    }

    private void addPhotoToZip(ZipOutputStream zos, PhotoRecord photo) throws IOException {
        File photoFile = new File(photo.filePath);
        if (!photoFile.exists()) return;

        try {
            zos.putNextEntry(new ZipEntry("photos/" + photoFile.getName()));
            try (FileInputStream fis = new FileInputStream(photoFile)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = fis.read(buffer)) >= 0) {
                    zos.write(buffer, 0, length);
                }
            }
            zos.closeEntry();
        } catch (IOException e) {
            zos.closeEntry();
            throw e;
        }
    }

    private void writeArtportalenCsv(ZipOutputStream zos, List<RecordWithPhotos> allData) throws IOException {
        zos.putNextEntry(new ZipEntry("artportalen_import.csv"));

        // Artportalen works best with BOM and Semicolon
        byte[] bom = new byte[] {(byte)0xEF, (byte)0xBB, (byte)0xBF};
        zos.write(bom);

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

        zos.write((String.join(";", headers) + "\n").getBytes(StandardCharsets.UTF_8));

        for (RecordWithPhotos item : allData) {
            zos.write((formatArtportalenRow(item) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        zos.closeEntry();
    }

    private String formatArtportalenRow(RecordWithPhotos item) {
        ObservationRecord r = item.location;
        SpeciesAttributes a = r.attributes != null ? r.attributes : new SpeciesAttributes();

        // Prepare data (Date/Time/Coordinates)
        String date = (r.localTime != null && r.localTime.length() >= 10) ? r.localTime.substring(0, 10) : "";
        String time = (r.localTime != null && r.localTime.length() >= 16) ? r.localTime.substring(11, 16) : "";

        Coordinates sweref = new Coordinates(r.latitude, r.longitude).toProjected(CoordSystem.SWEREF99TM);
        String ost = String.format(Locale.US, "%.0f", sweref.getEast());
        String nord = String.format(Locale.US, "%.0f", sweref.getNorth());

        // Build the list of columns
        List<String> columns = new ArrayList<String>();

        // Columns 1-10
        columns.add(clean(a.taxonName));                     // 1: Artnamn
        columns.add(a.organismQuantity != null ? String.valueOf(a.organismQuantity) : ""); // 2: Antal
        columns.add("");                                     // 3: Enhet
        columns.add("");                                     // 4: Antal substrat
        columns.add(clean(a.lifeStage));                     // 5: Ålder-Stadium
        columns.add(clean(a.sex));                           // 6: Kön
        columns.add(clean(a.activity));                      // 7: Aktivitet
        columns.add(clean(a.samplingProtocol));              // 8: Metod
        columns.add(truncate(clean(r.locality), 75));   // 9: Lokalnamn
        columns.add(ost);                                    // 10: Ost

        // Columns 11-20
        columns.add(nord);                                   // 11: Nord
        columns.add(mapArtportalenAccuracy(r.accuracy));     // 12: Noggrannhet
        columns.add("");                                     // 13: Diffusion
        columns.add("");                                     // 14: Djup min
        columns.add("");                                     // 15: Djup max
        columns.add(String.valueOf(Math.round(r.altitude))); // 16: Höjd min
        columns.add("");                                     // 17: Höjd max
        columns.add(date);                                   // 18: Startdatum
        columns.add(time);                                   // 19: Starttid
        columns.add("");                                     // 20: Slutdatum

        // Columns 21-38 (Attributes & Comments)
        columns.add("");                                     // 21: Sluttid
        columns.add(truncate(clean(r.note), 1000));     // 22: Publik kommentar
        columns.add("");                                     // 23: Intressant kommentar
        columns.add("");                                     // 24: Privat kommentar
        columns.add("");                                     // 25: Ej återfunnen
        columns.add("");                                     // 26: Dölj fyndet
        columns.add("");                                     // 27: Andrahand
        columns.add("");                                     // 28: Osäker
        columns.add("");                                     // 29: Ospontan
        columns.add("");                                     // 30: Biotop (Choice list)
        columns.add(clean(a.habitat));                       // 31: Biotop-beskrivning
        columns.add("");                                     // 32: Art som substrat
        columns.add("");                                     // 33: Art som substrat besk.
        columns.add("");                                     // 34: Substrat (Choice list)
        columns.add(clean(a.substrate));                     // 35: Substrat-beskrivning
        columns.add("");                                     // 36: Offentlig samling
        columns.add("");                                     // 37: Privat samling
        columns.add(clean(a.specimenNr));                    // 38: Samlings-nummer

        // Fill remaining columns to reach exactly 59
        while (columns.size() < 59) {
            columns.add("");
        }

        // Join with semicolon
        return String.join(";", columns);
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) : text;
    }

    private String mapArtportalenAccuracy(float accuracy) {
        final int[] steps = {1, 5, 10, 25, 50, 75, 100, 125, 150, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000, 2500, 3000, 5000};
        int selected = 5000; // Default max
        for (int step : steps) {
            if (accuracy <= step) {
                selected = step;
                break;
            }
        }
        return selected + " m";
    }

    private void writeReadme(ZipOutputStream zos, String format) throws IOException {
        zos.putNextEntry(new ZipEntry("readme.txt"));

        boolean isExcel = format.contains("Excel");
        boolean isArtportalen = format.contains("Artportalen");

        // Build the Technical Specification block dynamically
        StringBuilder tech = new StringBuilder();
        tech.append("Character Encoding: UTF-8\n");
        tech.append("Byte Order Mark (BOM): ").append((isExcel || isArtportalen) ? "Present" : "Absent").append("\n");
        tech.append("Line Endings: LF (Unix style)\n");
        tech.append("Field Separator: ").append(isExcel || isArtportalen ? "Semicolon (;)" : "Comma (,)").append("\n");
        tech.append("Text Fields Enclosed by: Double Quotes (\")\n");
        tech.append("Escape Character: Double Quote (\"\")\n");

        // Load template and replace placeholder
        String template = loadFromAssets("metadata_template.txt");
        String finalContent = template.replace("[TECHNICAL_DETAILS]", tech.toString());

        // Prepend the export date
        String timestamp = "Export Date: " + java.text.DateFormat.getDateTimeInstance().format(new java.util.Date()) + "\n\n";

        zos.write((timestamp + finalContent).getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String loadFromAssets(String fileName) {
        StringBuilder sb = new StringBuilder();
        try (java.io.InputStream is = getApplication().getAssets().open(fileName);
             java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            Log.e("Export", "Could not load metadata template from assets", e);
            return "Metadata template missing.";
        }
        return sb.toString();
    }

    public LiveData<List<ObservationRecord>> getSpecimenLocations() {
        return locationDao.getSpecimenLocations();
    }

    public LiveData<ExportState> getExportStatus() { return exportStatus; }
    public Uri getLastExportUri() { return lastExportUri; }
}