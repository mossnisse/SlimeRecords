package nisse.SlimeRecords.data;

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
 * Handles spatial lookups using Room for candidates and binary ray-casting for precision.
 * Updated to use lazy-loading for file channels to respect user settings.
 */
public class SpatialResolver implements AutoCloseable {
    private static volatile SpatialResolver instance;
    private final SpatialDao spatialDao;
    private final Context context;

    // Separate objects for lazy loading
    private FileChannel provinceChannel;
    private FileChannel districtChannel;
    private long provinceBaseOffset;
    private long districtBaseOffset;
    private FileInputStream provinceStream;
    private FileInputStream districtStream;

    private SpatialResolver(Context context) {
        this.context = context.getApplicationContext();
        // Pointing to the new dedicated SpatialDatabase
        spatialDao = SpatialDatabase.getInstance(this.context).spatialDao();
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

    /**
     * Lazily initialize the specific file channel only when needed.
     */
    private synchronized FileChannel getChannel(boolean isDistrict) throws IOException {
        if (isDistrict) {
            // Check if null OR closed
            if (districtChannel == null || !districtChannel.isOpen()) {
                AssetFileDescriptor afd = context.getAssets().openFd("district_coords.bin");
                districtStream = afd.createInputStream();
                districtChannel = districtStream.getChannel();
                districtBaseOffset = afd.getStartOffset();
            }
            return districtChannel;
        } else {
            if (provinceChannel == null || !provinceChannel.isOpen()) {
                AssetFileDescriptor afd = context.getAssets().openFd("province_coords.bin");
                provinceStream = afd.createInputStream();
                provinceChannel = provinceStream.getChannel();
                provinceBaseOffset = afd.getStartOffset();
            }
            return provinceChannel;
        }
    }

    public String getRegionName(int n, int e, boolean isDistrict) {
        synchronized (this) {
            try {
                FileChannel channel = getChannel(isDistrict);
                long baseOffset = isDistrict ? districtBaseOffset : provinceBaseOffset;

                // Query Room for bounding-box candidates
                List<? extends BaseGeometry> candidates = isDistrict ?
                        spatialDao.findDistrictCandidates(n, e) : spatialDao.findProvinceCandidates(n, e);

                if (candidates.isEmpty()) return isDistrict ? "Outside Districts" : "Not Found";

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
                Log.e("SpatialResolver", "Binary read error for " + (isDistrict ? "districts" : "provinces"), ex);
                return "Read Error";
            }
        }
    }

    private <G extends BaseGeometry> int resolveId(int n, int e, List<G> candidates, FileChannel fc, long baseOffset) throws IOException {
        Map<Integer, Integer> intersectionCounts = new HashMap<>();

        for (G geom : candidates) {
            int count = countIntersections(fc, baseOffset, geom, n, e);
            intersectionCounts.put(geom.parentId, intersectionCounts.getOrDefault(geom.parentId, 0) + count);
        }

        for (Map.Entry<Integer, Integer> entry : intersectionCounts.entrySet()) {
            if (entry.getValue() % 2 != 0) return entry.getKey();
        }
        return -1;
    }

    private int countIntersections(FileChannel fc, long baseOffset, BaseGeometry geom, int n, int e) throws IOException {
        int intersections = 0;
        long startPos = baseOffset + geom.byteOffset;

        ByteBuffer buffer = ByteBuffer.allocateDirect(geom.vertexCount * 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        fc.read(buffer, startPos);
        buffer.flip();

        // Get last vertex (j)
        int jIdx = (geom.vertexCount - 1) * 8;
        int vNj = buffer.getInt(jIdx);
        int vEj = buffer.getInt(jIdx + 4);

        for (int i = 0; i < geom.vertexCount; i++) {
            // Get current vertex (i)
            int vNi = buffer.getInt();
            int vEi = buffer.getInt();

            // Ray Casting logic using the direct values
            if (((vNi > n) != (vNj > n)) &&
                    (e < (long)(vEj - vEi) * (n - vNi) / (vNj - vNi) + vEi)) {
                intersections++;
            }

            // Current i becomes the next j
            vNj = vNi;
            vEj = vEi;
        }
        return intersections;
    }

    @Override
    public void close() {
        synchronized (SpatialResolver.class) {
            try {
                if (provinceChannel != null) provinceChannel.close();
                if (districtChannel != null) districtChannel.close();
                if (provinceStream != null) provinceStream.close();
                if (districtStream != null) districtStream.close();
            } catch (IOException e) {
                Log.e("SpatialResolver", "Error closing", e);
            } finally {
                // Nulling these out forces getChannel to re-init next time
                provinceChannel = null;
                districtChannel = null;
                provinceStream = null;
                districtStream = null;
            }
        }
    }
}