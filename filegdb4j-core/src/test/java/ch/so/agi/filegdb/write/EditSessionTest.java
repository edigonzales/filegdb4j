package ch.so.agi.filegdb.write;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.filegdb.*;
import ch.so.agi.filegdb.geometry.*;
import ch.so.agi.filegdb.table.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class EditSessionTest {
  @TempDir Path directory;

  private Path create() throws Exception {
    Path path = directory.resolve("test.gdb");
    try (var db = FileGeodatabase.create(path)) {
      try (var w =
          db.createTable(
              TableDefinition.builder("names").field(FileGdbField.string("name", 100)).build())) {
        w.write(new Object[] {"original"});
      }
      try (var w =
          db.createFeatureClass(
              FeatureClassDefinition.builder("points")
                  .field(FileGdbField.string("name", 100))
                  .geometry(GeometryFieldDefinition.of("Shape", GeometryKind.POINT))
                  .build())) {
        w.write(new Object[] {"first"}, new FileGdbPoint(10, 20));
      }
    }
    return path;
  }

  @Test
  void appendAndAddTogether() throws Exception {
    Path path = create();
    try (var edit = FileGeodatabase.edit(path)) {
      try (var w = edit.database().appendRows("names")) {
        assertThat(w.write(new Object[] {"second"})).isEqualTo(2);
      }
      try (var w = edit.database().appendFeatures("points", true)) {
        assertThat(w.write(new Object[] {"second"}, new FileGdbPoint(50, 60))).isEqualTo(2);
      }
      try (var w =
          edit.database()
              .createTable(
                  TableDefinition.builder("extra").field(FileGdbField.integer("id")).build())) {
        w.write(new Object[] {4});
      }
      edit.commit();
    }
    try (var db = FileGeodatabase.open(path);
        var t = db.table("names");
        var g = db.featureClass("points")) {
      assertThat(t.read(1).get("name")).isEqualTo("original");
      assertThat(t.read(2).get("name")).isEqualTo("second");
      assertThat(db.dataset("extra")).isPresent();
      assertThat(g.query(new Envelope(49, 59, 51, 61)).rows()).hasSize(1);
    }
  }

  @Test
  void closeWithoutCommitKeepsOriginal() throws Exception {
    Path path = create();
    byte[] before = Files.readAllBytes(path.resolve("a00000009.gdbtable"));
    try (var edit = FileGeodatabase.edit(path)) {
      try (var w = edit.database().appendRows("names")) {
        w.write(new Object[] {"discard"});
      }
    }
    assertThat(Files.readAllBytes(path.resolve("a00000009.gdbtable"))).isEqualTo(before);
    try (var db = FileGeodatabase.open(path);
        var t = db.table("names")) {
      assertThat(t.read(1).get("name")).isEqualTo("original");
    }
  }

  @Test
  void rejectsChangedOriginalAndForeignLocks() throws Exception {
    Path path = create();
    try (var edit = FileGeodatabase.edit(path)) {
      Files.writeString(path.resolve("external"), "changed");
      assertThatThrownBy(edit::commit).hasMessageContaining("changed during editing");
    }
    Files.writeString(path.resolve("external.lock"), "lock");
    assertThatThrownBy(() -> FileGeodatabase.edit(path)).hasMessageContaining("lock exists");
  }

  @Test
  void appendsIndependentGdalFileWithDeletedIdsAndDefaults() throws Exception {
    var python = ch.so.agi.filegdb.test.Ogr.findExecutable("python3");
    Assumptions.assumeTrue(ch.so.agi.filegdb.test.Ogr.ogrInfo() != null && python != null);
    Path path = directory.resolve("gdal.gdb");
    ch.so.agi.filegdb.test.Ogr.run(
        python,
        "-c",
        """
        import sys
        from pathlib import Path
        from osgeo import ogr, osr, gdal
        gdal.UseExceptions()
        osr.SetPROJSearchPaths([str(Path(sys.prefix)/'share/proj')])
        gdal.SetConfigOption('GDAL_DATA', str(Path(sys.prefix)/'share/gdal'))
        db=ogr.GetDriverByName('OpenFileGDB').CreateDataSource(sys.argv[1])
        srs=osr.SpatialReference(); srs.ImportFromEPSG(2056)
        layer=db.CreateLayer('points',srs,ogr.wkbPoint)
        name=ogr.FieldDefn('name',ogr.OFTString); name.SetWidth(50); name.SetDefault("'unknown'")
        layer.CreateField(name)
        for i in range(3):
            f=ogr.Feature(layer.GetLayerDefn()); f.SetField('name','old'+str(i))
            f.SetGeometry(ogr.CreateGeometryFromWkt('POINT(10 20)')); layer.CreateFeature(f)
        layer.DeleteFeature(2)
        layer.DeleteFeature(3)
        layer=db=None
        """,
        path.toString());
    try (var db = FileGeodatabase.open(path);
        var table = db.table("points")) {
      assertThat(
              table.fields().stream()
                  .filter(f -> f.name().equals("name"))
                  .findFirst()
                  .orElseThrow()
                  .defaultValue())
          .isEqualTo("unknown");
    }
    try (var edit = FileGeodatabase.edit(path)) {
      try (var writer = edit.database().appendFeatures("points", true)) {
        assertThat(writer.write(new Object[] {"new"}, new FileGdbPoint(50, 60))).isEqualTo(4);
      }
      edit.commit();
    }
    String result =
        ch.so.agi.filegdb.test.Ogr.run(
            ch.so.agi.filegdb.test.Ogr.ogrInfo(), "-al", path.toString());
    assertThat(result).contains("Feature Count: 2", "OGRFeature(points):4");
    assertThat(
            ch.so.agi.filegdb.test.Ogr.run(
                ch.so.agi.filegdb.test.Ogr.ogrInfo(),
                "-al",
                "-spat",
                "49",
                "59",
                "51",
                "61",
                path.toString()))
        .contains("Feature Count: 1", "new");
  }

  @Test
  void preservesDefaultsAndNoopBytes() throws Exception {
    Path path = directory.resolve("defaults.gdb");
    try (var db = FileGeodatabase.create(path);
        var w =
            db.createTable(
                TableDefinition.builder("values")
                    .field(FileGdbField.integer("number").withDefaultValue(42L))
                    .field(FileGdbField.string("text", 50).withDefaultValue("ümlaut"))
                    .build())) {
      w.write(new Object[] {1L, "initial"});
    }
    byte[] original = Files.readAllBytes(path.resolve("a00000009.gdbtable"));
    try (var edit = FileGeodatabase.edit(path)) {
      try (var w = edit.database().appendRows("values")) {}
      edit.commit();
    }
    assertThat(Files.readAllBytes(path.resolve("a00000009.gdbtable"))).isEqualTo(original);
    try (var db = FileGeodatabase.open(path);
        var table = db.table("values")) {
      assertThat(
              table.fields().stream()
                  .filter(f -> f.name().equals("number"))
                  .findFirst()
                  .orElseThrow()
                  .defaultValue())
          .isEqualTo(42L);
      assertThat(
              table.fields().stream()
                  .filter(f -> f.name().equals("text"))
                  .findFirst()
                  .orElseThrow()
                  .defaultValue())
          .isEqualTo("ümlaut");
    }
  }

  @Test
  void appendsSparseIndexesWithAllOffsetWidths() throws Exception {
    for (int width : new int[] {4, 5, 6}) {
      Path path = directory.resolve("sparse" + width + ".gdbtable");
      try (var writer = TableFileWriter.create(path, GeometryKind.NONE, false, false)) {
        writer.addField(FileGdbField.string("name", 50));
        writer.writeFieldDescriptors();
        writer.writeRow(new Object[] {"first"}, null);
        writer.writeRow(new Object[] {"last"}, null);
      }
      Path index = path.resolveSibling("sparse" + width + ".gdbtablx");
      var old =
          java.nio.ByteBuffer.wrap(Files.readAllBytes(index))
              .order(java.nio.ByteOrder.LITTLE_ENDIAN);
      long first = Integer.toUnsignedLong(old.getInt(16)),
          last = Integer.toUnsignedLong(old.getInt(20));
      var sparse =
          java.nio.ByteBuffer.allocate(16 + 2 * width * 1024 + 144)
              .order(java.nio.ByteOrder.LITTLE_ENDIAN);
      sparse.putInt(3).putInt(2).putInt(2049).putInt(width);
      for (int i = 0; i < width; i++) {
        sparse.put(16 + i, (byte) (first >>> (8 * i)));
        sparse.put(16 + width * 1024 + i, (byte) (last >>> (8 * i)));
      }
      sparse.position(16 + 2 * width * 1024);
      sparse.putInt(32).putInt(3).putInt(2).putInt(1).put((byte) 5);
      Files.write(index, sparse.array());
      try (var writer = TableFileWriter.append(path, false, java.util.Map.of())) {
        assertThat(writer.writeRow(new Object[] {"added"}, null)).isEqualTo(2050);
      }
      try (var table = FileGdbTableFile.open(path)) {
        assertThat(table.readRow(0)[1]).isEqualTo("first");
        assertThat(table.readRow(1024)).isNull();
        assertThat(table.readRow(2048)[1]).isEqualTo("last");
        assertThat(table.readRow(2049)[1]).isEqualTo("added");
        assertThat(table.writeLayout().offsetWidth()).isEqualTo(width);
      }
    }
  }

  @Test
  void recoversInterruptedDirectoryReplacement() throws Exception {
    Path target = create();
    Path backup = directory.resolve(".test.gdb.filegdb4j-backup.gdb");
    Path journal = directory.resolve(".test.gdb.filegdb4j-journal");
    // Process died after publishing the candidate but before recording COMMITTED.
    Files.move(target, backup);
    Files.createDirectory(target);
    Files.writeString(target.resolve("partial"), "candidate");
    Files.writeString(journal, "PREPARED_EXISTING");
    try (var edit = FileGeodatabase.edit(target)) {
      assertThat(edit.database().dataset("names")).isPresent();
    }
    assertThat(Files.exists(target.resolve("partial"))).isFalse();
    assertThat(Files.exists(backup)).isFalse();
    // Process died after the durable commit and before deleting the old backup.
    Files.createDirectory(backup);
    Files.writeString(backup.resolve("old"), "backup");
    Files.writeString(journal, "COMMITTED");
    try (var edit = FileGeodatabase.edit(target)) {
      assertThat(edit.database().dataset("points")).isPresent();
    }
    assertThat(Files.exists(backup)).isFalse();
  }

  @Test
  void cancellationClosesResourcesAndAllowsRetry() throws Exception {
    Path target = create();
    var stopped = new java.util.concurrent.atomic.AtomicBoolean();
    try (var edit =
        FileGdbEditSession.open(
            target,
            false,
            () -> {
              if (stopped.get()) throw new java.util.concurrent.CancellationException("cancelled");
            })) {
      try (var writer = edit.database().appendFeatures("points", true)) {
        writer.write(new Object[] {"cancel"}, new FileGdbPoint(30, 40));
      }
      stopped.set(true);
      assertThatThrownBy(edit::commit)
          .isInstanceOf(java.util.concurrent.CancellationException.class);
    } catch (java.io.IOException e) {
      assertThat(e).hasMessageContaining("close");
    }
    try (var edit = FileGeodatabase.edit(target)) {
      edit.commit();
    }
    try (var db = FileGeodatabase.open(target);
        var table = db.table("points")) {
      assertThat(table.rowCount()).isEqualTo(1);
    }
  }

  @Test
  void reusesDomainsAndRelationshipsAndRejectsConflicts() throws Exception {
    Path target = create();
    var domain =
        new ch.so.agi.filegdb.catalog.CodedValueDomain(
            "status",
            FileGdbFieldType.INT32,
            "Status",
            java.util.List.of(new ch.so.agi.filegdb.catalog.CodedValue("Active", "1")));
    var relation =
        RelationshipDefinition.builder("children")
            .originClass("names")
            .destinationClass("child")
            .originPrimaryKey("name")
            .originForeignKey("parent_name")
            .build();
    try (var edit = FileGeodatabase.edit(target)) {
      edit.database().createDomain(domain);
      try (var writer =
          edit.database()
              .createTable(
                  TableDefinition.builder("child")
                      .field(FileGdbField.string("parent_name", 100))
                      .field(FileGdbField.integer("status").withDomain("status"))
                      .build())) {
        writer.write(new Object[] {"original", 1});
      }
      edit.database().createRelationship(relation);
      edit.commit();
    }
    try (var edit = FileGeodatabase.edit(target)) {
      edit.database().createDomain(domain);
      edit.database().createRelationship(relation);
      edit.commit();
    }
    try (var db = FileGeodatabase.open(target)) {
      assertThat(db.domains()).hasSize(1);
      assertThat(db.relationships()).hasSize(1);
    }
    try (var edit = FileGeodatabase.edit(target)) {
      assertThatThrownBy(
              () ->
                  edit.database()
                      .createDomain(
                          new ch.so.agi.filegdb.catalog.CodedValueDomain(
                              "status", FileGdbFieldType.INT32, "Changed", domain.values())))
          .hasMessageContaining("Conflicting");
    }
  }

  @Test
  void rejectsDamagedSpatialIndexAndAttributeIndexes() throws Exception {
    Path target = create();
    Path table;
    try (var db = FileGeodatabase.open(target)) {
      table = db.dataset("points").orElseThrow().tableFile();
    }
    Files.write(ch.so.agi.filegdb.index.SpatialIndex.path(table), new byte[] {1});
    try (var edit = FileGeodatabase.edit(target)) {
      assertThatThrownBy(() -> edit.database().appendFeatures("points", false))
          .hasMessageContaining("Truncated spatial index");
    }
    Files.delete(ch.so.agi.filegdb.index.SpatialIndex.path(table));
    Files.writeString(ch.so.agi.filegdb.io.GdbPaths.companion(table, "name.atx"), "index");
    try (var edit = FileGeodatabase.edit(target)) {
      assertThatThrownBy(() -> edit.database().appendFeatures("points", false))
          .hasMessageContaining("Attribute indexes");
    }
  }

  /** Child process deliberately exits without closing its edit session. */
  public static void main(String[] args) throws Exception {
    Path target = Path.of(args[0]);
    FileGdbEditSession edit = FileGeodatabase.edit(target);
    try (var writer = edit.database().appendRows("names")) {
      writer.write(new Object[] {"candidate"});
    }
    edit.database().close();
    String prefix = "." + target.getFileName() + ".filegdb4j-";
    Path work = target.resolveSibling(prefix + "work.gdb");
    Path backup = target.resolveSibling(prefix + "backup.gdb");
    Path journal = target.resolveSibling(prefix + "journal");
    Files.writeString(journal, "PREPARED_EXISTING");
    Files.move(target, backup);
    Files.move(work, target);
    if (args[1].equals("COMMITTED")) Files.writeString(journal, "COMMITTED");
    Runtime.getRuntime().halt(0);
  }

  @Test
  void recoversAfterAbruptJvmTermination() throws Exception {
    Path target = create();
    for (String phase : new String[] {"PREPARED_EXISTING", "COMMITTED"}) {
      String javaCommand = Path.of(System.getProperty("java.home"), "bin", "java").toString();
      Process process =
          new ProcessBuilder(
                  javaCommand,
                  "-cp",
                  System.getProperty("java.class.path"),
                  EditSessionTest.class.getName(),
                  target.toString(),
                  phase)
              .redirectErrorStream(true)
              .start();
      String output =
          new String(
              process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      assertThat(process.waitFor()).as(output).isZero();
      try (var edit = FileGeodatabase.edit(target)) {
        try (var table = edit.database().table("names")) {
          assertThat(table.rowCount()).isEqualTo(phase.equals("COMMITTED") ? 2 : 1);
        }
      }
    }
  }
}
