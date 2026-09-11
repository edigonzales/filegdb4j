package ch.so.agi.filegdb.jts;

import ch.so.agi.filegdb.geometry.FileGdbGeometry;
import ch.so.agi.filegdb.geometry.FileGdbMultiPoint;
import ch.so.agi.filegdb.geometry.FileGdbPart;
import ch.so.agi.filegdb.geometry.FileGdbPoint;
import ch.so.agi.filegdb.geometry.FileGdbPolygon;
import ch.so.agi.filegdb.geometry.FileGdbPolyline;
import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Converts decoded file geodatabase geometries to JTS.
 *
 * <p>Esri stores exterior polygon rings clockwise and interior rings counter
 * clockwise. Rings are assigned by orientation; holes are attached to the
 * smallest containing exterior ring. Polygon rings with less than four points
 * are skipped, and unclosed rings are closed.
 */
public final class JtsGeometryReader {

  private final GeometryFactory factory;

  public JtsGeometryReader(int srid) {
    this.factory = new GeometryFactory(new PrecisionModel(), srid);
  }

  public GeometryFactory factory() {
    return factory;
  }

  public Geometry read(FileGdbGeometry geometry) {
    if (geometry == null) {
      return null;
    }
    if (geometry instanceof FileGdbPoint point) {
      return factory.createPoint(coordinate(point));
    }
    if (geometry instanceof FileGdbMultiPoint multiPoint) {
      List<Point> points = new ArrayList<>(multiPoint.points().size());
      for (FileGdbPoint point : multiPoint.points()) {
        points.add(factory.createPoint(coordinate(point)));
      }
      return factory.createMultiPoint(points.toArray(Point[]::new));
    }
    if (geometry instanceof FileGdbPolyline polyline) {
      return lineString(polyline);
    }
    if (geometry instanceof FileGdbPolygon polygon) {
      return polygon(polygon);
    }
    throw new IllegalArgumentException("Unsupported geometry: " + geometry.getClass().getName());
  }

  private Geometry lineString(FileGdbPolyline polyline) {
    List<LineString> lines = new ArrayList<>(polyline.parts().size());
    for (FileGdbPart part : polyline.parts()) {
      Coordinate[] coordinates = coordinates(part);
      if (coordinates.length >= 2) {
        lines.add(factory.createLineString(coordinates));
      }
    }
    if (lines.isEmpty()) {
      return factory.createLineString();
    }
    if (lines.size() == 1) {
      return lines.get(0);
    }
    return factory.createMultiLineString(lines.toArray(LineString[]::new));
  }

  private Geometry polygon(FileGdbPolygon polygon) {
    List<LinearRing> exteriors = new ArrayList<>();
    List<LinearRing> holes = new ArrayList<>();
    for (FileGdbPart part : polygon.parts()) {
      Coordinate[] coordinates = coordinates(part);
      if (coordinates.length < 4) {
        continue;
      }
      LinearRing ring = factory.createLinearRing(coordinates);
      if (isClockwise(ring)) {
        exteriors.add(ring);
      } else {
        holes.add(ring);
      }
    }
    if (exteriors.isEmpty()) {
      // Broken orientation: treat every ring as an exterior ring rather than
      // losing data.
      exteriors.addAll(holes);
      holes.clear();
    }

    List<Polygon> shells = new ArrayList<>(exteriors.size());
    for (LinearRing exterior : exteriors) {
      shells.add(factory.createPolygon(exterior));
    }
    List<List<LinearRing>> assignedHoles = new ArrayList<>(exteriors.size());
    for (int i = 0; i < exteriors.size(); i++) {
      assignedHoles.add(new ArrayList<>());
    }
    for (LinearRing hole : holes) {
      int bestIndex = -1;
      double bestArea = Double.POSITIVE_INFINITY;
      Point probe = factory.createPoint(hole.getCoordinateN(0));
      for (int i = 0; i < shells.size(); i++) {
        Polygon shell = shells.get(i);
        if (shell.covers(probe) && shell.getArea() < bestArea) {
          bestArea = shell.getArea();
          bestIndex = i;
        }
      }
      if (bestIndex < 0) {
        // Orphaned hole: keep it as its own polygon.
        exteriors.add(hole);
        shells.add(factory.createPolygon(hole));
        assignedHoles.add(new ArrayList<>());
      } else {
        assignedHoles.get(bestIndex).add(hole);
      }
    }

    List<Polygon> polygons = new ArrayList<>(exteriors.size());
    for (int i = 0; i < exteriors.size(); i++) {
      polygons.add(
          factory.createPolygon(
              exteriors.get(i), assignedHoles.get(i).toArray(LinearRing[]::new)));
    }
    if (polygons.size() == 1) {
      return polygons.get(0);
    }
    return factory.createMultiPolygon(polygons.toArray(Polygon[]::new));
  }

  private Coordinate[] coordinates(FileGdbPart part) {
    List<FileGdbPoint> points = part.points();
    if (points.isEmpty()) {
      return new Coordinate[0];
    }
    Coordinate[] coordinates = new Coordinate[points.size()];
    for (int i = 0; i < points.size(); i++) {
      coordinates[i] = coordinate(points.get(i));
    }
    // File geodatabase rings are closed, but be tolerant.
    if (!coordinates[0].equals2D(coordinates[coordinates.length - 1])) {
      Coordinate[] closed = new Coordinate[coordinates.length + 1];
      System.arraycopy(coordinates, 0, closed, 0, coordinates.length);
      closed[coordinates.length] = new Coordinate(coordinates[0]);
      coordinates = closed;
    }
    return coordinates;
  }

  static Coordinate coordinate(FileGdbPoint point) {
    if (point.m() != null) {
      return new CoordinateXYZM(
          point.x(),
          point.y(),
          point.z() == null ? Double.NaN : point.z(),
          point.m());
    }
    if (point.z() != null) {
      return new Coordinate(point.x(), point.y(), point.z());
    }
    return new Coordinate(point.x(), point.y());
  }

  /** Shoelace formula: negative area means clockwise in a y-up coordinate system. */
  private static boolean isClockwise(LinearRing ring) {
    double area = 0;
    Coordinate[] coordinates = ring.getCoordinates();
    for (int i = 0; i < coordinates.length - 1; i++) {
      area +=
          coordinates[i].x * coordinates[i + 1].y - coordinates[i + 1].x * coordinates[i].y;
    }
    return area < 0;
  }
}
