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
public abstract class LocationDao implements ImportRecordStore {

    @Insert
    public abstract long insertLocation(ObservationRecord location);

    @Insert
    public abstract void insertPhoto(PhotoRecord photo);

    @Transaction
    public void insertLocationWithPhotos(ObservationRecord location, List<String> photoPaths) {
        long locationId = insertLocation(location);
        // Room returns the generated key but does not write it back to a plain Java field.
        // Keep the in-memory model consistent so callers can immediately address the record.
        location.id = locationId;
        for (String path : photoPaths) {
            insertPhoto(new PhotoRecord(locationId, path));
        }
    }

    /**
     * Replaces a location and its photo links atomically. The returned paths
     * belong to the old record and may be deleted from disk after this
     * transaction commits, provided no other record still references them.
     */
    @Transaction
    public List<String> replaceLocationWithPhotos(long existingId,
                                                   ObservationRecord location,
                                                   List<String> photoPaths) {
        List<String> oldPhotoPaths = new java.util.ArrayList<>();
        RecordWithPhotos oldRecord = getLocationByIdSync(existingId);
        if (oldRecord != null) {
            if (oldRecord.photos != null) {
                for (PhotoRecord photo : oldRecord.photos) {
                    oldPhotoPaths.add(photo.filePath);
                    deletePhotoById(photo.id);
                }
            }
            deleteLocation(oldRecord.location);
        }

        location.id = existingId;
        insertLocationWithPhotos(location, photoPaths);
        return oldPhotoPaths;
    }

    /**
     * Deletes a location and its photo links atomically. The returned paths are
     * no longer referenced by any record and may be deleted from disk after
     * this transaction commits.
     */
    @Transaction
    public List<String> deleteLocationWithPhotos(RecordWithPhotos item) {
        List<String> orphanedPaths = new java.util.ArrayList<>();
        if (item.photos != null) {
            for (PhotoRecord photo : item.photos) {
                deletePhotoById(photo.id);
                if (getPhotoReferenceCount(photo.filePath) == 0) {
                    orphanedPaths.add(photo.filePath);
                }
            }
        }
        deleteLocation(item.location);
        return orphanedPaths;
    }

    @Update
    public abstract void updateLocation(ObservationRecord location);

    @Transaction
    @Query("SELECT * FROM location_table ORDER BY timestamp DESC")
    public abstract PagingSource<Integer, RecordWithPhotos> getAllLocationsPaged();

    @Query("DELETE FROM photo_table WHERE id = :photoId")
    public abstract void deletePhotoById(int photoId);

    /** Removes a photo link and checks file ownership in one database transaction. */
    @Transaction
    public boolean deletePhotoAndIsPathOrphaned(int photoId, String path) {
        deletePhotoById(photoId);
        return getPhotoReferenceCount(path) == 0;
    }

    @Transaction
    @Query("SELECT * FROM location_table WHERE id = :id LIMIT 1")
    public abstract LiveData<RecordWithPhotos> getLocationById(long id);

    @Transaction // Essential because this joins two tables
    @Query("SELECT * FROM location_table WHERE id = :id")
    public abstract RecordWithPhotos getLocationByIdSync(long id);

    // Loaded once per import so duplicate detection does not need a table
    // scan per CSV row; the 6-decimal rounding lives in ImportProcessor.
    @Query("SELECT id, latitude, longitude, localTime FROM location_table")
    public abstract List<RecordFingerprint> loadFingerprints();

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
