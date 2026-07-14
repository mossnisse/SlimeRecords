package nisse.SlimeRecords;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import nisse.SlimeRecords.data.ObservationRecord;
import nisse.SlimeRecords.data.SpeciesAttributes;

public class LabelAndPrintRangeTest {
    @Test
    public void labelReportFiltersObservationsAndEscapesAllUserText() {
        ObservationRecord specimen = specimen("42");
        specimen.id = 7;
        specimen.country = "Sweden";
        specimen.countryCode = "SE";
        specimen.province = "Uppland";
        specimen.district = "Uppsala";
        specimen.locality = "</div><script>alert(\"x\")</script>";
        specimen.localTime = "2026-07-14 12:30:00";
        specimen.attributes.taxonName = "A & B";
        ObservationRecord observation = new ObservationRecord();
        observation.attributes = new SpeciesAttributes();

        String report = SpecimenLabelBuilder.buildReport(
                "<html>{{LABELS_HERE}}</html>", Arrays.asList(specimen, observation));
        assertTrue(report.contains("Flora Suecica"));
        assertTrue(report.contains("Uppsala socken"));
        assertTrue(report.contains("A &amp; B"));
        assertTrue(report.contains("&lt;/div&gt;&lt;script&gt;"));
        assertFalse(report.contains("<script>"));
        assertTrue(report.contains("2026-07-14"));
    }

    @Test
    public void labelReportShowsEmptyMessageWithoutSpecimens() {
        assertEquals("<html><body><h1>No specimens found to print.</h1></body></html>",
                SpecimenLabelBuilder.buildReport("{{LABELS_HERE}}", Collections.emptyList()));
    }

    @Test
    public void rangeParsingHandlesEmptyValidAndOverflowValues() {
        assertEquals(null, PrintRangeFilter.parseNullableInt("  "));
        assertEquals(Integer.valueOf(42), PrintRangeFilter.parseNullableInt(" 42 "));
        assertFalse(PrintRangeFilter.isInvalidBound(""));
        assertTrue(PrintRangeFilter.isInvalidBound("999999999999999999"));
    }

    @Test
    public void rangeFilterUsesInclusiveBoundsAndRejectsNonNumericNumbers() {
        List<ObservationRecord> records = Arrays.asList(
                specimen("9"), specimen("10"), specimen("15"), specimen("x"));
        assertEquals(2, PrintRangeFilter.filter(records, 10, 15).size());
        assertEquals(2, PrintRangeFilter.filter(records, null, 10).size());
        assertSame(records, PrintRangeFilter.filter(records, null, null));
        assertTrue(PrintRangeFilter.filter(null, 1, 2).isEmpty());
    }

    private static ObservationRecord specimen(String number) {
        ObservationRecord record = new ObservationRecord();
        record.attributes = new SpeciesAttributes();
        record.attributes.isSpecimen = true;
        record.attributes.specimenNr = number;
        return record;
    }
}
