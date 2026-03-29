package nisse.SlimeRecords.coords;

// ENUM for dealing with transverse mercator projections
public enum CoordSystem {

    //parameters to compensate for the bessel elipsoid, to convert direct to the "wgs84" elipsoid
    RT90("RT 90 2.5 gon V 0:-15.", -667.711, 1500064.274, 15.0 + 48.0 / 60.0 + 22.624306 / 3600.0, 1.00000561024, 6378137.0, 1.0 / 298.257222101
    ,6100000,7700000,1200000,1900000),
    SWEREF99TM("SWEREF99 TM", 0.0, 500000.0, 15.00, 0.9996, 6378137.0, 1.0 / 298.257222101,
    6000000, 7700000, 200000, 960000);

    /*
    WGS84("WGS84","Global",CoordType.Ellipsoid2D),
    bessel("Bessel 1841","Global",CoordType.Ellipsoid2D),
    Sweref99("Sweref99","Global",CoordType.Ellipsoid2D);
    UTM
    MGRS
    RUBIN
    */

    /*
    static public enum CoordType {
        TransverseMercator, Ellipsoid2D, index,
    }*/

    public final String name;
    //public final String country;
    //public final CoordType type;

    public final double falseNorthing, falseEasting, centralMeridian, scale, axis, flattening;

    //limits what is valid values;
    public final double Nmin, Nmax, Emin, Emax;
    //wgs84 // -90 <= N <= 90, -180 <= E <= 180

    // Pre-calculated coefficients for Gauss-Krüger
    public final double e2, n, a_roof, lambda_zero;
    public final double beta1, beta2, beta3, beta4;
    public final double delta1, delta2, delta3, delta4;
    public final double B, C, D;
    public final double Astar, Bstar, Cstar, Dstar;

    CoordSystem(String name, double fn, double fe, double cm, double scale, double axis, double flattening, double nMin, double nMax, double eMin, double eMax) {
        this.name = name;
        this.falseNorthing = fn;
        this.falseEasting = fe;
        this.centralMeridian = cm;
        this.scale = scale;
        this.axis = axis;
        this.flattening = flattening;
        this.Nmin = nMin;
        this.Nmax = nMax;
        this.Emin = eMin;
        this.Emax = eMax;

        // Math prep
        this.lambda_zero = Math.toRadians(cm);
        this.e2 = flattening * (2.0 - flattening);
        this.n = flattening / (2.0 - flattening);
        this.a_roof = axis / (1.0 + n) * (1.0 + n * n / 4.0 + n * n * n * n / 64.0);

        // Forward coefficients (beta)
        this.beta1 = n/2.0 - 2.0*n*n/3.0 + 5.0*n*n*n/16.0 + 41.0*n*n*n*n/180.0;
        this.beta2 = 13.0*n*n/48.0 - 3.0*n*n*n/5.0 + 557.0*n*n*n*n/1440.0;
        this.beta3 = 61.0*n*n*n/240.0 - 103.0*n*n*n*n/140.0;
        this.beta4 = 49561.0*n*n*n*n/161280.0;

        // Backward coefficients (delta)
        this.delta1 = n/2.0 - 2.0*n*n/3.0 + 37.0*n*n*n/96.0 - n*n*n*n/360.0;
        this.delta2 = n*n/48.0 + n*n*n/15.0 - 437.0*n*n*n*n/1440.0;
        this.delta3 = 17.0*n*n*n/480.0 - 37.0*n*n*n*n/840.0;
        this.delta4 = 4397.0*n*n*n*n/161280.0;

        // Ellipsoid coefficients
        this.B = (5.0*e2*e2 - e2*e2*e2)/6.0;
        this.C = (104.0*e2*e2*e2 - 45.0*e2*e2*e2*e2)/120.0;
        this.D = (1237.0*e2*e2*e2*e2)/1260.0;

        this.Astar = e2 + e2*e2 + e2*e2*e2 + e2*e2*e2*e2;
        this.Bstar = -(7.0*e2*e2 + 17.0*e2*e2*e2 + 30.0*e2*e2*e2*e2)/6.0;
        this.Cstar = (224.0*e2*e2*e2 + 889.0*e2*e2*e2*e2)/120.0;
        this.Dstar = -(4279.0*e2*e2*e2*e2)/1260.0;
    }

    public String getName() {
        return name;
    }
}
