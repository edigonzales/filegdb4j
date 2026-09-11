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
public record CircularArcSegment(
    int startPointIndex,
    double interiorX,
    double interiorY,
    boolean byCenter,
    boolean counterClockwise)
    implements FileGdbSegment {}
