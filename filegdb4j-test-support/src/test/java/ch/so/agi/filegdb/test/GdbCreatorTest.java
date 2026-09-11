package ch.so.agi.filegdb.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.CodedValue;
import ch.so.agi.filegdb.catalog.CodedValueDomain;
import ch.so.agi.filegdb.catalog.CrsDefinition;
import ch.so.agi.filegdb.catalog.RangeDomain;
import ch.so.agi.filegdb.catalog.RelationshipCardinality;
import ch.so.agi.filegdb.geometry.FileGdbPart;
import ch.so.agi.filegdb.geometry.FileGdbPoint;
import ch.so.agi.filegdb.geometry.FileGdbPolygon;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import ch.so.agi.filegdb.geometry.GeometryKind;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.table.FileGdbRow;
import ch.so.agi.filegdb.table.FileGdbTable;
import ch.so.agi.filegdb.write.FeatureClassDefinition;
import ch.so.agi.filegdb.write.GdbFeatureWriter;
import ch.so.agi.filegdb.write.RelationshipDefinition;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End to end creation of a file geodatabase with feature classes, domains and relationships. */
class GdbCreatorTest {

  @Test
  void createsFeatureClassesDomainsAndRelationship(@TempDir Path directory) throws Exception {
    Path databasePath = directory.resolve("sample.gdb");
    LocalDateTime timestamp = LocalDateTime.of(2024, 5, 7, 8, 30, 15);
    UUID guid = UUID.fromString("9f8e7d6c-5b4a-4938-8271-0a1b2c3d4e5f");

    try (FileGeodatabase database = FileGeodatabase.create(databasePath)) {
      database.createDomain(
          new CodedValueDomain(
              "road_type",
              ch.so.agi.filegdb.table.FileGdbFieldType.STRING,
              "Road types",
              List.of(new CodedValue("Main road", "main"), new CodedValue("Minor road", "minor"))));
      database.createDomain(
          new RangeDomain(
              "lane_count", ch.so.agi.filegdb.table.FileGdbFieldType.INT32, "Lane count", "1",
              "8"));

      FeatureClassDefinition roads =
          FeatureClassDefinition.builder("roads")
              .field(FileGdbField.string("name", 255).asRequired())
              .field(FileGdbField.string("type", 40).asNullable().withDomain("road_type"))
              .field(FileGdbField.integer("lanes").asNullable().withDomain("lane_count"))
              .field(FileGdbField.real("length").asNullable())
              .geometry(GeometryFieldDefinition.of("shape", GeometryKind.POLYGON))
              .crs(new CrsDefinition(2056, 2056, ""))
              .build();
      try (GdbFeatureWriter writer = database.createFeatureClass(roads)) {
        writer.write(new Object[] {"A1", "main", 2L, 12.5}, square(2600000, 1200000, 100));
        writer.write(new Object[] {"B2", null, null, null}, null);
      }

      FeatureClassDefinition survey =
          FeatureClassDefinition.builder("survey")
              .field(FileGdbField.string("label", 64))
              .field(FileGdbField.integer("road_id").asNullable())
              .field(FileGdbField.dateTime("created"))
              .field(FileGdbField.guid("globalid"))
              .geometry(GeometryFieldDefinition.of("shape", GeometryKind.POINT))
              .crs(new CrsDefinition(2056, 2056, ""))
              .build();
      try (GdbFeatureWriter writer = database.createFeatureClass(survey)) {
        writer.write(
            new Object[] {"first", 1L, timestamp, guid}, new FileGdbPoint(2600050, 1200050));
      }

      database.createRelationship(
          RelationshipDefinition.builder("roads_survey")
              .originClass("roads")
              .destinationClass("survey")
              .cardinality(RelationshipCardinality.ONE_TO_MANY)
              .originPrimaryKey("OBJECTID")
              .originForeignKey("road_id")
              .labels("surveys", "road")
              .build());
      database.createRelationship(
          RelationshipDefinition.builder("roads_survey_m2m")
              .originClass("roads")
              .destinationClass("survey")
              .cardinality(RelationshipCardinality.MANY_TO_MANY)
              .originPrimaryKey("OBJECTID")
              .originForeignKey("origin_fk")
              .destinationPrimaryKey("OBJECTID")
              .destinationForeignKey("destination_fk")
              .build());

      try (var table =
          database.createTable(
              ch.so.agi.filegdb.write.TableDefinition.builder("documents")
                  .field(FileGdbField.string("title", 255))
                  .field(FileGdbField.dateTime("published").asNullable())
                  .build())) {
        table.write(new Object[] {"Zonenreglement", LocalDateTime.of(2003, 7, 1, 0, 0)});
        table.write(new Object[] {"Baureglement", null});
      }
    }

    // Read back with the library.
    try (FileGeodatabase database = FileGeodatabase.open(databasePath)) {
      assertThat(database.domains())
          .extracting(ch.so.agi.filegdb.catalog.Domain::name)
          .containsExactlyInAnyOrder("road_type", "lane_count");
      assertThat(database.relationships())
          .extracting(ch.so.agi.filegdb.catalog.RelationshipClass::name)
          .contains("roads_survey", "roads_survey_m2m");
      var manyToMany =
          database.relationships().stream()
              .filter(r -> r.name().equals("roads_survey_m2m"))
              .findFirst()
              .orElseThrow();
      assertThat(manyToMany.cardinality()).isEqualTo(RelationshipCardinality.MANY_TO_MANY);
      assertThat(manyToMany.destinationKeys())
          .extracting(ch.so.agi.filegdb.catalog.RelationshipKey::objectKeyName)
          .containsExactly("OBJECTID", "destination_fk");
      var relationship =
          database.relationships().stream()
              .filter(r -> r.name().equals("roads_survey"))
              .findFirst()
              .orElseThrow();
      assertThat(relationship.cardinality()).isEqualTo(RelationshipCardinality.ONE_TO_MANY);
      assertThat(relationship.originClassName()).isEqualTo("roads");
      assertThat(relationship.destinationClassName()).isEqualTo("survey");
      assertThat(relationship.forwardLabel()).isEqualTo("surveys");

      try (FileGdbTable table = database.featureClass("roads")) {
        assertThat(table.rowCount()).isEqualTo(2);
        assertThat(table.crs().effectiveWkid()).isEqualTo(2056);
        assertThat(table.field("lanes").orElseThrow().domain()).isEqualTo("lane_count");
        assertThat(table.domain("lanes")).containsInstanceOf(RangeDomain.class);
        assertThat(table.domain("type")).containsInstanceOf(CodedValueDomain.class);

        FileGdbRow row = table.read(1);
        assertThat(row.get("name")).isEqualTo("A1");
        assertThat(row.get("type")).isEqualTo("main");
        assertThat(row.get("lanes")).isEqualTo(2L);
        assertThat(row.get("length")).isEqualTo(12.5);
        FileGdbPolygon polygon = (FileGdbPolygon) row.geometry();
        assertThat(polygon.parts()).hasSize(1);
        assertThat(polygon.parts().get(0).points()).hasSize(5);
        assertThat(table.read(2).geometry()).isNull();
      }

      try (FileGdbTable table = database.featureClass("survey")) {
        assertThat(table.rowCount()).isEqualTo(1);
        FileGdbRow row = table.read(1);
        assertThat(row.get("label")).isEqualTo("first");
        assertThat(row.get("created")).isEqualTo(timestamp);
        assertThat(row.get("globalid")).isEqualTo(guid);
        assertThat(row.geometry()).isInstanceOf(FileGdbPoint.class);
      }

      try (FileGdbTable table = database.table("documents")) {
        assertThat(table.isFeatureClass()).isFalse();
        assertThat(table.rowCount()).isEqualTo(2);
        assertThat(table.read(1).get("title")).isEqualTo("Zonenreglement");
        assertThat(table.read(1).get("published"))
            .isEqualTo(LocalDateTime.of(2003, 7, 1, 0, 0));
        assertThat(table.read(2).get("published")).isNull();
      }
    }

    // Read back with GDAL.
    Path ogrInfo = Ogr.ogrInfo();
    if (ogrInfo != null) {
      String output = Ogr.run(ogrInfo, "-al", "-so", databasePath.toString());
      assertThat(output).contains("Layer name: roads");
      assertThat(output).contains("Layer name: survey");
      assertThat(output).contains("Layer name: documents");
      assertThat(output).contains("domain name=road_type");
      assertThat(output).contains("domain name=lane_count");
      assertThat(output).contains("Feature Count: 2");
      String features = Ogr.run(ogrInfo, "-al", "-geom=WKT", databasePath.toString());
      assertThat(features).contains("A1");
      assertThat(features).contains("B2");
      assertThat(features).contains("Zonenreglement");
      assertThat(features).contains("POLYGON");
      assertThat(features).contains("POINT");
      assertThat(features).contains("2024/05/07 08:30:15");
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
