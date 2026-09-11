package ch.so.agi.filegdb.geometry;

/**
 * Origin, scale and tolerance of the geometry field.
 *
 * <p>File geodatabases store integer coordinates. The exact coordinate is
 * {@code value / scale + origin}; the tolerance is the guaranteed spatial
 * accuracy in map units.
 */
public record CoordinatePrecision(
    double xOrigin,
    double yOrigin,
    double xyScale,
    double xyTolerance,
    double zOrigin,
    double zScale,
    double zTolerance,
    double mOrigin,
    double mScale,
    double mTolerance) {

  static double sanitizeScale(double scale) {
    return scale == 0 ? 1.0 : scale;
  }
}
