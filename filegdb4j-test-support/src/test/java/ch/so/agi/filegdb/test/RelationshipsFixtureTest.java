package ch.so.agi.filegdb.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.RelationshipCardinality;
import ch.so.agi.filegdb.catalog.RelationshipClass;
import ch.so.agi.filegdb.catalog.RelationshipKey;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Relationship class support, using the GDAL OpenFileGDB test data. */
class RelationshipsFixtureTest {

  private static Path gdbPath;

  @BeforeAll
  static void setUp() {
    gdbPath = TestData.gdal("relationships.gdb");
    assumeTrue(TestData.available(gdbPath), "relationships.gdb fixture is not available");
  }

  @Test
  void readsRelationshipClasses() throws Exception {
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath)) {
      assertThat(gdb.relationships())
          .extracting(RelationshipClass::name)
          .containsExactlyInAnyOrder(
              "simple_relationship_one_to_one",
              "simple_one_to_many",
              "simple_many_to_many",
              "composite_one_to_one",
              "composite_one_to_many",
              "composite_many_to_many",
              "simple_forward_message_direction",
              "simple_backward_message_direction",
              "simple_both_message_direction",
              "simple_attributed",
              "points__ATTACHREL");
    }
  }

  @Test
  void readsOneToOneRelationship() throws Exception {
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath)) {
      RelationshipClass relationship =
          gdb.relationships().stream()
              .filter(r -> r.name().equals("simple_relationship_one_to_one"))
              .findFirst()
              .orElseThrow();
      assertThat(relationship.cardinality()).isEqualTo(RelationshipCardinality.ONE_TO_ONE);
      assertThat(relationship.originClassName()).isEqualTo("table1");
      assertThat(relationship.destinationClassName()).isEqualTo("table2");
      assertThat(relationship.forwardLabel()).isEqualTo("my forward path label");
      assertThat(relationship.backwardLabel()).isEqualTo("my backward path label");
      assertThat(relationship.composite()).isFalse();
      assertThat(relationship.attributed()).isFalse();
      assertThat(relationship.originKeys())
          .containsExactly(
              new RelationshipKey("pk", RelationshipKey.Role.ORIGIN_PRIMARY),
              new RelationshipKey("parent_pk", RelationshipKey.Role.ORIGIN_FOREIGN));
    }
  }

  @Test
  void readsManyToManyKeys() throws Exception {
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath)) {
      RelationshipClass relationship =
          gdb.relationships().stream()
              .filter(r -> r.name().equals("simple_many_to_many"))
              .findFirst()
              .orElseThrow();
      assertThat(relationship.cardinality()).isEqualTo(RelationshipCardinality.MANY_TO_MANY);
      assertThat(relationship.destinationKeys())
          .containsExactly(
              new RelationshipKey("parent_pk", RelationshipKey.Role.DESTINATION_PRIMARY),
              new RelationshipKey("destination_foreign_key", RelationshipKey.Role.DESTINATION_FOREIGN));
      RelationshipClass composite =
          gdb.relationships().stream()
              .filter(r -> r.name().equals("composite_one_to_many"))
              .findFirst()
              .orElseThrow();
      assertThat(composite.composite()).isTrue();
    }
  }
}
