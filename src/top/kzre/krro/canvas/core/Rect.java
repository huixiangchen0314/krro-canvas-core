package top.kzre.krro.canvas.core;

public final class Rect {
    public final double xMin, yMin, xMax, yMax;

    public Rect(double xMin, double yMin, double xMax, double yMax) {
        if (xMin > xMax || yMin > yMax)
            throw new IllegalArgumentException("Invalid rect bounds");
        this.xMin = xMin;
        this.yMin = yMin;
        this.xMax = xMax;
        this.yMax = yMax;
    }

    public boolean contains(double x, double y) {
        return x >= xMin && x <= xMax && y >= yMin && y <= yMax;
    }

    public boolean intersects(Rect other) {
        return !(other.xMin > xMax || other.xMax < xMin ||
                other.yMin > yMax || other.yMax < yMin);
    }

    public double midX() {
        return (xMin + xMax) * 0.5;
    }

    public double midY() {
        return (yMin + yMax) * 0.5;
    }

    public double distToPointSq(double x, double y) {
        double dx = 0, dy = 0;
        if (x < xMin) dx = xMin - x;
        else if (x > xMax) dx = x - xMax;
        if (y < yMin) dy = yMin - y;
        else if (y > yMax) dy = y - yMax;
        return dx * dx + dy * dy;
    }

    @Override
    public String toString() {
        return String.format("Rect[%.2f,%.2f,%.2f,%.2f]", xMin, yMin, xMax, yMax);
    }
}
