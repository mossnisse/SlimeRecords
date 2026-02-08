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
public class SpatialResolver {
    private final SpatialDao spatialDao;
    private final Context context;

    public SpatialResolver(Context context) {
        this.context = context;
        this.spatialDao = AppDatabase.getInstance(context).spatialDao();
    }

    /**
     * The Master Generic Resolver
     */
    private <G extends BaseGeometry> int resolveId(int n, int e, List<G> candidates, FileChannel fc, long baseOffset) throws IOException {
        Map<Integer, Integer> intersectionCounts = new HashMap<>();

        for (G geom : candidates) {
            int count = countIntersections(fc, baseOffset, geom, n, e);
            int current = intersectionCounts.getOrDefault(geom.parentId, 0);
            intersectionCounts.put(geom.parentId, current + count);
        }

        for (Map.Entry<Integer, Integer> entry : intersectionCounts.entrySet()) {
            if (entry.getValue() % 2 != 0) return entry.getKey();
        }
        return -1;
    }

    // --- Public API for the App ---

    public String getProvinceName(int n, int e) {
        List<ProvinceGeometryEntity> candidates = spatialDao.findProvinceCandidates(n, e);
        if (candidates.isEmpty()) return "Unknown Socken";

        // Open the asset and channel ONCE
        try (AssetFileDescriptor afd = context.getAssets().openFd("province_coords.bin");
             FileInputStream fis = afd.createInputStream();
             FileChannel fc = fis.getChannel()) {

            int id = resolveId(n, e, candidates, fc, afd.getStartOffset());
            if (id == -1) return "Outside Provinces";

            ProvinceEntity d = spatialDao.getProvinceById(id);
            return d != null ? d.name : "Unknown Province";
        } catch (IOException ex) {
            Log.e("SpatialResolver", "Error reading province_coords.bin", ex);
            return "Error reading data";
        }
    }

    public String getSockenName(int n, int e) {
        List<DistrictGeometryEntity> candidates = spatialDao.findDistrictCandidates(n, e);
        if (candidates.isEmpty()) return "Unknown District";

        // Open the asset and channel ONCE
        try (AssetFileDescriptor afd = context.getAssets().openFd("district_coords.bin");
             FileInputStream fis = afd.createInputStream();
             FileChannel fc = fis.getChannel()) {

            int id = resolveId(n, e, candidates, fc, afd.getStartOffset());
            if (id == -1) return "Outside Districts";

            DistrictEntity d = spatialDao.getDistrictById(id);
            return d != null ? d.name : "Unknown District";
        } catch (IOException ex) {
            Log.e("SpatialResolver", "Error reading district_coords.bin", ex);
            return "Error reading data";
        }
    }

    // --- The Core Math (Identical for all layers) ---

    private int countIntersections(FileChannel fc, long baseOffset, BaseGeometry geom, int n, int e) throws IOException {
        int intersections = 0;
        long startPos = baseOffset + geom.byteOffset;

        ByteBuffer buffer = ByteBuffer.allocate(geom.vertexCount * 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        fc.read(buffer, startPos);
        buffer.flip();

        int[] vN = new int[geom.vertexCount];
        int[] vE = new int[geom.vertexCount];
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
}
