package nisse.whatsmysocken;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.rxjava3.PagingRx;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.rxjava3.core.Flowable;

public class LocationViewModel extends AndroidViewModel {
    private final LocationDao locationDao;
    private final ExecutorService executor;
    private final MutableLiveData<Boolean> saveOperationFinished = new MutableLiveData<>(false);

    // The "Paging" stream
    public final Flowable<PagingData<LocationWithPhotos>> historyFlow;

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
}