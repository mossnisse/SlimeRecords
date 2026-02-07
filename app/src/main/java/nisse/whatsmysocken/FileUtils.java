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

        // CSV Header
        sb.append("ID,Latitude,Longitude,Accuracy,Time,Note,Photos\n");

        for (LocationWithPhotos item : data) {
            sb.append(item.location.id).append(",");
            sb.append(item.location.latitude).append(",");
            sb.append(item.location.longitude).append(",");
            sb.append(item.location.accuracy).append(",");

            // Time is usually safe, but quotes don't hurt
            sb.append("\"").append(item.location.localTime).append("\",");

            // Handle the Note with standard CSV escaping
            String note = item.location.note != null ? item.location.note : "";
            String escapedNote = note.replace("\"", "\"\"");
            sb.append("\"").append(escapedNote).append("\",");

            sb.append(item.photos != null ? item.photos.size() : 0);
            sb.append("\n");
        }
        return sb.toString();
    }
}