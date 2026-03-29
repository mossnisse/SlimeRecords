package nisse.SlimeRecords;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.util.Log;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import nisse.SlimeRecords.coords.CoordSystem;
import nisse.SlimeRecords.coords.Coordinates;
import nisse.SlimeRecords.data.CountryEntity;
import nisse.SlimeRecords.data.SpatialDatabase;
import nisse.SlimeRecords.data.SpatialDao;
import nisse.SlimeRecords.data.SpatialResolver;

public class GeoResolver {
    private static final String TAG = "GeoResolver";
    private final SpatialDao spatialDao;
    private final SpatialResolver spatialResolver;
    private final Context context;

    public interface GeoCallback {
        void onResolved(String country, String province, String district, String countryCode);
        void onError(Exception e);
        void onManualEntryRequired();
    }

    public GeoResolver(Context context) {
        this.context = context;
        this.spatialDao = SpatialDatabase.getInstance(context).spatialDao();
        this.spatialResolver = SpatialResolver.getInstance(context);
    }

    public static void resolve(Context context, double lat, double lon, GeoCallback callback) {
        new Thread(() -> {
            try {
                GeoResolver resolver = new GeoResolver(context);

                // Convert to SWEREF 99 TM
                Coordinates wgs = new Coordinates(lat, lon);
                Coordinates sweref = wgs.toProjected(CoordSystem.SWEREF99TM);
                int n = (int) Math.round(sweref.getNorth());
                int e = (int) Math.round(sweref.getEast());

                // Try Historical Province
                String province = resolver.spatialResolver.getRegionName(n, e, false);

                if (isValidRegion(province)) {
                    // SUCCESS: Traditional Swedish Data
                    String district = resolver.spatialResolver.getRegionName(n, e, true);
                    if ("Outside Districts".equals(district)) district = "";
                    callback.onResolved("Sweden", province, district, "SE");
                } else {
                    // FALLBACK: International
                    resolver.fetchInternationalLocation(lat, lon, callback);
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        }).start();
    }

    private static boolean isValidRegion(String name) {
        return name != null && !name.isEmpty() &&
                !"Not Found".equals(name) && !"Read Error".equals(name);
    }

    private void fetchInternationalLocation(double lat, double lon, GeoCallback callback) {
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(lat, lon, 1, addresses ->
                processGeocoderResult(addresses, callback)
            );
        } else {
            try {
                List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
                processGeocoderResult(addresses, callback);
            } catch (IOException e) {
                callback.onError(e);
            }
        }
    }

    private void processGeocoderResult(List<Address> addresses, GeoCallback callback) {
        if (addresses == null || addresses.isEmpty()) {
            Log.w(TAG, "Geocoder returned no results.");
            callback.onManualEntryRequired();
            return;
        }

        Address addr = addresses.get(0);
        String countryCode = addr.getCountryCode();
        String province = addr.getAdminArea(); // This is often the modern "Län"
        String district = addr.getLocality();

        CountryEntity entity = spatialDao.getCountryByCode(countryCode);
        String countryName;
        if (entity != null) {
            countryName = (entity.nameEn != null) ? entity.nameEn : countryCode;
        } else {
            countryName = addr.getCountryName() != null ? addr.getCountryName() : countryCode;
        }

        callback.onResolved(countryName, province, district, countryCode);
    }
}