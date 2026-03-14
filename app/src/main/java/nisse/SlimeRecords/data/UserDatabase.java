package nisse.SlimeRecords.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import nisse.SlimeRecords.Converters;

@Database(entities = {LocationRecord.class, PhotoRecord.class, RecentCollector.class}, version = 3, exportSchema = false)
@TypeConverters({Converters.class}) // Add this line
public abstract class UserDatabase extends RoomDatabase {

    public abstract LocationDao locationDao();
    private static volatile UserDatabase instance;
    private static final ExecutorService dbExecutor = Executors.newFixedThreadPool(4);

    public static UserDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (UserDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    UserDatabase.class,
                                    "user_locations.db"
                            )
                            .fallbackToDestructiveMigration()
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