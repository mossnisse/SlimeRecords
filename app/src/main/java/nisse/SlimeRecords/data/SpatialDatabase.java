package nisse.SlimeRecords.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

// Added CountryEntity.class here and bumped version to 2
@Database(entities = {
        ProvinceEntity.class, ProvinceGeometryEntity.class,
        DistrictEntity.class, DistrictGeometryEntity.class,
        SpeciesReferenceEntity.class,
        CountryEntity.class
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
                            // Fallback will wipe the local DB and copy the new asset
                            // if version numbers don't match.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}