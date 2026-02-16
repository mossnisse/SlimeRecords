package nisse.whatsmysocken;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
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
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import nisse.whatsmysocken.coords.Coordinates;
import nisse.whatsmysocken.data.LocationDao;
import nisse.whatsmysocken.data.LocationRecord;
import nisse.whatsmysocken.data.PhotoRecord;
import nisse.whatsmysocken.data.RecentCollector;
import nisse.whatsmysocken.data.SpatialResolver;
import nisse.whatsmysocken.data.UserDatabase;
import android.content.Intent;

public class LocationViewModel extends AndroidViewModel {
    private final LocationDao locationDao;
    private android.location.Location currentBestLocation;
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

        String zipName = "WhatsMySocken_" + System.currentTimeMillis() + ".zip";
        Context context = getApplication();

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, zipName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        disposables.add(
                Observable.create(emitter -> {
                            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                            if (uri == null) { emitter.onError(new IOException("MediaStore failed")); return; }

                            try (OutputStream os = context.getContentResolver().openOutputStream(uri);
                                 ZipOutputStream zos = new ZipOutputStream(os)) {

                                // --- PHASE 1: CSV ---
                                zos.putNextEntry(new ZipEntry("data.csv"));
                                String header = "ID,Latitude,Longitude,Accuracy,Time,Species,Substrate,Collector,Locality,IsSpecimen,SpecimenNr,Note,Photo_Filenames\n";
                                zos.write(header.getBytes(StandardCharsets.UTF_8));

                                try (Cursor cursor = locationDao.getAllLocationsCursor()) {
                                    if (cursor != null && cursor.moveToFirst()) {
                                        //com.google.gson.Gson gson = new com.google.gson.Gson();
                                        do {
                                            LocationRecord record = cursorToLocation(cursor);
                                            List<PhotoRecord> photos = locationDao.getPhotosForLocationSync(record.id);

                                            String species = "", substrate = "", collector = "", locality = "", specNr = "";
                                            boolean isSpecimen = false;

                                            if (record.attributes != null) {
                                                species = record.attributes.species != null ? record.attributes.species : "";
                                                substrate = record.attributes.substrate != null ? record.attributes.substrate : "";
                                                collector = record.attributes.collector != null ? record.attributes.collector : "";
                                                locality = record.attributes.localityDescription != null ? record.attributes.localityDescription : "";
                                                isSpecimen = record.attributes.isSpecimen;
                                                specNr = record.attributes.specimenNr != null ? record.attributes.specimenNr : "";
                                            }

                                            String filenames = "";
                                            if (photos != null && !photos.isEmpty()) {
                                                StringBuilder sb = new StringBuilder();
                                                for (int i = 0; i < photos.size(); i++) {
                                                    sb.append(new File(photos.get(i).filePath).getName());
                                                    if (i < photos.size() - 1) sb.append("|");
                                                }
                                                filenames = sb.toString();
                                            }

                                            String escapedNote = record.note != null ? record.note.replace("\"", "\"\"") : "";
                                            String escapedLocality = locality.replace("\"", "\"\"");
                                            int wholeAccuracy = (int) Math.ceil(record.accuracy);

                                            String line = String.format(Locale.US, "%d,%.6f,%.6f,%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%b,\"%s\",\"%s\",\"%s\"\n",
                                                    record.id,
                                                    record.latitude,
                                                    record.longitude,
                                                    wholeAccuracy,
                                                    record.localTime,
                                                    species,
                                                    substrate,
                                                    collector,
                                                    escapedLocality,
                                                    isSpecimen,
                                                    specNr,
                                                    escapedNote,
                                                    filenames);

                                            zos.write(line.getBytes(StandardCharsets.UTF_8));
                                        } while (cursor.moveToNext());
                                    } else {
                                        Log.w("Export", "No data found in database to export!");
                                    }
                                }
                                zos.closeEntry(); // Finish CSV

                                // --- PHASE 2: PHOTOS ---
                                try (Cursor cursor = locationDao.getAllLocationsCursor()) {
                                    if (cursor != null && cursor.moveToFirst()) {
                                        do {
                                            long locId = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                                            List<PhotoRecord> photos = locationDao.getPhotosForLocationSync(locId);
                                            for (PhotoRecord photo : photos) {
                                                addPhotoToZip(zos, photo);
                                            }
                                        } while (cursor.moveToNext());
                                    }
                                }

                                zos.finish();
                                zos.flush();
                                zos.close();

                                values.clear();
                                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                                context.getContentResolver().update(uri, values, null, null);

                                // Emit ONLY the URI
                                emitter.onNext(uri);
                            } catch (Exception e) {
                                context.getContentResolver().delete(uri, null, null);
                                emitter.onError(e);
                            }
                            emitter.onComplete();
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                result -> {
                                    // Save the URI so the Activity can grab it
                                    if (result instanceof Uri) {
                                        this.lastExportUri = (Uri) result;
                                    }

                                    // Refresh MediaScanner so file shows up on PC immediately
                                    android.media.MediaScannerConnection.scanFile(getApplication(),
                                            new String[]{ Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() },
                                            null, null);

                                    exportStatus.onNext(ExportState.SUCCESS);
                                },
                                throwable -> {
                                    Log.e("Export", "Zip Failed", throwable);
                                    exportStatus.onNext(ExportState.ERROR);
                                }
                        )
        );
    }

    private LocationRecord cursorToLocation(Cursor cursor) {
        LocationRecord record = new LocationRecord();
        record.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        record.latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
        record.longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
        record.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));
        record.accuracy = cursor.getFloat(cursor.getColumnIndexOrThrow("accuracy"));
        record.localTime = cursor.getString(cursor.getColumnIndexOrThrow("localTime"));
        record.note = cursor.getString(cursor.getColumnIndexOrThrow("note"));

        // NEW: Load the attributes JSON string and convert it back to an object
        String attrJson = cursor.getString(cursor.getColumnIndexOrThrow("attributes"));
        if (attrJson != null) {
            record.attributes = new com.google.gson.Gson().fromJson(attrJson, nisse.whatsmysocken.data.SpeciesAttributes.class);
        }

        return record;
    }

    private void addPhotoToZip(ZipOutputStream zos, PhotoRecord photo) throws IOException {
        File photoFile = new File(photo.filePath);
        if (!photoFile.exists()) return;

        // Adding "photos/" prefix creates the folder inside the zip automatically
        ZipEntry entry = new ZipEntry("photos/" + photoFile.getName());
        zos.putNextEntry(entry);

        try (FileInputStream fis = new FileInputStream(photoFile)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                zos.write(buffer, 0, length);
            }
        }
        zos.closeEntry();
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
    public android.location.Location getCurrentBestLocation() { return currentBestLocation; }
    public void setCurrentBestLocation(android.location.Location loc) { this.currentBestLocation = loc; }
    public Observable<ExportState> getExportStatus() { return exportStatus; }
}