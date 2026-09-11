package ch.so.agi.filegdb.write;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.filegdb.geometry.FileGdbPart;
import ch.so.agi.filegdb.geometry.FileGdbPoint;
import ch.so.agi.filegdb.geometry.FileGdbPolygon;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import ch.so.agi.filegdb.geometry.GeometryKind;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.table.FileGdbGeomField;
import ch.so.agi.filegdb.table.FileGdbTableFile;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TableFileWriterTest {

  @Test
  void writesTableReadableByOurReader(@TempDir Path directory) throws Exception {
    Path table = directory.resolve("a00000009.gdbtable");
    GeometryFieldDefinition geometryDefinition =
        GeometryFieldDefinition.of("shape", GeometryKind.POLYGON);

    try (TableFileWriter writer =
        TableFileWriter.create(table, GeometryKind.POLYGON, false, false)) {
      writer.addField(FileGdbField.string("name", 64).asNullable());
      writer.addField(FileGdbField.integer("value"));
      writer.addField(FileGdbField.real("area").asNullable());
      writer.addGeometryField(new FileGdbGeomField("shape", "", true, "", geometryDefinition));
      writer.writeRow(new Object[] {"first", 42L, 1.5}, square(0, 0, 10));
      writer.writeRow(new Object[] {"second", 7L, null}, null);
    }

    try (FileGdbTableFile reader = FileGdbTableFile.open(table)) {
      assertThat(reader.validRecordCount()).isEqualTo(2);
      assertThat(reader.totalRecordCount()).isEqualTo(2);
      assertThat(reader.geomField()).isNotNull();

      Object[] row1 = reader.readRow(0);
      assertThat(row1[1]).isEqualTo("first");
      assertThat(row1[2]).isEqualTo(42L);
      assertThat(row1[3]).isEqualTo(1.5);
      FileGdbPolygon polygon = (FileGdbPolygon) row1[4];
      assertThat(polygon.parts()).hasSize(1);
      assertThat(polygon.parts().get(0).points()).hasSize(5);
      assertThat(polygon.parts().get(0).points().get(1).x()).isCloseTo(10, within(1e-6));

      Object[] row2 = reader.readRow(1);
      assertThat(row2[1]).isEqualTo("second");
      assertThat(row2[2]).isEqualTo(7L);
      assertThat(row2[3]).isNull();
      assertThat(row2[4]).isNull();

      var extent = reader.geomField().geometry().extent();
      assertThat(extent.xMin()).isCloseTo(0, within(1e-6));
      assertThat(extent.yMin()).isCloseTo(0, within(1e-6));
      assertThat(extent.xMax()).isCloseTo(10, within(1e-6));
      assertThat(extent.yMax()).isCloseTo(10, within(1e-6));
    }
  }

  private static final org.assertj.core.data.Offset<Double> within(double value) {
    return org.assertj.core.data.Offset.offset(value);
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
