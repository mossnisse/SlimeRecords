package nisse.whatsmysocken.coords;

public class Coordinates {
    private Double north, east;
    private static double degToRad = Math.PI/180;
    private static double radToDeg = 180/Math.PI;
    private static double sweref99TM_false_northing = 0.0;
    private static double sweref99TM_false_easting = 500000.0;
    private static double sweref99TM_centralMeridian = 0.26179938779914943653855361527329; //radians
    private static double sweref99TM_scale = 0.9996;
    private static double sweref99TM_axis = 6378137.0; // GRS 80.
    private static double sweref99TM_flattening = 1.0 / 298.257222101; // GRS 80.

    public Coordinates(double north, double east) {
        this.north = north;
        this.east = east;
    }

    public Coordinates(Point p) {
        this.north = (double)p.getY();
        this.east = (double)p.getX();
    }

    private static double sinh(double value) {
        return 0.5 * (Math.exp(value) - Math.exp(-value));
    }

    private static double cosh(double value) {
        return 0.5 * (Math.exp(value) + Math.exp(-value));
    }

    private static double atanh(double value) {
        return 0.5 * Math.log((1.0 + value) / (1.0 - value));
    }

    public void setCoordinate(double north, double east) {
        this.north = north;
        this.east = east;
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


    // Gauss–Krüger Formula to covert to Transverse mercator system from elipsodial system
    public Coordinates convertTo(CoordSystem CS) {
        double phi = north * degToRad;
        double lambda = east * degToRad;
        double phi_star = phi - Math.sin(phi) * Math.cos(phi) * (CS.e2 +
                CS.B * Math.pow(Math.sin(phi), 2) +
                CS. C * Math.pow(Math.sin(phi), 4) +
                CS.D * Math.pow(Math.sin(phi), 6));
        double delta_lambda = lambda - CS.lambda_zero;
        double xi_prim = Math.atan(Math.tan(phi_star) / Math.cos(delta_lambda));
        double eta_prim = atanh(Math.cos(phi_star) * Math.sin(delta_lambda));
        double x = CS.scale * CS.a_roof * (xi_prim +
                CS.beta1 * Math.sin(2.0 * xi_prim) * cosh(2.0 * eta_prim) +
                CS.beta2 * Math.sin(4.0 * xi_prim) * cosh(4.0 * eta_prim) +
                CS.beta3 * Math.sin(6.0 * xi_prim) * cosh(6.0 * eta_prim) +
                CS.beta4 * Math.sin(8.0 * xi_prim) * cosh(8.0 * eta_prim)) +
                CS.falseNorthing;
        double y = CS.scale * CS.a_roof * (eta_prim +
                CS.beta1 * Math.cos(2.0 * xi_prim) * sinh(2.0 * eta_prim) +
                CS.beta2 * Math.cos(4.0 * xi_prim) * sinh(4.0 * eta_prim) +
                CS.beta3 * Math.cos(6.0 * xi_prim) * sinh(6.0 * eta_prim) +
                CS.beta4 * Math.cos(8.0 * xi_prim) * sinh(8.0 * eta_prim)) +
                CS.falseEasting;
        return new Coordinates(x, y);
    }

    // Gauss–Krüger Formula to covert to elipsodial system from an Transverse mercator system
    public Coordinates convertFrom(CoordSystem CS) {
        double xi = (north - CS.falseNorthing) / (CS.scale * CS.a_roof);
        double eta = (east - CS.falseEasting) / (CS.scale * CS.a_roof);
        //System.out.println("new xi: "+xi+" eta: "+eta);
        double xi_prim = xi -
                CS.delta1 * Math.sin(2.0 * xi) * cosh(2.0 * eta) -
                CS.delta2 * Math.sin(4.0 * xi) * cosh(4.0 * eta) -
                CS.delta3 * Math.sin(6.0 * xi) * cosh(6.0 * eta) -
                CS.delta4 * Math.sin(8.0 * xi) * cosh(8.0 * eta);
        double eta_prim = eta -
                CS.delta1 * Math.cos(2.0 * xi) * sinh(2.0 * eta) -
                CS.delta2 * Math.cos(4.0 * xi) * sinh(4.0 * eta) -
                CS.delta3 * Math.cos(6.0 * xi) * sinh(6.0 * eta) -
                CS.delta4 * Math.cos(8.0 * xi) * sinh(8.0 * eta);

        //System.out.println("xi_prim: "+xi_prim + " eta_prim: "+eta_prim);
        double phi_star = Math.asin(Math.sin(xi_prim) / cosh(eta_prim));

        //System.out.println("phi_star: "+phi_star);
        double delta_lambda = Math.atan(sinh(eta_prim) / Math.cos(xi_prim));
        double lon_radian = CS.lambda_zero + delta_lambda;
        double lat_radian = phi_star + Math.sin(phi_star) * Math.cos(phi_star) *
                (CS.Astar +
                        CS.Bstar * Math.pow(Math.sin(phi_star), 2) +
                        CS.Cstar * Math.pow(Math.sin(phi_star), 4) +
                        CS.Dstar * Math.pow(Math.sin(phi_star), 6));
        return new Coordinates(lat_radian*radToDeg, lon_radian*radToDeg);
    }


    //double reast = (0.2758717076 + Math.atan(Math.sinh(np)/Math.cos(xp)))* radToDeg;

    public boolean isValid(CoordSystem CS) {
        return CS.Nmax >= north && CS.Nmin <= north && CS.Emax >= east && CS.Emin <= east;
    }

    //konverterar koordinater till RT90 från WGS84
    public Coordinates convertToRT90FromWGS84() {
        Double k0xa = 6.3674848719179137e6;
        Double FN = -667.711;			//false northing
        Double FE = 1.500064274e6;		//false easting
        Double A = 0.006694380021;
        Double B = 0.00003729560209;
        Double C = 2.592527517e-7;
        Double Dp = 1.971698945e-9;
        Double lambdanoll = 0.27587170754507245;  //longitude of the central meridian
        Double beta1 = 0.0008377318249;
        Double beta2 = 7.608527793e-7;
        Double beta3 = 1.197638020e-9;
        Double beta4 = 2.443376245e-12;

        Double Phi = (north/180)*Math.PI;	//Geodetic latitude in radians
        Double deltalambda= (east/180)*Math.PI-lambdanoll;

        Double Phistar = Phi-Math.sin(Phi)*Math.cos(Phi)*(A+B*Math.sin(2*Phi)+C*Math.sin(4*Phi)+Dp*Math.sin(6*Phi));

        Double xifjutt = Math.atan(Math.tan(Phistar)/Math.cos(deltalambda));
        Double etafjutt = atanh(Math.cos(Phistar)*Math.sin(deltalambda));

        Double rnorth =  k0xa*(xifjutt+beta1*Math.sin(2*xifjutt)*Math.cosh(2*etafjutt)+beta2*Math.sin(4*xifjutt)*Math.cosh(4*etafjutt)
                +beta3*Math.sin(6*xifjutt)*Math.cosh(6*etafjutt)+beta4*Math.sin(8*xifjutt)*Math.cosh(8*etafjutt))+FN;

        Double reast = k0xa*(etafjutt+beta1*Math.cos(2*xifjutt)*Math.sinh(2*etafjutt)+beta2*Math.cos(4*xifjutt)*Math.sinh(4*etafjutt)
                +beta3*Math.cos(6*xifjutt)*Math.sinh(6*etafjutt)+beta4*Math.cos(8*xifjutt)*Math.sinh(8*etafjutt))+FE;
        return new Coordinates(Math.round(rnorth), Math.round(reast));
    }


    //konverterar koordinater till sweref99TM från wgs84 Sweref99 är så likt wgs84 så ingen konvertering mellan ellipsoider behövs
    public Coordinates convertToSweref99TMFromWGS84() {
        double e2 = sweref99TM_flattening * (2.0 - sweref99TM_flattening);
        double n = sweref99TM_flattening / (2.0 - sweref99TM_flattening);
        double a_roof = sweref99TM_axis / (1.0 + n) * (1.0 + n * n / 4.0 + n * n * n * n / 64.0);
        //double A = e2;
        double B = (5.0 * e2 * e2 - e2 * e2 * e2) / 6.0;
        double C = (104.0 * e2 * e2 * e2 - 45.0 * e2 * e2 * e2 * e2) / 120.0;
        double D = (1237.0 * e2 * e2 * e2 * e2) / 1260.0;
        double beta1 = n / 2.0 - 2.0 * n * n / 3.0 + 5.0 * n * n * n / 16.0 + 41.0 * n * n * n * n / 180.0;
        double beta2 = 13.0 * n * n / 48.0 - 3.0 * n * n * n / 5.0 + 557.0 * n * n * n * n / 1440.0;
        double beta3 = 61.0 * n * n * n / 240.0 - 103.0 * n * n * n * n / 140.0;
        double beta4 = 49561.0 * n * n * n * n / 161280.0;

        double phi = north * degToRad;
        double lambda = east * degToRad;
        double lambda_zero = sweref99TM_centralMeridian;

        double phi_star = phi - Math.sin(phi) * Math.cos(phi) * (e2 +
                B * Math.pow(Math.sin(phi), 2) +
                C * Math.pow(Math.sin(phi), 4) +
                D * Math.pow(Math.sin(phi), 6));
        double delta_lambda = lambda - lambda_zero;
        double xi_prim = Math.atan(Math.tan(phi_star) / Math.cos(delta_lambda));
        double eta_prim = atanh(Math.cos(phi_star) * Math.sin(delta_lambda));
        double x = sweref99TM_scale * a_roof * (xi_prim +
                beta1 * Math.sin(2.0 * xi_prim) * cosh(2.0 * eta_prim) +
                beta2 * Math.sin(4.0 * xi_prim) * cosh(4.0 * eta_prim) +
                beta3 * Math.sin(6.0 * xi_prim) * cosh(6.0 * eta_prim) +
                beta4 * Math.sin(8.0 * xi_prim) * cosh(8.0 * eta_prim)) +
                sweref99TM_false_northing;
        double y = sweref99TM_scale * a_roof * (eta_prim +
                beta1 * Math.cos(2.0 * xi_prim) * sinh(2.0 * eta_prim) +
                beta2 * Math.cos(4.0 * xi_prim) * sinh(4.0 * eta_prim) +
                beta3 * Math.cos(6.0 * xi_prim) * sinh(6.0 * eta_prim) +
                beta4 * Math.cos(8.0 * xi_prim) * sinh(8.0 * eta_prim)) +
                sweref99TM_false_easting;
        return new Coordinates(x, y);
    }

    //konverterar koordinater till WGS84 från RT90
    public Coordinates convertToWGS84FromRT90() {
        //double x = north;
        //double y = east;

        double xi = (north  + 667.711) / 6367484.87;
        double ny = (east - 1500064.274) / 6367484.87;
        //System.out.println("xi: "+xi +" ny: "+ny);

        double s1 = 0.0008377321684;
        double s2 = 5.905869628E-8;
        double xp = xi - s1 * Math.sin(2*xi) * Math.cosh(2*ny) - s2 * Math.sin(4*xi) * Math.cosh(4*ny);
        double np = ny - s1 * Math.cos(2*xi) * Math.sinh(2*ny) - s2 * Math.cos(4*xi) * Math.sinh(4*ny);
        //System.out.println("xp: "+xp+" np: "+np);

        double reast = (0.2758717076 + Math.atan(Math.sinh(np)/Math.cos(xp)))* radToDeg;

        double qs = Math.asin(Math.sin(xp)/Math.cosh(np));
        //System.out.println("qs: "+qs);
        double rnorth = (qs + Math.sin(qs)*Math.cos(qs)*(0.00673949676 -0.00005314390556 * Math.pow(Math.sin(qs),2)) + 5.74891275E-7 * Math.pow(Math.sin(qs),4)) * radToDeg;
        return new Coordinates(rnorth,reast);
    }


    //konverterar koordinater till Sweref99  från sweref99TM Sweref99 är så likt wgs84 så ingen konvertering mellan ellipsoider behövs
    public Coordinates convertToWGS84FromSweref99TM() {
        double e2 = sweref99TM_flattening * (2.0 - sweref99TM_flattening);
        double n = sweref99TM_flattening / (2.0 - sweref99TM_flattening);
        double a_roof = sweref99TM_axis / (1.0 + n) * (1.0 + n * n / 4.0 + n * n * n * n / 64.0);
        double delta1 = n / 2.0 - 2.0 * n * n / 3.0 + 37.0 * n * n * n / 96.0 - n * n * n * n / 360.0;
        double delta2 = n * n / 48.0 + n * n * n / 15.0 - 437.0 * n * n * n * n / 1440.0;
        double delta3 = 17.0 * n * n * n / 480.0 - 37 * n * n * n * n / 840.0;
        double delta4 = 4397.0 * n * n * n * n / 161280.0;
        double Astar = e2 + e2 * e2 + e2 * e2 * e2 + e2 * e2 * e2 * e2;
        double Bstar = -(7.0 * e2 * e2 + 17.0 * e2 * e2 * e2 + 30.0 * e2 * e2 * e2 * e2) / 6.0;
        double Cstar = (224.0 * e2 * e2 * e2 + 889.0 * e2 * e2 * e2 * e2) / 120.0;
        double Dstar = -(4279.0 * e2 * e2 * e2 * e2) / 1260.0;

        // Convert.
        double lambda_zero = sweref99TM_centralMeridian;
        double xi = (north - sweref99TM_false_northing) / (sweref99TM_scale * a_roof);
        double eta = (east - sweref99TM_false_easting) / (sweref99TM_scale * a_roof);
        double xi_prim = xi -
                delta1 * Math.sin(2.0 * xi) * cosh(2.0 * eta) -
                delta2 * Math.sin(4.0 * xi) * cosh(4.0 * eta) -
                delta3 * Math.sin(6.0 * xi) * cosh(6.0 * eta) -
                delta4 * Math.sin(8.0 * xi) * cosh(8.0 * eta);
        double eta_prim = eta -
                delta1 * Math.cos(2.0 * xi) * sinh(2.0 * eta) -
                delta2 * Math.cos(4.0 * xi) * sinh(4.0 * eta) -
                delta3 * Math.cos(6.0 * xi) * sinh(6.0 * eta) -
                delta4 * Math.cos(8.0 * xi) * sinh(8.0 * eta);
        double phi_star = Math.asin(Math.sin(xi_prim) / cosh(eta_prim));
        double delta_lambda = Math.atan(sinh(eta_prim) / Math.cos(xi_prim));
        double lon_radian = lambda_zero + delta_lambda;
        double lat_radian = phi_star + Math.sin(phi_star) * Math.cos(phi_star) *
                (Astar +
                        Bstar * Math.pow(Math.sin(phi_star), 2) +
                        Cstar * Math.pow(Math.sin(phi_star), 4) +
                        Dstar * Math.pow(Math.sin(phi_star), 6));
        return new Coordinates(lat_radian*radToDeg, lon_radian*radToDeg);
    }


    public Coordinates convertToRT90FromSweref99TM() {
        Coordinates wgs84 = convertToWGS84FromSweref99TM();
        //System.out.println("wgs84: "+wgs84);
        return wgs84.convertToRT90FromWGS84();
    }


    public Coordinates convertToSweref99TMFromRT90() {
        Coordinates wgs84 = convertToWGS84FromRT90();
        return wgs84.convertToSweref99TMFromWGS84();

    }

    private double parseDouble(String str) {
        try {
            str = str.replace(',', '.');
            str = str.replace(" ", "");
            return Double.parseDouble(str);
        } catch(Exception e) {
            return 0;
        }
    }

    public void latlong(String latdeg, String longdeg, String latmin, String longmin, String latsec, String longsec, String latdir, String longdir) {
        latlong(parseDouble(latdeg), parseDouble(longdeg), parseDouble(latmin), parseDouble(longmin), parseDouble(latsec), parseDouble(longsec), latdir, longdir);
    }

    //function to convert lat/long degrees, min sec to decimal degrees
    public void latlong(double latdeg, double longdeg, double latmin, double longmin, double latsec, double longsec, String latdir, String longdir) {
        if (latdir == "S")
            this.north = -latdeg-latmin/60-latsec/3600;
        else
            this.north = latdeg+latmin/60+latsec/3600;
        if (longdir == "W")
            this.east =  -longdeg-longmin/60-longsec/3600;
        else
            this.east =  longdeg+longmin/60+longsec/3600;
    }


    // flyttar koordinaten distance i riktning direction. koordinaterna ska vara typ WGS84
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

    static private int alphaNum(Character a) {
        if(Character.isDigit(a)) {
            return Character.getNumericValue(a)-Character.getNumericValue('0');
        } else {
            if (Character.isLowerCase(a)) {
                return Character.getNumericValue(a)-Character.getNumericValue('a');
            } else {
                return Character.getNumericValue(a)-Character.getNumericValue('A');
            }
        }
    }

    static private String numAlphaU(int n) {
        int i = n+(char)'A';
        return Character.toString ((char) i);
    }

    static private String numAlphaL(int n) {
        int i = n+(char)'a';
        return Character.toString ((char) i);
    }

    public void setRUBINRT90(String rubin) {
        //System.out.println("Rubin before remove \""+rubin+" \"");
        rubin = rubin.replaceAll("\\s|\u00A0","");
        if(!Character.isDigit(rubin.charAt(1))) {
            rubin = "0"+rubin;
        }
        //System.out.println("Rubin after fix \""+rubin+" \"");
        //System.out.println("strange char \""+(int)rubin.charAt(3)+"\"");

        int a = Integer.parseInt(rubin.substring(0,2));
        int b = alphaNum(rubin.charAt(2));
        int c = alphaNum(rubin.charAt(3));
        int d = alphaNum(rubin.charAt(4));

        //System.out.println("a:"+a+", b:"+b+", c:"+c+", d:"+d);
        north = 6052500+a*50000+c*5000.0;
        east = 1202500+b*50000+d*5000.0;
    }

    public void setRUBINSweref99TM(String rubin) {
        setRUBINRT90(rubin);
        Coordinates sweref99tm = convertToSweref99TMFromRT90();
        this.north = sweref99tm.north;
        this.east = sweref99tm.east;
    }

    public String getRUBINfromRT90() {
        int n = (int)Math.round(north)-6050000;
        int e = (int)Math.round(east)-1200000;
        int n1 = (n/50000);
        int n2 = (n%50000)/5000;
        int n3 = (n%5000)/100;
        String es1 = numAlphaU(e/50000);
        String es2= numAlphaL((e%50000)/5000);
        int e3 = (e%5000)/100;
        String n3str, e3str;
        if (n3<10)
            n3str = "0"+ Integer.toString(n3);
        else
            n3str = Integer.toString(n3);
        if (e3<10)
            e3str = "0"+ Integer.toString(e3);
        else
            e3str = Integer.toString(e3);

        return n1+es1+n2+es2+" "+n3str+e3str;
    }

    public String getRUBINfromSweref99TM() {
        Coordinates rt90 = convertToRT90FromSweref99TM();
        //System.out.println("rt90: "+rt90);
        return rt90.getRUBINfromRT90();
    }

    public static String getRUBINfromRT90(Point p) {
        int n = (int)Math.round(p.getY())-6050000;
        int e = (int)Math.round(p.getX())-1200000;
        int n1 = n/50000;
        int n2 = (n%50000)/5000;
        String es1 = numAlphaU(e/50000);
        String es2= numAlphaL((e%50000)/5000);
        return n1+es1+n2+es2;
    }

    public static String getRUBINfromSweref99TM(Point p) {
        Coordinates sweref99TM = new Coordinates(p);
        //System.out.println("Sweref99TM: "+sweref99TM);
        return sweref99TM.getRUBINfromSweref99TM();
    }

    static public String getRUBIN50(int east, int north) {
        int n1 = (north-6050000)/50000;
        String es1 = numAlphaU((east-1200000)/50000);
        return n1+es1;
    }

    static public int getRUBIN50N(int east, int north) {
        int n1 = (north-6050000)/50000;
        int es1 = (east-1200000)/50000;
        return es1*33+n1;
    }

    public String toString() {
        return "("+north+", "+east+")";
    }

    public Point toPoint() {
        return new Point((int)Math.round(east), (int)Math.round(north));
    }

    public static void main(String[] args) {
        Coordinates rt90 = new Coordinates(6543540, 1457933);
        Coordinates wgs84old = rt90.convertToWGS84FromRT90();
        Coordinates wgs84 = rt90.convertFrom(CoordSystem.RT90);
        System.out.println("WGS84 old: " + wgs84old);
        System.out.println("wgs84: "+wgs84);
        //System.out.println("Sweref99TM: " + coord2);

        //Coordinates coord3 = new Coordinates(6758843, 487907);
        //Coordinates coord4 = convertToWGS84FromSweref99TM(coord3);

        //System.out.println("WGS84: " + coord3);
        //System.out.println("Sweref99TM: " + coord4);
    }

}