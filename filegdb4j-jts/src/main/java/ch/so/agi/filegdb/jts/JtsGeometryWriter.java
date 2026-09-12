package ch.so.agi.filegdb.jts;

import ch.so.agi.filegdb.geometry.FileGdbMultiPoint;
import ch.so.agi.filegdb.geometry.FileGdbPart;
import ch.so.agi.filegdb.geometry.FileGdbPoint;
import ch.so.agi.filegdb.geometry.FileGdbPolygon;
import ch.so.agi.filegdb.geometry.FileGdbPolyline;
import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

/**
 * Converts JTS geometries to the file geodatabase geometry model.
 *
 * <p>Polygon rings are written with the Esri orientation convention: exterior rings clockwise,
 * interior rings counter clockwise. Z and M ordinates are preserved when present.
 */
public final class JtsGeometryWriter {

  public FileGdbPolygon polygon(Polygon polygon) {
    List<FileGdbPart> parts = new ArrayList<>();
    parts.add(ring(polygon.getExteriorRing().getCoordinates(), true));
    for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
      parts.add(ring(polygon.getInteriorRingN(i).getCoordinates(), false));
    }
    return new FileGdbPolygon(parts);
  }

  public FileGdbPolyline lineString(LineString lineString) {
    return new FileGdbPolyline(List.of(part(lineString.getCoordinates())));
  }

  /** Converts any supported JTS geometry. */
  public ch.so.agi.filegdb.geometry.FileGdbGeometry write(Geometry geometry) {
    if (geometry == null || geometry.isEmpty()) {
      return null;
    }
    return switch (geometry.getGeometryType()) {
      case "Point" -> point(geometry.getCoordinate());
      case "MultiPoint" -> {
        List<FileGdbPoint> points = new ArrayList<>();
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
          points.add(point(geometry.getGeometryN(i).getCoordinate()));
        }
        yield new FileGdbMultiPoint(points);
      }
      case "LineString", "LinearRing", "CircularString" -> lineString((LineString) geometry);
      case "MultiLineString", "MultiCurve" -> {
        List<FileGdbPart> parts = new ArrayList<>();
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
          parts.add(part(((LineString) geometry.getGeometryN(i)).getCoordinates()));
        }
        yield new FileGdbPolyline(parts);
      }
      case "Polygon" -> polygon((Polygon) geometry);
      case "MultiPolygon" -> {
        List<FileGdbPart> parts = new ArrayList<>();
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
          Polygon polygon = (Polygon) geometry.getGeometryN(i);
          parts.addAll(polygon(polygon).parts());
        }
        yield new FileGdbPolygon(parts);
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported JTS geometry type: " + geometry.getGeometryType());
    };
  }

  private static FileGdbPoint point(Coordinate coordinate) {
    return new FileGdbPoint(coordinate.getX(), coordinate.getY(), z(coordinate), m(coordinate));
  }

  private static Double z(Coordinate coordinate) {
    double z = coordinate.getZ();
    return Double.isNaN(z) ? null : z;
  }

  private static Double m(Coordinate coordinate) {
    return Double.isNaN(coordinate.getM()) ? null : coordinate.getM();
  }

  private FileGdbPart part(Coordinate[] coordinates) {
    return new FileGdbPart(
        java.util.Arrays.stream(coordinates).map(JtsGeometryWriter::point).toList());
  }

  private FileGdbPart ring(Coordinate[] coordinates, boolean exterior) {
    List<Coordinate> values = new ArrayList<>(List.of(coordinates));
    if (!values.isEmpty() && !values.get(0).equals2D(values.get(values.size() - 1))) {
      values.add(values.get(0));
    }
    if (values.size() < 4 && exterior) {
      // A degenerate ring is not a polygon; keep the points as they are.
      values = new ArrayList<>(List.of(coordinates));
    } else if (values.size() > 1) {
      boolean clockwise = isClockwise(values);
      if (clockwise != exterior) {
        java.util.Collections.reverse(values);
      }
    }
    List<FileGdbPoint> points = new ArrayList<>(values.size());
    for (Coordinate coordinate : values) {
      points.add(point(coordinate));
    }
    return new FileGdbPart(points);
  }

  private static boolean isClockwise(List<Coordinate> coordinates) {
    double area = 0;
    for (int i = 0; i < coordinates.size() - 1; i++) {
      area +=
          coordinates.get(i).x * coordinates.get(i + 1).y
              - coordinates.get(i + 1).x * coordinates.get(i).y;
    }
    return area < 0;
  }
}
