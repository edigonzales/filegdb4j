package ch.so.agi.filegdb.geometry;

import java.util.Objects;

/**
 * Point with optional Z and M ordinates.
 *
 * <p>Properties:
 * <ul>
 *   <li>{@code x}: x ordinate, NaN if encoded as absent</li>
 *   <li>{@code y}: y ordinate, NaN if encoded as absent</li>
 *   <li>{@code z}: z ordinate or {@code null}</li>
 *   <li>{@code m}: measure or {@code null}</li>
 * </ul>
 */
public final class FileGdbPoint implements FileGdbGeometry {
  private final double x;
  private final double y;
  private final Double z;
  private final Double m;

  public FileGdbPoint(double x, double y, Double z, Double m) {
    this.x = x;
    this.y = y;
    this.z = z;
    this.m = m;
  }

  public FileGdbPoint(double x, double y) {
    this(x, y, null, null);
  }

  public double x() {
    return x;
  }

  public double y() {
    return y;
  }

  public Double z() {
    return z;
  }

  public Double m() {
    return m;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FileGdbPoint other = (FileGdbPoint) o;
    return Double.compare(x, other.x) == 0
        && Double.compare(y, other.y) == 0
        && Objects.equals(z, other.z)
        && Objects.equals(m, other.m);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Double.hashCode(x);
    result = 31 * result + Double.hashCode(y);
    result = 31 * result + Objects.hashCode(z);
    result = 31 * result + Objects.hashCode(m);
    return result;
  }

  @Override
  public final String toString() {
    return "FileGdbPoint[x=" + x + ", y=" + y + ", z=" + z + ", m=" + m + "]";
  }
}
