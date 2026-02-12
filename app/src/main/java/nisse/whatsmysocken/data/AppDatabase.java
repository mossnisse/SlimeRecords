package nisse.whatsmysocken.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {
        LocationRecord.class, PhotoRecord.class,
        ProvinceEntity.class, ProvinceGeometryEntity.class,
        DistrictEntity.class, DistrictGeometryEntity.class
}, version = 5, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract LocationDao locationDao();
    public abstract SpatialDao spatialDao();

    private static volatile AppDatabase userDataInstance;
    private static volatile AppDatabase spatialDataInstance;

    // Restore the Executor
    private static final ExecutorService dbExecutor = Executors.newFixedThreadPool(4);

    // Instance User Data (Persistent)
    public static synchronized AppDatabase getUserDatabase(Context context) {
        if (userDataInstance == null) {
            userDataInstance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "user_locations.db"
                    )
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return userDataInstance;
    }

    // Instance Spatial Data (Read-only Asset)
    public static synchronized AppDatabase getSpatialDatabase(Context context) {
        if (spatialDataInstance == null) {
            spatialDataInstance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "spatial_lookup.db"
                    )
                    .createFromAsset("databases/spatial_lookup.db")
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return spatialDataInstance;
    }

    // Restore the getter for your Activities/Resolvers
    public static ExecutorService getDbExecutor() {
        return dbExecutor;
    }
}