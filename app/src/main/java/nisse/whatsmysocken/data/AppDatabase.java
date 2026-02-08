package nisse.whatsmysocken.data;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

@Database(entities = {
        LocationRecord.class, PhotoRecord.class,
        ProvinceEntity.class, ProvinceGeometryEntity.class,
        DistrictEntity.class, DistrictGeometryEntity.class
}, version = 5, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract LocationDao locationDao();
    public abstract SpatialDao spatialDao();
    private static AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "location_database")
                    // Add the migration script here
                    .addMigrations(MIGRATION_4_5)

                    // Keep or update the callback for data seeding
                    .addCallback(new Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
                            Executors.newSingleThreadExecutor().execute(() -> {
                                importAllData(context, getInstance(context));
                            });
                        }

                        @Override
                        public void onOpen(@NonNull SupportSQLiteDatabase db) {
                            super.onOpen(db);
                            // This handles seeding the data after the migration tables are created
                            Executors.newSingleThreadExecutor().execute(() -> {
                                checkAndMigrateData(context, getInstance(context));
                            });
                        }
                    })
                    .build();
        }
        return instance;
    }

    private static void checkAndMigrateData(Context context, AppDatabase db) {
        // If we have no provinces, it means we upgraded from v4 and need to seed the data
        if (db.spatialDao().getProvinceCount() == 0) {
            Log.d("DB", "Migrating from v4 to v5: Seeding spatial data...");
            importAllData(context, db);
        }
    }
    private static void importAllData(Context context, AppDatabase db) {
        try {
            // Import Province Layer
            db.spatialDao().insertProvinces(parseMetadata(context, "province_metadata.csv", ProvinceEntity.class));
            db.spatialDao().insertProvinceGeoms(parseGeometry(context, "province_geometries.csv", ProvinceGeometryEntity.class));

            // Import District Layer
            db.spatialDao().insertDistricts(parseMetadata(context, "district_metadata.csv", DistrictEntity.class));
            db.spatialDao().insertDistrictGeoms(parseGeometry(context, "district_geometries.csv", DistrictGeometryEntity.class));

        } catch (Exception e) {
            Log.e("DB", "Import failed", e);
        }
    }

    // GENERIC METADATA PARSER
    private static <T extends BaseMetadata> List<T> parseMetadata(Context context, String file, Class<T> clazz) throws Exception {
        List<T> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open(file)))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                T item = clazz.getDeclaredConstructor().newInstance();
                item.id = Integer.parseInt(p[0]);
                item.name = p[1].replace("\"", "");
                list.add(item);
            }
        }
        return list;
    }

    // GENERIC GEOMETRY PARSER
    private static <T extends BaseGeometry> List<T> parseGeometry(Context context, String file, Class<T> clazz) throws Exception {
        List<T> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open(file)))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",");
                T g = clazz.getDeclaredConstructor().newInstance();
                g.parentId = Integer.parseInt(p[0]);
                g.minN = Integer.parseInt(p[1]);
                g.minE = Integer.parseInt(p[2]);
                g.maxN = Integer.parseInt(p[3]);
                g.maxE = Integer.parseInt(p[4]);
                g.byteOffset = Long.parseLong(p[5]);
                g.vertexCount = Integer.parseInt(p[6]);
                list.add(g);
            }
        }
        return list;
    }

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 1. Create Metadata Tables
            database.execSQL("CREATE TABLE IF NOT EXISTS `provinces` (`id` INTEGER NOT NULL, `name` TEXT, PRIMARY KEY(`id`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS `districts` (`id` INTEGER NOT NULL, `name` TEXT, PRIMARY KEY(`id`))");

            // 2. Create Province Geometry Table with ForeignKey and Index
            database.execSQL("CREATE TABLE IF NOT EXISTS `province_geometries` (" +
                    "`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`parentId` INTEGER NOT NULL, " +
                    "`minN` INTEGER NOT NULL, `minE` INTEGER NOT NULL, " +
                    "`maxN` INTEGER NOT NULL, `maxE` INTEGER NOT NULL, " +
                    "`byteOffset` INTEGER NOT NULL, `vertexCount` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`parentId`) REFERENCES `provinces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_province_geometries_parentId` ON `province_geometries` (`parentId`)");

            // 3. Create District Geometry Table with ForeignKey and Index
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