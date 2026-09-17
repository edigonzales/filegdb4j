package ch.so.agi.filegdb.write;

import java.io.IOException;

/** Writes rows into a plain attribute table created by {@link GdbCreator}. */
public final class GdbTableWriter implements AutoCloseable {

  private final String name;
  private final TableFileWriter table;

  GdbTableWriter(String name, TableFileWriter table) {
    this.name = name;
    this.table = table;
  }

  public String name() {
    return name;
  }

  public long rowCount() {
    return table.rowCount();
  }

  /** Writes one row; the values are aligned with the table definition fields. */
  public long write(Object[] values) throws IOException {
    return table.writeRow(values, null);
  }

  /**
   * Replaces the values of an existing row in place.
   *
   * <p>The object id stays allocated and unchanged. The previous row blob is marked deleted, so
   * readers never observe the old values.
   *
   * @param objectId one based object id
   * @param values values aligned with the table definition fields
   */
  public void updateRow(long objectId, Object[] values) throws IOException {
    table.replaceRow(objectId, values, null);
  }

  /**
   * Marks a row as deleted.
   *
   * @param objectId one based object id; deleting an already deleted row is a no-op
   */
  public void deleteRow(long objectId) throws IOException {
    table.deleteRow(objectId);
  }

  @Override
  public void close() throws IOException {
    table.close();
  }
}
