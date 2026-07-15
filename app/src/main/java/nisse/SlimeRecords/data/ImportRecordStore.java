package nisse.SlimeRecords.data;

import java.util.List;

/** Persistence operations needed by the platform-independent importer. */
public interface ImportRecordStore {
    boolean existsById(long id);
    /** Returns the identity of every stored record for duplicate detection. */
    List<RecordFingerprint> loadFingerprints();
    void insertLocationWithPhotos(ObservationRecord location, List<String> photoPaths);
    List<String> replaceLocationWithPhotos(long existingId,
                                           ObservationRecord location,
                                           List<String> photoPaths);

    /**
     * Persists an imported row. Production storage also advances its durable
     * specimen counter when {@code importedSpecimenNumber} is non-null.
     */
    default void insertImportedLocationWithPhotos(ObservationRecord location,
                                                  List<String> photoPaths,
                                                  Integer importedSpecimenNumber) {
        insertLocationWithPhotos(location, photoPaths);
    }

    /** See {@link #insertImportedLocationWithPhotos}. */
    default List<String> replaceImportedLocationWithPhotos(long existingId,
                                                           ObservationRecord location,
                                                           List<String> photoPaths,
                                                           Integer importedSpecimenNumber) {
        return replaceLocationWithPhotos(existingId, location, photoPaths);
    }
    int getPhotoReferenceCount(String path);
}
