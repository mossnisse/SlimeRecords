package nisse.whatsmysocken;

import java.io.File;
import android.util.Log;
import java.util.List;

public class FileUtils {
    public static void deleteFileAtPath(String path) {
        if (path == null || path.isEmpty()) return;

        File file = new File(path);
        if (file.exists()) {
            if (file.delete()) {
                Log.d("FileUtils", "Deleted file: " + path);
            } else {
                Log.e("FileUtils", "Failed to delete file: " + path);
            }
        }
    }
    public static String generateCsv(List<LocationWithPhotos> data) {
        StringBuilder sb = new StringBuilder();

        // Updated Header: Changed "Photos" to "Photo_Filenames"
        sb.append("ID,Latitude,Longitude,Accuracy,Time,Note,Photo_Filenames\n");

        for (LocationWithPhotos item : data) {
            String note = item.location.note != null ? item.location.note : "";
            String escapedNote = note.replace("\"", "\"\"");

            // --- New Logic to Extract Filenames ---
            String filenames = "";
            if (item.photos != null && !item.photos.isEmpty()) {
                StringBuilder nameBuilder = new StringBuilder();
                for (int i = 0; i < item.photos.size(); i++) {
                    File f = new File(item.photos.get(i).filePath);
                    nameBuilder.append(f.getName());
                    // Add a semicolon separator between names, but not after the last one
                    if (i < item.photos.size() - 1) {
                        nameBuilder.append("|");
                    }
                }
                filenames = nameBuilder.toString();
            }

            // Use Locale.US to ensure decimal dots
            String line = String.format(java.util.Locale.US, "%d,%f,%f,%f,\"%s\",\"%s\",\"%s\"\n",
                    item.location.id,
                    item.location.latitude,
                    item.location.longitude,
                    item.location.accuracy,
                    item.location.localTime,
                    escapedNote,
                    filenames // Now inserting the string of names instead of the count
            );
            sb.append(line);
        }
        return sb.toString();
    }
}