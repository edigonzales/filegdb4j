package ch.so.agi.filegdb.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Multi point geometry. */
public final class FileGdbMultiPoint implements FileGdbGeometry {
  private final List<FileGdbPoint> points;

  public FileGdbMultiPoint(List<FileGdbPoint> points) {
    this.points = Collections.unmodifiableList(new ArrayList<>(points));
  }

  public List<FileGdbPoint> points() {
    return points;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FileGdbMultiPoint other = (FileGdbMultiPoint) o;
    return Objects.equals(points, other.points);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(points);
    return result;
  }

  @Override
  public final String toString() {
    return "FileGdbMultiPoint[points=" + points + "]";
  }
}
