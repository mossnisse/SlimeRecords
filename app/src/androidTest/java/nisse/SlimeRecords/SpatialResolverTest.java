package nisse.SlimeRecords;

import static org.junit.Assert.assertEquals;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Arrays;
import java.util.Collection;
import nisse.SlimeRecords.data.SpatialResolver;
import nisse.SlimeRecords.data.SpatialDatabase;

@RunWith(Parameterized.class)
public class SpatialResolverTest {

    private final int n, e;
    private final String expectedProv, expectedDist;
    private SpatialResolver resolver;

    @Parameterized.Parameters(name = "{index}: Testing {2}, {3}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { 7086319, 761487, "Västerbotten", "Umeå" },
                { 6590809, 687592, "Uppland", "Östra Ryd" },
                { 6564300, 485300, "Närke", "Kvistbro" },
                { 6555192, 485792, "Närke", "Edsberg" },
                { 6520280, 375710, "Dalsland", "Outside Districts" },
                { 6316591, 696810, "Gotland", "Vamlingbo"},
                { 6657987, 633084, "Uppland", "Skuttunge"}
        });
    }

    public SpatialResolverTest(int n, int e, String prov, String dist) {
        this.n = n;
        this.e = e;
        this.expectedProv = prov;
        this.expectedDist = dist;
    }

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();

        // Force Room to copy the asset database by making a dummy call
        // This replaces your old 'while' loop and SharedPreferences check.
        SpatialDatabase.getInstance(context).getOpenHelper().getReadableDatabase();

        // Initialize the resolver (which internally opens the FileChannels)
        resolver = SpatialResolver.getInstance(context);
    }

    @Test
    public void runSpatialTest() {
        // Use the new single-method approach from your SpatialResolver rewrite
        String actualProv = resolver.getRegionName(n, e, false); // false = Province
        String actualDist = resolver.getRegionName(n, e, true);  // true = District

        assertEquals("Province mismatch at [" + n + ", " + e + "]", expectedProv, actualProv);
        assertEquals("District mismatch at [" + n + ", " + e + "]", expectedDist, actualDist);
    }
}