package ch.so.agi.filegdb.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Polyline geometry with one or more parts. */
public final class FileGdbPolyline implements FileGdbGeometry {
  private final List<FileGdbPart> parts;

  public FileGdbPolyline(List<FileGdbPart> parts) {
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
    FileGdbPolyline other = (FileGdbPolyline) o;
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
    return "FileGdbPolyline[parts=" + parts + "]";
  }
}
