package ch.so.agi.filegdb.write;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.filegdb.FileGdbEditSession;
import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.CrsDefinition;
import ch.so.agi.filegdb.geometry.FileGdbPoint;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import ch.so.agi.filegdb.geometry.GeometryKind;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.table.FileGdbRow;
import ch.so.agi.filegdb.table.FileGdbTable;
import ch.so.agi.filegdb.test.Ogr;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Update and delete of existing rows, verified with the reader and with GDAL. */
class GdbRowMutationTest {

  @Test
  void updatesAndDeletesRowsAndFeatures(@TempDir Path directory) throws Exception {
    Path gdb = directory.resolve("mutation.gdb");

    try (FileGeodatabase database = FileGeodatabase.create(gdb)) {
      try (GdbTableWriter writer =
          database.createTable(
              TableDefinition.builder("points_table")
                  .field(FileGdbField.string("name", 64).asNullable())
                  .field(FileGdbField.integer("value").asNullable())
                  .build())) {
        writer.write(new Object[] {"one", 1L});
        writer.write(new Object[] {"two", 2L});
        writer.write(new Object[] {"three", 3L});
      }
      try (GdbFeatureWriter writer =
          database.createFeatureClass(
              FeatureClassDefinition.builder("points_fc")
                  .field(FileGdbField.string("name", 64).asNullable())
                  .field(FileGdbField.integer("value").asNullable())
                  .geometry(GeometryFieldDefinition.of("shape", GeometryKind.POINT))
                  .crs(new CrsDefinition(2056, 2056, ""))
                  .build())) {
        writer.write(new Object[] {"one", 1L}, new FileGdbPoint(2600000, 1200000));
        writer.write(new Object[] {"two", 2L}, new FileGdbPoint(2600010, 1200010));
      }
    }

    try (FileGdbEditSession session = FileGdbEditSession.open(gdb, false, () -> {})) {
      try (GdbTableWriter writer = session.database().appendRows("points_table")) {
        writer.updateRow(2, new Object[] {"two-updated", 22L});
        writer.deleteRow(3);
        writer.deleteRow(3); // deleting twice is a no-op
      }
      try (GdbFeatureWriter writer = session.database().appendFeatures("points_fc", true)) {
        writer.updateRow(2, new Object[] {"two-updated", 22L}, new FileGdbPoint(2600020, 1200020));
      }
      session.commit();
    }

    try (FileGeodatabase database = FileGeodatabase.open(gdb)) {
      FileGdbTable table = database.table("points_table");
      assertThat(table.rowCount()).isEqualTo(2);
      assertThat(table.read(1).get("name")).isEqualTo("one");
      assertThat(table.read(2).get("name")).isEqualTo("two-updated");
      assertThat(table.read(2).get("value")).isEqualTo(22L);
      assertThat(table.read(3)).isNull();

      FileGdbTable features = database.featureClass("points_fc");
      assertThat(features.rowCount()).isEqualTo(2);
      FileGdbRow updated = features.read(2);
      assertThat(updated.get("name")).isEqualTo("two-updated");
      assertThat(updated.get("value")).isEqualTo(22L);
      assertThat(updated.geometry()).isEqualTo(new FileGdbPoint(2600020, 1200020));
    }

    Path ogrinfo = Ogr.findExecutable("ogrinfo");
    if (ogrinfo != null) {
      Map<String, Long> counts = Ogr.featureCounts(gdb);
      assertThat(counts).containsEntry("points_table", 2L);
      assertThat(counts).containsEntry("points_fc", 2L);
    }
  }
}
