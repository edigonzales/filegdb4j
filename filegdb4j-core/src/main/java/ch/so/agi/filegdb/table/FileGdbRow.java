package ch.so.agi.filegdb.table;

import ch.so.agi.filegdb.geometry.FileGdbGeometry;
import java.util.List;

/**
 * One decoded table row.
 *
 * <p>{@code values} is aligned with {@code fields}; geometry values are
 * {@link FileGdbGeometry} instances.
 */
public record FileGdbRow(long objectId, List<FileGdbField> fields, Object[] values) {

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
}
