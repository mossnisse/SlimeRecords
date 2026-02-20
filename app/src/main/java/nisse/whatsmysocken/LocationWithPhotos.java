package nisse.whatsmysocken;

import androidx.room.Embedded;
import androidx.room.Relation;
import java.util.List;
import nisse.whatsmysocken.data.LocationRecord;
import nisse.whatsmysocken.data.PhotoRecord;

public class LocationWithPhotos {
    @Embedded
    public LocationRecord location;

    @Relation(
            parentColumn = "id",
            entityColumn = "locationId"
    )
    public List<PhotoRecord> photos;
}
