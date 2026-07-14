package nisse.SlimeRecords;

/** Stable export choices independent of translated UI labels. */
public enum ExportFormat {
    STANDARD_CSV,
    EXCEL_CSV,
    ARTPORTALEN;

    public static ExportFormat fromDisplayName(String displayName) {
        if (displayName == null) return STANDARD_CSV;
        if (displayName.contains("Artportalen")) return ARTPORTALEN;
        if (displayName.contains("Excel")) return EXCEL_CSV;
        return STANDARD_CSV;
    }
}
