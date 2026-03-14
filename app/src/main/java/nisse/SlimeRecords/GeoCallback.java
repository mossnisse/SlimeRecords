package nisse.SlimeRecords;

public interface GeoCallback {
    // Called when the Geocoder finds data
    void onResolved(String country, String province, String district);

    // Called if there is no internet or the Geocoder fails
    void onManualEntryRequired();

    // Optional: Called if an error occurs
    void onError(Exception e);
}
