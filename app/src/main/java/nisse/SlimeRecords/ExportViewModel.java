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
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import nisse.SlimeRecords.data.LocationDao;
import nisse.SlimeRecords.data.LocationRecord;
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

                List<LocationWithPhotos> allData = locationDao.getAllLocationsWithPhotosSync();

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
                    for (LocationWithPhotos item : allData) {
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

    private void writeCsv(ZipOutputStream zos, List<LocationWithPhotos> allData, String d, boolean includeBom) throws IOException {
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
        for (LocationWithPhotos item : allData) {
            try {
                String row = formatLocationAsCsv(item, d) + "\n";
                zos.write(row.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                Log.e("Export", "Error formatting row for ID: " + item.location.id, e);
            }
        }
        zos.closeEntry();
    }

    private String formatLocationAsCsv(LocationWithPhotos item, String d) {
        LocationRecord r = item.location;
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

    private void writeArtportalenCsv(ZipOutputStream zos, List<LocationWithPhotos> allData) throws IOException {
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

        for (LocationWithPhotos item : allData) {
            zos.write((formatArtportalenRow(item) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        zos.closeEntry();
    }

    private String formatArtportalenRow(LocationWithPhotos item) {
        LocationRecord r = item.location;
        SpeciesAttributes a = r.attributes != null ? r.attributes : new SpeciesAttributes();

        // Split Date and Time (Assuming r.localTime is "YYYY-MM-DD HH:MM:SS")
        String date = "";
        String time = "";
        if (r.localTime != null && r.localTime.length() >= 10) {
            date = r.localTime.substring(0, 10);
            if (r.localTime.length() >= 16) time = r.localTime.substring(11, 16);
        }

        // SWEREF 99 TM Conversion
        Coordinates coord = new Coordinates(r.latitude, r.longitude);
        Coordinates sweref = coord.convertToSweref99TMFromWGS84();
        String ost = String.format(Locale.US, "%.0f", sweref.getEast());
        String nord = String.format(Locale.US, "%.0f", sweref.getNorth());

        // Build the row (matching the 59 columns in the header)
        StringBuilder sb = new StringBuilder();
        sb.append(clean(a.taxonName)).append(";");              // Artnamn
        sb.append(a.organismQuantity != null ? a.organismQuantity : "").append(";"); // Antal
        sb.append(";");                                       // Enhet
        sb.append(";");                                       // Antal substrat
        sb.append(clean(a.lifeStage)).append(";");           // Ålder-Stadium
        sb.append(clean(a.sex)).append(";");               // Kön
        sb.append(clean(a.activity)).append(";");             // Aktivitet
        sb.append(clean(a.samplingProtocol)).append(";");               // Metod
        sb.append(truncate(clean(r.locality), 75)).append(";"); // Lokalnamn
        sb.append(ost).append(";");                           // Ost
        sb.append(nord).append(";");                          // Nord
        sb.append(mapArtportalenAccuracy(r.accuracy)).append(";"); // Noggrannhet
        sb.append(";");                                       // Diffusion
        sb.append(";");                                       // Djup min
        sb.append(";");                                       // Djup max
        sb.append(Math.round(r.altitude)).append(";");        // Höjd min
        sb.append(";");                                       // Höjd max
        sb.append(date).append(";");                          // Startdatum
        sb.append(time).append(";");                          // Starttid
        sb.append(";");                                       // Slutdatum
        sb.append(";");                                       // Sluttid
        sb.append(truncate(clean(r.note), 1000)).append(";"); // Publik kommentar
        sb.append(";");                                       // Intressant kommentar
        sb.append(";");                                       // Privat kommentar
        sb.append(";");                                       // Ej återfunnen
        sb.append(";");                                       // Dölj fyndet
        sb.append(";");                                       // Andrahand
        sb.append(";");                                       // Osäker
        sb.append(";");                                       // Ospontan
        sb.append(";");                                       // Biotop (Choice list)
        sb.append(clean(a.habitat)).append(";");              // Biotop-beskrivning
        sb.append(";");                                       // Art som substrat
        sb.append(";");                                       // Art som substrat besk.
        sb.append(";");                                       // Substrat (Choice list)
        sb.append(clean(a.substrate)).append(";");            // Substrat-beskrivning
        sb.append(";");                                       // Offentlig samling
        sb.append(";");                                       // Privat samling
        sb.append(clean(a.specimenNr)).append(";");           // Samlings-nummer

        // Fill remaining 21 empty columns for Med-observatör, Externid, etc.
        for(int i=0; i<21; i++) sb.append(";");

        return sb.toString();
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) : text;
    }

    private String mapArtportalenAccuracy(float accuracy) {
        int[] steps = {1, 5, 10, 25, 50, 75, 100, 125, 150, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000, 2500, 3000, 5000};
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

    public LiveData<List<LocationRecord>> getSpecimenLocations() {
        return locationDao.getSpecimenLocations();
    }

    public LiveData<ExportState> getExportStatus() { return exportStatus; }
    public Uri getLastExportUri() { return lastExportUri; }
}
