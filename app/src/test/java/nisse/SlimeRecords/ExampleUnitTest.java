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
    public void specimenLabelEscapesExecutableHtml() {
        assertEquals("&lt;/div&gt;&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;",
                SpecimenLabelBuilder.escapeHtml("</div><script>alert(\"x\")</script>"));
    }
}
