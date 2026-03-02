package nisse.whatsmysocken;

import android.app.Application;
import android.location.Location;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.ArrayList;
import java.util.List;
import nisse.whatsmysocken.coords.Coordinates;
import nisse.whatsmysocken.data.SpatialDao;
import nisse.whatsmysocken.data.SpatialDatabase;
import nisse.whatsmysocken.data.SpatialResolver;
import nisse.whatsmysocken.data.SpeciesReferenceEntity;
import nisse.whatsmysocken.data.UserDatabase;

public class SearchViewModel extends AndroidViewModel {
    private final MutableLiveData<Location> currentBestLocation = new MutableLiveData<>();
    private final MutableLiveData<Boolean> userWantsSearching = new MutableLiveData<>(false);
    private final MutableLiveData<String> provinceResult = new MutableLiveData<>();
    private final MutableLiveData<String> districtResult = new MutableLiveData<>();
    private final MutableLiveData<List<SpeciesReferenceEntity>> speciesSuggestions = new MutableLiveData<>();
    private final SpatialDao spatialDao;
    private String lastQuery = "";

    public SearchViewModel(@NonNull Application application) {
        super(application);
        spatialDao = SpatialDatabase.getInstance(application).spatialDao();
    }

    public void setCurrentBestLocation(Location location) {
        currentBestLocation.setValue(location);
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

    public LiveData<List<SpeciesReferenceEntity>> getSpeciesSuggestions() {
        return speciesSuggestions;
    }

    public void findSpecies(String query) {
        if (query == null || query.trim().isEmpty()) {
            speciesSuggestions.postValue(new ArrayList<>());
            return;
        }

        final String currentQuery = query.trim();
        this.lastQuery = currentQuery;

        UserDatabase.getDbExecutor().execute(() -> {
            try {
                List<SpeciesReferenceEntity> results = spatialDao.searchSpecies(currentQuery);

                // Only update the UI if this is still the most recent query
                if (currentQuery.equals(lastQuery)) {
                    speciesSuggestions.postValue(results);
                }
            } catch (Exception e) {
                speciesSuggestions.postValue(new ArrayList<>());
            }
        });
    }
}