package nisse.whatsmysocken;

import androidx.lifecycle.LiveData;
import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public abstract class LocationDao {

    @Insert
    public abstract long insertLocation(LocationRecord location); // Returns the new ID

    @Insert
    public abstract void insertPhoto(PhotoRecord photo);

    @Transaction
    public void insertLocationWithPhotos(LocationRecord location, List<String> photoPaths) {
        // Insert the location and get the generated ID
        long locationId = insertLocation(location);

        // Map the paths to PhotoRecords and insert them
        for (String path : photoPaths) {
            insertPhoto(new PhotoRecord(locationId, path));
        }
    }

    @Delete
    public abstract void delete(LocationRecord record);

    @Transaction
    @Query("SELECT * FROM location_table ORDER BY timestamp DESC")
    public abstract PagingSource<Integer, LocationWithPhotos> getAllLocationsPaged();

    @Query("DELETE FROM photo_table WHERE filePath = :path")
    public abstract void deletePhotoByPath(String path);
    @Transaction @Query("SELECT * FROM location_table WHERE id = :id LIMIT 1")
    public abstract LiveData<LocationWithPhotos> getLocationById(long id);

    // used for exporting the data
    @Transaction
    @Query("SELECT * FROM location_table ORDER BY timestamp DESC")
    public abstract LiveData<List<LocationWithPhotos>> getAllLocationsForExport();
}
