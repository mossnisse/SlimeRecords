package nisse.whatsmysocken.data;

import android.database.Cursor;

import androidx.lifecycle.LiveData;
import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;
import nisse.whatsmysocken.LocationWithPhotos;

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

    @Transaction
    @Delete
    public abstract void deleteLocationAndPhotos(LocationRecord location, List<PhotoRecord> photos);

    // --- EXPORT METHODS ---
    // Live Count (UI use)
    @Query("SELECT COUNT(*) FROM location_table")
    public abstract LiveData<Integer> getLocationCount();

    @Query("SELECT * FROM location_table")
    public abstract Cursor getAllLocationsCursor();

    @Query("SELECT * FROM photo_table WHERE locationId = :locId")
    public abstract List<PhotoRecord> getPhotosForLocationSync(long locId);
}