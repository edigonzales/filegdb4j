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

  @Override
  public void close() throws IOException {
    table.close();
  }
}
