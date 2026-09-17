package ch.so.agi.filegdb.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Polygon geometry with one or more rings.
 *
 * <p>Rings are kept in file order. Exterior/interior classification follows the
 * Esri ring orientation convention (exterior rings clockwise) and is applied by
 * the consumer, for example the JTS adapter.
 */
public final class FileGdbPolygon implements FileGdbGeometry {
  private final List<FileGdbPart> parts;

  public FileGdbPolygon(List<FileGdbPart> parts) {
    this.parts = Collections.unmodifiableList(new ArrayList<>(parts));
  }

  public List<FileGdbPart> parts() {
    return parts;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FileGdbPolygon other = (FileGdbPolygon) o;
    return Objects.equals(parts, other.parts);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(parts);
    return result;
  }

  @Override
  public final String toString() {
    return "FileGdbPolygon[parts=" + parts + "]";
  }
}
