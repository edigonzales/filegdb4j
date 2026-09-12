package ch.so.agi.filegdb.test;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.filegdb.*;
import ch.so.agi.filegdb.geometry.*;
import ch.so.agi.filegdb.jts.*;
import ch.so.agi.filegdb.table.*;
import ch.so.agi.filegdb.write.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

class WriterRegressionTest {
  @TempDir Path directory;

  @Test
  void blockBoundariesAndCircularWriter() throws Exception {
    Path path = directory.resolve("written.gdb");
    try (var db = FileGeodatabase.create(path)) {
      for (int count : new int[] {1024, 1025, 2048, 2049, 8192, 8193}) {
        try (var w =
            db.createTable(
                TableDefinition.builder("rows" + count)
                    .field(FileGdbField.integer("value"))
                    .build())) {
          for (int i = 1; i <= count; i++) w.write(new Object[] {i});
        }
      }
      var part =
          new FileGdbPart(
              List.of(new FileGdbPoint(1, 0), new FileGdbPoint(0, 1)),
              List.of(new CircularArcSegment(0, 0, 0, true, false)));
      try (var w =
          db.createFeatureClass(
              FeatureClassDefinition.builder("arcs")
                  .geometry(GeometryFieldDefinition.of("shape", GeometryKind.LINE))
                  .build())) {
        w.write(new Object[0], new FileGdbPolyline(List.of(part)));
      }
    }
    try (var db = FileGeodatabase.open(path)) {
      for (int count : new int[] {1024, 1025, 2048, 2049, 8192, 8193})
        try (var t = db.table("rows" + count)) {
          assertThat(t.rowCount()).isEqualTo(count);
          int read = 0;
          for (var row : t) {
            read++;
            assertThat(row.get("value")).isEqualTo((long) read);
          }
          assertThat(read).isEqualTo(count);
        }
      try (var t = db.table("arcs")) {
        assertThat(((FileGdbPolyline) t.read(1).geometry()).parts().getFirst().segments())
            .hasSize(1);
        assertThat(t.geomField().geometry().extent().xMin()).isCloseTo(-1, within(1e-7));
      }
    }
    if (Ogr.ogrInfo() != null) {
      String output = Ogr.run(Ogr.ogrInfo(), "-al", "-so", path.toString());
      assertThat(output).doesNotContain("ERROR").contains("Feature Count: 8193");
      assertThat(Ogr.run(Ogr.ogrInfo(), "-al", path.toString(), "arcs"))
          .contains("CIRCULARSTRING")
          .doesNotContain("ERROR");
    }
  }

  @Test
  void nativeSpatialIndexMatchesScanAndGdal() throws Exception {
    Path path = directory.resolve("indexed.gdb");
    try (var db = FileGeodatabase.create(path);
        var w =
            db.createFeatureClass(
                FeatureClassDefinition.builder("points")
                    .geometry(GeometryFieldDefinition.of("shape", GeometryKind.POINT))
                    .build())) {
      for (int i = 0; i < 1500; i++) w.write(new Object[0], new FileGdbPoint(i, i));
    }
    try (var db = FileGeodatabase.open(path);
        var table = db.table("points")) {
      var box = new ch.so.agi.filegdb.geometry.Envelope(100, 100, 110, 110);
      var indexed = table.query(box);
      var scan = table.query(box, false);
      assertThat(indexed.indexUsed()).isTrue();
      assertThat(indexed.rows())
          .extracting(FileGdbRow::objectId)
          .containsExactlyElementsOf(scan.rows().stream().map(FileGdbRow::objectId).toList());
      assertThat(indexed.rows()).hasSize(11);
      assertThat(indexed.geometriesRead()).isLessThan(150);
    }
    if (Ogr.ogrInfo() != null) {
      String output =
          Ogr.run(
              Ogr.ogrInfo(),
              "--config",
              "OPENFILEGDB_IN_MEMORY_SPI",
              "NO",
              "-al",
              "-so",
              "-spat",
              "100",
              "100",
              "110",
              "110",
              path.toString());
      assertThat(output).doesNotContain("ERROR").contains("Feature Count: 11");
    }
  }

  @Test
  void readsGdalPointIndex() throws Exception {
    if (Ogr.ogr2Ogr() == null) return;
    Path source = directory.resolve("points.geojson");
    StringBuilder json = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
    for (int i = 0; i < 1500; i++) {
      if (i > 0) json.append(',');
      json.append(
              "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
          .append(i)
          .append(',')
          .append(i)
          .append("]}}");
    }
    java.nio.file.Files.writeString(source, json.append("]}").toString());
    Path target = directory.resolve("gdal.gdb");
    Ogr.run(
        Ogr.ogr2Ogr(), "-f", "OpenFileGDB", target.toString(), source.toString(), "-nln", "points");
    try (var db = FileGeodatabase.open(target);
        var table = db.table("points")) {
      var box = new ch.so.agi.filegdb.geometry.Envelope(100, 100, 110, 110);
      var result = table.query(box);
      assertThat(result.indexUsed()).isTrue();
      assertThat(result.rows()).hasSize(11);
      assertThat(result.geometriesRead()).isLessThan(150);
      assertThat(result.rows().stream().map(FileGdbRow::objectId).toList())
          .isEqualTo(table.query(box, false).rows().stream().map(FileGdbRow::objectId).toList());
    }
  }

  @Test
  void preservesOpenLinesAndMeasures() {
    var f = new GeometryFactory();
    var w = new JtsGeometryWriter();
    var line = f.createLineString(new Coordinate[] {new Coordinate(0, 0), new Coordinate(3, 4)});
    assertThat(new JtsGeometryReader(0).read(w.write(line)).equalsExact(line)).isTrue();
    assertThat(((FileGdbPoint) w.write(f.createPoint(new CoordinateXYM(1, 2, 99)))).m())
        .isEqualTo(99);
    for (boolean ccw : new boolean[] {false, true}) {
      var p = new FileGdbPoint(1, 0);
      var circle =
          new FileGdbPolyline(
              List.of(
                  new FileGdbPart(
                      List.of(p, p), List.of(new CircularArcSegment(0, 0, 0, true, ccw)))));
      assertThat(new JtsGeometryReader(0).read(circle).getLength())
          .isCloseTo(2 * Math.PI, within(0.001));
    }
  }
}
