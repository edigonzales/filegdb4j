package ch.so.agi.filegdb.table;

import ch.so.agi.filegdb.catalog.CrsDefinition;
import ch.so.agi.filegdb.catalog.Dataset;
import ch.so.agi.filegdb.catalog.Domain;
import ch.so.agi.filegdb.catalog.GdbCatalog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Public read access to one dataset of a file geodatabase.
 *
 * <p>Object ids are one based, matching the file geodatabase object id field.
 */
public final class FileGdbTable implements AutoCloseable, Iterable<FileGdbRow> {

  private final Dataset dataset;
  private final FileGdbTableFile tableFile;
  private final GdbCatalog catalog;

  public FileGdbTable(Dataset dataset) throws IOException {
    this(dataset, Map.of(), null);
  }

  public FileGdbTable(Dataset dataset, Map<String, FieldMetadata> metadata, GdbCatalog catalog)
      throws IOException {
    this.dataset = dataset;
    this.catalog = catalog;
    this.tableFile = FileGdbTableFile.open(dataset.tableFile(), metadata);
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

  /** Looks up a field by name, ignoring case as a fallback. */
  public Optional<FileGdbField> field(String name) {
    for (FileGdbField field : tableFile.fields()) {
      if (field.name().equals(name)) {
        return Optional.of(field);
      }
    }
    for (FileGdbField field : tableFile.fields()) {
      if (field.name().equalsIgnoreCase(name)) {
        return Optional.of(field);
      }
    }
    return Optional.empty();
  }

  /** Returns the geometry field or {@code null} for plain tables. */
  public FileGdbGeomField geomField() {
    return tableFile.geomField();
  }

  public boolean isFeatureClass() {
    return tableFile.geomField() != null;
  }

  /** Index of the geometry field in {@link #fields()}, or -1 for plain tables. */
  public int geomFieldIndex() {
    return tableFile.geomFieldIndex();
  }

  /** Index of the object id field in {@link #fields()}, or -1 if absent. */
  public int objectIdFieldIndex() {
    return tableFile.objectIdFieldIndex();
  }

  public CrsDefinition crs() {
    return dataset.crs();
  }

  /** Domain assigned to a field, resolved through the catalog. */
  public Optional<Domain> domain(String fieldName) {
    for (FileGdbField field : tableFile.fields()) {
      if (field.name().equals(fieldName) || field.name().equalsIgnoreCase(fieldName)) {
        if (field.domain() == null || catalog == null) {
          return Optional.empty();
        }
        return catalog.domain(field.domain());
      }
    }
    return Optional.empty();
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
