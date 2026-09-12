package ch.so.agi.filegdb.geometry;

/** Analytic XY circle geometry, using translated coordinates to avoid cancellation. */
public record ArcGeometry(
    double centerX, double centerY, double radius, double start, double sweep) {
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
}
