package nisse.SlimeRecords;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public  class ImportResult {
    /** Upper bound on per-row error details kept for logging. */
    public static final int MAX_REPORTED_ERRORS = 20;

    public int added = 0;
    public int updated = 0;
    public int skipped = 0;
    public int failed = 0;
    /** Details for the first {@link #MAX_REPORTED_ERRORS} failed rows. */
    public final List<String> errors = new ArrayList<>();

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.getDefault(),
                "Added: %d\nUpdated: %d\nSkipped: %d\nFailed: %d",
                added, updated, skipped, failed);
    }
}
