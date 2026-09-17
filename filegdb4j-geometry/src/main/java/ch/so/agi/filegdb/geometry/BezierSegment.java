package ch.so.agi.filegdb.geometry;

/**
 * Cubic Bezier segment between the point at {@code startPointIndex} and the
 * following point.
 *
 * <p>Properties:
 * <ul>
 *   <li>{@code startPointIndex}: index of the start point in the part</li>
 *   <li>{@code controlX1}: x of the first control point</li>
 *   <li>{@code controlY1}: y of the first control point</li>
 *   <li>{@code controlX2}: x of the second control point</li>
 *   <li>{@code controlY2}: y of the second control point</li>
 * </ul>
 */
public final class BezierSegment implements FileGdbSegment {
  private final int startPointIndex;
  private final double controlX1;
  private final double controlY1;
  private final double controlX2;
  private final double controlY2;

  public BezierSegment(
      int startPointIndex, double controlX1, double controlY1, double controlX2, double controlY2) {
    this.startPointIndex = startPointIndex;
    this.controlX1 = controlX1;
    this.controlY1 = controlY1;
    this.controlX2 = controlX2;
    this.controlY2 = controlY2;
  }

  @Override
  public int startPointIndex() {
    return startPointIndex;
  }

  public double controlX1() {
    return controlX1;
  }

  public double controlY1() {
    return controlY1;
  }

  public double controlX2() {
    return controlX2;
  }

  public double controlY2() {
    return controlY2;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BezierSegment other = (BezierSegment) o;
    return startPointIndex == other.startPointIndex
        && Double.compare(controlX1, other.controlX1) == 0
        && Double.compare(controlY1, other.controlY1) == 0
        && Double.compare(controlX2, other.controlX2) == 0
        && Double.compare(controlY2, other.controlY2) == 0;
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Integer.hashCode(startPointIndex);
    result = 31 * result + Double.hashCode(controlX1);
    result = 31 * result + Double.hashCode(controlY1);
    result = 31 * result + Double.hashCode(controlX2);
    result = 31 * result + Double.hashCode(controlY2);
    return result;
  }

  @Override
  public final String toString() {
    return "BezierSegment[startPointIndex="
        + startPointIndex
        + ", controlX1="
        + controlX1
        + ", controlY1="
        + controlY1
        + ", controlX2="
        + controlX2
        + ", controlY2="
        + controlY2
        + "]";
  }
}
