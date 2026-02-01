package nisse.whatsmysocken;

public class Point {
    private int x, y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Point(Point p) {
        this.x = p.x;
        this.y = p.y;
    }

    public Point(String inv) {
        String[] parts = inv.split(", ");
        this.y = Integer.parseInt(parts[0]);
        this.x = Integer.parseInt(parts[1]);
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public double distance(Point p) {
        return Math.sqrt(Math.pow(this.x-p.getX(),2) + Math.pow(this.y-p.getY(),2));
    }

    public double angle(Point p) {
        return Math.atan2(this.y-p.getY(), this.x-p.getX());
    }
    public String toString() {
        return "Point("+x+", "+y+")";
    }
}
