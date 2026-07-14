package nisse.SlimeRecords;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import nisse.SlimeRecords.coords.CoordSystem;
import nisse.SlimeRecords.coords.Coordinates;

public class CoordinatesTest {
    @Test
    public void swerefCentralMeridianHasExpectedFalseEasting() {
        Coordinates projected = new Coordinates(0, 15).toProjected(CoordSystem.SWEREF99TM);
        assertEquals(0, projected.getNorth(), 0.01);
        assertEquals(500000, projected.getEast(), 0.01);
    }

    @Test
    public void swerefAndRt90RoundTripAcrossSweden() {
        Coordinates source = new Coordinates(59.3293, 18.0686);
        for (CoordSystem system : CoordSystem.values()) {
            Coordinates restored = source.toProjected(system).toWGS84(system);
            assertEquals(source.getNorth(), restored.getNorth(), 0.000001);
            assertEquals(source.getEast(), restored.getEast(), 0.000001);
        }
    }

    @Test
    public void dmsSupportsDirectionsAndNeverPrintsSixtySeconds() {
        Coordinates coordinates = new Coordinates(0, 0);
        coordinates.setFromDMS(57, 30, 0, "S", 11, 15, 0, "W");
        assertEquals(-57.5, coordinates.getNorth(), 0.000001);
        assertEquals(-11.25, coordinates.getEast(), 0.000001);
        assertEquals("58\u00B0 0' 0.00\" N", new Coordinates(57.99999999, 11).getLatDMS());
    }

    @Test
    public void utmUsesCorrectBoundaryAndExceptionalZones() {
        assertEquals("31N", new Coordinates(0, 0).toUTM().gzd);
        assertEquals("32N", new Coordinates(0, 6).toUTM().gzd);
        assertEquals("32V", new Coordinates(56, 3).toUTM().gzd);
        assertEquals("33X", new Coordinates(72, 9).toUTM().gzd);
        assertEquals("33X", new Coordinates(84, 10).toUTM().gzd);
        assertEquals(null, new Coordinates(85, 10).toUTM());
    }

    @Test
    public void mgrsAndRubinRoundTripsStayWithinGridPrecision() {
        Coordinates stockholm = new Coordinates(59.3293, 18.0686);
        Coordinates restored = Coordinates.fromMGRS(stockholm.toMGRS());
        assertEquals(stockholm.getNorth(), restored.getNorth(), 0.00003);
        assertEquals(stockholm.getEast(), restored.getEast(), 0.00003);

        Coordinates rt90 = stockholm.toProjected(CoordSystem.RT90);
        Coordinates rubinCell = new Coordinates(0, 0);
        rubinCell.setFromRUBIN(rt90.toRUBIN(false), false);
        assertTrue(Math.abs(rt90.getNorth() - rubinCell.getNorth()) <= 2501);
        assertTrue(Math.abs(rt90.getEast() - rubinCell.getEast()) <= 2501);
    }

    @Test
    public void malformedGridReferencesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Coordinates(0, 0).setFromRUBIN("7", false));
        assertThrows(IllegalArgumentException.class, () -> Coordinates.fromMGRS("33VUC123"));
    }

    @Test
    public void projectedValidityIncludesBounds() {
        assertTrue(new Coordinates(CoordSystem.SWEREF99TM.Nmin,
                CoordSystem.SWEREF99TM.Emin).isValid(CoordSystem.SWEREF99TM));
        assertFalse(new Coordinates(CoordSystem.SWEREF99TM.Nmin - 1,
                CoordSystem.SWEREF99TM.Emin).isValid(CoordSystem.SWEREF99TM));
    }
}
