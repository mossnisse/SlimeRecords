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
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import nisse.whatsmysocken.data.AppDatabase;
import nisse.whatsmysocken.data.LocationDao;
import nisse.whatsmysocken.data.LocationRecord;
import nisse.whatsmysocken.data.PhotoRecord;

public class LocationViewModel extends AndroidViewModel {
    private final LocationDao locationDao;
    //private final AppDatabase db;
    private final LiveData<Boolean> isDbReady;

    private android.location.Location currentBestLocation;
    private final MutableLiveData<Boolean> saveOperationFinished = new MutableLiveData<>(false);

    public final Flowable<PagingData<LocationWithPhotos>> historyFlow;
    private final BehaviorSubject<ExportState> exportStatus = BehaviorSubject.createDefault(ExportState.IDLE);

    public enum ExportState { IDLE, LOADING, SUCCESS, ERROR }

    public LocationViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        this.locationDao = db.locationDao();
        this.isDbReady = db.getIsReady(application);

        Pager<Integer, LocationWithPhotos> pager = new Pager<>(
                new PagingConfig(20, 5, false),
                locationDao::getAllLocationsPaged
        );
        historyFlow = PagingRx.getFlowable(pager);
    }

    // --- Location Records & History ---
    public void saveLocationWithPhotos(LocationRecord record, List<String> photoPaths) {
        AppDatabase.getDbExecutor().execute(() -> {
            locationDao.insertLocationWithPhotos(record, photoPaths);
            saveOperationFinished.postValue(true);
        });
    }

    public void deleteLocationWithPhotos(LocationWithPhotos item) {
        AppDatabase.getDbExecutor().execute(() -> {
            if (item.photos != null) {
                for (PhotoRecord p : item.photos) FileUtils.deleteFileAtPath(p.filePath);
            }
            locationDao.delete(item.location);
        });
    }

    public void deletePhotoByPath(String path) {
        AppDatabase.getDbExecutor().execute(() ->
            locationDao.deletePhotoByPath(path)
        );
    }

    public void updateLocation(LocationRecord record) {
        AppDatabase.getDbExecutor().execute(() -> {
            locationDao.updateLocation(record);
            saveOperationFinished.postValue(true);
        });
    }

    // --- Export Logic ---
    public void startExport(List<LocationWithPhotos> data) {
        if (exportStatus.getValue() == ExportState.LOADING) return;
        exportStatus.onNext(ExportState.LOADING);

        Single.fromCallable(() -> performZipLogic(data))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        success -> exportStatus.onNext(success ? ExportState.SUCCESS : ExportState.ERROR),
                        throwable -> exportStatus.onNext(ExportState.ERROR)
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

            ZipEntry csvEntry = new ZipEntry("data.csv");
            zos.putNextEntry(csvEntry);
            zos.write(FileUtils.generateCsv(data).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            boolean folderCreated = false;
            for (LocationWithPhotos item : data) {
                if (item.photos == null) continue;
                for (PhotoRecord photo : item.photos) {
                    File file = new File(photo.filePath);
                    if (file.exists()) {
                        if (!folderCreated) {
                            zos.putNextEntry(new ZipEntry("photos/"));
                            zos.closeEntry();
                            folderCreated = true;
                        }
                        ZipEntry entry = new ZipEntry("photos/" + file.getName());
                        zos.putNextEntry(entry);
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
    }

    // Getters
    public LiveData<Boolean> getSaveOperationFinished() { return saveOperationFinished; }
    public LiveData<LocationWithPhotos> getLocationWithPhotos(long id) { return locationDao.getLocationById(id); }
    public android.location.Location getCurrentBestLocation() { return currentBestLocation; }
    public void setCurrentBestLocation(android.location.Location loc) { this.currentBestLocation = loc; }
    public Observable<ExportState> getExportStatus() { return exportStatus; }
    public LiveData<Boolean> getDatabaseReadyStatus() { return isDbReady; }
    public LiveData<List<LocationWithPhotos>> getAllDataForExport() { return locationDao.getAllLocationsForExport(); }
}