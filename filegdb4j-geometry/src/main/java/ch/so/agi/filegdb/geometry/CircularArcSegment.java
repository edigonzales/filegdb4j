package ch.so.agi.filegdb.geometry;

/**
 * Circular arc between the point at {@code startPointIndex} and the following
 * point.
 *
 * @param startPointIndex index of the start point in the part
 * @param interiorX x of the interior point or of the arc center
 * @param interiorY y of the interior point or of the arc center
 * @param byCenter whether the coordinates describe the arc center instead of
 *     an interior point
 * @param counterClockwise winding for center based arcs
 */
public final class CircularArcSegment implements FileGdbSegment {
  private final int startPointIndex;
  private final double interiorX;
  private final double interiorY;
  private final boolean byCenter;
  private final boolean counterClockwise;

  public CircularArcSegment(
      int startPointIndex,
      double interiorX,
      double interiorY,
      boolean byCenter,
      boolean counterClockwise) {
    this.startPointIndex = startPointIndex;
    this.interiorX = interiorX;
    this.interiorY = interiorY;
    this.byCenter = byCenter;
    this.counterClockwise = counterClockwise;
  }

  @Override
  public int startPointIndex() {
    return startPointIndex;
  }

  public double interiorX() {
    return interiorX;
  }

  public double interiorY() {
    return interiorY;
  }

  public boolean byCenter() {
    return byCenter;
  }

  public boolean counterClockwise() {
    return counterClockwise;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CircularArcSegment other = (CircularArcSegment) o;
    return startPointIndex == other.startPointIndex
        && Double.compare(interiorX, other.interiorX) == 0
        && Double.compare(interiorY, other.interiorY) == 0
        && byCenter == other.byCenter
        && counterClockwise == other.counterClockwise;
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Integer.hashCode(startPointIndex);
    result = 31 * result + Double.hashCode(interiorX);
    result = 31 * result + Double.hashCode(interiorY);
    result = 31 * result + Boolean.hashCode(byCenter);
    result = 31 * result + Boolean.hashCode(counterClockwise);
    return result;
  }

  @Override
  public final String toString() {
    return "CircularArcSegment[startPointIndex="
        + startPointIndex
        + ", interiorX="
        + interiorX
        + ", interiorY="
        + interiorY
        + ", byCenter="
        + byCenter
        + ", counterClockwise="
        + counterClockwise
        + "]";
  }
}
