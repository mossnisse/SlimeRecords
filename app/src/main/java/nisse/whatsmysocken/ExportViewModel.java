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

    public void startExport() {
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

                    zos.putNextEntry(new ZipEntry("data.csv"));
                    StringBuilder csv = new StringBuilder("ID,Latitude,Longitude,Accuracy,Altitude,Time,Species,Substrate,Habitat,Collector,Locality,IsSpecimen,SpecimenNr,Note,Photos\n");
                    for (LocationWithPhotos item : allData) csv.append(formatLocationAsCsv(item)).append("\n");
                    zos.write(csv.toString().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();

                    for (LocationWithPhotos item : allData) {
                        for (PhotoRecord photo : item.photos) addPhotoToZip(zos, photo);
                    }
                    zos.finish();
                }

                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                context.getContentResolver().update(uri, values, null, null);

                this.lastExportUri = uri;
                exportStatus.postValue(ExportState.SUCCESS);
            } catch (Exception e) {
                Log.e("Export", "Zip Failed", e);
                exportStatus.postValue(ExportState.ERROR);
            }
        });
    }

    private String formatLocationAsCsv(LocationWithPhotos item) {
        LocationRecord r = item.location;
        SpeciesAttributes attr = r.attributes != null ? r.attributes : new SpeciesAttributes();
        String photoNames = "";
        if (item.photos != null) {
            for (PhotoRecord p : item.photos) {
                photoNames += (photoNames.isEmpty() ? "" : "|") + new File(p.filePath).getName();
            }
        }
        return String.format(Locale.US, "%d,%.6f,%.6f,%d,%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%b,\"%s\",\"%s\",\"%s\"",
                r.id, r.latitude, r.longitude, (int)Math.ceil(r.accuracy), (int)Math.round(r.altitude), r.localTime,
                clean(attr.species), clean(attr.substrate), clean(attr.habitat), clean(attr.collector),
                clean(attr.localityDescription), attr.isSpecimen, clean(attr.specimenNr), clean(r.note), photoNames);
    }

    private String clean(String input) { return (input == null) ? "" : input.replace("\"", "\"\"").replace("\n", " "); }

    private void addPhotoToZip(ZipOutputStream zos, PhotoRecord photo) throws IOException {
        File photoFile = new File(photo.filePath);
        if (!photoFile.exists()) return;
        try (FileInputStream fis = new FileInputStream(photoFile)) {
            zos.putNextEntry(new ZipEntry("photos/" + photoFile.getName()));
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) >= 0) zos.write(buffer, 0, length);
            zos.closeEntry();
        }
    }

    public LiveData<List<LocationRecord>> getSpecimenLocations() {
        return locationDao.getSpecimenLocations();
    }

    public LiveData<ExportState> getExportStatus() { return exportStatus; }
    public Uri getLastExportUri() { return lastExportUri; }
}
