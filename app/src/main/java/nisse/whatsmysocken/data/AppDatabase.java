package nisse.whatsmysocken.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
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

    private static volatile AppDatabase instance;
    private static final String DB_NAME = "location_database";
    private static final String PREFS_NAME = "db_prefs";
    private static final String KEY_SPATIAL_SEEDED = "spatial_data_seeded_v5";

    // Reusable executor for database write operations
    private static final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private static final MutableLiveData<Boolean> isReady = new MutableLiveData<>(false);

    public static LiveData<Boolean> getIsReady() {
        return isReady;
    }

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, DB_NAME)
                            .addMigrations(MIGRATION_4_5)
                            .addCallback(new Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                    // First install: Seed immediately
                                    seedData(context.getApplicationContext());
                                }

                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    super.onOpen(db);
                                    // Migration scenario: onCreate isn't called, so we check here
                                    seedData(context.getApplicationContext());
                                }
                            })
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }

    /**
     * Robust Seeding Method
     * 1. Checks SharedPreferences (Fastest)
     * 2. Checks DB Count (Double-check)
     * 3. Runs in Transaction (Atomic Safety)
     */
    private static void seedData(Context context) {
        dbExecutor.execute(() -> {
            // 1. Fast Check: Avoid touching the DB if we know we are done
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs.getBoolean(KEY_SPATIAL_SEEDED, false)) {
                isReady.postValue(true);
                return;
            }

            AppDatabase db = getInstance(context);
            try {
                db.runInTransaction(() -> {
                    // 2. Safety Check: Ensure DB is actually empty inside the transaction lock
                    if (db.spatialDao().getProvinceCount() > 0) {
                        Log.d("DB", "Data already exists. Marking as seeded.");
                        prefs.edit().putBoolean(KEY_SPATIAL_SEEDED, true).apply();
                        return;
                    }

                    Log.d("DB", "Starting Spatial Data Import...");
                    long startTime = System.currentTimeMillis();

                    try {
                        importAllData(context, db);
                        // Only set this flag if importAllData throws no exceptions
                        prefs.edit().putBoolean(KEY_SPATIAL_SEEDED, true).apply();
                        Log.d("DB", "Import completed in " + (System.currentTimeMillis() - startTime) + "ms");
                    } catch (Exception e) {
                        // Throwing exception here rolls back the transaction automatically
                        throw new RuntimeException("Import failed", e);
                    }
                    prefs.edit().putBoolean(KEY_SPATIAL_SEEDED, true).apply();
                    isReady.postValue(true); // DATA IS FINALLY READY
                });
            } catch (Exception e) {
                Log.e("DB", "Seeding failed. Transaction rolled back.", e);
                // Note: We do NOT set the SharedPref flag here, so it tries again next launch.
            }
        });
    }

    private static void importAllData(Context context, AppDatabase db) throws Exception {
        // Import Province Layer
        List<ProvinceEntity> provinces = parseMetadata(context, "province_metadata.csv", ProvinceEntity.class);
        db.spatialDao().insertProvinces(provinces);

        List<ProvinceGeometryEntity> provGeoms = parseGeometry(context, "province_geometries.csv", ProvinceGeometryEntity.class);
        db.spatialDao().insertProvinceGeoms(provGeoms);

        // Import District Layer
        List<DistrictEntity> districts = parseMetadata(context, "district_metadata.csv", DistrictEntity.class);
        db.spatialDao().insertDistricts(districts);

        List<DistrictGeometryEntity> distGeoms = parseGeometry(context, "district_geometries.csv", DistrictGeometryEntity.class);
        db.spatialDao().insertDistrictGeoms(distGeoms);
    }

    // --- PARSERS REMAIN UNCHANGED BUT ARE NOW CALLED SAFELY ---

    private static <T extends BaseMetadata> List<T> parseMetadata(Context context, String file, Class<T> clazz) throws Exception {
        List<T> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open(file)))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                // Regex to split by comma, ignoring commas inside quotes
                String[] p = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                T item = clazz.getDeclaredConstructor().newInstance();
                item.id = Integer.parseInt(p[0].trim());
                item.name = p[1].replace("\"", "").trim();
                list.add(item);
            }
        }
        return list;
    }

    private static <T extends BaseGeometry> List<T> parseGeometry(Context context, String file, Class<T> clazz) throws Exception {
        List<T> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open(file)))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",");
                T g = clazz.getDeclaredConstructor().newInstance();
                g.parentId = Integer.parseInt(p[0].trim());
                g.minN = Integer.parseInt(p[1].trim());
                g.minE = Integer.parseInt(p[2].trim());
                g.maxN = Integer.parseInt(p[3].trim());
                g.maxE = Integer.parseInt(p[4].trim());
                g.byteOffset = Long.parseLong(p[5].trim());
                g.vertexCount = Integer.parseInt(p[6].trim());
                list.add(g);
            }
        }
        return list;
    }

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            //
            // Tables created here, populated via onOpen callback
            database.execSQL("CREATE TABLE IF NOT EXISTS `provinces` (`id` INTEGER NOT NULL, `name` TEXT, PRIMARY KEY(`id`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS `districts` (`id` INTEGER NOT NULL, `name` TEXT, PRIMARY KEY(`id`))");

            database.execSQL("CREATE TABLE IF NOT EXISTS `province_geometries` (" +
                    "`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`parentId` INTEGER NOT NULL, " +
                    "`minN` INTEGER NOT NULL, `minE` INTEGER NOT NULL, " +
                    "`maxN` INTEGER NOT NULL, `maxE` INTEGER NOT NULL, " +
                    "`byteOffset` INTEGER NOT NULL, `vertexCount` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`parentId`) REFERENCES `provinces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_province_geometries_parentId` ON `province_geometries` (`parentId`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `district_geometries` (" +
                    "`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`parentId` INTEGER NOT NULL, " +
                    "`minN` INTEGER NOT NULL, `minE` INTEGER NOT NULL, " +
                    "`maxN` INTEGER NOT NULL, `maxE` INTEGER NOT NULL, " +
                    "`byteOffset` INTEGER NOT NULL, `vertexCount` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`parentId`) REFERENCES `districts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_district_geometries_parentId` ON `district_geometries` (`parentId`)");
        }
    };
}