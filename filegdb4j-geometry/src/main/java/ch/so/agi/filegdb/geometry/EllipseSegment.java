package ch.so.agi.filegdb.geometry;

/**
 * Ellipse segment between the point at {@code startPointIndex} and the
 * following point.
 *
 * @param startPointIndex index of the start point in the part
 * @param centerX x of the ellipse center
 * @param centerY y of the ellipse center
 * @param rotationDegrees rotation of the semi major axis in degrees
 * @param semiMajor semi major axis length
 * @param minorMajorRatio ratio of the semi minor to the semi major axis
 * @param minor whether the minor arc is used
 * @param complete whether the ellipse is complete
 */
public final class EllipseSegment implements FileGdbSegment {
  private final int startPointIndex;
  private final double centerX;
  private final double centerY;
  private final double rotationDegrees;
  private final double semiMajor;
  private final double minorMajorRatio;
  private final boolean minor;
  private final boolean complete;

  public EllipseSegment(
      int startPointIndex,
      double centerX,
      double centerY,
      double rotationDegrees,
      double semiMajor,
      double minorMajorRatio,
      boolean minor,
      boolean complete) {
    this.startPointIndex = startPointIndex;
    this.centerX = centerX;
    this.centerY = centerY;
    this.rotationDegrees = rotationDegrees;
    this.semiMajor = semiMajor;
    this.minorMajorRatio = minorMajorRatio;
    this.minor = minor;
    this.complete = complete;
  }

  @Override
  public int startPointIndex() {
    return startPointIndex;
  }

  public double centerX() {
    return centerX;
  }

  public double centerY() {
    return centerY;
  }

  public double rotationDegrees() {
    return rotationDegrees;
  }

  public double semiMajor() {
    return semiMajor;
  }

  public double minorMajorRatio() {
    return minorMajorRatio;
  }

  public boolean minor() {
    return minor;
  }

  public boolean complete() {
    return complete;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EllipseSegment other = (EllipseSegment) o;
    return startPointIndex == other.startPointIndex
        && Double.compare(centerX, other.centerX) == 0
        && Double.compare(centerY, other.centerY) == 0
        && Double.compare(rotationDegrees, other.rotationDegrees) == 0
        && Double.compare(semiMajor, other.semiMajor) == 0
        && Double.compare(minorMajorRatio, other.minorMajorRatio) == 0
        && minor == other.minor
        && complete == other.complete;
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Integer.hashCode(startPointIndex);
    result = 31 * result + Double.hashCode(centerX);
    result = 31 * result + Double.hashCode(centerY);
    result = 31 * result + Double.hashCode(rotationDegrees);
    result = 31 * result + Double.hashCode(semiMajor);
    result = 31 * result + Double.hashCode(minorMajorRatio);
    result = 31 * result + Boolean.hashCode(minor);
    result = 31 * result + Boolean.hashCode(complete);
    return result;
  }

  @Override
  public final String toString() {
    return "EllipseSegment[startPointIndex="
        + startPointIndex
        + ", centerX="
        + centerX
        + ", centerY="
        + centerY
        + ", rotationDegrees="
        + rotationDegrees
        + ", semiMajor="
        + semiMajor
        + ", minorMajorRatio="
        + minorMajorRatio
        + ", minor="
        + minor
        + ", complete="
        + complete
        + "]";
  }
}
