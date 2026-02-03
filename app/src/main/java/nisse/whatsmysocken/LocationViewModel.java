package nisse.whatsmysocken;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import java.util.List;

public class LocationViewModel extends AndroidViewModel {
    // Change the type to LocationWithPhotos
    private final LiveData<List<LocationWithPhotos>> allLocations;

    public LocationViewModel(@NonNull Application application) {
        super(application);
        // Use the new method you just fixed in the DAO
        allLocations = AppDatabase.getInstance(application).locationDao().getAllLocationsWithPhotos();
    }

    public LiveData<List<LocationWithPhotos>> getAllLocations() {
        return allLocations;
    }

    public void delete(LocationRecord location) {
        new Thread(() ->
            AppDatabase.getInstance(getApplication()).locationDao().delete(location)
        ).start();
    }
}