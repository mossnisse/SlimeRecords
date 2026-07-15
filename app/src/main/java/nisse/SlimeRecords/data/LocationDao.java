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
     * Saves a specimen and reserves its number in the same Room transaction.
     * The preference value is used only to seed the durable counter for users
     * upgrading from an older version.
     *
     * @return the next number to show after this record
     */
    @Transaction
    public int insertSpecimenLocationWithPhotos(ObservationRecord location, List<String> photoPaths,
                                                int preferenceNextNumber) {
        if (location.attributes == null || !location.attributes.isSpecimen) {
            throw new IllegalArgumentException("A specimen record is required");
        }

        SpecimenCounter counter = getSpecimenCounter();
        int assignedNumber = counter != null ? counter.nextNumber : preferenceNextNumber;
        if (assignedNumber < 0) {
            throw new IllegalArgumentException("Specimen number must not be negative");
        }
        int nextNumber = Math.addExact(assignedNumber, 1);

        // The database counter is authoritative after the first specimen save,
        // including if a previous process died before SharedPreferences updated.
        location.attributes.specimenNr = String.valueOf(assignedNumber);
        insertLocationWithPhotos(location, photoPaths);

        if (counter == null) {
            insertSpecimenCounter(new SpecimenCounter(nextNumber));
        } else {
            counter.nextNumber = nextNumber;
            updateSpecimenCounter(counter);
        }
        return nextNumber;
    }

    /** Saves an imported row and keeps the durable counter ahead of its number. */
    @Transaction
    @Override
    public void insertImportedLocationWithPhotos(ObservationRecord location, List<String> photoPaths,
                                                 Integer importedSpecimenNumber) {
        insertLocationWithPhotos(location, photoPaths);
        advanceSpecimenCounterPast(importedSpecimenNumber);
    }

    /** Replaces an imported row and advances the counter in that same transaction. */
    @Transaction
    @Override
    public List<String> replaceImportedLocationWithPhotos(long existingId,
                                                           ObservationRecord location,
                                                           List<String> photoPaths,
                                                           Integer importedSpecimenNumber) {
        List<String> oldPaths = replaceLocationWithPhotos(existingId, location, photoPaths);
        advanceSpecimenCounterPast(importedSpecimenNumber);
        return oldPaths;
    }

    private void advanceSpecimenCounterPast(Integer importedSpecimenNumber) {
        if (importedSpecimenNumber == null) return;
        int requiredNextNumber = Math.addExact(importedSpecimenNumber, 1);
        SpecimenCounter counter = getSpecimenCounter();
        if (counter == null) {
            insertSpecimenCounter(new SpecimenCounter(requiredNextNumber));
        } else if (counter.nextNumber < requiredNextNumber) {
            counter.nextNumber = requiredNextNumber;
            updateSpecimenCounter(counter);
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
            "AND (:allLongitudes = 1 " +
            "OR (:wrapsAntimeridian = 0 AND longitude BETWEEN :minLon AND :maxLon) " +
            "OR (:wrapsAntimeridian = 1 AND (longitude >= :minLon OR longitude <= :maxLon))) " +
            "AND locality IS NOT NULL AND locality != '' " +
            "GROUP BY locality")
    public abstract LiveData<List<LocalitySuggestion>> getNearbyLocalityData(
            double minLat, double maxLat, double minLon, double maxLon,
            boolean wrapsAntimeridian, boolean allLongitudes);

    // --- EXPORT METHODS ---
    @Query("SELECT COUNT(*) FROM location_table")
    public abstract LiveData<Integer> getLocationCount();

    // The value itself is irrelevant. Referencing both tables makes Room emit
    // whenever any exportable record or photo link is inserted, updated, or deleted.
    @Query("SELECT (SELECT COUNT(*) FROM location_table) + (SELECT COUNT(*) FROM photo_table)")
    public abstract LiveData<Integer> getExportContentSignal();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertRecentCollector(RecentCollector collector);

    @Query("SELECT * FROM specimen_counter WHERE id = 1")
    public abstract SpecimenCounter getSpecimenCounter();

    @Insert
    public abstract void insertSpecimenCounter(SpecimenCounter counter);

    @Update
    public abstract void updateSpecimenCounter(SpecimenCounter counter);

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
