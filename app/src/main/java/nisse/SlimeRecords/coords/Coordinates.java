package nisse.SlimeRecords.coords;

public class Coordinates {
    private Double north, east; // Can represent Lat/Lon or N/E meters

    public Coordinates(double north, double east) {
        this.north = north;
        this.east = east;
    }

    public Coordinates(Point p) {
        this.north = p.getY();
        this.east = p.getX();
    }

    private static double atanh(double value) {
        return 0.5 * Math.log((1.0 + value) / (1.0 - value));
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

    public double getNorth() {
        return north;
    }

    public double getEast() {
        return east;
    }

    public Point getPoint() {
        return new Point((int)Math.round(east),(int)Math.round(north));
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
     * Returns the longitude in DMS format: 11° 58' 20.3" E
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
        return String.format("%d\u00B0 %d' %.1f\" %s", degrees, minutes, seconds, direction);
    }

    // flyttar koordinaten distance i riktning direction. koordinaterna ska vara typ WGS84 lat/long
    public Coordinates move(int distance, String direction) {
        double bearing =0;
        switch (direction) {
            case "N": bearing = 0; break;
            case "NNE": bearing = 22.5; break;
            case "NE": bearing = 45; break;
            case "ENE": bearing = 67.5; break;
            case "E": bearing = 90; break;
            case "ESE": bearing = 110.5; break;
            case "SE": bearing = 135; break;
            case "SSE": bearing = 157.5; break;
            case "S": bearing = 180; break;
            case "SSW": bearing = 202.5; break;
            case "SW": bearing = 225; break;
            case "WSW": bearing = 247.5; break;
            case "W": bearing = 270; break;
            case "WNW": bearing = 292.5; break;
            case "NW": bearing = 315; break;
            case "NNW": bearing = 337.5; break;
        }

        double brngRad = Math.toRadians(bearing);
        double latRad = Math.toRadians(north);
        double lonRad = Math.toRadians(east);
        double earthRadius = 6371000;
        double distFrac = distance / earthRadius;
        // Haversine - räknar med att gjorden är helt sfärisk men det borde duga.
        double latitudeResult = Math.asin(Math.sin(latRad) * Math.cos(distFrac) + Math.cos(latRad) * Math.sin(distFrac) * Math.cos(brngRad));
        double a = Math.atan2(Math.sin(brngRad) * Math.sin(distFrac) * Math.cos(latRad), Math.cos(distFrac) - Math.sin(latRad) * Math.sin(latitudeResult));
        double longitudeResult = (lonRad + a + 3 * Math.PI) % (2 * Math.PI) - Math.PI;
        //System.out.println("latitude: " + Math.toDegrees(latitudeResult) + ", longitude: " + Math.toDegrees(longitudeResult));
        return new Coordinates(Math.toDegrees(latitudeResult), Math.toDegrees(longitudeResult));
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

        return String.format("%d%c%d%c %02d%02d", n1, e1, n2, e2, n3, e3);
    }

    public String toString() {
        return "("+north+", "+east+")";
    }

    public Point toPoint() {
        return new Point((int)Math.round(east), (int)Math.round(north));
    }

    public static void main(String[] args) {
        Coordinates rt90 = new Coordinates(6543540, 1457933);
        Coordinates wgs84 = rt90.toWGS84(CoordSystem.RT90);
        System.out.println("wgs84: "+wgs84);
        //System.out.println("Sweref99TM: " + coord2);

        //Coordinates coord3 = new Coordinates(6758843, 487907);
        //Coordinates coord4 = convertToWGS84FromSweref99TM(coord3);

        //System.out.println("WGS84: " + coord3);
        //System.out.println("Sweref99TM: " + coord4);
    }

}