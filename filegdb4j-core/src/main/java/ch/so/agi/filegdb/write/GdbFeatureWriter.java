package ch.so.agi.filegdb.write;

import ch.so.agi.filegdb.geometry.FileGdbGeometry;
import java.io.IOException;

/** Writes features into a feature class created by {@link GdbCreator}. */
public final class GdbFeatureWriter implements AutoCloseable {

  private final String name;
  private final TableFileWriter table;

  GdbFeatureWriter(String name, TableFileWriter table) {
    this.name = name;
    this.table = table;
  }

  public String name() {
    return name;
  }

  public long featureCount() {
    return table.rowCount();
  }

  /**
   * Writes one feature.
   *
   * @param attributes values aligned with the attribute fields of the
   *     definition
   * @param geometry geometry value, may be null when the geometry field is
   *     nullable
   * @return the assigned object id
   */
  public long write(Object[] attributes, FileGdbGeometry geometry) throws IOException {
    return table.writeRow(attributes, geometry);
  }

  /** Writes one feature without geometry. */
  public long write(Object[] attributes) throws IOException {
    return table.writeRow(attributes, null);
  }

  @Override
  public void close() throws IOException {
    table.close();
  }
}
