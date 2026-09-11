package ch.so.agi.filegdb.table;

import ch.so.agi.filegdb.catalog.CrsDefinition;
import ch.so.agi.filegdb.catalog.Dataset;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Public read access to one dataset of a file geodatabase.
 *
 * <p>Object ids are one based, matching the file geodatabase object id field.
 */
public final class FileGdbTable implements AutoCloseable, Iterable<FileGdbRow> {

  private final Dataset dataset;
  private final FileGdbTableFile tableFile;

  public FileGdbTable(Dataset dataset) throws IOException {
    this.dataset = dataset;
    this.tableFile = FileGdbTableFile.open(dataset.tableFile());
  }

  public Dataset dataset() {
    return dataset;
  }

  public String name() {
    return dataset.name();
  }

  public Path path() {
    return dataset.tableFile();
  }

  public List<FileGdbField> fields() {
    return tableFile.fields();
  }

  /** Returns the geometry field or {@code null} for plain tables. */
  public FileGdbGeomField geomField() {
    return tableFile.geomField();
  }

  public boolean isFeatureClass() {
    return tableFile.geomField() != null;
  }

  public CrsDefinition crs() {
    return dataset.crs();
  }

  public long rowCount() {
    return tableFile.validRecordCount();
  }

  public long totalRecordCount() {
    return tableFile.totalRecordCount();
  }

  public FileGdbTableFile tableFile() {
    return tableFile;
  }

  /** Reads a row by its one based object id, or returns {@code null} if absent. */
  public FileGdbRow read(long objectId) throws IOException {
    if (objectId < 1) {
      throw new IndexOutOfBoundsException("Object id must be positive: " + objectId);
    }
    Object[] values = tableFile.readRow(objectId - 1);
    if (values == null) {
      return null;
    }
    return new FileGdbRow(objectId, tableFile.fields(), values);
  }

  @Override
  public Iterator<FileGdbRow> iterator() {
    return new RowIterator();
  }

  @Override
  public void close() throws IOException {
    tableFile.close();
  }

  private final class RowIterator implements Iterator<FileGdbRow> {

    private long index;
    private FileGdbRow next;

    RowIterator() {
      advance();
    }

    private void advance() {
      next = null;
      while (index < tableFile.totalRecordCount()) {
        long current = index++;
        Object[] values;
        try {
          values = tableFile.readRow(current);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
        if (values != null) {
          next = new FileGdbRow(current + 1, tableFile.fields(), values);
          return;
        }
      }
    }

    @Override
    public boolean hasNext() {
      return next != null;
    }

    @Override
    public FileGdbRow next() {
      if (next == null) {
        throw new NoSuchElementException();
      }
      FileGdbRow result = next;
      advance();
      return result;
    }
  }
}
