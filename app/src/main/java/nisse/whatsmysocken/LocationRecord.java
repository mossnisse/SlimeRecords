package nisse.whatsmysocken;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "location_table")
public class LocationRecord {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public double latitude;
    public double longitude;
    public long timestamp;
    public String note;

    public LocationRecord(double latitude, double longitude, long timestamp, String note) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.note = note;
    }
}