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
import nisse.whatsmysocken.data.SpatialResolver;
import nisse.whatsmysocken.data.UserDatabase;

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

        disposables.add(
                Single.fromCallable(() -> {
                            // 1. Fetch data synchronously on background thread
                            List<LocationWithPhotos> data = locationDao.getAllLocationsForExport();
                            // 2. Perform Zip
                            return performZipLogic(data);
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                success -> exportStatus.onNext(success ? ExportState.SUCCESS : ExportState.ERROR),
                                throwable -> {
                                    Log.e("Export", "Error", throwable);
                                    exportStatus.onNext(ExportState.ERROR);
                                }
                        )
        );
    }

    private boolean performZipLogic(List<LocationWithPhotos> data) {
        Context context = getApplication();
        if (data == null || data.isEmpty()) return false;

        String zipName = "WhatsMySocken_" + System.currentTimeMillis() + ".zip";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, zipName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return false;

        try (OutputStream os = context.getContentResolver().openOutputStream(uri);
             ZipOutputStream zos = new ZipOutputStream(os)) {

            // Write CSV
            zos.putNextEntry(new ZipEntry("data.csv"));
            zos.write(FileUtils.generateCsv(data).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Write Photos
            for (LocationWithPhotos item : data) {
                if (item.photos == null) continue;
                for (PhotoRecord photo : item.photos) {
                    File file = new File(photo.filePath);
                    if (file.exists()) {
                        // "photos/" prefix automatically handles the folder
                        zos.putNextEntry(new ZipEntry("photos/" + file.getName()));
                        copyFile(file, zos);
                        zos.closeEntry();
                    }
                }
            }
            return true;
        } catch (IOException e) {
            Log.e("ExportWorker", "Zip failed", e);
            return false;
        }
    }

    private void copyFile(File file, OutputStream out) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }
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