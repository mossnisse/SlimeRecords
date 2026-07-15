package nisse.SlimeRecords.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import nisse.SlimeRecords.Converters;

@Database(entities = {ObservationRecord.class, PhotoRecord.class, RecentCollector.class,
        SpecimenCounter.class}, version = 4, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class UserDatabase extends RoomDatabase {

    public abstract LocationDao locationDao();
    private static volatile UserDatabase instance;
    private static final ExecutorService dbExecutor = Executors.newFixedThreadPool(4);
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            createSpecimenCounterTable(database);
        }
    };

    // Version 2 already has the complete current schema. Version 4 is used so
    // the schema number stays above the version-3 build that was previously tagged.
    static final Migration MIGRATION_2_4 = new Migration(2, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // No schema changes are required.
        }
    };

    // The tagged version-3 schema predates the durable specimen counter.
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            createSpecimenCounterTable(database);
        }
    };

    private static void createSpecimenCounterTable(SupportSQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `specimen_counter` " +
                "(`id` INTEGER NOT NULL, `nextNumber` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))");
    }

    public static UserDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (UserDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                            UserDatabase.class,
                                    "user_locations.db"
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_4, MIGRATION_3_4)
                            .build();
                }
            }
        }
        return instance;
    }

    public static ExecutorService getDbExecutor() {
        return dbExecutor;
    }
}
