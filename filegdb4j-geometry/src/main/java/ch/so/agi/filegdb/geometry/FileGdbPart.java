package ch.so.agi.filegdb.geometry;

import java.util.List;

/**
 * One part (ring or line) of a polyline or polygon geometry.
 *
 * <p>Curved segments will be represented as explicit segments in a later
 * milestone; until then a part is a plain point sequence.
 */
public record FileGdbPart(List<FileGdbPoint> points) {
  public FileGdbPart {
    points = List.copyOf(points);
  }
}
