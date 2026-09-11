package ch.so.agi.filegdb.catalog;

import java.nio.file.Path;

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
public record Dataset(
    String name,
    DatasetKind kind,
    int tableNumber,
    Path tableFile,
    String definition,
    CrsDefinition crs) {

  public boolean isFeatureClass() {
    return kind == DatasetKind.FEATURE_CLASS;
  }
}
