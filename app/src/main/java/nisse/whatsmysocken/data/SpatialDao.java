package nisse.whatsmysocken.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface SpatialDao {
    // --- Province Layer ---
    @Insert
    void insertProvinces(List<ProvinceEntity> provinces);
    @Insert
    void insertProvinceGeoms(List<ProvinceGeometryEntity> geoms);

    @Query("SELECT * FROM province_geometries WHERE :n BETWEEN minN AND maxN AND :e BETWEEN minE AND maxE")
    List<ProvinceGeometryEntity> findProvinceCandidates(int n, int e);

    @Query("SELECT * FROM provinces WHERE id = :id")
    ProvinceEntity getProvinceById(int id);

    // --- District Layer ---
    @Insert
    void insertDistricts(List<DistrictEntity> districts);
    @Insert
    void insertDistrictGeoms(List<DistrictGeometryEntity> geoms);

    @Query("SELECT * FROM district_geometries WHERE :n BETWEEN minN AND maxN AND :e BETWEEN minE AND maxE")
    List<DistrictGeometryEntity> findDistrictCandidates(int n, int e);

    @Query("SELECT * FROM districts WHERE id = :id")
    DistrictEntity getDistrictById(int id);

    @Query("SELECT COUNT(*) FROM provinces")
    int getProvinceCount();
}
