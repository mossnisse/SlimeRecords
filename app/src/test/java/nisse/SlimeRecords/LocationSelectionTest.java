package nisse.SlimeRecords;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocationSelectionTest {
    @Test
    public void replacesFixWhenAccuracyImproves() {
        assertTrue(LocationSelection.shouldReplace(20, 1_000, 5, 2_000, 1));
    }

    @Test
    public void rejectsOlderFixEvenWhenItClaimsBetterAccuracy() {
        assertFalse(LocationSelection.shouldReplace(20, 2_000, 5, 1_000, 1));
    }

    @Test
    public void replacesAgedOrClearlyMovedFix() {
        assertTrue(LocationSelection.shouldReplace(5, 1_000, 8, 16_000, 1));
        assertTrue(LocationSelection.shouldReplace(5, 1_000, 8, 2_000, 20));
    }

    @Test
    public void retainsRecentFixWhenWorseCandidateIsWithinUncertainty() {
        assertFalse(LocationSelection.shouldReplace(5, 1_000, 8, 2_000, 10));
    }
}
