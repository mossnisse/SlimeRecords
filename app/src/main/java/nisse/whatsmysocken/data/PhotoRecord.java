package nisse.whatsmysocken.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "photo_table",
        foreignKeys = @ForeignKey(
                entity = LocationRecord.class,
                parentColumns = "id",
                childColumns = "locationId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("locationId")} // Add this line to fix the warning
)
public class PhotoRecord {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public long locationId; // Links to the LocationRecord
    public String filePath;

    public PhotoRecord(long locationId, String filePath) {
        this.locationId = locationId;
        this.filePath = filePath;
    }
}