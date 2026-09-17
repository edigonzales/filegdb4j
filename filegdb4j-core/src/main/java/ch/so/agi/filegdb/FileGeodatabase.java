package ch.so.agi.filegdb;

import ch.so.agi.filegdb.catalog.Dataset;
import ch.so.agi.filegdb.catalog.DatasetKind;
import ch.so.agi.filegdb.catalog.DefinitionXml;
import ch.so.agi.filegdb.catalog.Domain;
import ch.so.agi.filegdb.catalog.GdbCatalog;
import ch.so.agi.filegdb.catalog.GdbItem;
import ch.so.agi.filegdb.catalog.RelationshipClass;
import ch.so.agi.filegdb.table.FileGdbTable;
import ch.so.agi.filegdb.write.FeatureClassDefinition;
import ch.so.agi.filegdb.write.GdbCreator;
import ch.so.agi.filegdb.write.GdbFeatureWriter;
import ch.so.agi.filegdb.write.GdbTableWriter;
import ch.so.agi.filegdb.write.TableDefinition;
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
  private final GdbCreator creator;

  private FileGeodatabase(Path directory, GdbCatalog catalog, GdbCreator creator) {
    this.directory = directory;
    this.catalog = catalog;
    this.creator = creator;
  }

  public static FileGeodatabase open(Path directory) throws IOException {
    Path normalized = directory.toAbsolutePath().normalize();
    return new FileGeodatabase(normalized, GdbCatalog.open(normalized), null);
  }

  /**
   * Creates a new file geodatabase directory with its system tables.
   *
   * <p>The returned instance is writable: feature classes can be created with {@link
   * #createFeatureClass(FeatureClassDefinition)}. The catalog snapshot does not include datasets
   * created afterwards; close and reopen the database for reading.
   */
  public static FileGeodatabase create(Path directory) throws IOException {
    Path normalized = directory.toAbsolutePath().normalize();
    GdbCreator creator = GdbCreator.create(normalized);
    try {
      return new FileGeodatabase(normalized, GdbCatalog.open(normalized), creator);
    } catch (Exception e) {
      creator.close();
      throw e;
    }
  }

  /** Starts an exclusive, recoverable edit of an existing geodatabase. */
  public static FileGdbEditSession edit(Path directory) throws IOException {
    return FileGdbEditSession.open(directory, false, () -> {});
  }

  /**
   * Opens an existing geodatabase for writing (append, update, delete).
   *
   * <p>The caller must close the database before other applications access it.
   */
  public static FileGeodatabase openWritable(Path directory) throws IOException {
    Path normalized = directory.toAbsolutePath().normalize();
    return openWritable(normalized, () -> {});
  }

  static FileGeodatabase openWritable(Path directory, Runnable cancellation) throws IOException {
    return new FileGeodatabase(
        directory, GdbCatalog.open(directory), GdbCreator.openExisting(directory, cancellation));
  }

  public GdbFeatureWriter appendFeatures(String name, boolean createSpatialIndex)
      throws IOException {
    if (creator == null) throw new IllegalStateException("File geodatabase is not writable");
    return creator.appendFeatures(name, createSpatialIndex);
  }

  public void validateAppend(String name) throws IOException {
    GdbCreator.checkAppendSupport(catalog, requireDataset(name), () -> {});
  }

  public GdbTableWriter appendRows(String name) throws IOException {
    if (creator == null) throw new IllegalStateException("File geodatabase is not writable");
    return creator.appendRows(name);
  }

  public boolean isWritable() {
    return creator != null;
  }

  /**
   * Creates a feature class with the given definition.
   *
   * <p>Close the returned writer before closing the database.
   */
  public GdbFeatureWriter createFeatureClass(FeatureClassDefinition definition) throws IOException {
    if (creator == null) {
      throw new IllegalStateException("File geodatabase is not writable");
    }
    return creator.createFeatureClass(definition);
  }

  /** Creates an attribute domain in the catalog. */
  public void createDomain(Domain domain) throws IOException {
    if (creator == null) {
      throw new IllegalStateException("File geodatabase is not writable");
    }
    creator.createDomain(domain);
  }

  /** Creates a plain attribute table in the catalog. */
  public GdbTableWriter createTable(TableDefinition definition) throws IOException {
    if (creator == null) {
      throw new IllegalStateException("File geodatabase is not writable");
    }
    return creator.createTable(definition);
  }

  /** Creates a relationship class in the catalog. */
  public void createRelationship(ch.so.agi.filegdb.write.RelationshipDefinition definition)
      throws IOException {
    if (creator == null) {
      throw new IllegalStateException("File geodatabase is not writable");
    }
    creator.createRelationship(definition);
  }

  public Path path() {
    return directory;
  }

  /** All datasets (feature classes and tables) declared in the catalog. */
  public List<Dataset> datasets() {
    return catalog.datasets();
  }

  public List<Dataset> featureClasses() {
    return datasets().stream()
        .filter(Dataset::isFeatureClass)
        .collect(java.util.stream.Collectors.toList());
  }

  public List<Dataset> tables() {
    return datasets().stream()
        .filter(dataset -> dataset.kind() == DatasetKind.TABLE)
        .collect(java.util.stream.Collectors.toList());
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
  public void close() throws IOException {
    if (creator != null) {
      creator.close();
    }
  }
}
