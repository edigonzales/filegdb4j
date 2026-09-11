package ch.so.agi.filegdb.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.geometry.FileGdbPolygon;
import ch.so.agi.filegdb.geometry.FileGdbPolyline;
import ch.so.agi.filegdb.jts.JtsGeometryReader;
import ch.so.agi.filegdb.table.FileGdbRow;
import ch.so.agi.filegdb.table.FileGdbTable;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

/** Curved segments (circular arcs), using the GDAL OpenFileGDB test data. */
class CurvesFixtureTest {

  private static Path gdbPath;

  @BeforeAll
  static void setUp() {
    gdbPath = TestData.gdal("curves.gdb");
    assumeTrue(TestData.available(gdbPath), "curves.gdb fixture is not available");
  }

  @Test
  void readsCircularArcPolyline() throws Exception {
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath);
        FileGdbTable table = gdb.featureClass("line")) {
      FileGdbRow row = table.read(10);
      FileGdbPolyline polyline = (FileGdbPolyline) row.geometry();
      assertThat(polyline.parts()).hasSize(1);
      assertThat(polyline.parts().get(0).segments()).hasSize(1);

      Geometry geometry = new JtsGeometryReader(table.crs().effectiveWkid()).read(row.geometry());
      // GDAL reports SHAPE_Length = 8.39459167219456 for this feature.
      assertThat(geometry.getLength()).isCloseTo(8.3946, within(1e-2));
    }
  }

  @Test
  void readsCircularArcPolygon() throws Exception {
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath);
        FileGdbTable table = gdb.featureClass("polygon")) {
      FileGdbRow row = table.read(1);
      FileGdbPolygon polygon = (FileGdbPolygon) row.geometry();
      assertThat(polygon.parts().get(0).segments()).isNotEmpty();

      Geometry geometry = new JtsGeometryReader(table.crs().effectiveWkid()).read(row.geometry());
      // GDAL reports SHAPE_Area = 1.80492585969112 and length 4.76249590911315.
      assertThat(geometry.getArea()).isCloseTo(1.8049, within(1e-3));
      assertThat(geometry.getLength()).isCloseTo(4.7625, within(1e-2));
    }
  }
}
