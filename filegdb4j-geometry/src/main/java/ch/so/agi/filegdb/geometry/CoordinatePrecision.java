package ch.so.agi.filegdb.geometry;

/**
 * Origin, scale and tolerance of the geometry field.
 *
 * <p>File geodatabases store integer coordinates. The exact coordinate is {@code value / scale +
 * origin}; the tolerance is metadata for clustering/topology operations, not a position accuracy
 * guarantee.
 */
public final class CoordinatePrecision {
  private final double xOrigin;
  private final double yOrigin;
  private final double xyScale;
  private final double xyTolerance;
  private final double zOrigin;
  private final double zScale;
  private final double zTolerance;
  private final double mOrigin;
  private final double mScale;
  private final double mTolerance;

  public CoordinatePrecision(
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
    this.xOrigin = xOrigin;
    this.yOrigin = yOrigin;
    this.xyScale = xyScale;
    this.xyTolerance = xyTolerance;
    this.zOrigin = zOrigin;
    this.zScale = zScale;
    this.zTolerance = zTolerance;
    this.mOrigin = mOrigin;
    this.mScale = mScale;
    this.mTolerance = mTolerance;
  }

  public double xOrigin() {
    return xOrigin;
  }

  public double yOrigin() {
    return yOrigin;
  }

  public double xyScale() {
    return xyScale;
  }

  public double xyTolerance() {
    return xyTolerance;
  }

  public double zOrigin() {
    return zOrigin;
  }

  public double zScale() {
    return zScale;
  }

  public double zTolerance() {
    return zTolerance;
  }

  public double mOrigin() {
    return mOrigin;
  }

  public double mScale() {
    return mScale;
  }

  public double mTolerance() {
    return mTolerance;
  }

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

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CoordinatePrecision other = (CoordinatePrecision) o;
    return Double.compare(xOrigin, other.xOrigin) == 0
        && Double.compare(yOrigin, other.yOrigin) == 0
        && Double.compare(xyScale, other.xyScale) == 0
        && Double.compare(xyTolerance, other.xyTolerance) == 0
        && Double.compare(zOrigin, other.zOrigin) == 0
        && Double.compare(zScale, other.zScale) == 0
        && Double.compare(zTolerance, other.zTolerance) == 0
        && Double.compare(mOrigin, other.mOrigin) == 0
        && Double.compare(mScale, other.mScale) == 0
        && Double.compare(mTolerance, other.mTolerance) == 0;
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Double.hashCode(xOrigin);
    result = 31 * result + Double.hashCode(yOrigin);
    result = 31 * result + Double.hashCode(xyScale);
    result = 31 * result + Double.hashCode(xyTolerance);
    result = 31 * result + Double.hashCode(zOrigin);
    result = 31 * result + Double.hashCode(zScale);
    result = 31 * result + Double.hashCode(zTolerance);
    result = 31 * result + Double.hashCode(mOrigin);
    result = 31 * result + Double.hashCode(mScale);
    result = 31 * result + Double.hashCode(mTolerance);
    return result;
  }

  @Override
  public final String toString() {
    return "CoordinatePrecision[xOrigin="
        + xOrigin
        + ", yOrigin="
        + yOrigin
        + ", xyScale="
        + xyScale
        + ", xyTolerance="
        + xyTolerance
        + ", zOrigin="
        + zOrigin
        + ", zScale="
        + zScale
        + ", zTolerance="
        + zTolerance
        + ", mOrigin="
        + mOrigin
        + ", mScale="
        + mScale
        + ", mTolerance="
        + mTolerance
        + "]";
  }
}
