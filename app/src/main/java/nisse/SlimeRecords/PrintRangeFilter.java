package nisse.SlimeRecords;

import java.util.ArrayList;
import java.util.List;

import nisse.SlimeRecords.data.ObservationRecord;

/** Pure parsing and filtering rules for specimen collection-number ranges. */
final class PrintRangeFilter {
    private PrintRangeFilter() {}

    static Integer parseNullableInt(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static boolean isInvalidBound(String text) {
        return text != null && !text.trim().isEmpty() && parseNullableInt(text) == null;
    }

    static List<ObservationRecord> filter(List<ObservationRecord> records,
                                          Integer minimum,
                                          Integer maximum) {
        if (records == null) return new ArrayList<>();
        if (minimum == null && maximum == null) return records;
        List<ObservationRecord> filtered = new ArrayList<>();
        for (ObservationRecord record : records) {
            if (record.attributes == null || record.attributes.specimenNr == null) continue;
            try {
                int number = Integer.parseInt(record.attributes.specimenNr.trim());
                if (minimum != null && number < minimum) continue;
                if (maximum != null && number > maximum) continue;
                filtered.add(record);
            } catch (NumberFormatException ignored) {
                // A non-numeric collection number cannot be range matched.
            }
        }
        return filtered;
    }
}
