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
import nisse.SlimeRecords.RecordWithPhotos;

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
    public abstract PagingSource<Integer, RecordWithPhotos> getAllLocationsPaged();

    @Query("DELETE FROM photo_table WHERE id = :photoId")
    public abstract void deletePhotoById(int photoId);

    @Transaction
    @Query("SELECT * FROM location_table WHERE id = :id LIMIT 1")
    public abstract LiveData<RecordWithPhotos> getLocationById(long id);

    @Transaction // Essential because this joins two tables
    @Query("SELECT * FROM location_table WHERE id = :id")
    public abstract RecordWithPhotos getLocationByIdSync(long id);

    // This helper will check if a record exists by its unique "fingerprint"
    // in case the ID column is missing or we are in "SKIP" mode.
    // Coordinates are compared rounded to 6 decimals because the CSV export
    // writes %.6f, so re-imported values never exactly match the stored doubles.
    @Query("SELECT id FROM location_table WHERE ROUND(latitude, 6) = ROUND(:lat, 6) AND ROUND(longitude, 6) = ROUND(:lon, 6) AND localTime = :time LIMIT 1")
    public abstract Long findIdByFingerprint(double lat, double lon, String time);

    @Delete
    public abstract void deleteLocation(ObservationRecord location);

    @Query("SELECT COUNT(*) FROM photo_table WHERE filePath = :path")
    public abstract int getPhotoReferenceCount(String path);

    @Query("SELECT EXISTS(SELECT 1 FROM location_table WHERE id = :id)")
    public abstract boolean existsById(long id);

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
    public abstract List<RecordWithPhotos> getAllLocationsWithPhotosSync();

    // NOTE: matches against the raw Gson JSON stored by Converters; if the
    // serialized field name or format of SpeciesAttributes.isSpecimen ever
    // changes, this query must be updated too.
    @Query("SELECT * FROM location_table WHERE attributes LIKE '%\"isSpecimen\":true%'")
    public abstract LiveData<List<ObservationRecord>> getSpecimenLocations();
}