package nisse.whatsmysocken.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        tableName = "province_geometries",
        foreignKeys = @ForeignKey(
                entity = ProvinceEntity.class,
                parentColumns = "id",
                childColumns = "parentId", // This matches the column name below
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("parentId")}
)
public class ProvinceGeometryEntity extends BaseGeometry { }