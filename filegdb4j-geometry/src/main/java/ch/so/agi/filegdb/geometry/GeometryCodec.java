package ch.so.agi.filegdb.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes Esri shape buffers as stored in file geodatabase geometry fields.
 *
 * <p>Ported from GDAL OpenFileGDB ({@code filegdbtable.cpp},
 * {@code FileGDBOGRGeometryConverterImpl::GetAsGeometry}). The shape type
 * constants match {@code ogrpgeogeometry.h}, which is the header the GDAL
 * driver compiles against. This codec intentionally has no JTS or Hop
 * dependency.
 *
 * <p>Curved segments (arc, Bezier, ellipse) are not decoded yet and raise an
 * {@link IllegalArgumentException}. The fgdb table geometry type of a shape
 * buffer is encoded in the upper bits of the leading varint.
 */
public final class GeometryCodec {

  /** Shape types from {@code ogrpgeogeometry.h}. */
  private static final int SHPT_NULL = 0;
  private static final int SHPT_POINT = 1;
  private static final int SHPT_ARC = 3;
  private static final int SHPT_POLYGON = 5;
  private static final int SHPT_MULTIPOINT = 8;
  private static final int SHPT_POINTZ = 9;
  private static final int SHPT_ARCZ = 10;
  private static final int SHPT_POINTZM = 11;
  private static final int SHPT_ARCZM = 13;
  private static final int SHPT_POLYGONZM = 15;
  private static final int SHPT_MULTIPOINTZM = 18;
  private static final int SHPT_POLYGONZ = 19;
  private static final int SHPT_MULTIPOINTZ = 20;
  private static final int SHPT_POINTM = 21;
  private static final int SHPT_ARCM = 23;
  private static final int SHPT_POLYGONM = 25;
  private static final int SHPT_MULTIPOINTM = 28;
  private static final int SHPT_MULTIPATCHM = 31;
  private static final int SHPT_MULTIPATCH = 32;
  private static final int SHPT_GENERALPOLYLINE = 50;
  private static final int SHPT_GENERALPOLYGON = 51;
  private static final int SHPT_GENERALPOINT = 52;
  private static final int SHPT_GENERALMULTIPOINT = 53;
  private static final int SHPT_GENERALMULTIPATCH = 54;

  private static final long EXT_SHAPE_Z_FLAG = 0x80000000L;
  private static final long EXT_SHAPE_M_FLAG = 0x40000000L;
  private static final long EXT_SHAPE_CURVE_FLAG = 0x20000000L;

  private GeometryCodec() {}

  public static FileGdbGeometry decode(byte[] buffer, GeometryFieldDefinition definition) {
    if (buffer == null || buffer.length == 0) {
      return null;
    }
    Cursor cursor = new Cursor(buffer);
    long rawType = cursor.varUInt32();
    int shapeType = (int) (rawType & 0xFF);
    boolean hasZ = (rawType & EXT_SHAPE_Z_FLAG) != 0;
    boolean hasM = (rawType & EXT_SHAPE_M_FLAG) != 0;
    boolean hasCurves = (rawType & EXT_SHAPE_CURVE_FLAG) != 0;

    switch (shapeType) {
      case SHPT_NULL:
        return null;

      case SHPT_POINT:
      case SHPT_POINTM:
      case SHPT_POINTZ:
      case SHPT_POINTZM:
      case SHPT_GENERALPOINT:
        if (shapeType == SHPT_POINTZ || shapeType == SHPT_POINTZM) {
          hasZ = true;
        }
        if (shapeType == SHPT_POINTM || shapeType == SHPT_POINTZM) {
          hasM = true;
        }
        return readPoint(cursor, definition, hasZ, hasM);

      case SHPT_MULTIPOINT:
      case SHPT_MULTIPOINTM:
      case SHPT_MULTIPOINTZ:
      case SHPT_MULTIPOINTZM:
      case SHPT_GENERALMULTIPOINT:
        if (shapeType == SHPT_MULTIPOINTZ || shapeType == SHPT_MULTIPOINTZM) {
          hasZ = true;
        }
        if (shapeType == SHPT_MULTIPOINTM || shapeType == SHPT_MULTIPOINTZM) {
          hasM = true;
        }
        return readMultiPoint(cursor, definition, hasZ, hasM);

      case SHPT_ARC:
      case SHPT_ARCM:
      case SHPT_ARCZ:
      case SHPT_ARCZM:
      case SHPT_GENERALPOLYLINE:
        if (shapeType == SHPT_ARCZ || shapeType == SHPT_ARCZM) {
          hasZ = true;
        }
        if (shapeType == SHPT_ARCM || shapeType == SHPT_ARCZM) {
          hasM = true;
        }
        return new FileGdbPolyline(
            readParts(cursor, definition, hasZ, hasM, hasCurves, false));

      case SHPT_POLYGON:
      case SHPT_POLYGONM:
      case SHPT_POLYGONZ:
      case SHPT_POLYGONZM:
      case SHPT_GENERALPOLYGON:
        if (shapeType == SHPT_POLYGONZ || shapeType == SHPT_POLYGONZM) {
          hasZ = true;
        }
        if (shapeType == SHPT_POLYGONM || shapeType == SHPT_POLYGONZM) {
          hasM = true;
        }
        return new FileGdbPolygon(
            readParts(cursor, definition, hasZ, hasM, hasCurves, false));

      case SHPT_MULTIPATCH:
      case SHPT_MULTIPATCHM:
      case SHPT_GENERALMULTIPATCH:
        throw new IllegalArgumentException("Multipatch geometries are not supported yet");

      default:
        throw new IllegalArgumentException("Unsupported file geodatabase geometry type: " + shapeType);
    }
  }

  private static FileGdbPoint readPoint(
      Cursor cursor, GeometryFieldDefinition definition, boolean hasZ, boolean hasM) {
    CoordinatePrecision precision = definition.precision();
    long x = cursor.varUInt64();
    long y = cursor.varUInt64();
    double dfX = x == 0 ? Double.NaN : (x - 1) / precision.xyScale() + precision.xOrigin();
    double dfY = y == 0 ? Double.NaN : (y - 1) / precision.xyScale() + precision.yOrigin();

    Double dfZ = null;
    Double dfM = null;
    if (hasZ) {
      long z = cursor.varUInt64();
      dfZ =
          z == 0
              ? Double.NaN
              : (z - 1) / CoordinatePrecision.sanitizeScale(precision.zScale())
                  + precision.zOrigin();
      if (hasM) {
        long m = cursor.varUInt64();
        dfM =
            m == 0
                ? Double.NaN
                : (m - 1) / CoordinatePrecision.sanitizeScale(precision.mScale())
                    + precision.mOrigin();
      }
    } else if (hasM) {
      long m = cursor.varUInt64();
      dfM =
          m == 0
              ? Double.NaN
              : (m - 1) / CoordinatePrecision.sanitizeScale(precision.mScale())
                  + precision.mOrigin();
    }
    return new FileGdbPoint(dfX, dfY, dfZ, dfM);
  }

  private static FileGdbMultiPoint readMultiPoint(
      Cursor cursor, GeometryFieldDefinition definition, boolean hasZ, boolean hasM) {
    long pointCount = cursor.varUInt32();
    if (pointCount == 0) {
      return new FileGdbMultiPoint(List.of());
    }
    cursor.skipVarUInt(4);

    List<FileGdbPoint> points = new ArrayList<>((int) pointCount);
    Delta dx = new Delta();
    Delta dy = new Delta();
    for (long i = 0; i < pointCount; i++) {
      points.add(readDeltaPoint(cursor, definition, dx, dy));
    }
    if (hasZ) {
      Delta dz = new Delta();
      for (int i = 0; i < points.size(); i++) {
        dz.value += cursor.varIntDelta();
        FileGdbPoint p = points.get(i);
        points.set(
            i,
            new FileGdbPoint(
                p.x(), p.y(), dz.value / sanitizedZScale(definition) + zOrigin(definition), p.m()));
      }
    }
    if (hasM && cursor.remaining() >= pointCount) {
      Delta dm = new Delta();
      for (int i = 0; i < points.size(); i++) {
        dm.value += cursor.varIntDelta();
        FileGdbPoint p = points.get(i);
        points.set(
            i,
            new FileGdbPoint(
                p.x(),
                p.y(),
                p.z(),
                dm.value / sanitizedMScale(definition) + mOrigin(definition)));
      }
    }
    return new FileGdbMultiPoint(points);
  }

  private static List<FileGdbPart> readParts(
      Cursor cursor,
      GeometryFieldDefinition definition,
      boolean hasZ,
      boolean hasM,
      boolean hasCurveDescription,
      boolean isMultiPatch) {
    long pointCount = cursor.varUInt32();
    if (pointCount == 0) {
      return List.of();
    }
    if (isMultiPatch) {
      cursor.skipVarUInt(1);
    }
    long partCount = cursor.varUInt32();
    long curveCount = hasCurveDescription ? cursor.varUInt32() : 0;
    if (partCount == 0) {
      return List.of();
    }
    cursor.skipVarUInt(4);

    int[] pointsPerPart = new int[(int) partCount];
    long sum = 0;
    for (int i = 0; i < partCount - 1; i++) {
      long count = cursor.varUInt32();
      pointsPerPart[i] = (int) count;
      sum += count;
    }
    pointsPerPart[(int) partCount - 1] = (int) (pointCount - sum);

    if (curveCount > 0) {
      throw new IllegalArgumentException(
          "Curved file geodatabase segments are not supported yet (curve count "
              + curveCount
              + ")");
    }

    List<FileGdbPart> parts = new ArrayList<>(pointsPerPart.length);
    Delta dx = new Delta();
    Delta dy = new Delta();
    for (int count : pointsPerPart) {
      List<FileGdbPoint> points = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        points.add(readDeltaPoint(cursor, definition, dx, dy));
      }
      parts.add(new FileGdbPart(points));
    }

    if (hasZ) {
      Delta dz = new Delta();
      for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
        FileGdbPart part = parts.get(partIndex);
        List<FileGdbPoint> points = new ArrayList<>(part.points().size());
        for (FileGdbPoint p : part.points()) {
          dz.value += cursor.varIntDelta();
          points.add(
              new FileGdbPoint(
                  p.x(),
                  p.y(),
                  dz.value / sanitizedZScale(definition) + zOrigin(definition),
                  p.m()));
        }
        parts.set(partIndex, new FileGdbPart(points));
      }
    }

    if (hasM) {
      Delta dm = new Delta();
      for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
        FileGdbPart part = parts.get(partIndex);
        if (cursor.remaining() < part.points().size()) {
          break;
        }
        List<FileGdbPoint> points = new ArrayList<>(part.points().size());
        for (FileGdbPoint p : part.points()) {
          dm.value += cursor.varIntDelta();
          points.add(
              new FileGdbPoint(
                  p.x(),
                  p.y(),
                  p.z(),
                  dm.value / sanitizedMScale(definition) + mOrigin(definition)));
        }
        parts.set(partIndex, new FileGdbPart(points));
      }
    }
    return parts;
  }

  private static FileGdbPoint readDeltaPoint(
      Cursor cursor, GeometryFieldDefinition definition, Delta dx, Delta dy) {
    CoordinatePrecision precision = definition.precision();
    dx.value += cursor.varIntDelta();
    dy.value += cursor.varIntDelta();
    return new FileGdbPoint(
        dx.value / precision.xyScale() + precision.xOrigin(),
        dy.value / precision.xyScale() + precision.yOrigin());
  }

  private static double sanitizedZScale(GeometryFieldDefinition definition) {
    return CoordinatePrecision.sanitizeScale(definition.precision().zScale());
  }

  private static double sanitizedMScale(GeometryFieldDefinition definition) {
    return CoordinatePrecision.sanitizeScale(definition.precision().mScale());
  }

  private static double zOrigin(GeometryFieldDefinition definition) {
    return definition.precision().zOrigin();
  }

  private static double mOrigin(GeometryFieldDefinition definition) {
    return definition.precision().mOrigin();
  }

  private static final class Delta {
    long value;
  }

  /** Little endian byte cursor with the file geodatabase varint encodings. */
  private static final class Cursor {
    private final byte[] data;
    private final int limit;
    private int position;

    Cursor(byte[] data) {
      this.data = data;
      this.limit = data.length;
    }

    int remaining() {
      return limit - position;
    }

    private int u8() {
      if (position >= limit) {
        throw new IllegalArgumentException("Unexpected end of geometry buffer");
      }
      return data[position++] & 0xFF;
    }

    long varUInt32() {
      int b = u8();
      long value = b & 0x7FL;
      int shift = 7;
      while ((b & 0x80) != 0) {
        b = u8();
        value |= (long) (b & 0x7F) << shift;
        shift += 7;
        if (shift > 35) {
          throw new IllegalArgumentException("Invalid varuint32 in geometry buffer");
        }
      }
      return value;
    }

    long varUInt64() {
      int b = u8();
      long value = b & 0x7FL;
      int shift = 7;
      while ((b & 0x80) != 0) {
        b = u8();
        value |= (long) (b & 0x7F) << shift;
        shift += 7;
        if (shift > 70) {
          throw new IllegalArgumentException("Invalid varuint64 in geometry buffer");
        }
      }
      return value;
    }

    void skipVarUInt(int count) {
      for (int i = 0; i < count; i++) {
        while ((u8() & 0x80) != 0) {
          // consume continuation bytes
        }
      }
    }

    /** Signed varint used for the delta encoded coordinate arrays. */
    long varIntDelta() {
      int b = u8();
      long value = b & 0x3FL;
      boolean negative = (b & 0x40) != 0;
      if ((b & 0x80) == 0) {
        return negative ? -value : value;
      }
      int shift = 6;
      while (true) {
        b = u8();
        value |= (long) (b & 0x7F) << shift;
        if ((b & 0x80) == 0) {
          break;
        }
        shift += 7;
        if (shift >= 70) {
          break;
        }
      }
      return negative ? -value : value;
    }
  }
}
