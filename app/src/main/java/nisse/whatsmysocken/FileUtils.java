package nisse.whatsmysocken;

import java.io.File;
import android.util.Log;

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
}