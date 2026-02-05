package nisse.whatsmysocken;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {LocationRecord.class, PhotoRecord.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract LocationDao locationDao();
    private static AppDatabase instance;
    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "location_database")
                    .fallbackToDestructiveMigration()
                    //.allowMainThreadQueries() // Only for simplicity; use threads for real apps
                    .build();
        }
        return instance;
    }
}
