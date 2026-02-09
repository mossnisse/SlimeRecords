package nisse.whatsmysocken;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Arrays;
import java.util.Collection;
import static org.junit.Assert.assertEquals;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider; // Needs androidx.test:core
import nisse.whatsmysocken.data.AppDatabase;
import nisse.whatsmysocken.data.SpatialResolver;

@RunWith(Parameterized.class)
public class SpatialResolverTest {
    private final int n, e;
    private final String expectedProv, expectedDist;
    private SpatialResolver resolver;

    // Define the data set
    @Parameterized.Parameters(name = "{index}: Testing {2}, {3}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { 7086319, 761487, "Västerbotten", "Umeå" }, // North
                { 6590809, 687592, "Uppland", "Östra Ryd" },        // City
                { 6564300, 485300, "Närke", "Kvistbro" },            // This will still run!
                { 6555192, 485792, "Närke", "Edsberg" },   // enclave in Kvistbro socken
                { 6520280, 375710, "Dalsland", "Outside Districts" }, // Vänern outside any socken
                { 6316591, 696810, "Gotland", "Vamlingbo"}, // messy geometry
                { 6657987, 633084, "Uppland", "Skuttunge"} // hole in an enclave
        });
    }

    // Constructor receives the data
    public SpatialResolverTest(int n, int e, String prov, String dist) {
        this.n = n;
        this.e = e;
        this.expectedProv = prov;
        this.expectedDist = dist;
    }

    @Before
    public void setup() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(context);
        resolver = new SpatialResolver(context);

        // Give the background thread time to seed the database
        int timeout = 0;
        while (db.spatialDao().getProvinceCount() == 0 && timeout < 50) {
            Thread.sleep(200);
            timeout++;
        }
    }

    // The actual test
    @Test
    public void runSpatialTest() {
        String actualProv = resolver.getProvinceName(n, e);
        String actualDist = resolver.getSockenName(n, e);

        // Adding the actual values to the failure message makes it easier to debug
        assertEquals("Province mismatch at [" + n + ", " + e + "]", expectedProv, actualProv);
        assertEquals("District mismatch at [" + n + ", " + e + "]", expectedDist, actualDist);
    }
}