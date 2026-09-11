package ch.so.agi.filegdb;

import ch.so.agi.filegdb.catalog.Dataset;
import ch.so.agi.filegdb.catalog.DatasetKind;
import ch.so.agi.filegdb.catalog.DefinitionXml;
import ch.so.agi.filegdb.catalog.Domain;
import ch.so.agi.filegdb.catalog.GdbCatalog;
import ch.so.agi.filegdb.catalog.GdbItem;
import ch.so.agi.filegdb.catalog.RelationshipClass;
import ch.so.agi.filegdb.table.FileGdbTable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Entry point for reading a file geodatabase (an Esri {@code .gdb} directory).
 *
 * <pre>{@code
 * try (FileGeodatabase gdb = FileGeodatabase.open(Path.of("transport.gdb"))) {
 *   for (Dataset dataset : gdb.datasets()) {
 *     System.out.println(dataset.name());
 *   }
 *   try (FileGdbTable roads = gdb.featureClass("roads")) {
 *     for (FileGdbRow row : roads) {
 *       System.out.println(row.get("name") + " " + row.geometry());
 *     }
 *   }
 * }
 * }</pre>
 */
public final class FileGeodatabase implements AutoCloseable {

  private final Path directory;
  private final GdbCatalog catalog;

  private FileGeodatabase(Path directory, GdbCatalog catalog) {
    this.directory = directory;
    this.catalog = catalog;
  }

  public static FileGeodatabase open(Path directory) throws IOException {
    Path normalized = directory.toAbsolutePath().normalize();
    return new FileGeodatabase(normalized, GdbCatalog.open(normalized));
  }

  public Path path() {
    return directory;
  }

  /** All datasets (feature classes and tables) declared in the catalog. */
  public List<Dataset> datasets() {
    return catalog.datasets();
  }

  public List<Dataset> featureClasses() {
    return datasets().stream().filter(Dataset::isFeatureClass).toList();
  }

  public List<Dataset> tables() {
    return datasets().stream().filter(dataset -> dataset.kind() == DatasetKind.TABLE).toList();
  }

  public Optional<Dataset> dataset(String name) {
    return catalog.dataset(name);
  }

  /** Raw catalog items; used for domains and relationship classes. */
  public List<GdbItem> items() {
    return catalog.items();
  }

  public GdbCatalog catalog() {
    return catalog;
  }

  /** Attribute domains declared in the catalog. */
  public List<Domain> domains() {
    return catalog.domains();
  }

  public Optional<Domain> domain(String name) {
    return catalog.domain(name);
  }

  /** Relationship classes declared in the catalog. */
  public List<RelationshipClass> relationships() {
    return catalog.relationships();
  }

  public FileGdbTable featureClass(String name) throws IOException {
    Dataset dataset = requireDataset(name);
    if (!dataset.isFeatureClass()) {
      throw new IllegalArgumentException("Dataset is not a feature class: " + name);
    }
    return new FileGdbTable(dataset, DefinitionXml.fieldInfo(dataset.definition()), catalog);
  }

  public FileGdbTable table(String name) throws IOException {
    Dataset dataset = requireDataset(name);
    return new FileGdbTable(dataset, DefinitionXml.fieldInfo(dataset.definition()), catalog);
  }

  private Dataset requireDataset(String name) {
    return catalog
        .dataset(name)
        .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + name));
  }

  @Override
  public void close() {
    // Catalog readers open and close their table files eagerly.
  }
}
