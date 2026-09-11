package ch.so.agi.filegdb.geometry;

import java.util.List;

/**
 * One part (ring or line) of a polyline or polygon geometry.
 *
 * <p>{@code segments} describes curved connections between consecutive
 * points; each segment replaces the straight line starting at its
 * {@link FileGdbSegment#startPointIndex()}.
 */
public record FileGdbPart(List<FileGdbPoint> points, List<FileGdbSegment> segments) {

  public FileGdbPart {
    points = List.copyOf(points);
    segments = segments == null ? List.of() : List.copyOf(segments);
  }

  public FileGdbPart(List<FileGdbPoint> points) {
    this(points, List.of());
  }
}
