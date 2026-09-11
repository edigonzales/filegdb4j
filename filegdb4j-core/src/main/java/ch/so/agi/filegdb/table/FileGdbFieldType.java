package ch.so.agi.filegdb.table;

/**
 * File geodatabase field types.
 *
 * <p>Numeric ids mirror {@code FileGDBFieldType} of GDAL OpenFileGDB.
 */
public enum FileGdbFieldType {
  UNDEFINED(-1),
  INT16(0),
  INT32(1),
  FLOAT32(2),
  FLOAT64(3),
  STRING(4),
  DATETIME(5),
  OBJECTID(6),
  GEOMETRY(7),
  BINARY(8),
  RASTER(9),
  GUID(10),
  GLOBALID(11),
  XML(12),
  INT64(13),
  DATE(14),
  TIME(15),
  DATETIME_WITH_OFFSET(16);

  private final int id;

  FileGdbFieldType(int id) {
    this.id = id;
  }

  public int id() {
    return id;
  }

  public static FileGdbFieldType fromId(int id) {
    for (FileGdbFieldType type : values()) {
      if (type.id == id) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown file geodatabase field type: " + id);
  }
}
