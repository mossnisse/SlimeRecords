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

/*
* a class to look up an geographical regions/polygons from an coordinate
* the coordinate system has to be expressable  in ints
* check the QGIS folder and the script to export the .csv and .bin files that is used
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

    // Private constructor
    private SpatialResolver(Context context) {
        this.context = context.getApplicationContext();
        this.spatialDao = AppDatabase.getInstance(this.context).spatialDao();
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
            // Initialize Province Channel
            AssetFileDescriptor pAfd = context.getAssets().openFd("province_coords.bin");
            provinceStream = pAfd.createInputStream();
            provinceChannel = provinceStream.getChannel();
            provinceBaseOffset = pAfd.getStartOffset();

            // Initialize District Channel
            AssetFileDescriptor dAfd = context.getAssets().openFd("district_coords.bin");
            districtStream = dAfd.createInputStream();
            districtChannel = districtStream.getChannel();
            districtBaseOffset = dAfd.getStartOffset();
        } catch (IOException e) {
            Log.e("SpatialResolver", "Failed to initialize spatial channels", e);
        }
    }

    public String getProvinceName(int n, int e) {
        if (provinceChannel == null) return "Data Error";

        List<ProvinceGeometryEntity> candidates = spatialDao.findProvinceCandidates(n, e);
        if (candidates.isEmpty()) return "Outside Province";

        try {
            int id = resolveId(n, e, candidates, provinceChannel, provinceBaseOffset);
            if (id == -1) return "Outside Provinces";

            ProvinceEntity p = spatialDao.getProvinceById(id);
            return p != null ? p.name : "Unknown Province";
        } catch (IOException ex) {
            return "Error reading data";
        }
    }

    public String getSockenName(int n, int e) {
        if (districtChannel == null) return "Data Error";

        List<DistrictGeometryEntity> candidates = spatialDao.findDistrictCandidates(n, e);
        if (candidates.isEmpty()) return "Outside Districts";

        try {
            int id = resolveId(n, e, candidates, districtChannel, districtBaseOffset);
            if (id == -1) return "Outside Districts";

            DistrictEntity d = spatialDao.getDistrictById(id);
            return d != null ? d.name : "Unknown District";
        } catch (IOException ex) {
            return "Error reading data";
        }
    }

    private <G extends BaseGeometry> int resolveId(int n, int e, List<G> candidates, FileChannel fc, long baseOffset) throws IOException {
        // We use a Map to handle Multi-Polygons (where one ID has multiple geometry records)
        Map<Integer, Integer> intersectionCounts = new HashMap<>();

        for (G geom : candidates) {
            int count = countIntersections(fc, baseOffset, geom, n, e);

            // Accumulate intersections for this specific parentId
            int current = 0;
            if (intersectionCounts.containsKey(geom.parentId)) {
                current = intersectionCounts.get(geom.parentId);
            }
            intersectionCounts.put(geom.parentId, current + count);
        }

        // Even-Odd Rule: If total intersections for an ID is odd, the point is INSIDE
        for (Map.Entry<Integer, Integer> entry : intersectionCounts.entrySet()) {
            if (entry.getValue() % 2 != 0) {
                return entry.getKey();
            }
        }
        return -1;
    }

    private int countIntersections(FileChannel fc, long baseOffset, BaseGeometry geom, int n, int e) throws IOException {
        int intersections = 0;
        long startPos = baseOffset + geom.byteOffset;

        ByteBuffer buffer = ByteBuffer.allocate(geom.vertexCount * 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        fc.read(buffer, startPos);
        buffer.flip();

        int[] vN = new int[geom.vertexCount];
        int[] vE = new int[geom.vertexCount];

        // To perform the wrap-around (j=i-1), we still need the coordinates
        // accessible. Reading them into a local stack array is faster than
        // multiple buffer.get() calls in the loop.
        for (int i = 0; i < geom.vertexCount; i++) {
            vN[i] = buffer.getInt();
            vE[i] = buffer.getInt();
        }

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
            provinceChannel = null;
            districtChannel = null;
            instance = null; // Clear instance on close
        } catch (IOException e) {
            Log.e("SpatialResolver", "Error closing channels", e);
        }
    }
}