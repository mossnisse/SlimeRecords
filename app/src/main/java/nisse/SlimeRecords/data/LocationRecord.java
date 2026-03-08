package nisse.SlimeRecords.data;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "location_table")
public class LocationRecord {
    @PrimaryKey(autoGenerate = true)
    public long id;          // local identifier in the db it's not an global id.
    public double latitude;  // DwC decimalLatitude
    public double longitude;  // DwC decimalLongitude  DwC geodeticDatum?
    public long timestamp;
    public float accuracy;  // DwC coordinateUncertaintyInMeters
    public double altitude;  // DwC verbatimElevation  DwC verticalDatum?
    public boolean hasAltitude;
    @NonNull
    public String localTime ="";  // DwC eventDate
    @NonNull
    public String note = "";  // DwC occurrenceRemarks
    @NonNull
    public String locality = "";  // DwC Ok
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