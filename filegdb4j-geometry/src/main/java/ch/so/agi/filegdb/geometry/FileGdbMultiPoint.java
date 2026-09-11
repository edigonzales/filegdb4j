package ch.so.agi.filegdb.geometry;

import java.util.List;

/** Multi point geometry. */
public record FileGdbMultiPoint(List<FileGdbPoint> points) implements FileGdbGeometry {
  public FileGdbMultiPoint {
    points = List.copyOf(points);
  }
}
