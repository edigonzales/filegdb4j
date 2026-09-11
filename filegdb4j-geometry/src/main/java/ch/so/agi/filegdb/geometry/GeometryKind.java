package ch.so.agi.filegdb.geometry;

/**
 * Geometry type of a table as stored in the {@code .gdbtable} header.
 *
 * <p>Mirrors {@code FileGDBTableGeometryType} from GDAL OpenFileGDB.
 */
public enum GeometryKind {
  NONE(0),
  POINT(1),
  MULTIPOINT(2),
  LINE(3),
  POLYGON(4),
  MULTIPATCH(9);

  private final int id;

  GeometryKind(int id) {
    this.id = id;
  }

  public int id() {
    return id;
  }

  /** Returns the matching kind or {@code null} for unknown header values. */
  public static GeometryKind fromId(int id) {
    for (GeometryKind kind : values()) {
      if (kind.id == id) {
        return kind;
      }
    }
    return null;
  }
}
