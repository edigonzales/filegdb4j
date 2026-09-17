package ch.so.agi.filegdb.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Converts the curved connections of a {@link FileGdbPart} into polylines.
 *
 * <p>Circular arcs (interior point or center based) and cubic Bezier segments are densified with a
 * configurable number of steps. Ellipse segments are densified along the ellipse parameter; the
 * {@code minor} flag selects the smaller sweep.
 */
public final class CurveStroker {

  public static final int DEFAULT_STEPS = 256;

  private final int steps;

  public CurveStroker() {
    this(DEFAULT_STEPS);
  }

  public CurveStroker(int steps) {
    if (steps < 2) {
      throw new IllegalArgumentException("At least two steps are required");
    }
    this.steps = steps;
  }

  public List<FileGdbPoint> stroke(FileGdbPart part) {
    List<FileGdbPoint> points = part.points();
    if (part.segments().isEmpty() || points.size() < 2) {
      return points;
    }
    List<FileGdbSegment> segments = new ArrayList<>(part.segments());
    segments.sort(Comparator.comparingInt(FileGdbSegment::startPointIndex));
    List<FileGdbPoint> result = new ArrayList<>(points.size() * 2);
    int next = 0;
    for (int i = 0; i < points.size(); i++) {
      result.add(points.get(i));
      if (i == points.size() - 1) {
        continue;
      }
      while (next < segments.size() && segments.get(next).startPointIndex() < i) {
        next++;
      }
      if (next < segments.size() && segments.get(next).startPointIndex() == i) {
        FileGdbSegment segment = segments.get(next++);
        for (FileGdbPoint intermediate : intermediate(points.get(i), points.get(i + 1), segment)) {
          result.add(intermediate);
        }
      }
    }
    return result;
  }

  private List<FileGdbPoint> intermediate(
      FileGdbPoint start, FileGdbPoint end, FileGdbSegment segment) {
    if (segment instanceof CircularArcSegment) {
      return arc(start, end, (CircularArcSegment) segment);
    }
    if (segment instanceof BezierSegment) {
      return bezier(start, end, (BezierSegment) segment);
    }
    if (segment instanceof EllipseSegment) {
      return ellipse(start, end, (EllipseSegment) segment);
    }
    throw new IllegalArgumentException("Unsupported segment: " + segment);
  }

  private List<FileGdbPoint> arc(FileGdbPoint start, FileGdbPoint end, CircularArcSegment arc) {
    double[] center;
    double startAngle;
    double endAngle;
    double interiorAngle = Double.NaN;
    if (distance(start.x(), start.y(), end.x(), end.y()) < 1e-9) {
      // Full circle: the start point and the interior point are diametrically
      // opposite, the center is their midpoint.
      center =
          new double[] {
            arc.byCenter() ? arc.interiorX() : (start.x() + arc.interiorX()) / 2,
            arc.byCenter() ? arc.interiorY() : (start.y() + arc.interiorY()) / 2
          };
      double radius = distance(start.x(), start.y(), center[0], center[1]);
      startAngle = angle(center, start);
      List<FileGdbPoint> points = new ArrayList<>(steps - 1);
      for (int i = 1; i < steps; i++) {
        double a =
            startAngle
                + (arc.byCenter() && !arc.counterClockwise() ? -1 : 1) * 2 * Math.PI * i / steps;
        double t = (double) i / steps;
        points.add(
            interpolate(
                start, end, center[0] + radius * Math.cos(a), center[1] + radius * Math.sin(a), t));
      }
      return points;
    }
    if (arc.byCenter()) {
      center = new double[] {arc.interiorX(), arc.interiorY()};
      startAngle = angle(center, start);
      endAngle = angle(center, end);
    } else {
      center = circumcenter(start, arc.interiorX(), arc.interiorY(), end);
      if (center == null) {
        return Collections.emptyList();
      }
      startAngle = angle(center, start);
      interiorAngle = angle(center, arc.interiorX(), arc.interiorY());
      endAngle = angle(center, end);
    }
    double radius = distance(center[0], center[1], start.x(), start.y());
    boolean counterClockwise;
    if (arc.byCenter()) {
      counterClockwise = arc.counterClockwise();
    } else {
      double ccwSweep = normalize(endAngle - startAngle);
      double interiorSweep = normalize(interiorAngle - startAngle);
      counterClockwise = interiorSweep <= ccwSweep;
    }
    double sweep = normalize(endAngle - startAngle);
    if (!counterClockwise) {
      sweep = sweep - 2 * Math.PI;
    }
    List<FileGdbPoint> points = new ArrayList<>(steps - 1);
    for (int i = 1; i < steps; i++) {
      double t = (double) i / steps;
      double a = startAngle + sweep * t;
      points.add(
          interpolate(
              start, end, center[0] + radius * Math.cos(a), center[1] + radius * Math.sin(a), t));
    }
    return points;
  }

  private List<FileGdbPoint> bezier(FileGdbPoint start, FileGdbPoint end, BezierSegment bezier) {
    List<FileGdbPoint> points = new ArrayList<>(steps - 1);
    for (int i = 1; i < steps; i++) {
      double t = (double) i / steps;
      double u = 1 - t;
      double x =
          u * u * u * start.x()
              + 3 * u * u * t * bezier.controlX1()
              + 3 * u * t * t * bezier.controlX2()
              + t * t * t * end.x();
      double y =
          u * u * u * start.y()
              + 3 * u * u * t * bezier.controlY1()
              + 3 * u * t * t * bezier.controlY2()
              + t * t * t * end.y();
      points.add(interpolate(start, end, x, y, t));
    }
    return points;
  }

  private List<FileGdbPoint> ellipse(FileGdbPoint start, FileGdbPoint end, EllipseSegment ellipse) {
    double semiMajor = ellipse.semiMajor();
    double semiMinor = semiMajor * ellipse.minorMajorRatio();
    if (semiMajor <= 0 || semiMinor <= 0) {
      return Collections.emptyList();
    }
    double rotation = Math.toRadians(ellipse.rotationDegrees());
    double startAngle = ellipseAngle(start, ellipse, rotation, semiMajor, semiMinor);
    double endAngle = ellipseAngle(end, ellipse, rotation, semiMajor, semiMinor);
    double sweep = normalize(endAngle - startAngle);
    if (ellipse.complete()) {
      sweep = 2 * Math.PI;
    } else if (ellipse.minor() && sweep > Math.PI) {
      sweep -= 2 * Math.PI;
    } else if (!ellipse.minor() && sweep < Math.PI) {
      sweep -= 2 * Math.PI;
    }
    List<FileGdbPoint> points = new ArrayList<>(steps - 1);
    for (int i = 1; i < steps; i++) {
      double t = (double) i / steps;
      double a = startAngle + sweep * t;
      double localX = semiMajor * Math.cos(a);
      double localY = semiMinor * Math.sin(a);
      double x = ellipse.centerX() + localX * Math.cos(rotation) - localY * Math.sin(rotation);
      double y = ellipse.centerY() + localX * Math.sin(rotation) + localY * Math.cos(rotation);
      points.add(interpolate(start, end, x, y, t));
    }
    return points;
  }

  private static double ellipseAngle(
      FileGdbPoint point,
      EllipseSegment ellipse,
      double rotation,
      double semiMajor,
      double semiMinor) {
    double dx = point.x() - ellipse.centerX();
    double dy = point.y() - ellipse.centerY();
    double localX = dx * Math.cos(rotation) + dy * Math.sin(rotation);
    double localY = -dx * Math.sin(rotation) + dy * Math.cos(rotation);
    return Math.atan2(localY / semiMinor, localX / semiMajor);
  }

  private static FileGdbPoint interpolate(
      FileGdbPoint start, FileGdbPoint end, double x, double y, double fraction) {
    Double z = null;
    if (start.z() != null && end.z() != null) {
      z = start.z() + (end.z() - start.z()) * fraction;
    }
    Double m = null;
    if (start.m() != null && end.m() != null) {
      m = start.m() + (end.m() - start.m()) * fraction;
    }
    return new FileGdbPoint(x, y, z, m);
  }

  private static double normalize(double angle) {
    double value = angle % (2 * Math.PI);
    return value < 0 ? value + 2 * Math.PI : value;
  }

  private static double angle(double[] center, FileGdbPoint point) {
    return angle(center, point.x(), point.y());
  }

  private static double angle(double[] center, double x, double y) {
    return normalize(Math.atan2(y - center[1], x - center[0]));
  }

  private static double distance(double x1, double y1, double x2, double y2) {
    return Math.hypot(x2 - x1, y2 - y1);
  }

  /** Circle center through three points, or null if the points are collinear. */
  private static double[] circumcenter(FileGdbPoint a, double bx, double by, FileGdbPoint c) {
    double d = 2 * (a.x() * (by - c.y()) + bx * (c.y() - a.y()) + c.x() * (a.y() - by));
    if (Math.abs(d) < 1e-12) {
      return null;
    }
    double a2 = a.x() * a.x() + a.y() * a.y();
    double b2 = bx * bx + by * by;
    double c2 = c.x() * c.x() + c.y() * c.y();
    double ux = (a2 * (by - c.y()) + b2 * (c.y() - a.y()) + c2 * (a.y() - by)) / d;
    double uy = (a2 * (c.x() - bx) + b2 * (a.x() - c.x()) + c2 * (bx - a.x())) / d;
    return new double[] {ux, uy};
  }
}
