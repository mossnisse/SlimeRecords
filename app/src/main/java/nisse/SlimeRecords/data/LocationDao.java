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
    public abstract long insertLocation(LocationRecord location);

    @Insert
    public abstract void insertPhoto(PhotoRecord photo);

    @Transaction
    public void insertLocationWithPhotos(LocationRecord location, List<String> photoPaths) {
        long locationId = insertLocation(location);
        for (String path : photoPaths) {
            insertPhoto(new PhotoRecord(locationId, path));
        }
    }

    @Update
    public abstract void updateLocation(LocationRecord location);

    @Transaction
    @Query("SELECT * FROM location_table ORDER BY timestamp DESC")
    public abstract PagingSource<Integer, LocationWithPhotos> getAllLocationsPaged();

    @Query("DELETE FROM photo_table WHERE filePath = :path")
    public abstract void deletePhotoByPath(String path);

    @Transaction
    @Query("SELECT * FROM location_table WHERE id = :id LIMIT 1")
    public abstract LiveData<LocationWithPhotos> getLocationById(long id);

    @Delete
    public abstract void deleteLocation(LocationRecord location);

    @Query("SELECT DISTINCT localityDescription FROM location_table " +
            "WHERE latitude BETWEEN :minLat AND :maxLat " +
            "AND longitude BETWEEN :minLon AND :maxLon " +
            "AND localityDescription IS NOT NULL AND localityDescription != ''")
    public abstract LiveData<List<String>> getNearbyLocalitySuggestions(
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
    public abstract LiveData<List<LocationRecord>> getSpecimenLocations();
}