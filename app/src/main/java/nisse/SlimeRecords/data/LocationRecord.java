package nisse.SlimeRecords.data;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "location_table")
public class LocationRecord {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public double latitude;
    public double longitude;
    public long timestamp;
    public float accuracy;
    public double altitude;
    public boolean hasAltitude;
    @NonNull
    public String localTime ="";
    @NonNull
    public String note = "";
    @NonNull
    public String localityDescription = "";
    @Nullable
    public SpeciesAttributes attributes;

    public LocationRecord() {
        // Empty constructor for Room/Manual mapping
    }
    public LocationRecord(double latitude, double longitude, double altitude, long timestamp, float accuracy, @NonNull String localTime, @NonNull String note) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.timestamp = timestamp;
        this.accuracy = accuracy;
        this.localTime = localTime;
        this.note = note;
    }
}