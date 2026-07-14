package nisse.SlimeRecords;

import org.junit.Test;

import static org.junit.Assert.*;

import nisse.SlimeRecords.coords.Coordinates;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void emptyPrintRangeBoundIsUnbounded() {
        assertNull(PrintActivity.parseNullableInt("  "));
        assertFalse(PrintActivity.isInvalidRangeBound("  "));
    }

    @Test
    public void validPrintRangeBoundIsParsed() {
        assertEquals(Integer.valueOf(42), PrintActivity.parseNullableInt(" 42 "));
        assertFalse(PrintActivity.isInvalidRangeBound("42"));
    }

    @Test
    public void overflowingPrintRangeBoundIsRejected() {
        String overflow = "999999999999999999999";
        assertNull(PrintActivity.parseNullableInt(overflow));
        assertTrue(PrintActivity.isInvalidRangeBound(overflow));
    }

    @Test
    public void utmUsesEasternZoneAndUpperBandAtExactBoundaries() {
        assertEquals("31N", new Coordinates(0, 0).toUTM().gzd);
        assertEquals("32N", new Coordinates(0, 6).toUTM().gzd);
    }

    @Test
    public void utmAppliesNorwayAndSvalbardRulesAtLowerBoundaries() {
        assertEquals("32V", new Coordinates(56, 3).toUTM().gzd);
        assertEquals("33X", new Coordinates(72, 9).toUTM().gzd);
    }

    @Test
    public void utmAppliesSvalbardRulesAtNorthernLimit() {
        // Zones 32X/34X/36X do not exist; 84 is the last latitude UTM covers.
        assertEquals("33X", new Coordinates(84, 10).toUTM().gzd);
    }

    @Test
    public void legacyZeroAltitudeIsUnknown() {
        nisse.SlimeRecords.data.ObservationRecord record =
                new nisse.SlimeRecords.data.ObservationRecord();
        assertFalse(record.hasKnownAltitude());

        record.altitude = 12.0; // legacy record: value stored, flag never set
        assertTrue(record.hasKnownAltitude());

        record.altitude = 0.0;
        record.hasAltitude = true; // genuine 0 m measurement
        assertTrue(record.hasKnownAltitude());
    }

    @Test
    public void dmsSecondsNeverDisplaySixty() {
        // 57.9999999... must carry into 58 deg, not print 57 deg 59' 60.00"
        assertEquals("58° 0' 0.00\" N", new Coordinates(57.99999999, 11).getLatDMS());
        assertEquals("11° 30' 0.00\" E", new Coordinates(0, 11.5).getLonDMS());
    }

    @Test
    public void shortRubinStringIsRejectedCleanly() {
        try {
            new Coordinates(0, 0).setFromRUBIN("7", false);
            fail("Expected IllegalArgumentException for a too-short RUBIN string");
        } catch (IllegalArgumentException expected) {
            // rejected with the intended exception, not StringIndexOutOfBounds
        }
    }

    @Test
    public void specimenLabelEscapesExecutableHtml() {
        assertEquals("&lt;/div&gt;&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;",
                SpecimenLabelBuilder.escapeHtml("</div><script>alert(\"x\")</script>"));
    }
}
