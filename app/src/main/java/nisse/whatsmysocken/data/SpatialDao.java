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

    @Query("SELECT s1.*, s2.name AS acceptedName " +
            "FROM species_reference s1 " +
            "LEFT JOIN species_reference s2 ON s1.dyntaxaID = s2.dyntaxaID " +
            "AND s2.isSynonym = 0 AND s2.language = :prefLang " +
            "WHERE s1.name LIKE :query || '%' " +
            "AND s1.language IN (:searchLangs) " + // Dynamic filter
            "AND s1.taxonGroup IN (:searchGroups) " + // Dynamic filter
            "ORDER BY s1.isSynonym, s1.name " +
            "LIMIT 50")
    List<SpeciesReferenceWithAccepted> searchSpeciesWithAccepted(
            String query,
            String prefLang,
            List<String> searchLangs,
            List<String> searchGroups
    );

    @Query("SELECT * FROM species_reference " +
            "WHERE dyntaxaID = :tID " +
            "AND language = :lang " +
            "AND isSynonym = 0 LIMIT 1")
    SpeciesReferenceEntity getAcceptedName(int tID, String lang);
}
