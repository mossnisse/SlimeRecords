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
        SpecimenCounter.class}, version = 2, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class UserDatabase extends RoomDatabase {

    public abstract LocationDao locationDao();
    private static volatile UserDatabase instance;
    private static final ExecutorService dbExecutor = Executors.newFixedThreadPool(4);
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `specimen_counter` " +
                    "(`id` INTEGER NOT NULL, `nextNumber` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))");
        }
    };

    public static UserDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (UserDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                            UserDatabase.class,
                                    "user_locations.db"
                            )
                            .addMigrations(MIGRATION_1_2)
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
