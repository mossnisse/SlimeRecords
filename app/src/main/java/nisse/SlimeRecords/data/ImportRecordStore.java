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
    int getPhotoReferenceCount(String path);
}
