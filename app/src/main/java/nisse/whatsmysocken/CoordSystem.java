package nisse.whatsmysocken;

public enum CoordSystem {

    //parameters to compensate for the bessel elipsoid, to convert direct to the "wgs84" elipsoid
    RT90("RT 90 2.5 gon V 0:-15.","Sweden",CoordType.TransverseMercator
            ,-667.711,1500064.274,15.0 + 48.0 / 60.0 + 22.624306 / 3600.0,1.00000561024,6378137.0,1.0 / 298.257222101
            ,6100000,7700000,1200000,1900000
    ),
    Sweref99TM("Sweref99TM", "Sweden",CoordType.TransverseMercator,
            0.0,500000.0,15.00,0.9996,6378137.0,1.0 / 298.257222101
            ,6000000,7700000,200000,960000
    ),
    WGS84("WGS84","Global",CoordType.Ellipsoid2D),
    bessel("Bessel 1841","Global",CoordType.Ellipsoid2D),
    Sweref99("Sweref99","Global",CoordType.Ellipsoid2D);

    static public enum CoordType {
        TransverseMercator, Ellipsoid2D
    }

    public String name;
    public String country;
    public CoordType type;
    public double falseNorthing;
    public double falseEasting;
    public double centralMeridian;
    public double scale;
    public double axis;
    public double flattening;
    //limits what is valid values;
    public double Nmin;
    public double Nmax;
    public double Emin;
    public double Emax;

    //wgs84 // -90 <= N <= 90, -180 <= E <= 180

    // Gaus Kryger formula stuff, constants for a particular transverse mercator system and can thus need only be calculated once for each CS
    public double e2;
    public double n ;
    public double a_roof;
    public double A;
    public double B;
    public double C;
    public double D;
    public double beta1;
    public double beta2;
    public double beta3;
    public double beta4;
    public double phi;
    public double lambda;
    public double lambda_zero;
    public double Astar;
    public double Bstar;
    public double Cstar;
    public double Dstar;
    public double delta1;
    public double delta2;
    public double delta3;
    public double delta4;

    CoordSystem(String name, String country, CoordType type) {
        this.name = name;
        this.country = country;
        this.type = type;
    }

    CoordSystem(String name, String country, CoordType type, double falseNorthing, double falseEasting, double centralMeridian, double scale, double axis, double flattening) {
        this.name = name;
        this.country = country;
        this.type = type;
        this.falseNorthing = falseNorthing;
        this.falseEasting = falseEasting;
        this.centralMeridian = centralMeridian;
        this.scale = scale;
        this.axis = axis;
        this.flattening = flattening;

        // Gaus Kryger formula stuff
        e2 = flattening * (2.0 - flattening);
        n = flattening / (2.0 - flattening);
        a_roof = axis / (1.0 + n) * (1.0 + n * n / 4.0 + n * n * n * n / 64.0);
        A = e2;
        B = (5.0 * e2 * e2 - e2 * e2 * e2) / 6.0;
        C = (104.0 * e2 * e2 * e2 - 45.0 * e2 * e2 * e2 * e2) / 120.0;
        D = (1237.0 * e2 * e2 * e2 * e2) / 1260.0;
        Astar = e2 + e2 * e2 + e2 * e2 * e2 + e2 * e2 * e2 * e2;
        Bstar = -(7.0 * e2 * e2 + 17.0 * e2 * e2 * e2 + 30.0 * e2 * e2 * e2 * e2) / 6.0;
        Cstar = (224.0 * e2 * e2 * e2 + 889.0 * e2 * e2 * e2 * e2) / 120.0;
        Dstar = -(4279.0 * e2 * e2 * e2 * e2) / 1260.0;
        beta1 = n / 2.0 - 2.0 * n * n / 3.0 + 5.0 * n * n * n / 16.0 + 41.0 * n * n * n * n / 180.0;
        beta2 = 13.0 * n * n / 48.0 - 3.0 * n * n * n / 5.0 + 557.0 * n * n * n * n / 1440.0;
        beta3 = 61.0 * n * n * n / 240.0 - 103.0 * n * n * n * n / 140.0;
        beta4 = 49561.0 * n * n * n * n / 161280.0;
        lambda_zero = centralMeridian*Math.PI/180;
        delta1 = n / 2.0 - 2.0 * n * n / 3.0 + 37.0 * n * n * n / 96.0 - n * n * n * n / 360.0;
        delta2 = n * n / 48.0 + n * n * n / 15.0 - 437.0 * n * n * n * n / 1440.0;
        delta3 = 17.0 * n * n * n / 480.0 - 37 * n * n * n * n / 840.0;
        delta4 = 4397.0 * n * n * n * n / 161280.0;
    }

    CoordSystem(String name, String country, CoordType type, double falseNorthing, double falseEasting, double centralMeridian, double scale, double axis, double flattening, double Nmin, double Nmax, double Emin, double Emax) {
        this(name, country, type, falseNorthing, falseEasting, centralMeridian, scale, axis, flattening);
        this.Nmin = Nmin;
        this.Nmax = Nmax;
        this.Emin = Emin;
        this.Emax = Emax;
    }

    public String getName() {
        return name;
    }
}
