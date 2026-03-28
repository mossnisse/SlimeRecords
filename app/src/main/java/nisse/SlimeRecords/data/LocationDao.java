package nisse.SlimeRecords.data;

import androidx.lifecycle.LiveData;
import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import java.util.List;
import nisse.SlimeRecords.LocationWithPhotos;

@Dao
public abstract class LocationDao {

    @Insert
    public abstract long insertLocation(ObservationRecord location);

    @Insert
    public abstract void insertPhoto(PhotoRecord photo);

    @Transaction
    public void insertLocationWithPhotos(ObservationRecord location, List<String> photoPaths) {
        long locationId = insertLocation(location);
        for (String path : photoPaths) {
            insertPhoto(new PhotoRecord(locationId, path));
        }
    }

    @Update
    public abstract void updateLocation(ObservationRecord location);

    @Transaction
    @Query("SELECT * FROM location_table ORDER BY timestamp DESC")
    public abstract PagingSource<Integer, LocationWithPhotos> getAllLocationsPaged();

    @Query("DELETE FROM photo_table WHERE id = :photoId")
    public abstract void deletePhotoById(int photoId);

    @Transaction
    @Query("SELECT * FROM location_table WHERE id = :id LIMIT 1")
    public abstract LiveData<LocationWithPhotos> getLocationById(long id);

    @Transaction // Essential because this joins two tables
    @Query("SELECT * FROM location_table WHERE id = :id")
    public abstract LocationWithPhotos getLocationByIdSync(long id);

    // This helper will check if a record exists by its unique "fingerprint"
    // in case the ID column is missing or we are in "SKIP" mode.
    @Query("SELECT id FROM location_table WHERE latitude = :lat AND longitude = :lon AND localTime = :time LIMIT 1")
    public abstract Long findIdByFingerprint(double lat, double lon, String time);

    @Delete
    public abstract void deleteLocation(ObservationRecord location);

    @Query("SELECT COUNT(*) FROM photo_table WHERE filePath = :path")
    public abstract int getPhotoReferenceCount(String path);

    @Query("SELECT EXISTS(SELECT 1 FROM location_table WHERE id = :id)")
    public abstract boolean existsById(long id);

    @Transaction
    void replaceLocationWithPhotos(ObservationRecord record, List<String> photoPaths) {
        deleteLocation(record); // Room uses the ID in the 'record' object to match
        insertLocationWithPhotos(record, photoPaths);
    }

    @Query("SELECT locality as name, AVG(latitude) as latitude, AVG(longitude) as longitude " +
            "FROM location_table " +
            "WHERE latitude BETWEEN :minLat AND :maxLat " +
            "AND longitude BETWEEN :minLon AND :maxLon " +
            "AND locality IS NOT NULL AND locality != '' " +
            "GROUP BY locality")
    public abstract LiveData<List<LocalitySuggestion>> getNearbyLocalityData(
            double minLat, double maxLat, double minLon, double maxLon);

    // --- EXPORT METHODS ---
    @Query("SELECT COUNT(*) FROM location_table")
    public abstract LiveData<Integer> getLocationCount();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertRecentCollector(RecentCollector collector);

    @Query("SELECT name FROM recent_collectors ORDER BY lastUsed DESC LIMIT 5")
    public abstract LiveData<List<String>> getRecentCollectorNames();

    @Transaction
    @Query("SELECT * FROM location_table ORDER BY timestamp DESC")
    public abstract List<LocationWithPhotos> getAllLocationsWithPhotosSync();

    @Query("SELECT * FROM location_table WHERE attributes LIKE '%\"isSpecimen\":true%'")
    public abstract LiveData<List<ObservationRecord>> getSpecimenLocations();
}