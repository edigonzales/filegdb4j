package ch.so.agi.filegdb.geometry;

/** Two dimensional bounding box. */
public final class Envelope {
  private final double xMin;
  private final double yMin;
  private final double xMax;
  private final double yMax;

  public Envelope(double xMin, double yMin, double xMax, double yMax) {
    this.xMin = xMin;
    this.yMin = yMin;
    this.xMax = xMax;
    this.yMax = yMax;
  }

  public double xMin() {
    return xMin;
  }

  public double yMin() {
    return yMin;
  }

  public double xMax() {
    return xMax;
  }

  public double yMax() {
    return yMax;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Envelope other = (Envelope) o;
    return Double.compare(xMin, other.xMin) == 0
        && Double.compare(yMin, other.yMin) == 0
        && Double.compare(xMax, other.xMax) == 0
        && Double.compare(yMax, other.yMax) == 0;
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Double.hashCode(xMin);
    result = 31 * result + Double.hashCode(yMin);
    result = 31 * result + Double.hashCode(xMax);
    result = 31 * result + Double.hashCode(yMax);
    return result;
  }

  @Override
  public final String toString() {
    return "Envelope[xMin=" + xMin + ", yMin=" + yMin + ", xMax=" + xMax + ", yMax=" + yMax + "]";
  }
}
