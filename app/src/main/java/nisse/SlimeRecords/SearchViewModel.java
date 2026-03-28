package nisse.SlimeRecords;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Location;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;
import java.util.ArrayList;
import java.util.List;
import nisse.SlimeRecords.data.SpatialDao;
import nisse.SlimeRecords.data.SpatialDatabase;
import nisse.SlimeRecords.data.SpeciesReferenceWithAccepted;
import nisse.SlimeRecords.data.UserDatabase;

public class SearchViewModel extends AndroidViewModel {
    private final MutableLiveData<Location> currentBestLocation = new MutableLiveData<>();
    private final MutableLiveData<Boolean> userWantsSearching = new MutableLiveData<>(false);
    private final MutableLiveData<String> countryResult = new MutableLiveData<>();
    private final MutableLiveData<String> countryCodeResult = new MutableLiveData<>();
    private final MutableLiveData<String> provinceResult = new MutableLiveData<>();
    private final MutableLiveData<String> districtResult = new MutableLiveData<>();
    private final MutableLiveData<List<SpeciesReferenceWithAccepted>> speciesSuggestions = new MutableLiveData<>();
    private final SpatialDao spatialDao;
    private String lastQuery = "";

    public SearchViewModel(@NonNull Application application) {
        super(application);
        spatialDao = SpatialDatabase.getInstance(application).spatialDao();
    }

    public void performFullSpatialLookup(double lat, double lon) {
        GeoResolver.resolve(getApplication(), lat, lon, new GeoResolver.GeoCallback() {
            @Override
            public void onResolved(String country, String province, String district, String countryCode) {
                // All values are posted at once from a single source
                countryResult.postValue(country);
                countryCodeResult.postValue(countryCode);
                provinceResult.postValue(province);
                districtResult.postValue(district);
            }

            @Override
            public void onManualEntryRequired() {
                countryResult.postValue("Unknown");
                countryCodeResult.postValue("");
            }

            @Override
            public void onError(Exception e) {
                Log.e("Geo", "Unified lookup failed", e);
            }
        });
    }

    public void setCurrentBestLocation(Location location) {
        currentBestLocation.setValue(location);
    }
    public Location getCurrentBestLocation() { return currentBestLocation.getValue(); }
    public LiveData<Boolean> getUserWantsSearching() { return userWantsSearching; }
    public void setUserWantsSearching(boolean value) { userWantsSearching.setValue(value); }

    public LiveData<String> getCountryResult() { return countryResult; }
    public LiveData<String> getCountryCodeResult() { return countryCodeResult; }
    public LiveData<String> getProvinceResult() { return provinceResult; }
    public LiveData<String> getDistrictResult() { return districtResult; }

    public LiveData<List<SpeciesReferenceWithAccepted>> getSpeciesSuggestions() {
        return speciesSuggestions;
    }

    public void findSpecies(String query, String prefLang) {
        if (query == null || query.trim().isEmpty()) {
            speciesSuggestions.postValue(new ArrayList<>());
            return;
        }

        final String currentQuery = query.trim();
        this.lastQuery = currentQuery;

        UserDatabase.getDbExecutor().execute(() -> {
            try {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
                Resources res = getApplication().getResources();

                // Languages
                List<String> activeLangs = new ArrayList<>();
                if (prefs.getBoolean("lang_la", true)) activeLangs.add("la");
                if (prefs.getBoolean("lang_sv", true)) activeLangs.add("sv");
                if (prefs.getBoolean("lang_en", false)) activeLangs.add("en");

                // Groups (Dynamic Mapping)
                String[] prefKeys = res.getStringArray(R.array.taxon_group_pref_keys);
                String[] dbValues = res.getStringArray(R.array.taxon_group_db_values);
                List<String> activeGroups = new ArrayList<>();

                for (int i = 0; i < prefKeys.length; i++) {
                    // If the user checked this group in settings...
                    if (prefs.getBoolean(prefKeys[i], true)) {
                        // ...add the corresponding DB string to our search list
                        activeGroups.add(dbValues[i]);
                    }
                }

                // Safety check
                if (activeLangs.isEmpty() || activeGroups.isEmpty()) {
                    speciesSuggestions.postValue(new ArrayList<>());
                    return;
                }

                List<SpeciesReferenceWithAccepted> results = spatialDao.searchSpeciesWithAccepted(
                        currentQuery, prefLang, activeLangs, activeGroups
                );

                if (currentQuery.equals(lastQuery)) {
                    speciesSuggestions.postValue(results);
                }
            } catch (Exception e) {
                Log.e("SearchViewModel", "Search failed", e);
                speciesSuggestions.postValue(new ArrayList<>());
            }
        });
    }
}