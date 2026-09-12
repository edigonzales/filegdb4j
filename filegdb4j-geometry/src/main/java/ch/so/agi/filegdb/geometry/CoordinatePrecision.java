package ch.so.agi.filegdb.geometry;

/**
 * Origin, scale and tolerance of the geometry field.
 *
 * <p>File geodatabases store integer coordinates. The exact coordinate is {@code value / scale +
 * origin}; the tolerance is metadata for clustering/topology operations, not a position accuracy
 * guarantee.
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

  public double xyResolution() {
    return 1.0 / xyScale;
  }

  public CoordinatePrecision withXY(double resolution, double tolerance, double x, double y) {
    CoordinatePrecision result =
        new CoordinatePrecision(
            x,
            y,
            1.0 / resolution,
            tolerance,
            zOrigin,
            zScale,
            zTolerance,
            mOrigin,
            mScale,
            mTolerance);
    result.validateForWriting();
    return result;
  }

  public void validateForWriting() {
    for (double origin : new double[] {xOrigin, yOrigin, zOrigin, mOrigin}) {
      if (!Double.isFinite(origin)) throw new IllegalArgumentException("Origins must be finite");
    }
    for (double scale : new double[] {xyScale, zScale, mScale}) {
      if (!Double.isFinite(scale) || scale <= 0)
        throw new IllegalArgumentException("Scales must be positive and finite");
    }
    for (double tolerance : new double[] {xyTolerance, zTolerance, mTolerance}) {
      if (!Double.isFinite(tolerance) || tolerance <= 0)
        throw new IllegalArgumentException("Tolerances must be positive and finite");
    }
    if (xyTolerance < 2 / xyScale)
      throw new IllegalArgumentException("XY tolerance must be at least twice the resolution");
  }

  public static long gridCoordinate(double value, double origin, double scale) {
    double grid = (value - origin) * scale;
    if (!Double.isFinite(grid) || grid < 0 || grid > 9e15) {
      throw new IllegalArgumentException("Coordinate outside FileGDB precision domain: " + value);
    }
    return Math.round(grid);
  }

  public double quantizeX(double value) {
    return gridCoordinate(value, xOrigin, xyScale) / xyScale + xOrigin;
  }

  public double quantizeY(double value) {
    return gridCoordinate(value, yOrigin, xyScale) / xyScale + yOrigin;
  }

  static double sanitizeScale(double scale) {
    return scale == 0 ? 1.0 : scale;
  }
}
