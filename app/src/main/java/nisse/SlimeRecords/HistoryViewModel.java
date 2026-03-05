package nisse.SlimeRecords;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.PagingLiveData;
import java.util.List;
import nisse.SlimeRecords.data.LocationDao;
import nisse.SlimeRecords.data.LocationRecord;
import nisse.SlimeRecords.data.PhotoRecord;
import nisse.SlimeRecords.data.RecentCollector;
import nisse.SlimeRecords.data.UserDatabase;

public class HistoryViewModel extends AndroidViewModel {
    private final LocationDao locationDao;
    public final LiveData<PagingData<LocationWithPhotos>> historyLiveData;
    private final MutableLiveData<Boolean> operationFinished = new MutableLiveData<>(false);

    public HistoryViewModel(@NonNull Application application) {
        super(application);
        locationDao = UserDatabase.getInstance(application).locationDao();

        Pager<Integer, LocationWithPhotos> pager = new Pager<>(
                new PagingConfig(20, 5, false),
                locationDao::getAllLocationsPaged
        );
        historyLiveData = PagingLiveData.getLiveData(pager);
    }

    public void saveLocationWithPhotos(LocationRecord record, List<String> photoPaths) {
        UserDatabase.getDbExecutor().execute(() -> {
            locationDao.insertLocationWithPhotos(record, photoPaths);
            operationFinished.postValue(true);
        });
    }

    public void updateLocation(LocationRecord record) {
        UserDatabase.getDbExecutor().execute(() -> {
            locationDao.updateLocation(record);
            operationFinished.postValue(true);
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

    public LiveData<LocationWithPhotos> getLocationWithPhotos(long id) { return locationDao.getLocationById(id); }
    public LiveData<Boolean> getOperationFinished() { return operationFinished; }
    public LiveData<List<String>> getRecentCollectors() { return locationDao.getRecentCollectorNames(); }

    public void updateRecentCollector(String name) {
        if (name == null || name.trim().isEmpty()) return;
        UserDatabase.getDbExecutor().execute(() ->
                locationDao.insertRecentCollector(new RecentCollector(name.trim(), System.currentTimeMillis()))
        );
    }

    public void deletePhotoByPath(String path) {
        UserDatabase.getDbExecutor().execute(() -> locationDao.deletePhotoByPath(path));
    }

    public LiveData<Integer> getLocationCount() {
        return locationDao.getLocationCount();
    }

    public LiveData<List<String>> getNearbyLocalitySuggestions(double lat, double lon) {
        // Approx 2km bounding box
        double latDelta = 0.018;
        double lonDelta = 0.036;

        return locationDao.getNearbyLocalitySuggestions(
                lat - latDelta, lat + latDelta,
                lon - lonDelta, lon + lonDelta
        );
    }
}