package ch.so.agi.filegdb.geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Decodes Esri shape buffers as stored in file geodatabase geometry fields.
 *
 * <p>Ported from GDAL OpenFileGDB ({@code filegdbtable.cpp}, {@code
 * FileGDBOGRGeometryConverterImpl::GetAsGeometry}). The shape type constants match {@code
 * ogrpgeogeometry.h}, which is the header the GDAL driver compiles against. This codec
 * intentionally has no JTS or Hop dependency.
 *
 * <p>Curved segments are decoded without linearization. The fgdb table geometry type of a shape
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
        return new FileGdbPolyline(readParts(cursor, definition, hasZ, hasM, hasCurves, false));

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
        return new FileGdbPolygon(readParts(cursor, definition, hasZ, hasM, hasCurves, false));

      case SHPT_MULTIPATCH:
      case SHPT_MULTIPATCHM:
      case SHPT_GENERALMULTIPATCH:
        throw new IllegalArgumentException("Multipatch geometries are not supported yet");

      default:
        throw new IllegalArgumentException(
            "Unsupported file geodatabase geometry type: " + shapeType);
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
      return new FileGdbMultiPoint(Collections.<FileGdbPoint>emptyList());
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
                p.x(), p.y(), p.z(), dm.value / sanitizedMScale(definition) + mOrigin(definition)));
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
      return Collections.emptyList();
    }
    if (isMultiPatch) {
      cursor.skipVarUInt(1);
    }
    long partCount = cursor.varUInt32();
    long curveCount = hasCurveDescription ? cursor.varUInt32() : 0;
    if (partCount == 0) {
      return Collections.emptyList();
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
    if (curveCount > 0) {
      parts = attachCurves(parts, cursor, curveCount);
    }
    return parts;
  }

  private static List<FileGdbPart> attachCurves(
      List<FileGdbPart> parts, Cursor cursor, long curveCount) {
    int[] starts = new int[parts.size()];
    int offset = 0;
    for (int i = 0; i < parts.size(); i++) {
      starts[i] = offset;
      offset += parts.get(i).points().size();
    }
    List<List<FileGdbSegment>> perPart = new ArrayList<>(parts.size());
    for (int i = 0; i < parts.size(); i++) {
      perPart.add(new ArrayList<>());
    }
    for (long curve = 0; curve < curveCount; curve++) {
      int startIndex = (int) cursor.varUInt32();
      int curveType = cursor.u8();
      FileGdbSegment segment;
      switch (curveType) {
        case 1:
          {
            double value1 = cursor.f64();
            double value2 = cursor.f64();
            long bits = cursor.u32();
            boolean interiorPoint = (bits & 0x80) != 0 && (bits & 0x20) == 0;
            boolean centerPoint = (bits & 0x1) == 0 && (bits & 0x20) == 0 && (bits & 0x40) == 0;
            if (interiorPoint) {
              segment = new CircularArcSegment(startIndex, value1, value2, false, false);
            } else if (centerPoint) {
              segment = new CircularArcSegment(startIndex, value1, value2, true, (bits & 0x8) != 0);
            } else {
              segment = null;
            }
            break;
          }
        case 4:
          segment =
              new BezierSegment(
                  startIndex, cursor.f64(), cursor.f64(), cursor.f64(), cursor.f64());
          break;
        case 5:
          {
            double centerX = cursor.f64();
            double centerY = cursor.f64();
            double rotation = cursor.f64();
            double semiMajor = cursor.f64();
            double ratio = cursor.f64();
            long bits = cursor.u32();
            if ((bits & 0x200) == 0 && (bits & 0x400) == 0) {
              segment =
                  new EllipseSegment(
                      startIndex,
                      centerX,
                      centerY,
                      Math.toDegrees(rotation),
                      semiMajor,
                      ratio,
                      (bits & 0x1000) != 0,
                      (bits & 0x2000) != 0);
            } else {
              segment = null;
            }
            break;
          }
        default:
          throw new IllegalArgumentException("Unsupported curve type: " + curveType);
      }
      if (segment == null) {
        continue;
      }
      int partIndex = partForIndex(starts, startIndex);
      if (partIndex >= 0
          && startIndex - starts[partIndex] < parts.get(partIndex).points().size() - 1) {
        perPart.get(partIndex).add(shift(segment, starts[partIndex]));
      }
    }
    List<FileGdbPart> result = new ArrayList<>(parts.size());
    for (int i = 0; i < parts.size(); i++) {
      FileGdbPart part = parts.get(i);
      List<FileGdbSegment> segments = perPart.get(i);
      result.add(segments.isEmpty() ? part : new FileGdbPart(part.points(), segments));
    }
    return result;
  }

  private static int partForIndex(int[] starts, int index) {
    for (int i = starts.length - 1; i >= 0; i--) {
      if (index >= starts[i]) {
        return i;
      }
    }
    return -1;
  }

  private static FileGdbSegment shift(FileGdbSegment segment, int offset) {
    int index = segment.startPointIndex() - offset;
    if (segment instanceof CircularArcSegment) {
      CircularArcSegment arc = (CircularArcSegment) segment;
      return new CircularArcSegment(
          index, arc.interiorX(), arc.interiorY(), arc.byCenter(), arc.counterClockwise());
    }
    if (segment instanceof BezierSegment) {
      BezierSegment bezier = (BezierSegment) segment;
      return new BezierSegment(
          index,
          bezier.controlX1(),
          bezier.controlY1(),
          bezier.controlX2(),
          bezier.controlY2());
    }
    if (segment instanceof EllipseSegment) {
      EllipseSegment ellipse = (EllipseSegment) segment;
      return new EllipseSegment(
          index,
          ellipse.centerX(),
          ellipse.centerY(),
          ellipse.rotationDegrees(),
          ellipse.semiMajor(),
          ellipse.minorMajorRatio(),
          ellipse.minor(),
          ellipse.complete());
    }
    throw new IncompatibleClassChangeError();
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

    long u32() {
      if (position + 4 > limit) {
        throw new IllegalArgumentException("Unexpected end of geometry buffer");
      }
      long value =
          (data[position] & 0xFFL)
              | ((data[position + 1] & 0xFFL) << 8)
              | ((data[position + 2] & 0xFFL) << 16)
              | ((data[position + 3] & 0xFFL) << 24);
      position += 4;
      return value;
    }

    double f64() {
      if (position + 8 > limit) {
        throw new IllegalArgumentException("Unexpected end of geometry buffer");
      }
      long value = 0;
      for (int i = 0; i < 8; i++) {
        value |= (data[position + i] & 0xFFL) << (8 * i);
      }
      position += 8;
      return Double.longBitsToDouble(value);
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

  /**
   * Encodes a geometry as an Esri shape buffer.
   *
   * <p>The Z and M flags of the geometry field definition decide the shape type and the presence of
   * the Z and M arrays, mirroring GDAL's {@code EncodeGeometry()}.
   */
  public static byte[] encode(FileGdbGeometry geometry, GeometryFieldDefinition definition) {
    Buffer buffer = new Buffer();
    CoordinatePrecision precision = definition.precision();
    boolean hasZ = definition.hasZ();
    boolean hasM = definition.hasM();

    if (geometry instanceof FileGdbPoint) {
      FileGdbPoint point = (FileGdbPoint) geometry;
      int type;
      if (hasZ) {
        type = hasM ? SHPT_POINTZM : SHPT_POINTZ;
      } else {
        type = hasM ? SHPT_POINTM : SHPT_POINT;
      }
      buffer.u8(type);
      buffer.varUInt64(
          CoordinatePrecision.gridCoordinate(point.x(), precision.xOrigin(), precision.xyScale())
              + 1);
      buffer.varUInt64(
          CoordinatePrecision.gridCoordinate(point.y(), precision.yOrigin(), precision.xyScale())
              + 1);
      if (hasZ) {
        buffer.varUInt64(
            encodeUnsigned(
                requiredOrdinate(point.z(), "Z"),
                precision.zOrigin(),
                sanitizeScale(precision.zScale())));
      }
      if (hasM) {
        buffer.varUInt64(
            encodeUnsigned(
                requiredOrdinate(point.m(), "M"),
                precision.mOrigin(),
                sanitizeScale(precision.mScale())));
      }
      return buffer.toByteArray();
    }

    if (geometry instanceof FileGdbMultiPoint) {
      FileGdbMultiPoint multiPoint = (FileGdbMultiPoint) geometry;
      int type;
      if (hasZ) {
        type = hasM ? SHPT_MULTIPOINTZM : SHPT_MULTIPOINTZ;
      } else {
        type = hasM ? SHPT_MULTIPOINTM : SHPT_MULTIPOINT;
      }
      buffer.u8(type);
      buffer.varUInt32(multiPoint.points().size());
      if (!multiPoint.points().isEmpty()) {
        writeEnvelope(buffer, multiPoint.points(), precision);
        writeDeltaArray(buffer, multiPoint.points(), precision, hasZ, hasM);
      }
      return buffer.toByteArray();
    }

    boolean polyline = geometry instanceof FileGdbPolyline;
    List<FileGdbPart> parts =
        polyline ? ((FileGdbPolyline) geometry).parts() : ((FileGdbPolygon) geometry).parts();
    int curveCount = 0;
    for (FileGdbPart part : parts) {
      java.util.Set<Integer> starts = new java.util.HashSet<>();
      for (FileGdbSegment segment : part.segments()) {
        if (!(segment instanceof CircularArcSegment))
          throw new UnsupportedOperationException("Only circular arcs can be written");
        int i = segment.startPointIndex();
        if (i < 0 || i >= part.points().size() - 1 || !starts.add(i))
          throw new IllegalArgumentException("Invalid curve segment index: " + i);
        curveCount++;
      }
    }
    int type;
    if (polyline) {
      type = hasZ ? (hasM ? SHPT_ARCZM : SHPT_ARCZ) : (hasM ? SHPT_ARCM : SHPT_ARC);
    } else {
      type = hasZ ? (hasM ? SHPT_POLYGONZM : SHPT_POLYGONZ) : (hasM ? SHPT_POLYGONM : SHPT_POLYGON);
    }
    buffer.varUInt32(
        curveCount == 0
            ? type
            : (polyline ? SHPT_GENERALPOLYLINE : SHPT_GENERALPOLYGON)
                | EXT_SHAPE_CURVE_FLAG
                | (hasZ ? EXT_SHAPE_Z_FLAG : 0)
                | (hasM ? EXT_SHAPE_M_FLAG : 0));

    List<FileGdbPoint> points = new ArrayList<>();
    for (FileGdbPart part : parts) {
      points.addAll(part.points());
    }
    buffer.varUInt32(points.size());
    if (points.isEmpty()) {
      return buffer.toByteArray();
    }
    buffer.varUInt32(parts.size());
    if (curveCount > 0) buffer.varUInt32(curveCount);
    // Bounds must describe the coordinates that will actually be stored.
    List<FileGdbPart> storedParts = new ArrayList<>();
    for (FileGdbPart part : parts)
      storedParts.add(
          new FileGdbPart(
              part.points().stream()
                  .map(
                      p ->
                          new FileGdbPoint(
                              precision.quantizeX(p.x()), precision.quantizeY(p.y()), p.z(), p.m()))
                  .collect(Collectors.toList()),
              part.segments()));
    Envelope bounds =
        GeometryBounds.of(
            polyline ? new FileGdbPolyline(storedParts) : new FileGdbPolygon(storedParts));
    writeEnvelope(
        buffer,
        Arrays.asList(
            new FileGdbPoint(bounds.xMin(), bounds.yMin()),
            new FileGdbPoint(bounds.xMax(), bounds.yMax())),
        precision);
    for (int i = 0; i < parts.size() - 1; i++) {
      buffer.varUInt32(parts.get(i).points().size());
    }
    writeDeltaArray(buffer, points, precision, hasZ, hasM);
    int offset = 0;
    for (FileGdbPart part : parts) {
      for (FileGdbSegment segment :
          part.segments().stream()
              .sorted(java.util.Comparator.comparingInt(FileGdbSegment::startPointIndex))
              .collect(Collectors.toList())) {
        CircularArcSegment arc = (CircularArcSegment) segment;
        buffer.varUInt32(offset + arc.startPointIndex());
        buffer.u8(1);
        buffer.f64(arc.interiorX());
        buffer.f64(arc.interiorY());
        buffer.u32(arc.byCenter() ? (arc.counterClockwise() ? 8 : 0) : 128);
      }
      offset += part.points().size();
    }
    return buffer.toByteArray();
  }

  private static void writeEnvelope(
      Buffer buffer, List<FileGdbPoint> points, CoordinatePrecision precision) {
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    for (FileGdbPoint point : points) {
      minX = Math.min(minX, point.x());
      minY = Math.min(minY, point.y());
      maxX = Math.max(maxX, point.x());
      maxY = Math.max(maxY, point.y());
    }
    buffer.varUInt64(encodeUnsigned(minX, precision.xOrigin(), precision.xyScale()));
    buffer.varUInt64(encodeUnsigned(minY, precision.yOrigin(), precision.xyScale()));
    buffer.varUInt64(encodeUnsigned(maxX - minX, 0, precision.xyScale()));
    buffer.varUInt64(encodeUnsigned(maxY - minY, 0, precision.xyScale()));
  }

  private static void writeDeltaArray(
      Buffer buffer,
      List<FileGdbPoint> points,
      CoordinatePrecision precision,
      boolean hasZ,
      boolean hasM) {
    long lastX = 0;
    long lastY = 0;
    for (FileGdbPoint point : points) {
      long x =
          CoordinatePrecision.gridCoordinate(point.x(), precision.xOrigin(), precision.xyScale());
      long y =
          CoordinatePrecision.gridCoordinate(point.y(), precision.yOrigin(), precision.xyScale());
      buffer.varInt(x - lastX);
      buffer.varInt(y - lastY);
      lastX = x;
      lastY = y;
    }
    if (hasZ) {
      double zScale = sanitizeScale(precision.zScale());
      long lastZ = 0;
      for (FileGdbPoint point : points) {
        long z = Math.round((requiredOrdinate(point.z(), "Z") - precision.zOrigin()) * zScale);
        buffer.varInt(z - lastZ);
        lastZ = z;
      }
    }
    if (hasM) {
      double mScale = sanitizeScale(precision.mScale());
      long lastM = 0;
      for (FileGdbPoint point : points) {
        long m = Math.round((requiredOrdinate(point.m(), "M") - precision.mOrigin()) * mScale);
        buffer.varInt(m - lastM);
        lastM = m;
      }
    }
  }

  private static double requiredOrdinate(Double value, String name) {
    if (value == null || !Double.isFinite(value))
      throw new IllegalArgumentException(
          "Missing or non-finite " + name + " ordinate in dimensioned geometry");
    return value;
  }

  private static long encodeUnsigned(double value, double origin, double scale) {
    if (Double.isNaN(value)) {
      return 0;
    }
    double encoded = (value - origin) * scale + 1;
    if (!(encoded >= 0) || encoded > Long.MAX_VALUE) {
      throw new IllegalArgumentException("Coordinate out of range: " + value);
    }
    return Math.round(encoded);
  }

  private static double sanitizeScale(double scale) {
    return scale == 0 ? 1 : scale;
  }

  /** Growable little endian buffer with the file geodatabase varint encodings. */
  private static final class Buffer {
    private byte[] data = new byte[256];
    private int size;

    void u8(int value) {
      ensure(1);
      data[size++] = (byte) value;
    }

    void u32(long value) {
      for (int i = 0; i < 4; i++) u8((int) (value >>> (8 * i)));
    }

    void f64(double value) {
      long bits = Double.doubleToLongBits(value);
      for (int i = 0; i < 8; i++) u8((int) (bits >>> (8 * i)));
    }

    void varUInt32(long value) {
      varUInt(value);
    }

    void varUInt64(long value) {
      varUInt(value);
    }

    private void varUInt(long value) {
      if (value < 0) {
        throw new IllegalArgumentException("Negative unsigned varint: " + value);
      }
      while (true) {
        if (value >= 0x80) {
          u8((int) (0x80 | (value & 0x7F)));
          value >>>= 7;
        } else {
          u8((int) value);
          return;
        }
      }
    }

    void varInt(long value) {
      boolean negative = value < 0;
      long magnitude = Math.abs(value);
      if (magnitude >= 0x40) {
        int first = (int) (magnitude & 0x3F) | (negative ? 0x40 : 0) | 0x80;
        u8(first);
        varUInt(magnitude >>> 6);
      } else {
        u8((int) magnitude | (negative ? 0x40 : 0));
      }
    }

    private void ensure(int additional) {
      if (size + additional > data.length) {
        byte[] grown = new byte[Math.max(data.length * 2, size + additional)];
        System.arraycopy(data, 0, grown, 0, size);
        data = grown;
      }
    }

    byte[] toByteArray() {
      byte[] result = new byte[size];
      System.arraycopy(data, 0, result, 0, size);
      return result;
    }
  }
}
