package ch.so.agi.filegdb.geometry;

/** Analytic XY circle geometry, using translated coordinates to avoid cancellation. */
public final class ArcGeometry {
  private final double centerX;
  private final double centerY;
  private final double radius;
  private final double start;
  private final double sweep;

  public ArcGeometry(double centerX, double centerY, double radius, double start, double sweep) {
    this.centerX = centerX;
    this.centerY = centerY;
    this.radius = radius;
    this.start = start;
    this.sweep = sweep;
  }

  public static ArcGeometry of(FileGdbPoint a, FileGdbPoint b, CircularArcSegment arc) {
    double cx, cy;
    boolean closed = a.x() == b.x() && a.y() == b.y();
    if (arc.byCenter()) {
      cx = arc.interiorX();
      cy = arc.interiorY();
    } else if (closed) {
      cx = (a.x() + arc.interiorX()) / 2;
      cy = (a.y() + arc.interiorY()) / 2;
    } else {
      double ux = arc.interiorX() - a.x(), uy = arc.interiorY() - a.y();
      double vx = b.x() - a.x(), vy = b.y() - a.y();
      double d = 2 * (ux * vy - uy * vx);
      if (d == 0) return null;
      double u2 = ux * ux + uy * uy, v2 = vx * vx + vy * vy;
      cx = a.x() + (u2 * vy - v2 * uy) / d;
      cy = a.y() + (v2 * ux - u2 * vx) / d;
    }
    double start = Math.atan2(a.y() - cy, a.x() - cx);
    double sweep = positive(Math.atan2(b.y() - cy, b.x() - cx) - start);
    boolean ccw =
        arc.byCenter()
            ? arc.counterClockwise()
            : positive(Math.atan2(arc.interiorY() - cy, arc.interiorX() - cx) - start) <= sweep;
    if (closed) sweep = (arc.byCenter() && !ccw ? -1 : 1) * 2 * Math.PI;
    else if (!ccw) sweep -= 2 * Math.PI;
    return new ArcGeometry(cx, cy, Math.hypot(a.x() - cx, a.y() - cy), start, sweep);
  }

  public static double positive(double a) {
    double v = a % (2 * Math.PI);
    return v < 0 ? v + 2 * Math.PI : v;
  }

  public FileGdbPoint point(double t) {
    return new FileGdbPoint(
        centerX + radius * Math.cos(start + sweep * t),
        centerY + radius * Math.sin(start + sweep * t));
  }

  public boolean containsAngle(double angle) {
    return positive(sweep >= 0 ? angle - start : start - angle) <= Math.abs(sweep) + 1e-14;
  }

  public double centerX() {
    return centerX;
  }

  public double centerY() {
    return centerY;
  }

  public double radius() {
    return radius;
  }

  public double start() {
    return start;
  }

  public double sweep() {
    return sweep;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ArcGeometry other = (ArcGeometry) o;
    return Double.compare(centerX, other.centerX) == 0
        && Double.compare(centerY, other.centerY) == 0
        && Double.compare(radius, other.radius) == 0
        && Double.compare(start, other.start) == 0
        && Double.compare(sweep, other.sweep) == 0;
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Double.hashCode(centerX);
    result = 31 * result + Double.hashCode(centerY);
    result = 31 * result + Double.hashCode(radius);
    result = 31 * result + Double.hashCode(start);
    result = 31 * result + Double.hashCode(sweep);
    return result;
  }

  @Override
  public final String toString() {
    return "ArcGeometry[centerX="
        + centerX
        + ", centerY="
        + centerY
        + ", radius="
        + radius
        + ", start="
        + start
        + ", sweep="
        + sweep
        + "]";
  }
}
