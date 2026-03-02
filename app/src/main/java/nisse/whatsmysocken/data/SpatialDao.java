package nisse.whatsmysocken.data;

import androidx.room.Dao;
import androidx.room.Query;
import java.util.List;

@Dao
public interface SpatialDao {
    // --- Province Layer ---
    @Query("SELECT * FROM province_geometries WHERE :n BETWEEN minN AND maxN AND :e BETWEEN minE AND maxE")
    List<ProvinceGeometryEntity> findProvinceCandidates(int n, int e);

    @Query("SELECT * FROM provinces WHERE id = :id")
    ProvinceEntity getProvinceById(int id);

    // --- District Layer
    @Query("SELECT * FROM district_geometries WHERE :n BETWEEN minN AND maxN AND :e BETWEEN minE AND maxE")
    List<DistrictGeometryEntity> findDistrictCandidates(int n, int e);

    @Query("SELECT * FROM districts WHERE id = :id")
    DistrictEntity getDistrictById(int id);

    @Query("SELECT * FROM species_reference " +
            "WHERE LOWER(name) LIKE LOWER(:query) || '%' " +
            "ORDER BY isSynonym ASC, name ASC " +
            "LIMIT 20")
    List<SpeciesReferenceEntity> searchSpecies(String query);
}
