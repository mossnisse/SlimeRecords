package nisse.whatsmysocken.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        tableName = "district_geometries",
        foreignKeys = @ForeignKey(
                entity = DistrictEntity.class,
                parentColumns = "id",
                childColumns = "parentId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("parentId")}
)
public class DistrictGeometryEntity extends BaseGeometry { }