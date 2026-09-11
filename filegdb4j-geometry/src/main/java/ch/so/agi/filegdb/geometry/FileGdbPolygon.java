package ch.so.agi.filegdb.geometry;

import java.util.List;

/**
 * Polygon geometry with one or more rings.
 *
 * <p>Rings are kept in file order. Exterior/interior classification follows the
 * Esri ring orientation convention (exterior rings clockwise) and is applied by
 * the consumer, for example the JTS adapter.
 */
public record FileGdbPolygon(List<FileGdbPart> parts) implements FileGdbGeometry {
  public FileGdbPolygon {
    parts = List.copyOf(parts);
  }
}
