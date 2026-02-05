package nisse.whatsmysocken;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationViewModel extends AndroidViewModel {
    // Change the type to LocationWithPhotos
    private final LiveData<List<LocationWithPhotos>> allLocations;
    private final LocationDao locationDao;
    private final ExecutorService executor;

    public LocationViewModel(@NonNull Application application) {
        super(application);
        // Use the new method you just fixed in the DAO
        AppDatabase db = AppDatabase.getInstance(application);
        locationDao = db.locationDao();

        executor = Executors.newSingleThreadExecutor();
        allLocations = locationDao.getAllLocationsWithPhotos();
    }

    public LiveData<List<LocationWithPhotos>> getAllLocations() {
        return allLocations;
    }
    public LiveData<LocationWithPhotos> getLocationWithPhotos(long id) {
        return locationDao.getLocationById(id);
    }
    public void delete(LocationRecord location) {
        executor.execute(() -> locationDao.delete(location));
    }

    public void deleteLocationWithPhotos(LocationWithPhotos item) {
        executor.execute(() -> {
            for (PhotoRecord p : item.photos) {
                FileUtils.deleteFileAtPath(p.filePath);
            }
            locationDao.delete(item.location);
        });
    }
}