package nisse.SlimeRecords;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import nisse.SlimeRecords.data.LocalitySuggestion;
import nisse.SlimeRecords.data.LocationDao;
import nisse.SlimeRecords.data.ObservationRecord;
import nisse.SlimeRecords.data.RecentCollector;
import nisse.SlimeRecords.data.RecordFingerprint;
import nisse.SlimeRecords.data.SpeciesAttributes;
import nisse.SlimeRecords.data.UserDatabase;

@RunWith(AndroidJUnit4.class)
public class LocationDaoTest {
    private UserDatabase database;
    private LocationDao dao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, UserDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.locationDao();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void insertsAndLoadsLocationWithPhotos() {
        ObservationRecord record = record(0, 59.1, 18.2, "2026-07-14 10:00:00", "Park");
        dao.insertLocationWithPhotos(record, Arrays.asList("one.jpg", "two.jpg"));

        RecordWithPhotos loaded = dao.getLocationByIdSync(record.id);
        assertNotNull(loaded);
        assertEquals("Park", loaded.location.locality);
        assertEquals(2, loaded.photos.size());
        assertNotNull(dao.getAllLocationsPaged());
    }

    @Test
    public void loadFingerprintsReturnsStoredIdentity() {
        ObservationRecord record = record(0, 59.12345649, 18.00000049,
                "2026-07-14 10:00:00", "Site");
        long id = dao.insertLocation(record);

        List<RecordFingerprint> fingerprints = dao.loadFingerprints();
        assertEquals(1, fingerprints.size());
        assertEquals(id, fingerprints.get(0).id);
        assertEquals(59.12345649, fingerprints.get(0).latitude, 0.0);
        assertEquals(18.00000049, fingerprints.get(0).longitude, 0.0);
        assertEquals("2026-07-14 10:00:00", fingerprints.get(0).localTime);
    }

    @Test
    public void replacementIsAtomicAndReturnsOldPhotoPaths() {
        ObservationRecord original = record(0, 59, 18, "2026-07-14 10:00:00", "Old");
        dao.insertLocationWithPhotos(original, Collections.singletonList("old.jpg"));
        ObservationRecord replacement = record(original.id, 60, 19,
                "2026-07-15 10:00:00", "New");

        List<String> old = dao.replaceLocationWithPhotos(original.id, replacement,
                Collections.singletonList("new.jpg"));
        assertEquals(Collections.singletonList("old.jpg"), old);
        RecordWithPhotos loaded = dao.getLocationByIdSync(original.id);
        assertEquals("New", loaded.location.locality);
        assertEquals("new.jpg", loaded.photos.get(0).filePath);
    }

    @Test
    public void deletionOnlyReturnsPhotoAfterLastReferenceIsRemoved() {
        ObservationRecord first = record(0, 59, 18, "2026-07-14 10:00:00", "One");
        ObservationRecord second = record(0, 60, 19, "2026-07-15 10:00:00", "Two");
        dao.insertLocationWithPhotos(first, Collections.singletonList("shared.jpg"));
        dao.insertLocationWithPhotos(second, Collections.singletonList("shared.jpg"));

        assertTrue(dao.deleteLocationWithPhotos(dao.getLocationByIdSync(first.id)).isEmpty());
        assertEquals(Collections.singletonList("shared.jpg"),
                dao.deleteLocationWithPhotos(dao.getLocationByIdSync(second.id)));
    }

    @Test
    public void individualPhotoDeletionPreservesSharedFileOwnership() {
        ObservationRecord first = record(0, 59, 18, "2026-07-14 10:00:00", "One");
        ObservationRecord second = record(0, 60, 19, "2026-07-15 10:00:00", "Two");
        dao.insertLocationWithPhotos(first, Collections.singletonList("shared.jpg"));
        dao.insertLocationWithPhotos(second, Collections.singletonList("shared.jpg"));

        int firstPhotoId = dao.getLocationByIdSync(first.id).photos.get(0).id;
        int secondPhotoId = dao.getLocationByIdSync(second.id).photos.get(0).id;
        assertFalse(dao.deletePhotoAndIsPathOrphaned(firstPhotoId, "shared.jpg"));
        assertTrue(dao.deletePhotoAndIsPathOrphaned(secondPhotoId, "shared.jpg"));
    }

    @Test
    public void specimenCollectorAndLocalityQueriesReturnExpectedData() throws Exception {
        ObservationRecord first = record(0, 59.0, 18.0, "2026-07-14 10:00:00", "Shared");
        first.attributes = new SpeciesAttributes();
        first.attributes.isSpecimen = true;
        ObservationRecord second = record(0, 59.01, 18.01, "2026-07-15 10:00:00", "Shared");
        second.attributes = new SpeciesAttributes();
        dao.insertLocation(first);
        dao.insertLocation(second);

        assertEquals(1, await(dao.getSpecimenLocations()).size());
        List<LocalitySuggestion> suggestions = await(dao.getNearbyLocalityData(
                58.9, 59.1, 17.9, 18.1));
        assertEquals(1, suggestions.size());
        assertEquals(59.005, suggestions.get(0).latitude, 0.000001);

        for (int i = 0; i < 7; i++) dao.insertRecentCollector(new RecentCollector("C" + i, i));
        List<String> collectors = await(dao.getRecentCollectorNames());
        assertEquals(5, collectors.size());
        assertEquals("C6", collectors.get(0));
    }

    private static ObservationRecord record(long id, double lat, double lon,
                                            String time, String locality) {
        ObservationRecord record = new ObservationRecord();
        record.id = id;
        record.latitude = lat;
        record.longitude = lon;
        record.localTime = time;
        record.locality = locality;
        return record;
    }

    private static <T> T await(LiveData<T> liveData) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        Observer<T> observer = new Observer<T>() {
            @Override
            public void onChanged(T current) {
                value.set(current);
                latch.countDown();
                liveData.removeObserver(this);
            }
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> liveData.observeForever(observer));
        if (!latch.await(2, TimeUnit.SECONDS)) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> liveData.removeObserver(observer));
            throw new AssertionError("LiveData did not emit");
        }
        return value.get();
    }
}
