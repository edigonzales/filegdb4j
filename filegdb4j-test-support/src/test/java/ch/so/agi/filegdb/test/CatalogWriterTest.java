package ch.so.agi.filegdb.test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.so.agi.filegdb.*;
import ch.so.agi.filegdb.catalog.*;
import ch.so.agi.filegdb.table.*;
import ch.so.agi.filegdb.write.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogWriterTest {
  @TempDir Path temp;

  private Path create() throws Exception {
    var path = temp.resolve("catalog.gdb");
    try (var db = FileGeodatabase.create(path)) {
      db.createDomain(
          new CodedValueDomain(
              "status",
              FileGdbFieldType.INT32,
              "A & B <status>",
              List.of(new CodedValue("One & only", "1")),
              DomainSplitPolicy.DUPLICATE,
              DomainMergePolicy.DEFAULT_VALUE));
      db.createDomain(
          new RangeDomain(
              "height",
              FileGdbFieldType.FLOAT64,
              "Height",
              "0",
              "100",
              DomainSplitPolicy.GEOMETRY_RATIO,
              DomainMergePolicy.SUM_VALUES));
      try (var table =
          db.createTable(
              TableDefinition.builder("buildings")
                  .field(FileGdbField.integer("id"))
                  .field(FileGdbField.integer("status").withDomain("status"))
                  .field(FileGdbField.real("height").withDomain("height"))
                  .build())) {
        table.write(new Object[] {1, 1, 12.5});
      }
      try (var table =
          db.createTable(
              TableDefinition.builder("entrances")
                  .field(FileGdbField.integer("building_id"))
                  .build())) {
        table.write(new Object[] {1});
      }
      for (var cardinality :
          List.of(RelationshipCardinality.ONE_TO_ONE, RelationshipCardinality.ONE_TO_MANY))
        db.createRelationship(
            RelationshipDefinition.builder("rel_" + cardinality.name())
                .originClass("buildings")
                .destinationClass("entrances")
                .originPrimaryKey("id")
                .originForeignKey("building_id")
                .cardinality(cardinality)
                .labels("A & B", "Back")
                .build());
    }
    return path;
  }

  @Test
  void roundTripPoliciesAndCatalogLinks() throws Exception {
    try (var db = FileGeodatabase.open(create())) {
      assertThat(db.domain("status").orElseThrow().splitPolicy())
          .isEqualTo(DomainSplitPolicy.DUPLICATE);
      assertThat(db.domain("height").orElseThrow().mergePolicy())
          .isEqualTo(DomainMergePolicy.SUM_VALUES);
      assertThat(db.relationships()).hasSize(2);
      try (var table = db.table("buildings")) {
        assertThat(table.domain("status")).isPresent();
      }
      try (var links =
          FileGdbTableFile.open(db.catalog().tableFile("GDB_ItemRelationships").orElseThrow())) {
        var items = new HashMap<String, UUID>();
        for (var i : db.items()) items.put(i.name(), i.uuid());
        boolean found = false;
        for (long i = 0; i < links.totalRecordCount(); i++) {
          var row = links.readRow(i);
          if (row != null
              && Arrays.asList(row).contains(items.get("status"))
              && Arrays.asList(row).contains(items.get("buildings"))) found = true;
        }
        assertThat(found).isTrue();
      }
    }
  }

  @Test
  void rejectsInvalidDefinitionsBeforeWriting() throws Exception {
    try (var db = FileGeodatabase.create(temp.resolve("invalid.gdb"))) {
      assertThatThrownBy(
              () -> db.createDomain(new RangeDomain("bad", FileGdbFieldType.INT32, "", "5", "1")))
          .isInstanceOf(IllegalArgumentException.class);
      db.createDomain(
          new CodedValueDomain(
              "status", FileGdbFieldType.INT32, "", List.of(new CodedValue("one", "1"))));
      assertThatThrownBy(
              () ->
                  db.createDomain(
                      new CodedValueDomain("STATUS", FileGdbFieldType.INT32, "", List.of())))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(
              () ->
                  db.createTable(
                      TableDefinition.builder("bad")
                          .field(FileGdbField.string("value", 20).withDomain("status"))
                          .build()))
          .isInstanceOf(IllegalArgumentException.class);
      try (var a =
          db.createTable(TableDefinition.builder("a").field(FileGdbField.integer("id")).build())) {}
      try (var b =
          db.createTable(
              TableDefinition.builder("b").field(FileGdbField.string("fk", 20)).build())) {}
      assertThatThrownBy(
              () ->
                  db.createRelationship(
                      RelationshipDefinition.builder("r")
                          .originClass("a")
                          .destinationClass("b")
                          .originPrimaryKey("missing")
                          .originForeignKey("fk")
                          .build()))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(
              () ->
                  db.createRelationship(
                      RelationshipDefinition.builder("r")
                          .originClass("a")
                          .destinationClass("b")
                          .originPrimaryKey("id")
                          .originForeignKey("fk")
                          .build()))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void readsIndependentlyCreatedGdalPolicies() throws Exception {
    assumeTrue(Ogr.ogrInfo() != null, "GDAL required");
    String prefix = System.getenv("GDAL_PREFIX");
    assumeTrue(
        prefix != null && !prefix.isBlank(), "Set GDAL_PREFIX for Python GDAL fixture generation");
    var python = Path.of(prefix, "bin", "python3");
    assumeTrue(Files.isExecutable(python), "Python GDAL required");
    var path = temp.resolve("reference.gdb");
    Ogr.run(
        python,
        "-c",
        """
        from osgeo import gdal, ogr
        import sys
        gdal.UseExceptions()
        d=gdal.GetDriverByName('OpenFileGDB').Create(sys.argv[1],0,0,0,gdal.GDT_Unknown)
        domain=ogr.CreateCodedFieldDomain('status','A & B',ogr.OFTInteger,ogr.OFSTNone,{1:'one',2:'two'})
        domain.SetSplitPolicy(ogr.OFDSP_DUPLICATE)
        assert d.AddFieldDomain(domain)
        for name in ['origin','destination']:
            layer=d.CreateLayer(name,geom_type=ogr.wkbNone)
            field=ogr.FieldDefn('id',ogr.OFTInteger)
            field.SetDomainName('status')
            layer.CreateField(field)
        d.FlushCache()  # GDAL 3.8 registers newly created tables on flush
        r=gdal.Relationship('relation','origin','destination',gdal.GRC_ONE_TO_MANY)
        r.SetLeftTableFields(['id'])
        r.SetRightTableFields(['id'])
        assert d.AddRelationship(r)
        d=None
        """,
        path.toString());
    try (var db = FileGeodatabase.open(path)) {
      assertThat(db.domain("status").orElseThrow().splitPolicy())
          .isEqualTo(DomainSplitPolicy.DUPLICATE);
      assertThat(db.relationships()).hasSize(1);
      assertThat(db.relationships().getFirst().originKeys())
          .extracting(RelationshipKey::objectKeyName)
          .contains("id");
      try (var table = db.table("origin")) {
        assertThat(table.domain("id")).isPresent();
      }
    }
  }

  @Test
  void gdalRecognizesDomainsAndRelationships() throws Exception {
    var ogr = Ogr.ogrInfo();
    assumeTrue(ogr != null, "GDAL required for independent catalog verification");
    var path = create();
    String info = Ogr.run(ogr, "-json", "-al", "-so", path.toString());
    assertThat(info)
        .contains("rel_ONE_TO_ONE", "rel_ONE_TO_MANY", "building_id", "status", "height");
    assertThat(Ogr.run(ogr, "-fielddomain", "status", path.toString()))
        .contains("One & only", "duplicate");
  }
}
