package nisse.SlimeRecords.coords;

public class Point {
    private final double x, y; // Use double for precision until the very last step

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }
    public double getX() { return x; }
    public double getY() { return y; }

    public double distance(Point p) {
        return Math.hypot(this.x - p.x, this.y - p.y);
    }
    public double angle(Point p) {
        return Math.atan2(this.y-p.getY(), this.x-p.getX());
    }
    public String toString() {
        return "Point("+x+", "+y+")";
    }
}