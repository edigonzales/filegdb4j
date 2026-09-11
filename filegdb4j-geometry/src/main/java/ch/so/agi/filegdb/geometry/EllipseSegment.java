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
public record EllipseSegment(
    int startPointIndex,
    double centerX,
    double centerY,
    double rotationDegrees,
    double semiMajor,
    double minorMajorRatio,
    boolean minor,
    boolean complete)
    implements FileGdbSegment {}
