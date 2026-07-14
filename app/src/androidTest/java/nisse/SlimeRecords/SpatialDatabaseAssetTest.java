package nisse.SlimeRecords;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

import nisse.SlimeRecords.data.CountryEntity;
import nisse.SlimeRecords.data.SpatialDao;
import nisse.SlimeRecords.data.SpatialDatabase;
import nisse.SlimeRecords.data.SpeciesReferenceWithAccepted;

@RunWith(AndroidJUnit4.class)
public class SpatialDatabaseAssetTest {
    private SpatialDao dao;

    @Before
    public void openPackagedDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        SpatialDatabase database = SpatialDatabase.getInstance(context);
        database.getOpenHelper().getReadableDatabase();
        dao = database.spatialDao();
    }

    @Test
    public void countryTableContainsIsoNames() {
        CountryEntity sweden = dao.getCountryByCode("SE");
        assertNotNull(sweden);
        assertEquals("Sweden", sweden.nameEn);
    }

    @Test
    public void synonymSearchReturnsAcceptedTaxonName() {
        List<SpeciesReferenceWithAccepted> results = dao.searchSpeciesWithAccepted(
                "Monima", "la", Collections.singletonList("la"),
                Collections.singletonList("fjärilar"));
        assertFalse(results.isEmpty());
        assertEquals("Monima", results.get(0).getName());
        assertEquals("Orthosia", results.get(0).acceptedName);
    }
}
