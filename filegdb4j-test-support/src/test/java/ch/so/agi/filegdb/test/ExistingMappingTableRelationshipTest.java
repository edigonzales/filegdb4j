package ch.so.agi.filegdb.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.RelationshipCardinality;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.write.RelationshipDefinition;
import ch.so.agi.filegdb.write.TableDefinition;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A many-to-many relationship class can be bound to an existing mapping table (for example the
 * association table of ili2db) instead of letting filegdb4j create one. The relationship name must
 * equal the table name, and the definition marks the relationship as attributed if requested.
 */
class ExistingMappingTableRelationshipTest {

    @Test
    void relationshipOverAnExistingMappingTable(@TempDir Path directory) throws Exception {
        Path gdb = directory.resolve("mapping.gdb");
        try (FileGeodatabase database = FileGeodatabase.create(gdb)) {
            try (var writer = database.createTable(TableDefinition.builder("origin")
                    .field(FileGdbField.integer("t_id")).build())) {
                writer.write(new Object[] {1L});
            }
            try (var writer = database.createTable(TableDefinition.builder("destination")
                    .field(FileGdbField.integer("t_id")).build())) {
                writer.write(new Object[] {1L});
            }
            try (var writer = database.createTable(TableDefinition.builder("assoc")
                    .field(FileGdbField.integer("origin_ref").asNullable())
                    .field(FileGdbField.integer("destination_ref").asNullable())
                    .field(FileGdbField.real("weight").asNullable())
                    .build())) {
                writer.write(new Object[] {1L, 1L, 0.5});
            }
            database.createRelationship(
                    RelationshipDefinition.builder("assoc")
                            .originClass("origin")
                            .destinationClass("destination")
                            .cardinality(RelationshipCardinality.MANY_TO_MANY)
                            .originPrimaryKey("t_id")
                            .originForeignKey("origin_ref")
                            .destinationPrimaryKey("t_id")
                            .destinationForeignKey("destination_ref")
                            .mappingTable("assoc")
                            .attributed(true)
                            .labels("toDestination", "toOrigin")
                            .build());
        }

        try (FileGeodatabase database = FileGeodatabase.open(gdb)) {
            assertThat(database.relationships()).hasSize(1);
            assertThat(database.relationships().get(0).name()).isEqualTo("assoc");
            String definition = database.items().stream()
                    .map(item -> item.definition())
                    .filter(Objects::nonNull)
                    .filter(xml -> xml.contains("DERelationshipClassInfo"))
                    .findFirst()
                    .orElse("");
            assertThat(definition).contains("esriRelCardinalityManyToMany");
            assertThat(definition).contains("IsAttributed").contains(">true<");
            // the existing mapping table (with its attribute columns) is kept as is
            try (var table = database.table("assoc")) {
                assertThat(table.rowCount()).isEqualTo(1);
                assertThat(table.field("weight")).isPresent();
            }
        }

        Path ogrinfo = Ogr.findExecutable("ogrinfo");
        if (ogrinfo != null) {
            String info = Ogr.run(ogrinfo, "-json", "-al", "-so", gdb.toString());
            assertThat(info).contains("assoc");
        }
    }

    @Test
    void rejectsAParameterTableNameMismatch() {
        assertThatThrownBy(() -> RelationshipDefinition.builder("relationship")
                        .originClass("origin")
                        .destinationClass("destination")
                        .cardinality(RelationshipCardinality.MANY_TO_MANY)
                        .originPrimaryKey("t_id")
                        .originForeignKey("origin_ref")
                        .destinationPrimaryKey("t_id")
                        .destinationForeignKey("destination_ref")
                        .mappingTable("other_table")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mapping table name");
    }

    @Test
    void rejectsAMissingMappingTable(@TempDir Path directory) throws Exception {
        Path gdb = directory.resolve("missing.gdb");
        try (FileGeodatabase database = FileGeodatabase.create(gdb)) {
            try (var writer = database.createTable(TableDefinition.builder("origin")
                    .field(FileGdbField.integer("t_id")).build())) {
                writer.write(new Object[] {1L});
            }
            try (var writer = database.createTable(TableDefinition.builder("destination")
                    .field(FileGdbField.integer("t_id")).build())) {
                writer.write(new Object[] {1L});
            }
            assertThatThrownBy(() -> database.createRelationship(
                            RelationshipDefinition.builder("assoc_missing")
                                    .originClass("origin")
                                    .destinationClass("destination")
                                    .cardinality(RelationshipCardinality.MANY_TO_MANY)
                                    .originPrimaryKey("t_id")
                                    .originForeignKey("origin_ref")
                                    .destinationPrimaryKey("t_id")
                                    .destinationForeignKey("destination_ref")
                                    .mappingTable("assoc_missing")
                                    .build()))
                    .hasMessageContaining("Mapping table does not exist");
        }
    }
}
