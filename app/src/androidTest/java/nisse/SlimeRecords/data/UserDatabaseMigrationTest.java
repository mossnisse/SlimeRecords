package nisse.SlimeRecords.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import nisse.SlimeRecords.RecordWithPhotos;

@RunWith(AndroidJUnit4.class)
public class UserDatabaseMigrationTest {
    private static final String DATABASE_NAME = "user-database-v3-migration-test";

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE_NAME);
    }

    @After
    public void tearDown() {
        context.deleteDatabase(DATABASE_NAME);
    }

    @Test
    public void versionThreeDatabaseMigratesWithoutLosingRecords() {
        createVersionThreeDatabase();

        UserDatabase database = Room.databaseBuilder(context, UserDatabase.class, DATABASE_NAME)
                .addMigrations(UserDatabase.MIGRATION_1_2, UserDatabase.MIGRATION_2_4,
                        UserDatabase.MIGRATION_3_4)
                .allowMainThreadQueries()
                .build();
        try {
            RecordWithPhotos loaded = database.locationDao().getLocationByIdSync(1);
            assertNotNull(loaded);
            assertEquals("Legacy locality", loaded.location.locality);
            assertEquals(1, loaded.photos.size());
            assertEquals("legacy.jpg", loaded.photos.get(0).filePath);
            assertNull(database.locationDao().getSpecimenCounter());

            ObservationRecord specimen = new ObservationRecord();
            specimen.localTime = "2026-07-15 12:00:00";
            specimen.attributes = new SpeciesAttributes();
            specimen.attributes.isSpecimen = true;
            assertEquals(8, database.locationDao().insertSpecimenLocationWithPhotos(
                    specimen, Collections.emptyList(), 7));
            assertEquals(8, database.locationDao().getSpecimenCounter().nextNumber);
        } finally {
            database.close();
        }
    }

    private void createVersionThreeDatabase() {
        SupportSQLiteOpenHelper.Configuration configuration =
                SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(DATABASE_NAME)
                        .callback(new SupportSQLiteOpenHelper.Callback(3) {
                            @Override
                            public void onCreate(SupportSQLiteDatabase database) {
                                database.execSQL("CREATE TABLE IF NOT EXISTS `location_table` " +
                                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, " +
                                        "`timestamp` INTEGER NOT NULL, `accuracy` REAL NOT NULL, " +
                                        "`altitude` REAL NOT NULL, `hasAltitude` INTEGER NOT NULL, " +
                                        "`localTime` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                                        "`countryCode` TEXT NOT NULL, `country` TEXT NOT NULL, " +
                                        "`province` TEXT NOT NULL, `district` TEXT NOT NULL, " +
                                        "`locality` TEXT NOT NULL, `attributes` TEXT)");
                                database.execSQL("CREATE TABLE IF NOT EXISTS `photo_table` " +
                                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`locationId` INTEGER NOT NULL, `filePath` TEXT NOT NULL, " +
                                        "FOREIGN KEY(`locationId`) REFERENCES `location_table`(`id`) " +
                                        "ON UPDATE NO ACTION ON DELETE CASCADE)");
                                database.execSQL("CREATE INDEX IF NOT EXISTS " +
                                        "`index_photo_table_locationId` ON `photo_table` (`locationId`)");
                                database.execSQL("CREATE TABLE IF NOT EXISTS `recent_collectors` " +
                                        "(`name` TEXT NOT NULL, `lastUsed` INTEGER NOT NULL, " +
                                        "PRIMARY KEY(`name`))");
                                database.execSQL("INSERT INTO `location_table` " +
                                        "(`id`, `latitude`, `longitude`, `timestamp`, `accuracy`, " +
                                        "`altitude`, `hasAltitude`, `localTime`, `note`, " +
                                        "`countryCode`, `country`, `province`, `district`, " +
                                        "`locality`, `attributes`) VALUES " +
                                        "(1, 59.0, 18.0, 1, 5.0, 0.0, 0, " +
                                        "'2026-07-14 12:00:00', '', '', '', '', '', " +
                                        "'Legacy locality', NULL)");
                                database.execSQL("INSERT INTO `photo_table` " +
                                        "(`id`, `locationId`, `filePath`) VALUES " +
                                        "(1, 1, 'legacy.jpg')");
                                database.execSQL("INSERT INTO `recent_collectors` " +
                                        "(`name`, `lastUsed`) VALUES ('Legacy collector', 1)");
                            }

                            @Override
                            public void onUpgrade(SupportSQLiteDatabase database,
                                                  int oldVersion, int newVersion) {
                                throw new AssertionError("Unexpected upgrade while creating fixture");
                            }
                        })
                        .build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory()
                .create(configuration);
        helper.getWritableDatabase();
        helper.close();
    }
}
