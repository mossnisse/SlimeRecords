package nisse.SlimeRecords.coords;

import androidx.annotation.NonNull;

import java.util.Locale;

public class Coordinates {
    private double north, east; // Can represent Lat/Lon or N/E meters

    public Coordinates(double north, double east) {
        this.north = north;
        this.east = east;
    }

    private static double atanh(double x) {
        if (x > 0.9999999999) return 20.0;
        if (x < -0.9999999999) return -20.0;
        return 0.5 * Math.log((1.0 + x) / (1.0 - x));
    }

    public Coordinates toProjected(CoordSystem cs) {
        double phi = Math.toRadians(this.north);
        double lambda = Math.toRadians(this.east);

        double phiStar = phi - Math.sin(phi) * Math.cos(phi) * (cs.e2 +
                cs.B * Math.pow(Math.sin(phi), 2) +
                cs.C * Math.pow(Math.sin(phi), 4) +
                cs.D * Math.pow(Math.sin(phi), 6));

        double deltaLambda = lambda - cs.lambda_zero;
        double xiPrim = Math.atan(Math.tan(phiStar) / Math.cos(deltaLambda));
        double etaPrim = atanh(Math.cos(phiStar) * Math.sin(deltaLambda));

        double n = cs.scale * cs.a_roof * (xiPrim +
                cs.beta1 * Math.sin(2 * xiPrim) * Math.cosh(2 * etaPrim) +
                cs.beta2 * Math.sin(4 * xiPrim) * Math.cosh(4 * etaPrim) +
                cs.beta3 * Math.sin(6 * xiPrim) * Math.cosh(6 * etaPrim) +
                cs.beta4 * Math.sin(8 * xiPrim) * Math.cosh(8 * etaPrim)) + cs.falseNorthing;

        double e = cs.scale * cs.a_roof * (etaPrim +
                cs.beta1 * Math.cos(2 * xiPrim) * Math.sinh(2 * etaPrim) +
                cs.beta2 * Math.cos(4 * xiPrim) * Math.sinh(4 * etaPrim) +
                cs.beta3 * Math.cos(6 * xiPrim) * Math.sinh(6 * etaPrim) +
                cs.beta4 * Math.cos(8 * xiPrim) * Math.sinh(8 * etaPrim)) + cs.falseEasting;

        return new Coordinates(n, e);
    }

    private Coordinates toProjected(CoordSystem cs, double centralMeridianDeg, double fn, double fe, double k0) {
        double phi = Math.toRadians(this.north);
        double lambda = Math.toRadians(this.east);
        double lambdaZero = Math.toRadians(centralMeridianDeg);

        // Conformal latitude
        double phiStar = phi - Math.sin(phi) * Math.cos(phi) * (cs.e2 +
                cs.B * Math.pow(Math.sin(phi), 2) +
                cs.C * Math.pow(Math.sin(phi), 4) +
                cs.D * Math.pow(Math.sin(phi), 6));

        double deltaLambda = lambda - lambdaZero;
        double xiPrim = Math.atan(Math.tan(phiStar) / Math.cos(deltaLambda));
        double etaPrim = atanh(Math.cos(phiStar) * Math.sin(deltaLambda));

        // Scale and Radius
        double aRoof = cs.a_roof;

        double n = k0 * aRoof * (xiPrim +
                cs.beta1 * Math.sin(2 * xiPrim) * Math.cosh(2 * etaPrim) +
                cs.beta2 * Math.sin(4 * xiPrim) * Math.cosh(4 * etaPrim) +
                cs.beta3 * Math.sin(6 * xiPrim) * Math.cosh(6 * etaPrim) +
                cs.beta4 * Math.sin(8 * xiPrim) * Math.cosh(8 * etaPrim)) + fn;

        double e = k0 * aRoof * (etaPrim +
                cs.beta1 * Math.cos(2 * xiPrim) * Math.sinh(2 * etaPrim) +
                cs.beta2 * Math.cos(4 * xiPrim) * Math.sinh(4 * etaPrim) +
                cs.beta3 * Math.cos(6 * xiPrim) * Math.sinh(6 * etaPrim) +
                cs.beta4 * Math.cos(8 * xiPrim) * Math.sinh(8 * etaPrim)) + fe;

        return new Coordinates(n, e);
    }

    public Coordinates toWGS84(CoordSystem cs) {
        double xi = (this.north - cs.falseNorthing) / (cs.scale * cs.a_roof);
        double eta = (this.east - cs.falseEasting) / (cs.scale * cs.a_roof);

        double xiPrim = xi -
                cs.delta1 * Math.sin(2 * xi) * Math.cosh(2 * eta) -
                cs.delta2 * Math.sin(4 * xi) * Math.cosh(4 * eta) -
                cs.delta3 * Math.sin(6 * xi) * Math.cosh(6 * eta) -
                cs.delta4 * Math.sin(8 * xi) * Math.cosh(8 * eta);

        double etaPrim = eta -
                cs.delta1 * Math.cos(2 * xi) * Math.sinh(2 * eta) -
                cs.delta2 * Math.cos(4 * xi) * Math.sinh(4 * eta) -
                cs.delta3 * Math.cos(6 * xi) * Math.sinh(6 * eta) -
                cs.delta4 * Math.cos(8 * xi) * Math.sinh(8 * eta);

        double phiStar = Math.asin(Math.sin(xiPrim) / Math.cosh(etaPrim));
        double deltaLambda = Math.atan(Math.sinh(etaPrim) / Math.cos(xiPrim));

        double latRad = phiStar + Math.sin(phiStar) * Math.cos(phiStar) * (cs.Astar +
                cs.Bstar * Math.pow(Math.sin(phiStar), 2) +
                cs.Cstar * Math.pow(Math.sin(phiStar), 4) +
                cs.Dstar * Math.pow(Math.sin(phiStar), 6));

        double lonRad = cs.lambda_zero + deltaLambda;

        return new Coordinates(Math.toDegrees(latRad), Math.toDegrees(lonRad));
    }

    /**
     * Core reverse Gauss-Krüger engine.
     * Converts Northing/Easting back to WGS84 Coordinates.
     */
    private static Coordinates toWGS84(CoordSystem cs,
                                                           double n, double e,
                                                           double centralMeridianDeg,
                                                           double fn, double fe, double k0) {

        double xi = (n - fn) / (k0 * cs.a_roof);
        double eta = (e - fe) / (k0 * cs.a_roof);

        double xiPrim = xi -
                cs.delta1 * Math.sin(2 * xi) * Math.cosh(2 * eta) -
                cs.delta2 * Math.sin(4 * xi) * Math.cosh(4 * eta) -
                cs.delta3 * Math.sin(6 * xi) * Math.cosh(6 * eta) -
                cs.delta4 * Math.sin(8 * xi) * Math.cosh(8 * eta);

        double etaPrim = eta -
                cs.delta1 * Math.cos(2 * xi) * Math.sinh(2 * eta) -
                cs.delta2 * Math.cos(4 * xi) * Math.sinh(4 * eta) -
                cs.delta3 * Math.cos(6 * xi) * Math.sinh(6 * eta) -
                cs.delta4 * Math.cos(8 * xi) * Math.sinh(8 * eta);

        double phiStar = Math.asin(Math.sin(xiPrim) / Math.cosh(etaPrim));
        double deltaLambda = Math.atan(Math.sinh(etaPrim) / Math.cos(xiPrim));

        double latRad = phiStar + Math.sin(phiStar) * Math.cos(phiStar) * (cs.Astar +
                cs.Bstar * Math.pow(Math.sin(phiStar), 2) +
                cs.Cstar * Math.pow(Math.sin(phiStar), 4) +
                cs.Dstar * Math.pow(Math.sin(phiStar), 6));

        double lonRad = Math.toRadians(centralMeridianDeg) + deltaLambda;

        return new Coordinates(Math.toDegrees(latRad), Math.toDegrees(lonRad));
    }

    public double getNorth() {
        return north;
    }

    public double getEast() {
        return east;
    }

    public boolean isValid(CoordSystem CS) {
        return CS.Nmax >= north && CS.Nmin <= north && CS.Emax >= east && CS.Emin <= east;
    }

    public Coordinates convertToRT90FromSweref99TM() {
        Coordinates wgs84 = toWGS84(CoordSystem.SWEREF99TM);
        return wgs84.toProjected(CoordSystem.RT90);
    }

    public Coordinates convertToSweref99TMFromRT90() {
        Coordinates wgs84 = toWGS84(CoordSystem.RT90);
        return wgs84.toProjected(CoordSystem.SWEREF99TM);

    }

    // lat long degrees, minutes conversion functions
    /**
     * Sets coordinates from Degrees, Minutes, Seconds (DMS).
     * Use 0 for any missing components (e.g., if you only have DM).
     */
    public void setFromDMS(double latDeg, double latMin, double latSec, String latDir,
                           double lonDeg, double lonMin, double lonSec, String lonDir) {

        this.north = dmsToDecimal(latDeg, latMin, latSec, latDir);
        this.east = dmsToDecimal(lonDeg, lonMin, lonSec, lonDir);
    }

    /**
     * String-based overload for convenience.
     * Handles cleaning up spaces and different decimal separators.
     */
    public void setFromDMS(String latD, String latM, String latS, String latDir,
                           String lonD, String lonM, String lonS, String lonDir) {

        setFromDMS(
                parseDouble(latD), parseDouble(latM), parseDouble(latS), latDir,
                parseDouble(lonD), parseDouble(lonM), parseDouble(lonS), lonDir
        );
    }

    /**
     * Core logic to convert Degrees Minutes Seconds to Decimal Degrees.
     */
    private double dmsToDecimal(double deg, double min, double sec, String direction) {
        double decimal = Math.abs(deg) + (min / 60.0) + (sec / 3600.0);

        // Normalize direction string
        String dir = (direction == null) ? "" : direction.trim().toUpperCase();

        if (dir.equals("S") || dir.equals("W") || deg < 0) {
            return -decimal;
        }
        return decimal;
    }

    /**
     * Improved parser that handles European comma decimals and spaces.
     */
    private double parseDouble(String str) {
        if (str == null || str.isBlank()) return 0.0;
        try {
            return Double.parseDouble(str.replace(',', '.').replace(" ", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Returns the latitude in DMS format: 57° 42' 31.9" N
     */
    public String getLatDMS() {
        return toDMS(this.north, "N", "S");
    }

    /**
     * Returns the longitude in DMS format: 11° 58' 20.32" E
     */
    public String getLonDMS() {
        return toDMS(this.east, "E", "W");
    }

    /**
     * Core logic to convert Decimal Degrees to a DMS String.
     */
    private String toDMS(double decimal, String posDir, String negDir) {
        String direction = decimal >= 0 ? posDir : negDir;
        double absValue = Math.abs(decimal);

        int degrees = (int) absValue;
        double remainderMinutes = (absValue - degrees) * 60.0;

        int minutes = (int) remainderMinutes;
        double seconds = (remainderMinutes - minutes) * 60.0;

        // We use String.format to control the precision of the seconds (e.g., 1 decimal place)
        // The \u00B0 is the unicode for the degree symbol °
        return String.format(Locale.US, "%d\u00B0 %d' %.2f\" %s", degrees, minutes, seconds, direction);
    }

    // RUBIN index related functions
    private static final int RUBIN_ORIGIN_N = 6050000;
    private static final int RUBIN_ORIGIN_E = 1200000;

    private static int alphaToNum(char c) {
        if (Character.isDigit(c)) return c - '0';
        return Character.toUpperCase(c) - 'A';
    }

    private static char numToAlpha(int n, boolean uppercase) {
        return (char) ((uppercase ? 'A' : 'a') + n);
    }

    public void setFromRUBIN(String rubin, boolean targetSweref) {
        // Clean string: remove spaces and non-breaking spaces
        String clean = rubin.replaceAll("[\\s\\u00A0]", "");

        // Pad if first part is single digit (e.g., "7" -> "07")
        if (clean.length() > 0 && !Character.isDigit(clean.charAt(1))) {
            clean = "0" + clean;
        }

        if (clean.length() < 5) throw new IllegalArgumentException("Invalid RUBIN string length");

        // Parse components
        int n1 = Integer.parseInt(clean.substring(0, 2));
        int e1 = alphaToNum(clean.charAt(2));
        int n2 = alphaToNum(clean.charAt(3));
        int e2 = alphaToNum(clean.charAt(4));

        // Center of the 5x5km square (adding 2500m offset)
        this.north = RUBIN_ORIGIN_N + (n1 * 50000) + (n2 * 5000) + 2500.0;
        this.east = RUBIN_ORIGIN_E + (e1 * 50000) + (e2 * 5000) + 2500.0;

        // If target is Sweref, convert from the current RT90 state
        if (targetSweref) {
            Coordinates sweref = this.toWGS84(CoordSystem.RT90).toProjected(CoordSystem.SWEREF99TM);
            this.north = sweref.north;
            this.east = sweref.east;
        }
    }

    public String toRUBIN(boolean isCurrentlySweref) {
        Coordinates rt90 = isCurrentlySweref ?
                this.toWGS84(CoordSystem.SWEREF99TM).toProjected(CoordSystem.RT90) : this;

        int nTotal = (int) Math.round(rt90.north) - RUBIN_ORIGIN_N;
        int eTotal = (int) Math.round(rt90.east) - RUBIN_ORIGIN_E;

        // Level 1: 50x50 km (e.g., "6G")
        int n1 = nTotal / 50000;
        char e1 = numToAlpha(eTotal / 50000, true);

        // Level 2: 5x5 km (e.g., "6g7e")
        int n2 = (nTotal % 50000) / 5000;
        char e2 = numToAlpha((eTotal % 50000) / 5000, false);

        // Level 3: 100x100 m (Precise index 6G7e 0420)
        int n3 = (nTotal % 5000) / 100;
        int e3 = (eTotal % 5000) / 100;

        return String.format(Locale.US, "%d%c%d%c %02d%02d", n1, e1, n2, e2, n3, e3);
    }

    // UTM convertion methods

    /**
     * Converts WGS84 to UTM.
     */
    public UTMResult toUTM() {
        if (this.north < -80 || this.north > 84) {
            return null; // Polar areas use UPS, not UTM
        }

        String gzd = getUTMGridZone();

        int zone = Integer.parseInt(gzd.replaceAll("[^0-9]", ""));
        double centralMeridian = (zone * 6.0) - 183.0;

        double falseNorthing = (this.north < 0) ? 10000000.0 : 0.0;
        double falseEasting = 500000.0;
        double utmScale = 0.9996;

        Coordinates projected = toProjected(CoordSystem.SWEREF99TM,
                centralMeridian,
                falseNorthing,
                falseEasting,
                utmScale);

        return new UTMResult(gzd, projected.getEast(), projected.getNorth());
    }

    private String getUTMGridZone() {
        int zn = (int) Math.ceil((this.east + 180) / 6.0);
        if (this.east == 180) zn = 60;

        // Latitude Band
        char zl;
        if (this.north >= 72) zl = 'X';
        else if (this.north < -80) zl = 'C';
        else {
            // This generates a 1-based index (1, 2, 3...)
            int index = (int) Math.ceil((this.north + 80) / 8.0);

            // FIX: Must use the UTM specific method, not MGRS!
            // Index 1 needs to output 'C', not 'B'.
            zl = utmNumToAlpha(index);
        }

        String zoneStr = String.valueOf(zn) + zl;

        // Norway/Svalbard Exceptions
        if (this.north > 56 && this.north < 64 && this.east > 3 && this.east < 6) return "32V";
        if (this.north > 72) {
            if (this.east >= 0 && this.east < 9) return "31X";
            if (this.east >= 9 && this.east < 21) return "33X";
            if (this.east >= 21 && this.east < 33) return "35X";
            if (this.east >= 33 && this.east < 42) return "37X";
        }
        return zoneStr;
    }


    private char utmNumToAlpha(int num) {
        int code = num + 66; // 1 -> 'C'
        if (code > 72) code++; // Skip 'I'
        if (code > 78) code++; // Skip 'O'
        return (char) code;
    }

    private static char mgrsNumToAlpha(int num) {
        int code = num + 'A';
        if (code >= 'I') { code++; }
        if (code >= 'O') { code++; }
        return (char) code;
    }

    private static int mgrsAlphaToNum(char c) {
        char upper = Character.toUpperCase(c);
        int res = upper - 'A';
        if (upper > 'O') {
            return res - 2;
        } else if (upper > 'I') {
            return res - 1;
        }
        return res;
    }

    // convert wgs84 to and MGRS string
    public String toMGRS() {
        UTMResult utm = this.toUTM();
        if (utm == null) return "OUTSIDE UTM RANGE";

        int zone = Integer.parseInt(utm.gzd.replaceAll("[^0-9]", ""));
        double e = utm.easting;
        double n = utm.northing;

        // Identify the 100km Square Column (East-West)
        int set = zone % 3;
        int e100k = (int) Math.floor(e / 100000);
        int colBase = 0;

        // colBase relies on A=0, J=8, S=16
        if (set == 1) colBase = mgrsAlphaToNum('A');
        else if (set == 2) colBase = mgrsAlphaToNum('J');
        else if (set == 0) colBase = mgrsAlphaToNum('S');

        char columnId = mgrsNumToAlpha(colBase + e100k - 1);

        // Identify the 100km Square Row (North-South)
        // FIX: Changed mgrsNumToAlpha('F') to mgrsAlphaToNum('F')
        int rowBase = (zone % 2 != 0) ? mgrsAlphaToNum('A') : mgrsAlphaToNum('F');

        // The northing math works perfectly for both hemispheres because
        // the Southern Hemisphere False Northing (10,000,000) is a clean multiple of 2,000,000 (20 * 100k).
        int n100k = (int) Math.floor(n / 100000) % 20;

        char rowId = mgrsNumToAlpha((rowBase + n100k) % 20);

        // Calculate final numerical values
        int finalE = (int) Math.round(e % 100000);
        int finalN = (int) Math.round(n % 100000);

        return String.format(Locale.US, "%s%c%c%05d%05d", utm.gzd, columnId, rowId, finalE, finalN);
    }

    /**
     * Parses an MGRS string and returns a WGS84 Coordinates object.
     * Example input: "33V UC 12345 67890" or "33VUC1234567890"
     */
    public static Coordinates fromMGRS(String mgrsStr) {
        // 1. Clean the string
        String cleanStr = mgrsStr.replaceAll("\\s+", "").toUpperCase();
        if (cleanStr.length() < 5) throw new IllegalArgumentException("Invalid MGRS string");

        // 2. Extract components
        // Find where the letters start (usually index 1 or 2)
        int firstLetterIdx = Character.isLetter(cleanStr.charAt(1)) ? 1 : 2;
        int zone = Integer.parseInt(cleanStr.substring(0, firstLetterIdx));
        char latBand = cleanStr.charAt(firstLetterIdx);

        // Ensure it's a valid UTM band (Polar UPS areas not supported in this basic parser)
        if (latBand < 'C' || latBand > 'X') throw new IllegalArgumentException("Unsupported UTM Latitude Band");

        // Extract the 100km square letters (e.g. 'U' and 'C')
        char colLetter = cleanStr.charAt(firstLetterIdx + 1);
        char rowLetter = cleanStr.charAt(firstLetterIdx + 2);

        // Extract precision coordinates (e.g. '12345' and '67890')
        String numPart = cleanStr.substring(firstLetterIdx + 3);
        if (numPart.length() % 2 != 0) throw new IllegalArgumentException("Invalid MGRS numerical part length");
        int precisionLength = numPart.length() / 2;

        String eStr = numPart.substring(0, precisionLength);
        String nStr = numPart.substring(precisionLength);

        // Scale precision back up to meters (e.g. '123' becomes '12300')
        double eMeters = Double.parseDouble(eStr) * Math.pow(10, 5 - precisionLength);
        double nMeters = Double.parseDouble(nStr) * Math.pow(10, 5 - precisionLength);

        // 3. Calculate Easting
        // MGRS Columns repeat every 3 zones: A, J, S
        int setCol = zone % 3;
        int e100kBase = (setCol == 1) ? mgrsAlphaToNum('A') :
                (setCol == 2) ? mgrsAlphaToNum('J') : mgrsAlphaToNum('S');

        // Number of 100km steps from the base
        int e100kSteps = mgrsAlphaToNum(colLetter) - e100kBase;
        // Wrap around logic if negative
        if (e100kSteps < 0) e100kSteps += 8; // MGRS columns (East-West) are groups of 8 per zone set

        // Total UTM Easting
        double utmEasting = (e100kSteps + 1) * 100000.0 + eMeters;

        // 4. Calculate Northing
        // MGRS Rows start at A or F and repeat every 2,000,000 meters (20 letters * 100k)
        int rowBase = (zone % 2 != 0) ? mgrsAlphaToNum('A') : mgrsAlphaToNum('F');
        int n100kSteps = mgrsAlphaToNum(rowLetter) - rowBase;
        if (n100kSteps < 0) n100kSteps += 20;

        double utmNorthing = n100kSteps * 100000.0 + nMeters;

        // 5. Resolving the 2,000km ambiguity using actual Latitude
        // We calculate the minimum possible northing for this UTM Latitude Band.
        // 'C' starts at -80 deg. Each band is 8 degrees.
        int bandIndex = utmNumToAlphaRev(latBand); // 1-indexed (C=1, D=2, etc.)
        double minLatitudeDeg = (bandIndex * 8.0) - 88.0;

        // Convert this rough minimum latitude to rough WGS84 northing
        // 1 deg latitude ≈ 111,132 meters (WGS84 average)
        // We add a safety buffer so we don't accidentally pick a band too low.
        double minNorthingEstimate = (latBand < 'N') ?
                10000000.0 + (minLatitudeDeg * 111132.0) : // Southern Hemisphere
                (minLatitudeDeg * 111132.0) - 100000.0; // Northern Hemisphere

        // Keep adding 2,000,000 until we exceed the minimum possible northing for the band
        while (utmNorthing < minNorthingEstimate) {
            utmNorthing += 2000000.0;
        }

        // 6. Convert the finalized UTM coordinate back to WGS84
        double centralMeridian = (zone * 6.0) - 183.0;
        double falseNorthing = (latBand < 'N') ? 10000000.0 : 0.0;

        // We need a custom reverse engine for this.
        return toWGS84(CoordSystem.SWEREF99TM,
                utmNorthing, utmEasting,
                centralMeridian, falseNorthing, 500000.0, 0.9996);
    }

    // Helper: Reverse the 1-indexed UTM band logic (e.g. 'C' -> 1)
    private static int utmNumToAlphaRev(char c) {
        int code = c;
        if (code > 'O') code--;
        if (code > 'I') code--;
        return code - 66;
    }

    @NonNull
    @Override
    public String toString() { return String.format(Locale.US, "(%.5f, %.5f)", north, east); }
}