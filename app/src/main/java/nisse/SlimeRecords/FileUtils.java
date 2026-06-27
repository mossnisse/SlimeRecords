package nisse.SlimeRecords;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import android.util.Log;

public class FileUtils {
    /** Streams all bytes from {@code in} to {@code out} using an 8KB buffer. Does not close either stream. */
    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
    }

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