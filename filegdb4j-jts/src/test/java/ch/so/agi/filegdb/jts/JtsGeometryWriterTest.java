package ch.so.agi.filegdb.jts;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.filegdb.geometry.FileGdbPart;
import ch.so.agi.filegdb.geometry.FileGdbPoint;
import ch.so.agi.filegdb.geometry.FileGdbPolygon;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

class JtsGeometryWriterTest {

  private final GeometryFactory factory = new GeometryFactory();
  private final JtsGeometryWriter writer = new JtsGeometryWriter();
  private final JtsGeometryReader reader = new JtsGeometryReader(2056);

  @Test
  void polygonExteriorIsClockwiseAndHoleCounterClockwise() {
    // OGC orientation: CCW exterior, CW hole.
    Geometry polygon =
        factory.createPolygon(
            factory.createLinearRing(
                new Coordinate[] {
                  new Coordinate(0, 0),
                  new Coordinate(10, 0),
                  new Coordinate(10, 10),
                  new Coordinate(0, 10),
                  new Coordinate(0, 0)
                }),
            new org.locationtech.jts.geom.LinearRing[] {
              factory.createLinearRing(
                  new Coordinate[] {
                    new Coordinate(2, 2),
                    new Coordinate(2, 4),
                    new Coordinate(4, 4),
                    new Coordinate(4, 2),
                    new Coordinate(2, 2)
                  })
            });

    FileGdbPolygon fileGdb = writer.polygon((org.locationtech.jts.geom.Polygon) polygon);
    assertThat(fileGdb.parts()).hasSize(2);
    assertThat(signedArea(fileGdb.parts().get(0))).isLessThan(0); // clockwise
    assertThat(signedArea(fileGdb.parts().get(1))).isGreaterThan(0); // counter clockwise

    Geometry roundTrip = reader.read(fileGdb);
    assertThat(roundTrip.equalsTopo(polygon)).isTrue();
  }

  @Test
  void preservesZandM() {
    Geometry point =
        factory.createPoint(new CoordinateXYZM(7, 8, 9, 10));
    var fileGdb = (FileGdbPoint) writer.write(point);
    assertThat(fileGdb.x()).isEqualTo(7);
    assertThat(fileGdb.y()).isEqualTo(8);
    assertThat(fileGdb.z()).isEqualTo(9);
    assertThat(fileGdb.m()).isEqualTo(10);

    Geometry roundTrip = reader.read(fileGdb);
    assertThat(roundTrip).isInstanceOf(Point.class);
    assertThat(roundTrip.getCoordinate().getZ()).isEqualTo(9);
  }

  private static double signedArea(FileGdbPart part) {
    double area = 0;
    var points = part.points();
    for (int i = 0; i < points.size() - 1; i++) {
      area += points.get(i).x() * points.get(i + 1).y() - points.get(i + 1).x() * points.get(i).y();
    }
    return area;
  }
}
