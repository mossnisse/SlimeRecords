package nisse.whatsmysocken;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface LocationDao {
    @Transaction
    @Query("SELECT * FROM location_table ORDER BY timestamp DESC") // Use the tableName here!
    LiveData<List<LocationWithPhotos>> getAllLocationsWithPhotos();
    @Insert
    long insertLocation(LocationRecord location); // Returns the new ID
    @Insert
    void insertPhoto(PhotoRecord photo);
    @Delete
    void delete(LocationRecord record);
    @Query("DELETE FROM photo_table WHERE filePath = :path")
    void deletePhotoByPath(String path);
    @Transaction @Query("SELECT * FROM location_table WHERE id = :id LIMIT 1")
    LiveData<LocationWithPhotos> getLocationById(long id);
}