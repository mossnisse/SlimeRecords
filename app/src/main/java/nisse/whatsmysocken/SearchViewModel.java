package nisse.whatsmysocken;

import android.app.Application;
import android.location.Location;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import nisse.whatsmysocken.coords.Coordinates;
import nisse.whatsmysocken.data.SpatialResolver;
import nisse.whatsmysocken.data.UserDatabase;

public class SearchViewModel extends AndroidViewModel {
    private final MutableLiveData<Location> currentBestLocation = new MutableLiveData<>();
    private final MutableLiveData<Boolean> userWantsSearching = new MutableLiveData<>(false);
    private final MutableLiveData<String> provinceResult = new MutableLiveData<>();
    private final MutableLiveData<String> districtResult = new MutableLiveData<>();

    public SearchViewModel(@NonNull Application application) {
        super(application);
    }

    public void setCurrentBestLocation(Location location) {
        if (location != null) currentBestLocation.postValue(location);
    }
    public Location getCurrentBestLocation() { return currentBestLocation.getValue(); }
    public LiveData<Boolean> getUserWantsSearching() { return userWantsSearching; }
    public void setUserWantsSearching(boolean value) { userWantsSearching.setValue(value); }

    public void fetchProvinceName(double lat, double lon) {
        UserDatabase.getDbExecutor().execute(() -> {
            int[] coords = convertToSweref(lat, lon);
            String name = SpatialResolver.getInstance(getApplication()).getRegionName(coords[0], coords[1], false);
            provinceResult.postValue(name);
        });
    }

    public void fetchDistrictName(double lat, double lon) {
        UserDatabase.getDbExecutor().execute(() -> {
            int[] coords = convertToSweref(lat, lon);
            String name = SpatialResolver.getInstance(getApplication()).getRegionName(coords[0], coords[1], true);
            districtResult.postValue(name);
        });
    }

    private int[] convertToSweref(double lat, double lon) {
        Coordinates sweref = new Coordinates(lat, lon).convertToSweref99TMFromWGS84();
        return new int[]{(int)Math.round(sweref.getNorth()), (int)Math.round(sweref.getEast())};
    }

    public LiveData<String> getProvinceResult() { return provinceResult; }
    public LiveData<String> getDistrictResult() { return districtResult; }
}