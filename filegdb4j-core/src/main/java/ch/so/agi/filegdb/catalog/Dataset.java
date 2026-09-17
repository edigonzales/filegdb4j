package ch.so.agi.filegdb.catalog;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Dataset metadata from the geodatabase catalog.
 *
 * @param name dataset name
 * @param kind feature class or plain table
 * @param tableNumber one based physical table number in the system catalog
 * @param tableFile physical {@code .gdbtable} file
 * @param definition catalog definition XML
 * @param crs spatial reference declared in the definition XML
 */
public final class Dataset {
  private final String name;
  private final DatasetKind kind;
  private final int tableNumber;
  private final Path tableFile;
  private final String definition;
  private final CrsDefinition crs;

  public Dataset(
      String name,
      DatasetKind kind,
      int tableNumber,
      Path tableFile,
      String definition,
      CrsDefinition crs) {
    this.name = name;
    this.kind = kind;
    this.tableNumber = tableNumber;
    this.tableFile = tableFile;
    this.definition = definition;
    this.crs = crs;
  }

  public String name() {
    return name;
  }

  public DatasetKind kind() {
    return kind;
  }

  public int tableNumber() {
    return tableNumber;
  }

  public Path tableFile() {
    return tableFile;
  }

  public String definition() {
    return definition;
  }

  public CrsDefinition crs() {
    return crs;
  }

  public boolean isFeatureClass() {
    return kind == DatasetKind.FEATURE_CLASS;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Dataset other = (Dataset) o;
    return tableNumber == other.tableNumber
        && Objects.equals(name, other.name)
        && kind == other.kind
        && Objects.equals(tableFile, other.tableFile)
        && Objects.equals(definition, other.definition)
        && Objects.equals(crs, other.crs);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(name);
    result = 31 * result + Objects.hashCode(kind);
    result = 31 * result + Integer.hashCode(tableNumber);
    result = 31 * result + Objects.hashCode(tableFile);
    result = 31 * result + Objects.hashCode(definition);
    result = 31 * result + Objects.hashCode(crs);
    return result;
  }

  @Override
  public final String toString() {
    return "Dataset[name="
        + name
        + ", kind="
        + kind
        + ", tableNumber="
        + tableNumber
        + ", tableFile="
        + tableFile
        + ", definition="
        + definition
        + ", crs="
        + crs
        + "]";
  }
}
