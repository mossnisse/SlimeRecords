package nisse.SlimeRecords;

import androidx.room.Embedded;
import androidx.room.Relation;
import java.util.List;
import nisse.SlimeRecords.data.ObservationRecord;
import nisse.SlimeRecords.data.PhotoRecord;

public class RecordWithPhotos {
    @Embedded
    public ObservationRecord location;

    @Relation(
            parentColumn = "id",
            entityColumn = "locationId"
    )
    public List<PhotoRecord> photos;
}
