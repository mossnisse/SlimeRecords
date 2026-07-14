package nisse.SlimeRecords.data;

import java.util.List;

/** Persistence operations needed by the platform-independent importer. */
public interface ImportRecordStore {
    boolean existsById(long id);
    Long findIdByFingerprint(double latitude, double longitude, String localTime);
    void insertLocationWithPhotos(ObservationRecord location, List<String> photoPaths);
    List<String> replaceLocationWithPhotos(long existingId,
                                           ObservationRecord location,
                                           List<String> photoPaths);
    int getPhotoReferenceCount(String path);
}
