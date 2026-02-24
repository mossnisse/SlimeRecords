package nisse.whatsmysocken;

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
import nisse.whatsmysocken.data.LocationDao;
import nisse.whatsmysocken.data.LocationRecord;
import nisse.whatsmysocken.data.PhotoRecord;
import nisse.whatsmysocken.data.SpeciesAttributes;
import nisse.whatsmysocken.data.UserDatabase;

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
                String zipName = "WhatsMySocken_" + System.currentTimeMillis() + ".zip";

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
                    if (format.contains("Excel") || format.contains("Semicolon")) {
                        writeCsv(zos, allData, ";", true);
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

    public LiveData<List<LocationRecord>> getSpecimenLocations() {
        return locationDao.getSpecimenLocations();
    }

    public LiveData<ExportState> getExportStatus() { return exportStatus; }
    public Uri getLastExportUri() { return lastExportUri; }
}
