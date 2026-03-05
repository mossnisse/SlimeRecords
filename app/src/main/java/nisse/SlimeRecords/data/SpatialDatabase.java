package nisse.SlimeRecords.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {
        ProvinceEntity.class, ProvinceGeometryEntity.class,
        DistrictEntity.class, DistrictGeometryEntity.class,
        SpeciesReferenceEntity.class
}, version = 1, exportSchema = false)
public abstract class SpatialDatabase extends RoomDatabase {

    public abstract SpatialDao spatialDao();
    private static volatile SpatialDatabase instance;

    public static SpatialDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (SpatialDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    SpatialDatabase.class,
                                    "spatial_lookup.db"
                            )
                            .createFromAsset("databases/spatial_lookup.db")
                            // Asset DBs usually shouldn't migrate;
                            // if the schema changes, you ship a new asset.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
