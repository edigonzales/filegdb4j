package ch.so.agi.filegdb.geometry;

import java.util.List;

/** Polyline geometry with one or more parts. */
public record FileGdbPolyline(List<FileGdbPart> parts) implements FileGdbGeometry {
  public FileGdbPolyline {
    parts = List.copyOf(parts);
  }
}
