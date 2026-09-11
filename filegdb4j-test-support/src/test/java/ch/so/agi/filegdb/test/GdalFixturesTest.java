package ch.so.agi.filegdb.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.geometry.FileGdbPoint;
import ch.so.agi.filegdb.geometry.FileGdbPolygon;
import ch.so.agi.filegdb.jts.JtsGeometryReader;
import ch.so.agi.filegdb.table.FileGdbRow;
import ch.so.agi.filegdb.table.FileGdbTable;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

/** Additional GDAL OpenFileGDB test data: UTF-16 strings, sparse rows, 3D and V4 tables. */
class GdalFixturesTest {

  @Test
  void readsUtf16Strings() throws Exception {
    Path gdbPath = TestData.gdal("test_utf16.gdb");
    assumeTrue(TestData.available(gdbPath), "test_utf16.gdb fixture is not available");
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath);
        FileGdbTable table = gdb.table("foo")) {
      assertThat(table.rowCount()).isEqualTo(1);
      FileGdbRow row = table.read(1);
      assertThat(row.get("str")).isEqualTo("évenéven");
    }
  }

  @Test
  void readsSparseRowsWithLargeObjectIds() throws Exception {
    Path gdbPath = TestData.gdal("testdatetimeutc.gdb");
    assumeTrue(TestData.available(gdbPath), "testdatetimeutc.gdb fixture is not available");
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath);
        FileGdbTable table = gdb.featureClass("surveyPoint")) {
      assertThat(table.rowCount()).isEqualTo(4);
      List<Long> objectIds = new ArrayList<>();
      for (FileGdbRow row : table) {
        objectIds.add(row.objectId());
      }
      assertThat(objectIds).containsExactly(359L, 360L, 361L, 362L);

      FileGdbRow first = table.read(359);
      assertThat(first.get("what_did_you_kill")).isEqualTo("male");
      assertThat(first.get("CreationDate")).isEqualTo(LocalDateTime.of(2020, 6, 22, 7, 49, 36));
      assertThat(first.geometry()).isInstanceOf(FileGdbPoint.class);
    }
  }

  @Test
  void reads3dPolygonsFromV4Table() throws Exception {
    Path gdbPath = TestData.gdal("objectid64/3features.gdb");
    assumeTrue(TestData.available(gdbPath), "objectid64/3features.gdb fixture is not available");
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath);
        FileGdbTable table = gdb.featureClass("testpolygon")) {
      assertThat(table.rowCount()).isEqualTo(3);
      assertThat(table.geomField().geometry().hasZ()).isTrue();
      FileGdbRow row = table.read(1);
      FileGdbPolygon polygon = (FileGdbPolygon) row.geometry();
      FileGdbPoint first = polygon.parts().get(0).points().get(0);
      assertThat(first.z()).isEqualTo(0.0);

      Geometry geometry = new JtsGeometryReader(table.crs().effectiveWkid()).read(row.geometry());
      assertThat(geometry.getCoordinate().getZ()).isEqualTo(0.0);
    }
  }
}
