package nisse.SlimeRecords.data;

public class LocalitySuggestion {
    public String name;
    public double latitude;
    public double longitude;
    @androidx.room.Ignore
    public float distance; // We'll fill this in the ViewModel

    public LocalitySuggestion(String name, double latitude, double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
