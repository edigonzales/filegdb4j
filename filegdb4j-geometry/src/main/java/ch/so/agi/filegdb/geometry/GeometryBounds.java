package ch.so.agi.filegdb.geometry;

import java.util.ArrayList;
import java.util.List;

/** Bounds including circular arc extrema, not just stored vertices. */
public final class GeometryBounds {
  private GeometryBounds() {}

  public static Envelope of(FileGdbGeometry geometry) {
    List<FileGdbPoint> points = new ArrayList<>();
    if (geometry == null) return null;
    if (geometry instanceof FileGdbPoint) points.add((FileGdbPoint) geometry);
    else if (geometry instanceof FileGdbMultiPoint) points.addAll(((FileGdbMultiPoint) geometry).points());
    else {
      List<FileGdbPart> parts =
          geometry instanceof FileGdbPolyline
              ? ((FileGdbPolyline) geometry).parts()
              : ((FileGdbPolygon) geometry).parts();
      for (FileGdbPart part : parts) {
        points.addAll(part.points());
        for (FileGdbSegment segment : part.segments()) {
          if (segment instanceof CircularArcSegment) {
            CircularArcSegment arc = (CircularArcSegment) segment;
            ArcGeometry circle =
                ArcGeometry.of(
                    part.points().get(arc.startPointIndex()),
                    part.points().get(arc.startPointIndex() + 1),
                    arc);
            if (circle != null)
              for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2;
                if (circle.containsAngle(a))
                  points.add(
                      new FileGdbPoint(
                          circle.centerX() + circle.radius() * Math.cos(a),
                          circle.centerY() + circle.radius() * Math.sin(a)));
              }
          } else {
            throw new UnsupportedOperationException(
                "Exact envelope queries for Bezier/ellipse curves are not supported");
          }
        }
      }
    }
    double xmin = Double.POSITIVE_INFINITY,
        ymin = xmin,
        xmax = Double.NEGATIVE_INFINITY,
        ymax = xmax;
    for (FileGdbPoint p : points)
      if (Double.isFinite(p.x()) && Double.isFinite(p.y())) {
        xmin = Math.min(xmin, p.x());
        ymin = Math.min(ymin, p.y());
        xmax = Math.max(xmax, p.x());
        ymax = Math.max(ymax, p.y());
      }
    return xmin == Double.POSITIVE_INFINITY ? null : new Envelope(xmin, ymin, xmax, ymax);
  }

  public static boolean intersects(Envelope a, Envelope b) {
    return a != null
        && b != null
        && a.xMin() <= b.xMax()
        && a.xMax() >= b.xMin()
        && a.yMin() <= b.yMax()
        && a.yMax() >= b.yMin();
  }
}
