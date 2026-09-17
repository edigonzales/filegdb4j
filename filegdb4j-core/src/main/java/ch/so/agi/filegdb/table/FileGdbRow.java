package ch.so.agi.filegdb.table;

import ch.so.agi.filegdb.geometry.FileGdbGeometry;
import java.util.List;
import java.util.Objects;

/**
 * One decoded table row.
 *
 * <p>{@code values} is aligned with {@code fields}; geometry values are
 * {@link FileGdbGeometry} instances.
 */
public final class FileGdbRow {
  private final long objectId;
  private final List<FileGdbField> fields;
  private final Object[] values;

  public FileGdbRow(long objectId, List<FileGdbField> fields, Object[] values) {
    this.objectId = objectId;
    this.fields = fields;
    this.values = values;
  }

  public long objectId() {
    return objectId;
  }

  public List<FileGdbField> fields() {
    return fields;
  }

  public Object[] values() {
    return values;
  }

  public Object get(int index) {
    return values[index];
  }

  public Object get(String fieldName) {
    for (int i = 0; i < fields.size(); i++) {
      if (fields.get(i).name().equals(fieldName)) {
        return values[i];
      }
    }
    for (int i = 0; i < fields.size(); i++) {
      if (fields.get(i).name().equalsIgnoreCase(fieldName)) {
        return values[i];
      }
    }
    throw new IllegalArgumentException("Unknown field: " + fieldName);
  }

  public FileGdbGeometry geometry() {
    for (int i = 0; i < fields.size(); i++) {
      if (fields.get(i).type() == FileGdbFieldType.GEOMETRY) {
        return (FileGdbGeometry) values[i];
      }
    }
    return null;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FileGdbRow other = (FileGdbRow) o;
    return objectId == other.objectId
        && Objects.equals(fields, other.fields)
        && Objects.equals(values, other.values);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Long.hashCode(objectId);
    result = 31 * result + Objects.hashCode(fields);
    result = 31 * result + Objects.hashCode(values);
    return result;
  }

  @Override
  public final String toString() {
    return "FileGdbRow[objectId=" + objectId + ", fields=" + fields + ", values=" + values + "]";
  }
}
