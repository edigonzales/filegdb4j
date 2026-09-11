package ch.so.agi.filegdb.geometry;

/**
 * Point with optional Z and M ordinates.
 *
 * @param x x ordinate, NaN if encoded as absent
 * @param y y ordinate, NaN if encoded as absent
 * @param z z ordinate or {@code null}
 * @param m measure or {@code null}
 */
public record FileGdbPoint(double x, double y, Double z, Double m) implements FileGdbGeometry {
  public FileGdbPoint(double x, double y) {
    this(x, y, null, null);
  }
}
