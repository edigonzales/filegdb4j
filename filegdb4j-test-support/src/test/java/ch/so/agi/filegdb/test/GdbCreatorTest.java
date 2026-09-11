package ch.so.agi.filegdb.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.CrsDefinition;
import ch.so.agi.filegdb.geometry.FileGdbPart;
import ch.so.agi.filegdb.geometry.FileGdbPoint;
import ch.so.agi.filegdb.geometry.FileGdbPolygon;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import ch.so.agi.filegdb.geometry.GeometryKind;
import ch.so.agi.filegdb.table.FileGdbRow;
import ch.so.agi.filegdb.table.FileGdbTable;
import ch.so.agi.filegdb.write.FeatureClassDefinition;
import ch.so.agi.filegdb.write.GdbFeatureWriter;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End to end creation of a file geodatabase with one feature class. */
class GdbCreatorTest {

  @Test
  void createsFeatureClassReadableByJavaAndGdal(@TempDir Path directory) throws Exception {
    Path databasePath = directory.resolve("sample.gdb");
    java.time.LocalDateTime timestamp = java.time.LocalDateTime.of(2024, 5, 7, 8, 30, 15);
    java.util.UUID guid = java.util.UUID.fromString("9f8e7d6c-5b4a-4938-8271-0a1b2c3d4e5f");
    try (FileGeodatabase database = FileGeodatabase.create(databasePath)) {
      FeatureClassDefinition definition =
          FeatureClassDefinition.builder("roads")
              .field(ch.so.agi.filegdb.table.FileGdbField.string("name", 255).asRequired())
              .field(ch.so.agi.filegdb.table.FileGdbField.integer("lanes").asNullable())
              .field(ch.so.agi.filegdb.table.FileGdbField.real("length").asNullable())
              .geometry(GeometryFieldDefinition.of("shape", GeometryKind.POLYGON))
              .crs(new CrsDefinition(2056, 2056, ""))
              .build();
      try (GdbFeatureWriter writer = database.createFeatureClass(definition)) {
        writer.write(new Object[] {"A1", 2L, 12.5}, square(2600000, 1200000, 100));
        writer.write(new Object[] {"B2", null, null}, null);
      }

      FeatureClassDefinition surveyDefinition =
          FeatureClassDefinition.builder("survey")
              .field(ch.so.agi.filegdb.table.FileGdbField.string("label", 64))
              .field(ch.so.agi.filegdb.table.FileGdbField.dateTime("created"))
              .field(ch.so.agi.filegdb.table.FileGdbField.guid("globalid"))
              .geometry(GeometryFieldDefinition.of("shape", GeometryKind.POINT))
              .crs(new CrsDefinition(2056, 2056, ""))
              .build();
      try (GdbFeatureWriter writer = database.createFeatureClass(surveyDefinition)) {
        writer.write(
            new Object[] {"first", timestamp, guid},
            new FileGdbPoint(2600050, 1200050));
      }
    }

    // Read back with the library.
    try (FileGeodatabase database = FileGeodatabase.open(databasePath);
        FileGdbTable table = database.featureClass("roads")) {
      assertThat(table.rowCount()).isEqualTo(2);
      assertThat(table.crs().effectiveWkid()).isEqualTo(2056);
      FileGdbRow row = table.read(1);
      assertThat(row.get("name")).isEqualTo("A1");
      assertThat(row.get("lanes")).isEqualTo(2L);
      assertThat(row.get("length")).isEqualTo(12.5);
      FileGdbPolygon polygon = (FileGdbPolygon) row.geometry();
      assertThat(polygon.parts()).hasSize(1);
      assertThat(polygon.parts().get(0).points()).hasSize(5);
      assertThat(table.read(2).geometry()).isNull();
    }
    try (FileGeodatabase database = FileGeodatabase.open(databasePath);
        FileGdbTable table = database.featureClass("survey")) {
      assertThat(table.rowCount()).isEqualTo(1);
      FileGdbRow row = table.read(1);
      assertThat(row.get("label")).isEqualTo("first");
      assertThat(row.get("created")).isEqualTo(timestamp);
      assertThat(row.get("globalid")).isEqualTo(guid);
      assertThat(row.geometry()).isInstanceOf(FileGdbPoint.class);
    }

    // Read back with GDAL.
    Path ogrInfo = Ogr.ogrInfo();
    if (ogrInfo != null) {
      String output = Ogr.run(ogrInfo, "-al", "-geom=WKT", databasePath.toString());
      assertThat(output).contains("Layer name: roads");
      assertThat(output).contains("Layer name: survey");
      assertThat(output).contains("Feature Count: 2");
      assertThat(output).contains("A1");
      assertThat(output).contains("B2");
      assertThat(output).contains("POLYGON");
      assertThat(output).contains("POINT");
      assertThat(output).contains("2024/05/07 08:30:15");
    }

    Path ogr2Ogr = Ogr.ogr2Ogr();
    if (ogr2Ogr != null) {
      Path geoJson = directory.resolve("roads.geojson");
      Ogr.run(ogr2Ogr, "-f", "GeoJSON", geoJson.toString(), databasePath.toString(), "roads");
      assertThat(geoJson).exists();
      assertThat(new String(java.nio.file.Files.readAllBytes(geoJson)))
          .contains("\"name\":\"A1\"");
    }
  }

  private static FileGdbPolygon square(double x, double y, double size) {
    List<FileGdbPoint> ring =
        List.of(
            new FileGdbPoint(x, y),
            new FileGdbPoint(x + size, y),
            new FileGdbPoint(x + size, y + size),
            new FileGdbPoint(x, y + size),
            new FileGdbPoint(x, y));
    return new FileGdbPolygon(List.of(new FileGdbPart(ring)));
  }
}
