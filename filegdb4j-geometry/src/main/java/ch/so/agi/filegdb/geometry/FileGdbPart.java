package ch.so.agi.filegdb.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One part (ring or line) of a polyline or polygon geometry.
 *
 * <p>{@code segments} describes curved connections between consecutive
 * points; each segment replaces the straight line starting at its
 * {@link FileGdbSegment#startPointIndex()}.
 */
public final class FileGdbPart {
  private final List<FileGdbPoint> points;
  private final List<FileGdbSegment> segments;

  public FileGdbPart(List<FileGdbPoint> points, List<FileGdbSegment> segments) {
    this.points = Collections.unmodifiableList(new ArrayList<>(points));
    this.segments =
        segments == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(segments));
  }

  public FileGdbPart(List<FileGdbPoint> points) {
    this(points, Collections.emptyList());
  }

  public List<FileGdbPoint> points() {
    return points;
  }

  public List<FileGdbSegment> segments() {
    return segments;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FileGdbPart other = (FileGdbPart) o;
    return Objects.equals(points, other.points) && Objects.equals(segments, other.segments);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(points);
    result = 31 * result + Objects.hashCode(segments);
    return result;
  }

  @Override
  public final String toString() {
    return "FileGdbPart[points=" + points + ", segments=" + segments + "]";
  }
}
