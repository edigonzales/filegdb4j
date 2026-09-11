package ch.so.agi.filegdb.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.so.agi.filegdb.geometry.FileGdbPart;
import ch.so.agi.filegdb.geometry.FileGdbPoint;
import ch.so.agi.filegdb.geometry.FileGdbPolygon;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import ch.so.agi.filegdb.geometry.GeometryKind;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.table.FileGdbGeomField;
import ch.so.agi.filegdb.write.TableFileWriter;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks that GDAL can read a table created by {@link TableFileWriter}. */
class TableWriterGdalTest {

  @Test
  void gdalReadsWrittenTable(@TempDir Path directory) throws Exception {
    Path ogrInfo = Ogr.ogrInfo();
    assumeTrue(ogrInfo != null, "ogrinfo is not available");

    Path table = directory.resolve("a00000009.gdbtable");
    try (TableFileWriter writer =
        TableFileWriter.create(table, GeometryKind.POLYGON, false, false)) {
      writer.addField(FileGdbField.string("name", 64).asNullable());
      writer.addField(FileGdbField.integer("value"));
      writer.addField(FileGdbField.real("area").asNullable());
      writer.addGeometryField(
          new FileGdbGeomField(
              "shape", "", true, "", GeometryFieldDefinition.of("shape", GeometryKind.POLYGON)));
      writer.writeRow(new Object[] {"first", 42L, 1.5}, square(100, 200, 10));
      writer.writeRow(new Object[] {"second", 7L, null}, null);
    }

    String output = Ogr.run(ogrInfo, "-al", "-geom=WKT", table.toString());
    assertThat(output).contains("name (String)");
    assertThat(output).contains("first");
    assertThat(output).contains("42");
    assertThat(output).contains("second");
    assertThat(output).contains("POLYGON");
    assertThat(output).doesNotContain("Geometry: None");
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
