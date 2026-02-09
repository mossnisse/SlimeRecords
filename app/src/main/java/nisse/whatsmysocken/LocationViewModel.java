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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import nisse.whatsmysocken.data.SpatialResolver;

public class LocationViewModel extends AndroidViewModel {
    private final LocationDao locationDao;
    private final ExecutorService executor;
    private final MutableLiveData<Boolean> saveOperationFinished = new MutableLiveData<>(false);

    // The "Paging" stream
    public final Flowable<PagingData<LocationWithPhotos>> historyFlow;
    private final BehaviorSubject<ExportState> exportStatus = BehaviorSubject.createDefault(ExportState.IDLE);

    public enum ExportState { IDLE, LOADING, SUCCESS, ERROR }

    private final SpatialResolver spatialResolver;

    // LiveData to hold the resolved names
    private final MutableLiveData<String> currentSockenName = new MutableLiveData<>("");
    private final MutableLiveData<String> currentProvinceName = new MutableLiveData<>("");

    public LocationViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        locationDao = db.locationDao();
        executor = Executors.newSingleThreadExecutor();

        // Configure the Pager
        Pager<Integer, LocationWithPhotos> pager = new Pager<>(
                new PagingConfig(
                        20,            // Page size
                        5,             // Prefetch distance
                        false          // Enable placeholders
                ),
                () -> locationDao.getAllLocationsPaged()
        );

        // Convert the pager to a Flowable for the HistoryActivity to consume
        historyFlow = PagingRx.getFlowable(pager);
        this.spatialResolver = new SpatialResolver(application);
    }

    public LiveData<LocationWithPhotos> getLocationWithPhotos(long id) {
        return locationDao.getLocationById(id);
    }

    public LiveData<Boolean> getSaveOperationFinished() {
        return saveOperationFinished;
    }

    public void saveLocationWithPhotos(LocationRecord record, List<String> photoPaths) {
        executor.execute(() -> {
            locationDao.insertLocationWithPhotos(record, photoPaths);
            saveOperationFinished.postValue(true);
        });
    }

    public void updateLocation(LocationRecord record) {
        executor.execute(() -> {
            locationDao.updateLocation(record);
            // If you want to trigger a finish in the UI:
            saveOperationFinished.postValue(true);
        });
    }
    public void deleteLocationWithPhotos(LocationWithPhotos item) {
        executor.execute(() -> {
            if (item.photos != null) {
                for (PhotoRecord p : item.photos) {
                    FileUtils.deleteFileAtPath(p.filePath);
                }
            }
            locationDao.delete(item.location);
        });
    }
    public LiveData<List<LocationWithPhotos>> getAllDataForExport() {
        return locationDao.getAllLocationsForExport();
    }

    // Inside LocationViewModel.java
    public void deletePhotoByPath(String path) {
        executor.execute(() -> {
            locationDao.deletePhotoByPath(path);
        });
    }

    public Observable<ExportState> getExportStatus() { return exportStatus; }

    public void startExport(Context context, List<LocationWithPhotos> data) {
        if (exportStatus.getValue() == ExportState.LOADING) return;

        exportStatus.onNext(ExportState.LOADING);

        // Using RxJava to handle the thread switching cleanly
        Single.fromCallable(() -> performZipLogic(context, data))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        success -> exportStatus.onNext(success ? ExportState.SUCCESS : ExportState.ERROR),
                        throwable -> exportStatus.onNext(ExportState.ERROR)
                );
    }

    // Move the performZip logic here (see code below)
    private boolean performZipLogic(Context context, List<LocationWithPhotos> data) {
        // Data check: use the 'data' parameter passed to the method
        if (data == null || data.isEmpty()) return false;

        String zipName = "WhatsMySocken_" + System.currentTimeMillis() + ".zip";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, zipName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        // Access ContentResolver via the passed context
        Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return false;

        try (OutputStream os = context.getContentResolver().openOutputStream(uri);
             ZipOutputStream zos = new ZipOutputStream(os)) {

            // Add the CSV file - passing the 'data' parameter to FileUtils
            ZipEntry csvEntry = new ZipEntry("data.csv");
            zos.putNextEntry(csvEntry);
            zos.write(FileUtils.generateCsv(data).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // add Photos using the local 'data' parameter
            boolean folderCreated = false;
            for (LocationWithPhotos item : data) {
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

    public void resolveLocationNames(int n, int e) {
        // Check if DB is ready first
        Boolean ready = AppDatabase.getIsReady().getValue();
        if (ready == null || !ready) {
            currentSockenName.setValue("Initializing database...");
            return;
        }

        // If ready, proceed with RxJava call as before
        Single.fromCallable(() -> spatialResolver.getSockenName(n, e))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(currentSockenName::setValue, t -> {});
    }

    public MutableLiveData<String> getCurrentSockenName() {
        return currentSockenName;
    }

    public MutableLiveData<String> getCurrentProvinceName() {
        return currentProvinceName;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // CRITICAL: Close the file channels when the ViewModel is destroyed
        if (spatialResolver != null) {
            spatialResolver.close();
        }
    }
}