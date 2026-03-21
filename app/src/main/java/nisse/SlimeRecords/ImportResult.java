package nisse.SlimeRecords;

import java.util.Locale;

public  class ImportResult {
    public int added = 0;
    public int updated = 0;
    public int skipped = 0;
    public int failed = 0;

    @Override
    public String toString() {
        return String.format(Locale.getDefault(),
                "Added: %d\nUpdated: %d\nSkipped: %d\nFailed: %d",
                added, updated, skipped, failed);
    }
}
