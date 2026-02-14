package nisse.whatsmysocken.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "location_table")
public class LocationRecord {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public double latitude;
    public double longitude;
    public long timestamp;
    public float accuracy; // Added this, change to int
    public String localTime;
    public String note;

    public LocationRecord() {
        // Empty constructor for Room/Manual mapping
    }
    public LocationRecord(double latitude, double longitude, long timestamp, float accuracy, String localTime, String note) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.accuracy = accuracy;
        this.localTime = localTime;
        this.note = note;
    }
}