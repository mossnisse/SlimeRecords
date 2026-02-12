package nisse.whatsmysocken.data;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles spatial lookups by combining Room database candidate searches
 * with binary ray-casting checks against coordinate files in assets.
 */
public class SpatialResolver implements AutoCloseable {
    private static volatile SpatialResolver instance;
    private final SpatialDao spatialDao;
    private final Context context;

    private FileChannel provinceChannel;
    private FileChannel districtChannel;
    private FileInputStream provinceStream;
    private FileInputStream districtStream;
    private long provinceBaseOffset;
    private long districtBaseOffset;

    private SpatialResolver(Context context) {
        this.context = context.getApplicationContext();
        // POINT TO THE SPATIAL DB INSTANCE
        spatialDao = AppDatabase.getSpatialDatabase(this.context).spatialDao();
        initChannels();
    }

    public static SpatialResolver getInstance(Context context) {
        if (instance == null) {
            synchronized (SpatialResolver.class) {
                if (instance == null) {
                    instance = new SpatialResolver(context);
                }
            }
        }
        return instance;
    }

    private void initChannels() {
        try {
            // Filenames must match your QGIS Python script exactly
            AssetFileDescriptor pAfd = context.getAssets().openFd("province_coords.bin");
            provinceStream = pAfd.createInputStream();
            provinceChannel = provinceStream.getChannel();
            provinceBaseOffset = pAfd.getStartOffset();

            AssetFileDescriptor dAfd = context.getAssets().openFd("district_coords.bin");
            districtStream = dAfd.createInputStream();
            districtChannel = districtStream.getChannel();
            districtBaseOffset = dAfd.getStartOffset();
        } catch (IOException e) {
            Log.e("SpatialResolver", "Failed to initialize spatial channels from assets", e);
        }
    }

    public String getRegionName(int n, int e, boolean isDistrict) {
        FileChannel channel = isDistrict ? districtChannel : provinceChannel;
        long baseOffset = isDistrict ? districtBaseOffset : provinceBaseOffset;

        if (channel == null) return "Data Error";

        // Query Room for bounding-box candidates
        List<? extends BaseGeometry> candidates = isDistrict ?
                spatialDao.findDistrictCandidates(n, e) : spatialDao.findProvinceCandidates(n, e);

        if (candidates.isEmpty()) return isDistrict ? "Outside Districts" : "Not Found";

        try {
            int id = resolveId(n, e, candidates, channel, baseOffset);
            if (id == -1) return isDistrict ? "Outside Districts" : "Not Found";

            if (isDistrict) {
                DistrictEntity d = spatialDao.getDistrictById(id);
                return (d != null) ? d.name : "Unknown District";
            } else {
                ProvinceEntity p = spatialDao.getProvinceById(id);
                return (p != null) ? p.name : "Unknown Province";
            }
        } catch (IOException ex) {
            Log.e("SpatialResolver", "Binary read error", ex);
            return "Read Error";
        }
    }

    private <G extends BaseGeometry> int resolveId(int n, int e, List<G> candidates, FileChannel fc, long baseOffset) throws IOException {
        Map<Integer, Integer> intersectionCounts = new HashMap<>();

        for (G geom : candidates) {
            int count = countIntersections(fc, baseOffset, geom, n, e);
            intersectionCounts.put(geom.parentId, intersectionCounts.getOrDefault(geom.parentId, 0) + count);
        }

        // Even-Odd Rule
        for (Map.Entry<Integer, Integer> entry : intersectionCounts.entrySet()) {
            if (entry.getValue() % 2 != 0) return entry.getKey();
        }
        return -1;
    }

    private int countIntersections(FileChannel fc, long baseOffset, BaseGeometry geom, int n, int e) throws IOException {
        int intersections = 0;
        long startPos = baseOffset + geom.byteOffset;

        // Each vertex is two 32-bit ints (8 bytes)
        ByteBuffer buffer = ByteBuffer.allocate(geom.vertexCount * 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        fc.read(buffer, startPos);
        buffer.flip();

        int[] vN = new int[geom.vertexCount];
        int[] vE = new int[geom.vertexCount];

        for (int i = 0; i < geom.vertexCount; i++) {
            vN[i] = buffer.getInt(); // Y coordinate from script
            vE[i] = buffer.getInt(); // X coordinate from script
        }

        // Ray Casting algorithm
        for (int i = 0, j = geom.vertexCount - 1; i < geom.vertexCount; j = i++) {
            if (((vN[i] > n) != (vN[j] > n)) &&
                    (e < (long)(vE[j] - vE[i]) * (n - vN[i]) / (vN[j] - vN[i]) + vE[i])) {
                intersections++;
            }
        }
        return intersections;
    }

    @Override
    public void close() {
        try {
            if (provinceStream != null) provinceStream.close();
            if (districtStream != null) districtStream.close();
            instance = null;
        } catch (IOException e) {
            Log.e("SpatialResolver", "Error closing channels", e);
        }
    }
}