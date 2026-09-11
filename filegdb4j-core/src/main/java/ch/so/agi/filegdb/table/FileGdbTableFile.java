package ch.so.agi.filegdb.table;

import ch.so.agi.filegdb.GdbException;
import ch.so.agi.filegdb.geometry.CoordinatePrecision;
import ch.so.agi.filegdb.geometry.Envelope;
import ch.so.agi.filegdb.geometry.FileGdbGeometry;
import ch.so.agi.filegdb.geometry.GeometryCodec;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import ch.so.agi.filegdb.geometry.GeometryKind;
import ch.so.agi.filegdb.io.ByteCursor;
import ch.so.agi.filegdb.io.GdbPaths;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read access to a {@code .gdbtable} file and its {@code .gdbtablx} row index.
 *
 * <p>Ported from GDAL OpenFileGDB ({@code filegdbtable.cpp}). The class exposes
 * the physical table; feature classes and tables are layered on top by the
 * catalog reader.
 *
 * <p>Rows are decoded sequentially in field order. Random access to a single
 * field of a row is not offered because the row format has no random access.
 */
public final class FileGdbTableFile implements AutoCloseable {

  private static final int FLAG_NULLABLE = 1;
  private static final int FLAG_REQUIRED = 1 << 1;
  private static final int FLAG_EDITABLE = 1 << 2;

  private static final int MAX_FIELD_DESCRIPTOR_LENGTH = 10 * 1024 * 1024;

  private final Path path;
  private final FileChannel table;
  private final FileChannel tableX;
  private final int version;
  private final long validRecordCount;
  private final long headerBufferMaxSize;
  private final long offsetFieldDesc;
  private final int fieldDescLength;
  private final boolean stringsUtf8;
  private final GeometryKind geometryKind;
  private final boolean hasZ;
  private final boolean hasM;
  private final List<FileGdbField> fields;
  private final FileGdbGeomField geomField;
  private final int objectIdFieldIndex;
  private final int geomFieldIndex;
  private final int nullableFieldCount;
  private final int nullMaskSize;
  private final long totalRecordCount;

  private final int tableXOffsetSize;
  private final byte[] blockMap;
  private final boolean reliableObjectIds;

  private FileGdbTableFile(Path path, FileChannel table) throws IOException {
    this.path = path;
    this.table = table;

    byte[] header = readFully(table, 0, 40);
    int fileVersion = (int) u32(header, 0);
    if (fileVersion != 3 && fileVersion != 4) {
      throw new GdbException(
          "Unsupported file geodatabase table version "
              + fileVersion
              + " in "
              + path.getFileName());
    }
    this.version = fileVersion;

    long validRecords = fileVersion == 3 ? i32(header, 4) : i64(header, 16);
    if (validRecords < 0) {
      throw new GdbException("Negative record count in " + path.getFileName());
    }
    this.headerBufferMaxSize = u32(header, 8);
    this.offsetFieldDesc = u64(header, 32);

    // .gdbtablx row index. GDAL can guess feature locations when the index is
    // missing; this reader requires the index.
    FileChannel tableXChannel = null;
    long totalRecords = 0;
    int offsetSize = 0;
    byte[] blocks = null;
    boolean reliable = true;
    if (validRecords > 0) {
      Path tableXPath = GdbPaths.companion(path, "gdbtablx");
      if (!Files.isRegularFile(tableXPath)) {
        throw new GdbException(
            "Missing row index "
                + tableXPath.getFileName()
                + "; guessing feature locations is not supported");
      }
      tableXChannel = FileChannel.open(tableXPath, StandardOpenOption.READ);
      TableXHeader tableXHeader = readTableXHeader(tableXChannel, fileVersion);
      totalRecords = tableXHeader.totalRecordCount();
      offsetSize = tableXHeader.offsetSize();
      blocks = tableXHeader.blockMap();
      reliable = tableXHeader.reliable();
      if (validRecords > totalRecords) {
        validRecords = totalRecords;
      }
    }
    this.tableX = tableXChannel;
    this.totalRecordCount = totalRecords;
    this.tableXOffsetSize = offsetSize;
    this.blockMap = blocks;
    this.reliableObjectIds = reliable;
    this.validRecordCount = validRecords;

    // Field descriptor section.
    byte[] descriptorHeader = readFully(table, offsetFieldDesc, 14);
    int descriptorLength = (int) u32(descriptorHeader, 0);
    this.fieldDescLength = descriptorLength;
    if (descriptorLength < 10 || descriptorLength > MAX_FIELD_DESCRIPTOR_LENGTH) {
      throw new GdbException(
          "Invalid field descriptor length " + descriptorLength + " in " + path.getFileName());
    }

    int tableGeometryType = descriptorHeader[8] & 0xFF;
    this.geometryKind = GeometryKind.fromId(tableGeometryType);
    this.stringsUtf8 = (descriptorHeader[9] & 0x1) != 0;
    int geometryFlags = descriptorHeader[11] & 0xFF;
    this.hasM = (geometryFlags & (1 << 6)) != 0;
    this.hasZ = (geometryFlags & (1 << 7)) != 0;
    int fieldCount = u16(descriptorHeader, 12);

    byte[] descriptor =
        readFully(table, offsetFieldDesc + 14, Math.max(0, descriptorLength - 10));
    FieldParseResult parsed = parseFields(descriptor, fieldCount);
    this.fields = parsed.fields();
    this.geomField = parsed.geomField();
    this.objectIdFieldIndex = parsed.objectIdFieldIndex();
    this.geomFieldIndex = parsed.geomFieldIndex();
    this.nullableFieldCount = parsed.nullableFieldCount();
    this.nullMaskSize = (nullableFieldCount + 7) / 8;
  }

  public static FileGdbTableFile open(Path path) throws IOException {
    FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
    try {
      return new FileGdbTableFile(path, channel);
    } catch (Exception e) {
      channel.close();
      throw e;
    }
  }

  public Path path() {
    return path;
  }

  public int version() {
    return version;
  }

  public long validRecordCount() {
    return validRecordCount;
  }

  public long totalRecordCount() {
    return totalRecordCount;
  }

  public long headerBufferMaxSize() {
    return headerBufferMaxSize;
  }

  public int fieldDescriptorLength() {
    return fieldDescLength;
  }

  public boolean stringsUtf8() {
    return stringsUtf8;
  }

  public GeometryKind geometryKind() {
    return geometryKind;
  }

  public boolean hasZ() {
    return hasZ;
  }

  public boolean hasM() {
    return hasM;
  }

  public List<FileGdbField> fields() {
    return fields;
  }

  /** Returns the geometry field or {@code null} for plain tables. */
  public FileGdbGeomField geomField() {
    return geomField;
  }

  public int geomFieldIndex() {
    return geomFieldIndex;
  }

  public int objectIdFieldIndex() {
    return objectIdFieldIndex;
  }

  public boolean reliableObjectIds() {
    return reliableObjectIds;
  }

  /**
   * Reads a row and decodes all field values.
   *
   * <p>Values use the following Java types: {@code Long} for the object id and
   * integer fields, {@code Double} for real fields, {@code String} for string
   * and XML fields, {@code LocalDateTime}/{@code LocalDate}/{@code LocalTime}/
   * {@code OffsetDateTime} for date and time fields, {@code UUID} for GUID
   * fields, {@code byte[]} for binary fields and {@link FileGdbGeometry} for
   * geometry fields.
   *
   * @return the decoded values or {@code null} if the row is deleted or empty
   */
  public Object[] readRow(long rowIndex) throws IOException {
    if (rowIndex < 0 || rowIndex >= totalRecordCount) {
      throw new IndexOutOfBoundsException(
          "Row " + rowIndex + " is outside of 0.." + (totalRecordCount - 1));
    }
    long offset = rowOffset(rowIndex);
    if (offset == 0) {
      return null;
    }
    long blobLength = u32(readFully(table, offset, 4), 0);
    if (blobLength == 0) {
      return null;
    }
    if (blobLength > Integer.MAX_VALUE - 8) {
      throw new GdbException("Invalid row blob length " + blobLength + " in " + path.getFileName());
    }
    byte[] blob = readFully(table, offset + 4, (int) blobLength);
    return decodeRow(rowIndex, blob);
  }

  @Override
  public void close() throws IOException {
    try {
      if (tableX != null) {
        tableX.close();
      }
    } finally {
      table.close();
    }
  }

  private long rowOffset(long rowIndex) throws IOException {
    if (tableX == null) {
      throw new GdbException("Table " + path.getFileName() + " has no row index");
    }
    long correctedRow = rowIndex;
    if (blockMap != null && blockMap.length > 0) {
      int block = (int) (rowIndex / 1024);
      if ((block >> 3) >= blockMap.length || !testBit(blockMap, block)) {
        return 0;
      }
      long blocksBefore = countBits(blockMap, block);
      correctedRow = blocksBefore * 1024 + (rowIndex % 1024);
    }
    long position = 16 + (long) tableXOffsetSize * correctedRow;
    byte[] raw = readFully(tableX, position, tableXOffsetSize);
    long value = 0;
    for (int i = 0; i < tableXOffsetSize; i++) {
      value |= (raw[i] & 0xFFL) << (8 * i);
    }
    return value;
  }

  private Object[] decodeRow(long rowIndex, byte[] blob) {
    Object[] values = new Object[fields.size()];
    ByteCursor cursor = new ByteCursor(blob, nullMaskSize, blob.length - nullMaskSize);
    int bitIndex = 0;
    for (int i = 0; i < fields.size(); i++) {
      FileGdbField field = fields.get(i);
      if (field.nullable()) {
        boolean isNull = testBit(blob, bitIndex);
        bitIndex++;
        if (isNull) {
          values[i] = null;
          continue;
        }
      }
      values[i] = readValue(cursor, field, rowIndex);
    }
    return values;
  }

  private Object readValue(ByteCursor cursor, FileGdbField field, long rowIndex) {
    switch (field.type()) {
      case OBJECTID:
        return rowIndex + 1;

      case STRING:
        {
          int length = (int) cursor.varUInt32();
          return stringsUtf8 ? cursor.utf8(length) : cursor.utf16(length / 2);
        }

      case XML:
        {
          int length = (int) cursor.varUInt32();
          return cursor.utf8(length);
        }

      case INT16:
        return (long) cursor.i16();

      case INT32:
        return (long) cursor.i32();

      case INT64:
        return cursor.i64();

      case FLOAT32:
        return (double) cursor.f32();

      case FLOAT64:
        return cursor.f64();

      case DATETIME:
        return decodeDateTime(cursor.f64(), field.highPrecision());

      case DATE:
        return decodeDateTime(cursor.f64(), field.highPrecision()).toLocalDate();

      case TIME:
        return decodeTime(cursor.f64());

      case DATETIME_WITH_OFFSET:
        {
          double days = cursor.f64();
          int offsetMinutes = cursor.i16();
          return OffsetDateTime.of(
              decodeDateTime(days, true), ZoneOffset.ofTotalSeconds(offsetMinutes * 60));
        }

      case GUID:
      case GLOBALID:
        return decodeUuid(cursor.bytes(16));

      case BINARY:
        {
          int length = (int) cursor.varUInt32();
          return cursor.bytes(length);
        }

      case GEOMETRY:
        {
          int length = (int) cursor.varUInt32();
          byte[] shapeBuffer = cursor.bytes(length);
          if (geomField == null) {
            throw new GdbException("Geometry value without a geometry field definition");
          }
          try {
            return GeometryCodec.decode(shapeBuffer, geomField.geometry());
          } catch (IllegalArgumentException e) {
            throw new GdbException(
                "Cannot decode geometry in "
                    + path.getFileName()
                    + " at row "
                    + (rowIndex + 1)
                    + ": "
                    + e.getMessage(),
                e);
          }
        }

      case RASTER:
        throw new GdbException("Raster fields are not supported (" + field.name() + ")");

      default:
        throw new GdbException("Unsupported field type " + field.type());
    }
  }

  private FieldParseResult parseFields(byte[] descriptor, int expectedFieldCount) {
    ByteCursor cursor = new ByteCursor(descriptor);
    List<FileGdbField> parsedFields = new ArrayList<>(expectedFieldCount);
    FileGdbGeomField parsedGeomField = null;
    int parsedObjectIdIndex = -1;
    int parsedGeomIndex = -1;
    int parsedNullableCount = 0;

    for (int i = 0; i < expectedFieldCount; i++) {
      String name = readShortString(cursor);
      String alias = readShortString(cursor);
      FileGdbFieldType type = FileGdbFieldType.fromId(cursor.u8());

      if (type != FileGdbFieldType.GEOMETRY && type != FileGdbFieldType.RASTER) {
        int flags;
        int maxWidth = 0;
        int defaultValueLength = 0;
        switch (type) {
          case STRING -> {
            maxWidth = cursor.i32();
            flags = cursor.u8();
            defaultValueLength = (int) cursor.varUInt32();
          }
          case OBJECTID, BINARY, GUID, GLOBALID, XML -> {
            cursor.u8();
            flags = cursor.u8();
          }
          default -> {
            cursor.u8();
            flags = cursor.u8();
            defaultValueLength = cursor.u8();
          }
        }
        if ((flags & FLAG_EDITABLE) != 0 && defaultValueLength > 0) {
          cursor.skip(defaultValueLength);
        }
        if (type == FileGdbFieldType.OBJECTID) {
          if (flags != FLAG_REQUIRED) {
            throw new GdbException("Object id field with unexpected flags: " + flags);
          }
          if (parsedObjectIdIndex >= 0) {
            throw new GdbException("Multiple object id fields");
          }
          parsedObjectIdIndex = i;
        }
        boolean nullable = (flags & FLAG_NULLABLE) != 0;
        if (nullable) {
          parsedNullableCount++;
        }
        parsedFields.add(
            new FileGdbField(
                name,
                alias,
                type,
                nullable,
                (flags & FLAG_REQUIRED) != 0,
                (flags & FLAG_EDITABLE) != 0,
                maxWidth,
                false));
        continue;
      }

      if (type == FileGdbFieldType.RASTER) {
        throw new GdbException("Raster fields are not supported (" + name + ")");
      }
      if (parsedGeomIndex >= 0) {
        throw new GdbException("Multiple geometry fields are not supported");
      }
      parsedGeomIndex = i;

      cursor.u8();
      int flags = cursor.u8();
      boolean nullable = (flags & 1) != 0;

      int wktLength = cursor.u16();
      String wkt = cursor.utf16(wktLength / 2);
      int geometryFlags = cursor.u8();
      boolean hasMOriginScaleTolerance = (geometryFlags & 2) != 0;
      boolean hasZOriginScaleTolerance = (geometryFlags & 4) != 0;

      double xOrigin = 0;
      double yOrigin = 0;
      double xyScale = 0;
      double xyTolerance = 0;
      double mOrigin = 0;
      double mScale = 0;
      double mTolerance = 0;
      double zOrigin = 0;
      double zScale = 0;
      double zTolerance = 0;

      xOrigin = cursor.f64();
      yOrigin = cursor.f64();
      xyScale = cursor.f64();
      if (xyScale == 0) {
        throw new GdbException("Geometry field " + name + " has an invalid XY scale of zero");
      }
      if (hasMOriginScaleTolerance) {
        mOrigin = cursor.f64();
        mScale = cursor.f64();
      }
      if (hasZOriginScaleTolerance) {
        zOrigin = cursor.f64();
        zScale = cursor.f64();
      }
      xyTolerance = cursor.f64();
      if (hasMOriginScaleTolerance) {
        mTolerance = cursor.f64();
      }
      if (hasZOriginScaleTolerance) {
        zTolerance = cursor.f64();
      }

      double xMin = cursor.f64();
      double yMin = cursor.f64();
      double xMax = cursor.f64();
      double yMax = cursor.f64();
      if (hasZ) {
        cursor.f64();
        cursor.f64();
      }
      if (hasM) {
        cursor.f64();
        cursor.f64();
      }
      cursor.u8();
      long gridCount = cursor.u32();
      if (gridCount == 0 || gridCount > 3) {
        throw new GdbException("Invalid spatial index grid count: " + gridCount);
      }
      List<Double> gridResolution = new ArrayList<>((int) gridCount);
      for (int g = 0; g < gridCount; g++) {
        gridResolution.add(cursor.f64());
      }

      CoordinatePrecision precision =
          new CoordinatePrecision(
              xOrigin, yOrigin, xyScale, xyTolerance, zOrigin, zScale, zTolerance, mOrigin, mScale,
              mTolerance);
      GeometryFieldDefinition definition =
          new GeometryFieldDefinition(
              name,
              alias,
              nullable,
              wkt,
              geometryKind,
              hasZ,
              hasM,
              precision,
              new Envelope(xMin, yMin, xMax, yMax),
              gridResolution);
      parsedGeomField =
          new FileGdbGeomField(name, alias, nullable, wkt, definition);
      parsedFields.add(
          new FileGdbField(
              name, alias, FileGdbFieldType.GEOMETRY, nullable, false, false, 0, false));
      if (nullable) {
        parsedNullableCount++;
      }
    }
    return new FieldParseResult(
        List.copyOf(parsedFields),
        parsedGeomField,
        parsedObjectIdIndex,
        parsedGeomIndex,
        parsedNullableCount);
  }

  private static String readShortString(ByteCursor cursor) {
    int characterCount = cursor.u8();
    return cursor.utf16(characterCount);
  }

  private static TableXHeader readTableXHeader(FileChannel tableX, int version)
      throws IOException {
    byte[] header = readFully(tableX, 0, 16);
    long headerVersion = u32(header, 0);
    if (headerVersion != version) {
      throw new GdbException(
          "Row index version " + headerVersion + " does not match table version " + version);
    }
    long blocksPresent;
    long totalRecordCount;
    int offsetSize;
    if (version == 3) {
      blocksPresent = u32(header, 4);
      totalRecordCount = i32(header, 8);
      offsetSize = (int) u32(header, 12);
    } else {
      blocksPresent = u64(header, 4);
      totalRecordCount = -1;
      offsetSize = (int) u32(header, 12);
    }
    if (blocksPresent == 0) {
      return new TableXHeader(0, offsetSize, null, true);
    }
    if (offsetSize < 4 || offsetSize > 6) {
      throw new GdbException("Invalid row index offset size: " + offsetSize);
    }
    long trailerOffset = 16 + (long) offsetSize * 1024 * blocksPresent;

    if (version == 3) {
      byte[] trailer = readFully(tableX, trailerOffset, 16);
      long bitmapWords = u32(trailer, 0);
      long bitsForBlockMap = u32(trailer, 4);
      long blocksBis = u32(trailer, 8);
      if (blocksBis != blocksPresent) {
        throw new GdbException("Inconsistent row index block count");
      }
      byte[] blockMap = null;
      if (bitmapWords != 0) {
        int sizeInBytes = (int) ((bitsForBlockMap + 7) / 8);
        blockMap = readFully(tableX, trailerOffset + 16, sizeInBytes);
        long count = countBits(blockMap, (int) bitsForBlockMap);
        if (count != blocksPresent) {
          throw new GdbException("Inconsistent row index block map");
        }
      } else if (bitsForBlockMap != blocksPresent) {
        throw new GdbException("Inconsistent row index block map size");
      }
      return new TableXHeader(totalRecordCount, offsetSize, blockMap, true);
    }

    // Version 4: total record count lives in the trailer, the block map is an
    // optional section whose layout is only partially reverse engineered.
    byte[] trailer = readFully(tableX, trailerOffset, 12);
    totalRecordCount = u64(trailer, 0);
    long bitmapSectionSize = u32(trailer, 8);
    byte[] blockMap = null;
    boolean reliable = true;
    if (bitmapSectionSize == 22 + 32768 + 52 && totalRecordCount <= 32768L * 1024 * 8) {
      byte[] section = readFully(tableX, trailerOffset + 12, (int) bitmapSectionSize);
      if (matchesVersion4BitmapHeader(section)) {
        blockMap = new byte[32768];
        System.arraycopy(section, 22, blockMap, 0, 32768);
      } else {
        reliable = false;
      }
    } else if (bitmapSectionSize != 0) {
      reliable = false;
    }
    if (!reliable) {
      totalRecordCount = 1024 * blocksPresent;
    }
    return new TableXHeader(totalRecordCount, offsetSize, blockMap, reliable);
  }

  private static boolean matchesVersion4BitmapHeader(byte[] section) {
    if (section.length < 22 + 32768 + 12) {
      return false;
    }
    return section[0] == 1
        && section[1] == 0
        && section[2] == 1
        && section[3] == 0
        && section[4] == 0
        && section[5] == 0
        && section[22 + 32768] == 1
        && section[22 + 32768 + 1] == 0
        && section[22 + 32768 + 2] == 0
        && section[22 + 32768 + 3] == 0
        && section[22 + 32768 + 4] == 0
        && section[22 + 32768 + 5] == 0
        && section[22 + 32768 + 6] == 0
        && section[22 + 32768 + 7] == 0
        && section[22 + 32768 + 8] == 0
        && section[22 + 32768 + 9] == 0
        && section[22 + 32768 + 10] == 0
        && section[22 + 32768 + 11] == 0;
  }

  private static boolean testBit(byte[] array, int bit) {
    return (array[bit >> 3] & (1 << (bit & 7))) != 0;
  }

  private static long countBits(byte[] array, int bitCount) {
    long count = 0;
    for (int i = 0; i < bitCount; i++) {
      if (testBit(array, i)) {
        count++;
      }
    }
    return count;
  }

  private static LocalDateTime decodeDateTime(double days, boolean highPrecision) {
    double seconds = (days - 25569.0) * 3600.0 * 24.0;
    if (Double.isNaN(seconds) || Double.isInfinite(seconds)) {
      throw new GdbException("Invalid date value: " + days);
    }
    if (!highPrecision) {
      seconds = Math.floor(seconds + 0.5);
    } else if (seconds % 1.0 > 1 - 1e-4) {
      seconds = Math.floor(seconds + 0.5);
    }
    long wholeSeconds = (long) Math.floor(seconds);
    long nanos = Math.round((seconds - wholeSeconds) * 1_000_000_000L);
    return LocalDateTime.ofEpochSecond(wholeSeconds, (int) nanos, ZoneOffset.UTC);
  }

  private static LocalTime decodeTime(double fractionOfDay) {
    double seconds = fractionOfDay * 3600.0 * 24.0;
    if (Double.isNaN(seconds) || seconds < 0 || seconds > 86400) {
      throw new GdbException("Invalid time value: " + fractionOfDay);
    }
    long nanos = Math.round(seconds * 1_000_000_000L);
    if (nanos >= 86_400_000_000_000L) {
      nanos = 86_399_999_999_999L;
    }
    return LocalTime.ofNanoOfDay(nanos);
  }

  private static UUID decodeUuid(byte[] bytes) {
    long mostSignificant = 0;
    mostSignificant |= (long) (bytes[3] & 0xFF) << 56;
    mostSignificant |= (long) (bytes[2] & 0xFF) << 48;
    mostSignificant |= (long) (bytes[1] & 0xFF) << 40;
    mostSignificant |= (long) (bytes[0] & 0xFF) << 32;
    mostSignificant |= (long) (bytes[5] & 0xFF) << 24;
    mostSignificant |= (long) (bytes[4] & 0xFF) << 16;
    mostSignificant |= (long) (bytes[7] & 0xFF) << 8;
    mostSignificant |= bytes[6] & 0xFFL;
    long leastSignificant = 0;
    for (int i = 8; i < 16; i++) {
      leastSignificant = (leastSignificant << 8) | (bytes[i] & 0xFFL);
    }
    return new UUID(mostSignificant, leastSignificant);
  }

  private static byte[] readFully(FileChannel channel, long position, int length)
      throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(length);
    int total = 0;
    while (total < length) {
      int read = channel.read(buffer, position + total);
      if (read < 0) {
        throw new EOFException("Unexpected end of file");
      }
      total += read;
    }
    return buffer.array();
  }

  private static long u32(byte[] bytes, int offset) {
    return (bytes[offset] & 0xFFL)
        | ((bytes[offset + 1] & 0xFFL) << 8)
        | ((bytes[offset + 2] & 0xFFL) << 16)
        | ((bytes[offset + 3] & 0xFFL) << 24);
  }

  private static int i32(byte[] bytes, int offset) {
    return (int) u32(bytes, offset);
  }

  private static long u64(byte[] bytes, int offset) {
    long value = 0;
    for (int i = 0; i < 8; i++) {
      value |= (bytes[offset + i] & 0xFFL) << (8 * i);
    }
    return value;
  }

  private static long i64(byte[] bytes, int offset) {
    return u64(bytes, offset);
  }

  private static int u16(byte[] bytes, int offset) {
    return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
  }

  private record TableXHeader(
      long totalRecordCount, int offsetSize, byte[] blockMap, boolean reliable) {}

  private record FieldParseResult(
      List<FileGdbField> fields,
      FileGdbGeomField geomField,
      int objectIdFieldIndex,
      int geomFieldIndex,
      int nullableFieldCount) {}
}
