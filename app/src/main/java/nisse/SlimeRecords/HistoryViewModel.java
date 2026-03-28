package nisse.SlimeRecords;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.PagingLiveData;
import java.util.ArrayList;
import java.util.List;
import nisse.SlimeRecords.data.LocalitySuggestion;
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
                for (PhotoRecord p : item.photos) {
                    // Delete ONLY the link for THIS specific location
                    locationDao.deletePhotoById(p.id);

                    // NOW check if any OTHER record still uses this path
                    if (locationDao.getPhotoReferenceCount(p.filePath) == 0) {
                        FileUtils.deleteFileAtPath(p.filePath);
                    }
                }
            }
            // 3. Finally delete the location itself
            locationDao.deleteLocation(item.location);
        });
    }

    public void deletePhoto(PhotoRecord photo) {
        UserDatabase.getDbExecutor().execute(() -> {
            // Delete the specific photo entry by ID
            locationDao.deletePhotoById(photo.id);

            // Check reference count for the path
            if (locationDao.getPhotoReferenceCount(photo.filePath) == 0) {
                FileUtils.deleteFileAtPath(photo.filePath);
            }
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

    public LiveData<Integer> getLocationCount() {
        return locationDao.getLocationCount();
    }

    public LiveData<List<String>> getSortedNearbyLocalities(double userLat, double userLon) {
        // Configuration: Adjust these to change the search behavior
        final double searchRadiusMeters = 2000.0;
        final double kmPerDegreeLat = 111.0;

        // Calculate the Bounding Box
        // Latitude range is constant: degrees = distance / kmPerDegree
        double latRange = (searchRadiusMeters / 1000.0) / kmPerDegreeLat;

        // Longitude range shrinks as we move toward the poles
        double cosLat = Math.cos(Math.toRadians(userLat));
        // Use a small epsilon (1e-6) to avoid division by zero at the exact poles
        double lonRange = (Math.abs(cosLat) > 1e-6) ?
                ((searchRadiusMeters / 1000.0) / (kmPerDegreeLat * cosLat)) : latRange;

        double minLat = userLat - latRange;
        double maxLat = userLat + latRange;
        double minLon = userLon - lonRange;
        double maxLon = userLon + lonRange;

        return Transformations.map(locationDao.getNearbyLocalityData(minLat, maxLat, minLon, maxLon), list -> {
            List<String> names = new ArrayList<>();
            if (list == null || list.isEmpty()) return names;

            List<LocalitySuggestion> filteredList = new ArrayList<>();

            // Fine-filter the results into a true circle
            for (LocalitySuggestion item : list) {
                float[] results = new float[1];
                android.location.Location.distanceBetween(
                        userLat, userLon,
                        item.latitude, item.longitude,
                        results);

                item.distance = results[0];

                if (item.distance <= searchRadiusMeters) {
                    filteredList.add(item);
                }
            }

            // Sort by distance (closest first)
            filteredList.sort((a, b) -> Float.compare(a.distance, b.distance));

            // Convert to string list for the AutoComplete adapter
            for (LocalitySuggestion item : filteredList) {
                names.add(item.name);
            }

            return names;
        });
    }
}