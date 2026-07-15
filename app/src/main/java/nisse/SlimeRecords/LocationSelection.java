package nisse.SlimeRecords;

/** Pure policy for choosing between successive GPS fixes. */
final class LocationSelection {
    private static final long MAX_RETAINED_FIX_AGE_MS = 15_000L;
    private static final float MIN_MOVEMENT_METERS = 10f;

    private LocationSelection() {}

    /**
     * Timestamps must come from a monotonic clock — milliseconds derived from
     * Location#getElapsedRealtimeNanos() — never Location#getTime(): the fused
     * provider mixes GPS- and device-clock wall times, which can run backwards
     * and would permanently latch the ordering check below.
     */
    static boolean shouldReplace(float currentAccuracy, long currentElapsedMillis,
                                 float candidateAccuracy, long candidateElapsedMillis,
                                 float distanceMeters) {
        // Never let an out-of-order historical result replace a newer fix.
        if (candidateElapsedMillis < currentElapsedMillis) return false;

        if (candidateAccuracy < currentAccuracy) return true;

        // Do not retain an old position indefinitely if the user keeps searching.
        if (candidateElapsedMillis - currentElapsedMillis >= MAX_RETAINED_FIX_AGE_MS) return true;

        // A displacement larger than both fixes' uncertainty is real movement,
        // even when the newest accuracy estimate is slightly worse.
        float movementThreshold = Math.max(MIN_MOVEMENT_METERS,
                currentAccuracy + candidateAccuracy);
        return distanceMeters > movementThreshold;
    }
}
