package ch.so.agi.filegdb.table;

import ch.so.agi.filegdb.catalog.CrsDefinition;
import ch.so.agi.filegdb.catalog.Dataset;
import ch.so.agi.filegdb.catalog.Domain;
import ch.so.agi.filegdb.catalog.GdbCatalog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
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
    this(dataset, Collections.<String, FieldMetadata>emptyMap(), null);
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

  public static final class QueryResult implements Iterable<FileGdbRow> {
    private final List<FileGdbRow> rows;
    private final boolean indexUsed;
    private final long geometriesRead;

    public QueryResult(List<FileGdbRow> rows, boolean indexUsed, long geometriesRead) {
      this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
      this.indexUsed = indexUsed;
      this.geometriesRead = geometriesRead;
    }

    public List<FileGdbRow> rows() {
      return rows;
    }

    public boolean indexUsed() {
      return indexUsed;
    }

    public long geometriesRead() {
      return geometriesRead;
    }

    @Override
    public Iterator<FileGdbRow> iterator() {
      return rows.iterator();
    }

    @Override
    public final boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      QueryResult other = (QueryResult) o;
      return indexUsed == other.indexUsed
          && geometriesRead == other.geometriesRead
          && Objects.equals(rows, other.rows);
    }

    @Override
    public final int hashCode() {
      int result = 0;
      result = 31 * result + Objects.hashCode(rows);
      result = 31 * result + Boolean.hashCode(indexUsed);
      result = 31 * result + Long.hashCode(geometriesRead);
      return result;
    }

    @Override
    public final String toString() {
      return "QueryResult[rows="
          + rows
          + ", indexUsed="
          + indexUsed
          + ", geometriesRead="
          + geometriesRead
          + "]";
    }
  }

  /**
   * Inclusive geometry-envelope intersection. Foreign geometry-cell indexes cannot prove envelope
   * intersection (e.g. a rectangle inside a polygon hole), so use a scan for those.
   */
  public QueryResult query(ch.so.agi.filegdb.geometry.Envelope filter) throws IOException {
    return query(filter, true);
  }

  public QueryResult query(ch.so.agi.filegdb.geometry.Envelope filter, boolean useIndex)
      throws IOException {
    if (filter == null
        || !Double.isFinite(filter.xMin())
        || !Double.isFinite(filter.yMin())
        || !Double.isFinite(filter.xMax())
        || !Double.isFinite(filter.yMax())
        || filter.xMin() > filter.xMax()
        || filter.yMin() > filter.yMax())
      throw new IllegalArgumentException("Invalid query envelope");
    if (geomField() == null) throw new IllegalStateException("Table has no geometry");
    java.nio.file.Path index = ch.so.agi.filegdb.index.SpatialIndex.path(path());
    boolean indexed = false;
    if (useIndex && java.nio.file.Files.exists(index)) {
      try (java.io.RandomAccessFile file =
          new java.io.RandomAccessFile(path().toFile(), "r")) {
        file.seek(40);
        int size = Integer.reverseBytes(file.readInt());
        if (size > 0 && size < 128) {
          byte[] creator = new byte[size];
          file.readFully(creator);
          indexed =
              new String(creator, java.nio.charset.StandardCharsets.US_ASCII)
                  .equals("filegdb4j-envelope-spx-1");
        }
      }
    }
    // A point's envelope equals the point itself, including in foreign geometry-cell indexes.
    indexed |=
        useIndex
            && java.nio.file.Files.exists(index)
            && geomField().geometry().kind() == ch.so.agi.filegdb.geometry.GeometryKind.POINT;
    java.util.SortedSet<Long> candidates = null;
    if (indexed)
      candidates =
          ch.so.agi.filegdb.index.SpatialIndex.candidates(
              index, geomField().geometry().spatialIndexGridResolution(), filter);
    java.util.ArrayList<FileGdbRow> rows = new java.util.ArrayList<>();
    long read = 0;
    if (candidates != null) {
      for (long id : candidates) {
        FileGdbRow row = read(id);
        if (row == null) continue;
        read++;
        if (ch.so.agi.filegdb.geometry.GeometryBounds.intersects(
            ch.so.agi.filegdb.geometry.GeometryBounds.of(row.geometry()), filter)) rows.add(row);
      }
    } else {
      for (FileGdbRow row : this) {
        read++;
        if (ch.so.agi.filegdb.geometry.GeometryBounds.intersects(
            ch.so.agi.filegdb.geometry.GeometryBounds.of(row.geometry()), filter)) rows.add(row);
      }
    }
    return new QueryResult(rows, indexed, read);
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
