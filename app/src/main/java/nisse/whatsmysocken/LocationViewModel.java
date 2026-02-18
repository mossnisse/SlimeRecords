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
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.rxjava3.PagingRx;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import nisse.whatsmysocken.coords.Coordinates;
import nisse.whatsmysocken.data.LocationDao;
import nisse.whatsmysocken.data.LocationRecord;
import nisse.whatsmysocken.data.PhotoRecord;
import nisse.whatsmysocken.data.RecentCollector;
import nisse.whatsmysocken.data.SpatialResolver;
import nisse.whatsmysocken.data.SpeciesAttributes;
import nisse.whatsmysocken.data.UserDatabase;
import android.content.Intent;

public class LocationViewModel extends AndroidViewModel {
    private final LocationDao locationDao;
    //private android.location.Location currentBestLocation;
    private final BehaviorSubject<Location> currentBestLocationSubject = BehaviorSubject.create();
    private final MutableLiveData<Boolean> saveOperationFinished = new MutableLiveData<>(false);
    public final Flowable<PagingData<LocationWithPhotos>> historyFlow;
    private final MutableLiveData<String> provinceResult = new MutableLiveData<>();
    private final MutableLiveData<String> districtResult = new MutableLiveData<>();
    // Export State Management
    private final BehaviorSubject<ExportState> exportStatus = BehaviorSubject.createDefault(ExportState.IDLE);
    public enum ExportState { IDLE, LOADING, SUCCESS, ERROR }
    private final CompositeDisposable disposables = new CompositeDisposable();
    private Uri lastExportUri;

    public LocationViewModel(@NonNull Application application) {
        super(application);
        locationDao = UserDatabase.getInstance(application).locationDao();

        Pager<Integer, LocationWithPhotos> pager = new Pager<>(
                new PagingConfig(20, 5, false),
                locationDao::getAllLocationsPaged
        );
        historyFlow = PagingRx.getFlowable(pager);
    }

    // --- Standard Database Ops ---

    public void saveLocationWithPhotos(LocationRecord record, List<String> photoPaths) {
        UserDatabase.getDbExecutor().execute(() -> {
            locationDao.insertLocationWithPhotos(record, photoPaths);
            saveOperationFinished.postValue(true);
        });
    }

    public void deleteLocationWithPhotos(LocationWithPhotos item) {
        UserDatabase.getDbExecutor().execute(() -> {
            if (item.photos != null) {
                for (PhotoRecord p : item.photos) {
                    FileUtils.deleteFileAtPath(p.filePath);
                }
            }
            locationDao.deleteLocationAndPhotos(item.location, item.photos);
        });
    }

    public void deletePhotoByPath(String path) {
        UserDatabase.getDbExecutor().execute(() -> locationDao.deletePhotoByPath(path));
    }

    public void updateLocation(LocationRecord record) {
        UserDatabase.getDbExecutor().execute(() -> {
            locationDao.updateLocation(record);
            saveOperationFinished.postValue(true);
        });
    }
    public void setCurrentBestLocation(Location location) {
        if (location == null) {
            return;
        }
        currentBestLocationSubject.onNext(location);
    }
    public LiveData<String> getProvinceResult() { return provinceResult; }
    public LiveData<String> getDistrictResult() { return districtResult; }

    public void fetchProvinceName(double lat, double lon) {
        UserDatabase.getDbExecutor().execute(() -> {
            int[] coords = convertToSweref(lat, lon);
            String name = SpatialResolver.getInstance(getApplication())
                    .getRegionName(coords[0], coords[1], false);
            provinceResult.postValue(name);
        });
    }

    public void fetchDistrictName(double lat, double lon) {
        UserDatabase.getDbExecutor().execute(() -> {
            int[] coords = convertToSweref(lat, lon);
            String name = SpatialResolver.getInstance(getApplication())
                    .getRegionName(coords[0], coords[1], true);
            districtResult.postValue(name);
        });
    }

    // Helper to avoid repeating conversion math
    private int[] convertToSweref(double lat, double lon) {
        Coordinates sweref = new Coordinates(lat, lon).convertToSweref99TMFromWGS84();
        return new int[]{(int)Math.round(sweref.getNorth()), (int)Math.round(sweref.getEast())};
    }

    public LiveData<List<String>> getRecentCollectors() {
        return locationDao.getRecentCollectorNames();
    }

    public void updateRecentCollector(String name) {
        if (name == null || name.trim().isEmpty()) return;
        UserDatabase.getDbExecutor().execute(() ->
            locationDao.insertRecentCollector(new RecentCollector(name.trim(), System.currentTimeMillis()))
        );
    }

    // --- Export Logic ---
    public boolean isExporting() {
        return exportStatus.getValue() == ExportState.LOADING;
    }

    // New: Helper for the Activity to show "Found X files"
    public LiveData<Integer> getLocationCount() {
        return locationDao.getLocationCount();
    }

    public void startExport() {
        if (isExporting()) return;
        exportStatus.onNext(ExportState.LOADING);

        Context context = getApplication();
        String zipName = "WhatsMySocken_" + System.currentTimeMillis() + ".zip";

        disposables.add(
                Single.fromCallable(() -> {
                            // Prepare MediaStore Entry
                            ContentValues values = new ContentValues();
                            values.put(MediaStore.MediaColumns.DISPLAY_NAME, zipName);
                            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
                            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                            if (uri == null) throw new IOException("Failed to create MediaStore entry");

                            // Fetch all data in one go (Room handles the join/relation)
                            List<LocationWithPhotos> allData = locationDao.getAllLocationsWithPhotosSync();

                            try (OutputStream os = context.getContentResolver().openOutputStream(uri);
                                 ZipOutputStream zos = new ZipOutputStream(os)) {

                                // --- PHASE 1: Write CSV Entry ---
                                zos.putNextEntry(new ZipEntry("data.csv"));
                                StringBuilder csvBuilder = new StringBuilder();
                                csvBuilder.append("ID,Latitude,Longitude,Accuracy,Altitude,Time,Species,Substrate,Habitat,Collector,Locality,IsSpecimen,SpecimenNr,Note,Photos\n");

                                for (LocationWithPhotos item : allData) {
                                    csvBuilder.append(formatLocationAsCsv(item)).append("\n");
                                }
                                zos.write(csvBuilder.toString().getBytes(StandardCharsets.UTF_8));
                                zos.closeEntry();

                                // --- PHASE 2: Write Photos (One-pass) ---
                                for (LocationWithPhotos item : allData) {
                                    for (PhotoRecord photo : item.photos) {
                                        addPhotoToZip(zos, photo);
                                    }
                                }

                                zos.finish();
                            }

                            // Mark as no longer pending
                            values.clear();
                            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                            context.getContentResolver().update(uri, values, null, null);
                            return uri;
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                uri -> {
                                    this.lastExportUri = uri;
                                    exportStatus.onNext(ExportState.SUCCESS);
                                },
                                throwable -> {
                                    Log.e("Export", "Zip Failed", throwable);
                                    exportStatus.onNext(ExportState.ERROR);
                                }
                        )
        );
    }

    private String formatLocationAsCsv(LocationWithPhotos item) {
        LocationRecord r = item.location;
        SpeciesAttributes attr = r.attributes != null ? r.attributes : new SpeciesAttributes();

        // Join photo filenames with a pipe |
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
        return input == null ? "" : input.replace("\"", "\"\"");
    }

    private void addPhotoToZip(ZipOutputStream zos, PhotoRecord photo) throws IOException {
        File photoFile = new File(photo.filePath);
        if (!photoFile.exists()) {
            Log.w("Export", "Photo not found: " + photo.filePath);
            return;
        }

        try (FileInputStream fis = new FileInputStream(photoFile)) {
            ZipEntry entry = new ZipEntry("photos/" + photoFile.getName());
            zos.putNextEntry(entry);

            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
        }
    }

    public void shareExportedZip(Context context, Uri zipUri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_STREAM, zipUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Critical for security

        // Optional: Pre-fill email subject
        intent.putExtra(Intent.EXTRA_SUBJECT, "WhatsMySocken Export: " + System.currentTimeMillis());

        context.startActivity(Intent.createChooser(intent, "Share Export via..."));
    }

    public Uri getLastExportUri() {
        return lastExportUri;
    }
    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }

    // Getters
    public LiveData<Boolean> getSaveOperationFinished() { return saveOperationFinished; }
    public LiveData<LocationWithPhotos> getLocationWithPhotos(long id) { return locationDao.getLocationById(id); }
    public Location getCurrentBestLocation() {
        return currentBestLocationSubject.getValue();
    }

    public Observable<ExportState> getExportStatus() { return exportStatus; }
}