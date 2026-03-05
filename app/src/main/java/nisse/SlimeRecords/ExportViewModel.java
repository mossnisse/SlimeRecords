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
            byte[] bom = new byte[] {(byte)0xEF, (byte)0xBB, (byte)0xBF};
            zos.write(bom);
        }

        // Build Header
        String header = String.join(d, "ID", "Latitude", "Longitude", "Accuracy", "Altitude",
                "Time", "Species", "Substrate", "Habitat", "Collector", "Locality",
                "IsSpecimen", "SpecimenNr", "Note", "Photos") + "\n";

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

        String photoNames = "";
        if (item.photos != null) {
            for (PhotoRecord p : item.photos) {
                photoNames += (photoNames.isEmpty() ? "" : "|") + new File(p.filePath).getName();
            }
        }

        // Using Locale.US to ensure decimal dots even if the column delimiter is a semicolon
        return String.format(Locale.US,
                "%d%s%.6f%s%.6f%s%d%s%d%s\"%s\"%s\"%s\"%s\"%s\"%s\"%s\"%s\"%s\"%s\"%s\"%s%b%s\"%s\"%s\"%s\"%s\"%s\"",
                r.id, d, r.latitude, d, r.longitude, d, (int)Math.ceil(r.accuracy), d, (int)Math.round(r.altitude), d,
                r.localTime, d, clean(attr.species), d, clean(attr.substrate), d, clean(attr.habitat), d,
                clean(attr.collector), d, clean(r.localityDescription), d, attr.isSpecimen, d,
                clean(attr.specimenNr), d, clean(r.note), d, photoNames);
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

        // 1. Split Date and Time (Assuming r.localTime is "YYYY-MM-DD HH:MM:SS")
        String date = "";
        String time = "";
        if (r.localTime != null && r.localTime.length() >= 16) {
            date = r.localTime.substring(0, 10);
            time = r.localTime.substring(11, 16);
        }

        // 2. SWEREF 99 TM Conversion
        // Replace this with your actual conversion call
        Coordinates coord = new Coordinates(r.latitude, r.longitude);
        Coordinates sweref = coord.convertToSweref99TMFromWGS84();
        String ost = String.format(Locale.US, "%.0f", sweref.getNorth());
        String nord = String.format(Locale.US, "%.0f", sweref.getEast());

        // 3. Build the row (matching the 59 columns in the header)
        StringBuilder sb = new StringBuilder();
        sb.append(clean(a.species)).append(";");              // Artnamn
        sb.append(";");                                       // Antal (Empty)
        sb.append(";");                                       // Enhet
        sb.append(";");                                       // Antal substrat
        sb.append(";");                                       // Ålder
        sb.append(";");                                       // Kön
        sb.append(";");                                       // Aktivitet
        sb.append(";");                                       // Metod
        sb.append(truncate(clean(r.localityDescription), 75)).append(";"); // Lokalnamn
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
    public LiveData<List<LocationRecord>> getSpecimenLocations() {
        return locationDao.getSpecimenLocations();
    }

    public LiveData<ExportState> getExportStatus() { return exportStatus; }
    public Uri getLastExportUri() { return lastExportUri; }
}
