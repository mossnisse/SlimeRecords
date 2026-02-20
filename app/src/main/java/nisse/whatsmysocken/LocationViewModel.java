package nisse.whatsmysocken;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.PagingLiveData;
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
import nisse.whatsmysocken.coords.Coordinates;
import nisse.whatsmysocken.data.LocationDao;
import nisse.whatsmysocken.data.LocationRecord;
import nisse.whatsmysocken.data.PhotoRecord;
import nisse.whatsmysocken.data.RecentCollector;
import nisse.whatsmysocken.data.SpatialResolver;
import nisse.whatsmysocken.data.SpeciesAttributes;
import nisse.whatsmysocken.data.UserDatabase;

public class LocationViewModel extends AndroidViewModel {
    private final LocationDao locationDao;

    // --- State: GPS & Search ---
    private final MutableLiveData<Location> currentBestLocation = new MutableLiveData<>();
    private final MutableLiveData<Boolean> userWantsSearching = new MutableLiveData<>(false);

    // --- State: UI Feedbacks ---
    private final MutableLiveData<Boolean> saveOperationFinished = new MutableLiveData<>(false);
    private final MutableLiveData<String> provinceResult = new MutableLiveData<>();
    private final MutableLiveData<String> districtResult = new MutableLiveData<>();

    // --- State: Paging History ---
    public final LiveData<PagingData<LocationWithPhotos>> historyLiveData;

    // --- State: Export Management ---
    public enum ExportState { IDLE, LOADING, SUCCESS, ERROR }
    private final MutableLiveData<ExportState> exportStatus = new MutableLiveData<>(ExportState.IDLE);
    private Uri lastExportUri;

    public LocationViewModel(@NonNull Application application) {
        super(application);
        locationDao = UserDatabase.getInstance(application).locationDao();

        // 1. Setup Paging with LiveData
        Pager<Integer, LocationWithPhotos> pager = new Pager<>(
                new PagingConfig(20, 5, false),
                locationDao::getAllLocationsPaged
        );
        historyLiveData = PagingLiveData.getLiveData(pager);
    }

    // --- GPS Logic ---
    public void setCurrentBestLocation(Location location) {
        if (location != null) currentBestLocation.postValue(location);
    }
    public Location getCurrentBestLocation() { return currentBestLocation.getValue(); }
    public LiveData<Boolean> getUserWantsSearching() { return userWantsSearching; }
    public void setUserWantsSearching(boolean value) { userWantsSearching.setValue(value); }

    // --- Database Operations (Using Executor) ---
    public void saveLocationWithPhotos(LocationRecord record, List<String> photoPaths) {
        UserDatabase.getDbExecutor().execute(() -> {
            locationDao.insertLocationWithPhotos(record, photoPaths);
            saveOperationFinished.postValue(true);
        });
    }

    public void updateLocation(LocationRecord record) {
        UserDatabase.getDbExecutor().execute(() -> {
            locationDao.updateLocation(record);
            saveOperationFinished.postValue(true);
        });
    }

    public void deleteLocationWithPhotos(LocationWithPhotos item) {
        UserDatabase.getDbExecutor().execute(() -> {
            if (item.photos != null) {
                for (PhotoRecord p : item.photos) FileUtils.deleteFileAtPath(p.filePath);
            }
            locationDao.deleteLocation(item.location);
        });
    }

    // --- Spatial/Region Resolving ---
    public void fetchProvinceName(double lat, double lon) {
        UserDatabase.getDbExecutor().execute(() -> {
            int[] coords = convertToSweref(lat, lon);
            String name = SpatialResolver.getInstance(getApplication()).getRegionName(coords[0], coords[1], false);
            provinceResult.postValue(name);
        });
    }

    public void fetchDistrictName(double lat, double lon) {
        UserDatabase.getDbExecutor().execute(() -> {
            int[] coords = convertToSweref(lat, lon);
            String name = SpatialResolver.getInstance(getApplication()).getRegionName(coords[0], coords[1], true);
            districtResult.postValue(name);
        });
    }

    private int[] convertToSweref(double lat, double lon) {
        Coordinates sweref = new Coordinates(lat, lon).convertToSweref99TMFromWGS84();
        return new int[]{(int)Math.round(sweref.getNorth()), (int)Math.round(sweref.getEast())};
    }

    // --- Export Logic (The Big Cleanup) ---

    public void startExport() {
        if (exportStatus.getValue() == ExportState.LOADING) return;

        exportStatus.setValue(ExportState.LOADING);

        UserDatabase.getDbExecutor().execute(() -> {
            try {
                Context context = getApplication();
                String zipName = "WhatsMySocken_" + System.currentTimeMillis() + ".zip";

                // Create MediaStore entry
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, zipName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("Failed to create MediaStore entry");

                // Perform the Zip operation
                List<LocationWithPhotos> allData = locationDao.getAllLocationsWithPhotosSync();
                try (OutputStream os = context.getContentResolver().openOutputStream(uri);
                     ZipOutputStream zos = new ZipOutputStream(os)) {

                    // CSV File
                    zos.putNextEntry(new ZipEntry("data.csv"));
                    StringBuilder csv = new StringBuilder("ID,Latitude,Longitude,Accuracy,Altitude,Time,Species,Substrate,Habitat,Collector,Locality,IsSpecimen,SpecimenNr,Note,Photos\n");
                    for (LocationWithPhotos item : allData) csv.append(formatLocationAsCsv(item)).append("\n");
                    zos.write(csv.toString().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();

                    // Photos
                    for (LocationWithPhotos item : allData) {
                        for (PhotoRecord photo : item.photos) addPhotoToZip(zos, photo);
                    }
                    zos.finish();
                }

                // Mark as complete
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
        String photoNames = item.photos.stream()
                .map(p -> new File(p.filePath).getName())
                .reduce((a, b) -> a + "|" + b).orElse("");

        return String.format(Locale.US, "%d,%.6f,%.6f,%d,%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%b,\"%s\",\"%s\",\"%s\"",
                r.id, r.latitude, r.longitude, (int)Math.ceil(r.accuracy), (int)Math.round(r.altitude), r.localTime,
                clean(attr.species), clean(attr.substrate), clean(attr.habitat), clean(attr.collector),
                clean(attr.localityDescription), attr.isSpecimen, clean(attr.specimenNr),
                clean(r.note), photoNames);
    }

    private String clean(String input) {
        if (input == null) return "";
        return input.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ");
    }

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

    // --- Simple Getters ---
    public LiveData<ExportState> getExportStatus() { return exportStatus; }
    public Uri getLastExportUri() { return lastExportUri; }
    public LiveData<Integer> getLocationCount() { return locationDao.getLocationCount(); }
    public LiveData<String> getProvinceResult() { return provinceResult; }
    public LiveData<String> getDistrictResult() { return districtResult; }
    public LiveData<Boolean> getSaveOperationFinished() { return saveOperationFinished; }

    // Map Room entities to UI model using Transformations (Replaces RxJava map)
    public LiveData<List<LocationWithPhotos>> getSpecimenLocationsWithPhotos() {
        // Explicitly define the input type (List<LocationRecord>) in the lambda
        return Transformations.map(locationDao.getSpecimenLocations(), (List<LocationRecord> records) -> {
            List<LocationWithPhotos> result = new ArrayList<>();

            // Now 'records' is recognized as a List, so 'foreach' will work
            if (records != null) {
                for (LocationRecord record : records) {
                    LocationWithPhotos item = new LocationWithPhotos();
                    item.location = record;
                    item.photos = new ArrayList<>();
                    result.add(item);
                }
            }
            return result;
        });
    }

    public LiveData<List<String>> getRecentCollectors() {
        return locationDao.getRecentCollectorNames();
    }
    public LiveData<LocationWithPhotos> getLocationWithPhotos(long id) { return locationDao.getLocationById(id); }
    public void updateRecentCollector(String name) {
        if (name == null || name.trim().isEmpty()) return;
        UserDatabase.getDbExecutor().execute(() ->
                locationDao.insertRecentCollector(new RecentCollector(name.trim(), System.currentTimeMillis()))
        );
    }
    public void deletePhotoByPath(String path) {
        UserDatabase.getDbExecutor().execute(() -> locationDao.deletePhotoByPath(path));
    }
}