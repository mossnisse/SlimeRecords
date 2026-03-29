package nisse.SlimeRecords.coords;

import java.util.Locale;

public class UTMResult {
    public final String gzd; // Grid Zone Designation (e.g., "33V")
    public final double easting;
    public final double northing;

    public UTMResult(String gzd, double easting, double northing) {
        this.gzd = gzd;
        this.easting = easting;
        this.northing = northing;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s %.0f %.0f", gzd, easting, northing);
    }
}
