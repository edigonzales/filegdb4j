package ch.so.agi.filegdb.geometry;

/**
 * Cubic Bezier segment between the point at {@code startPointIndex} and the
 * following point.
 *
 * @param startPointIndex index of the start point in the part
 * @param controlX1 x of the first control point
 * @param controlY1 y of the first control point
 * @param controlX2 x of the second control point
 * @param controlY2 y of the second control point
 */
public record BezierSegment(
    int startPointIndex, double controlX1, double controlY1, double controlX2, double controlY2)
    implements FileGdbSegment {}
