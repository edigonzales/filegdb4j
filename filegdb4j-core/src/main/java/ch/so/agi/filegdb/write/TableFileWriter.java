package ch.so.agi.filegdb.write;

import ch.so.agi.filegdb.GdbException;
import ch.so.agi.filegdb.geometry.CoordinatePrecision;
import ch.so.agi.filegdb.geometry.FileGdbGeometry;
import ch.so.agi.filegdb.geometry.FileGdbMultiPoint;
import ch.so.agi.filegdb.geometry.FileGdbPart;
import ch.so.agi.filegdb.geometry.FileGdbPoint;
import ch.so.agi.filegdb.geometry.FileGdbPolygon;
import ch.so.agi.filegdb.geometry.FileGdbPolyline;
import ch.so.agi.filegdb.geometry.GeometryCodec;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import ch.so.agi.filegdb.geometry.GeometryKind;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.table.FileGdbFieldType;
import ch.so.agi.filegdb.table.FileGdbGeomField;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Creates and fills a {@code .gdbtable} file with its {@code .gdbtablx} row index.
 *
 * <p>Ported from GDAL OpenFileGDB ({@code filegdbtable_write.cpp}, {@code
 * filegdbtable_write_fields.cpp}). The writer supports sequential object ids, no free list, no
 * attribute indexes and no updates. Optional native spatial indexes are built at close. Fields must
 * be added before the first row is written.
 */
public final class TableFileWriter implements AutoCloseable {

  private static final int HEADER_SIZE = 40;
  private static final int TABLX_HEADER_SIZE = 16;
  private static final int TABLX_FEATURES_PER_PAGE = 1024;
  private static final int TABLX_OFFSET_SIZE = 4;
  private static final String CREATOR = "filegdb4j-envelope-spx-1";

  private final Path path;
  private final FileChannel table;
  private final FileChannel tableX;
  private final GeometryKind geometryKind;
  private final boolean hasZ;
  private final boolean hasM;
  private final List<FileGdbField> attributeFields = new ArrayList<>();
  private final Set<String> fieldNames = new HashSet<>(Set.of("objectid"));
  private List<FileGdbField> physicalFields;
  private FileGdbGeomField geometryField;
  private int geometryFieldIndex = -1;
  private int nullableCount;
  private int nullMaskSize;
  private boolean fieldsWritten;
  private boolean closed;
  private boolean spatialIndex;
  private int offsetWidth = TABLX_OFFSET_SIZE;
  private boolean stringsUtf8 = true;
  private Runnable cancellation = () -> {};
  private int gridCount = 1;
  private boolean changed = true;

  public void setCancellation(Runnable check) {
    cancellation = java.util.Objects.requireNonNull(check);
  }

  /** Opens a physical table in a private edit workspace. Does not truncate existing rows. */
  public static TableFileWriter append(
      Path path,
      boolean createIndex,
      java.util.Map<String, ch.so.agi.filegdb.table.FieldMetadata> metadata)
      throws IOException {
    return append(path, createIndex, metadata, () -> {});
  }

  public static TableFileWriter append(
      Path path,
      boolean createIndex,
      java.util.Map<String, ch.so.agi.filegdb.table.FieldMetadata> metadata,
      Runnable cancellation)
      throws IOException {
    cancellation.run();
    try (var source = ch.so.agi.filegdb.table.FileGdbTableFile.open(path, metadata)) {
      var layout = source.writeLayout();
      if (layout.version() != 3 || !source.reliableObjectIds())
        throw new GdbException(
            "Append requires a version 3 table with reliable OBJECTIDs: " + path);
      FileChannel data = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
      try {
        FileChannel offsets =
            FileChannel.open(
                withExtension(path, ".gdbtablx"),
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
        try {
          return new TableFileWriter(path, data, offsets, source, createIndex, cancellation);
        } catch (Exception e) {
          offsets.close();
          throw e;
        }
      } catch (Exception e) {
        data.close();
        throw e;
      }
    }
  }

  private TableFileWriter(
      Path path,
      FileChannel data,
      FileChannel offsets,
      ch.so.agi.filegdb.table.FileGdbTableFile source,
      boolean createIndex,
      Runnable cancellation)
      throws IOException {
    this.cancellation = cancellation;
    this.path = path;
    table = data;
    tableX = offsets;
    geometryKind = source.geometryKind();
    hasZ = source.hasZ();
    hasM = source.hasM();
    var layout = source.writeLayout();
    offsetWidth = layout.offsetWidth();
    if (offsetWidth == 0) {
      ByteBuffer header = ByteBuffer.allocate(16).order(java.nio.ByteOrder.LITTLE_ENDIAN);
      while (header.hasRemaining())
        if (tableX.read(header) < 0) throw new IOException("Missing row index header");
      offsetWidth = header.getInt(12);
    }
    if (offsetWidth < 4 || offsetWidth > 6) throw new IOException("Unsupported row offset width");
    stringsUtf8 = source.stringsUtf8();
    physicalFields = source.fields();
    attributeFields.addAll(
        physicalFields.stream()
            .filter(
                f -> f.type() != FileGdbFieldType.OBJECTID && f.type() != FileGdbFieldType.GEOMETRY)
            .toList());
    geometryField = source.geomField();
    geometryFieldIndex = source.geomFieldIndex();
    nullableCount = (int) physicalFields.stream().filter(FileGdbField::nullable).count();
    nullMaskSize = (nullableCount + 7) / 8;
    fieldsWritten = true;
    totalRecordCount = source.totalRecordCount();
    validRecordCount = source.validRecordCount();
    fileSize = table.size();
    offsetFieldDesc = layout.descriptorOffset();
    fieldDescLength = source.fieldDescriptorLength();
    headerBufferMaxSize = source.headerBufferMaxSize();
    bboxFileOffset = layout.extentOffset();
    gridResFileOffset = layout.gridOffset();
    blockMap = layout.blockMap();
    if (blockMap == null || blockMap.length == 0) {
      int pages = (int) ((totalRecordCount + 1023) / 1024);
      blockMap = new byte[(pages + 7) / 8];
      for (int i = 0; i < pages; i++) blockMap[i / 8] |= (byte) (1 << (i % 8));
    }
    boolean indexed = java.nio.file.Files.exists(ch.so.agi.filegdb.index.SpatialIndex.path(path));
    spatialIndex = geometryField != null && (createIndex || indexed);
    changed = spatialIndex && !indexed;
    if (geometryField != null) {
      gridCount = geometryField.geometry().spatialIndexGridResolution().size();
      // Scan stored geometries: missing/stale source extents must not lose old features.
      for (long i = 0; i < totalRecordCount; i++) {
        cancellation.run();
        Object[] row = source.readRow(i);
        if (row != null && row[geometryFieldIndex] instanceof FileGdbGeometry g) {
          updateExtent(g);
          var b = ch.so.agi.filegdb.geometry.GeometryBounds.of(g);
          if (b != null) {
            include(new FileGdbPoint(b.xMin(), b.yMin()));
            include(new FileGdbPoint(b.xMax(), b.yMax()));
          }
        }
      }
    }
  }

  boolean changed() {
    return changed;
  }

  /** Internal catalog replacement retaining the row identity and all other rows. */
  void replaceRow(long objectId, Object[] values, FileGdbGeometry geometry) throws IOException {
    if (closed || objectId < 1 || objectId > totalRecordCount)
      throw new IOException("Invalid catalog row");
    cancellation.run();
    ByteBuffer previous = ByteBuffer.allocate(offsetWidth);
    long position = indexPosition(objectId - 1);
    while (previous.hasRemaining())
      if (tableX.read(previous, position + previous.position()) < 0)
        throw new IOException("Truncated row index");
    long oldOffset = 0;
    for (int i = 0; i < offsetWidth; i++) oldOffset |= (previous.array()[i] & 255L) << (8 * i);
    if (oldOffset == 0) throw new IOException("Missing catalog row");
    byte[] encoded = encodeRow(values, geometry);
    if (fileSize + encoded.length + 4 >= (1L << (offsetWidth * 8)))
      throw new IOException("Catalog exceeds offset width");
    BinaryBuffer row = new BinaryBuffer(encoded.length + 4);
    row.u32(encoded.length);
    row.bytes(encoded);
    writeFully(table, fileSize, row.toByteArray());
    byte[] offset = new byte[offsetWidth];
    for (int i = 0; i < offsetWidth; i++) offset[i] = (byte) (fileSize >>> (8 * i));
    writeFully(tableX, position, offset);
    ByteBuffer oldLength = ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    while (oldLength.hasRemaining())
      if (table.read(oldLength, oldOffset + oldLength.position()) < 0)
        throw new IOException("Truncated old catalog row");
    BinaryBuffer deleted = new BinaryBuffer(4);
    deleted.i32(-oldLength.getInt(0));
    writeFully(table, oldOffset, deleted.toByteArray());
    fileSize += row.size();
    headerBufferMaxSize = Math.max(headerBufferMaxSize, encoded.length);
    changed = true;
    updateHeaders();
  }

  private long indexPosition(long row) {
    int block = (int) (row / 1024);
    long before = 0;
    for (int i = 0; i < block / 8; i++) before += Integer.bitCount(blockMap[i] & 255);
    if (block % 8 != 0)
      before += Integer.bitCount((blockMap[block / 8] & 255) & ((1 << (block % 8)) - 1));
    return TABLX_HEADER_SIZE + (before * 1024 + row % 1024) * offsetWidth;
  }

  private long validRecordCount;
  private long totalRecordCount;
  private long fileSize;
  private long offsetFieldDesc;
  private int fieldDescLength;
  private long headerBufferMaxSize;
  private long rowBufferMaxSize;
  private long bboxFileOffset = -1;
  private long gridResFileOffset = -1;
  private double minX = Double.NaN;
  private double minY = Double.NaN;
  private double maxX = Double.NaN;
  private double maxY = Double.NaN;
  private double minZ = Double.NaN;
  private double maxZ = Double.NaN;
  private double minM = Double.NaN, maxM = Double.NaN;
  private byte[] blockMap = new byte[0];

  private TableFileWriter(
      Path path,
      FileChannel table,
      FileChannel tableX,
      GeometryKind geometryKind,
      boolean hasZ,
      boolean hasM)
      throws IOException {
    this.path = path;
    this.table = table;
    this.tableX = tableX;
    this.geometryKind = geometryKind;
    this.hasZ = hasZ;
    this.hasM = hasM;

    // Initial table header (patched at sync) and creator string.
    BinaryBuffer header = new BinaryBuffer(64);
    header.u32(3); // version
    header.u32(0); // valid record count
    header.u32(0); // largest row / field descriptor size
    header.u32(5); // magic
    header.u32(0);
    header.u32(0);
    header.i64(0); // file size
    header.i64(0); // offset of field descriptor
    header.u32(CREATOR.length());
    header.ascii(CREATOR);
    writeFully(table, 0, header.toByteArray());
    this.fileSize = HEADER_SIZE + 4 + CREATOR.length();

    BinaryBuffer tableXHeader = new BinaryBuffer(16);
    tableXHeader.u32(3);
    tableXHeader.u32(0);
    tableXHeader.u32(0);
    tableXHeader.u32(TABLX_OFFSET_SIZE);
    writeFully(tableX, 0, tableXHeader.toByteArray());
  }

  public static TableFileWriter create(
      Path tablePath, GeometryKind geometryKind, boolean hasZ, boolean hasM) throws IOException {
    FileChannel table =
        FileChannel.open(
            tablePath,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE);
    try {
      Path tableXPath = withExtension(tablePath, ".gdbtablx");
      FileChannel tableX =
          FileChannel.open(
              tableXPath,
              StandardOpenOption.CREATE,
              StandardOpenOption.TRUNCATE_EXISTING,
              StandardOpenOption.READ,
              StandardOpenOption.WRITE);
      try {
        return new TableFileWriter(tablePath, table, tableX, geometryKind, hasZ, hasM);
      } catch (Exception e) {
        tableX.close();
        throw e;
      }
    } catch (Exception e) {
      table.close();
      throw e;
    }
  }

  public void setSpatialIndex(boolean enabled) {
    if (fieldsWritten) throw new IllegalStateException("Index setting must precede fields");
    if (enabled && geometryField == null)
      throw new IllegalStateException("Spatial index requires geometry");
    spatialIndex = enabled;
    if (enabled)
      geometryField =
          new FileGdbGeomField(
              geometryField.name(),
              geometryField.alias(),
              geometryField.nullable(),
              geometryField.wkt(),
              geometryField.geometry().withGridResolution(List.of(1.0)));
  }

  public Path path() {
    return path;
  }

  public long rowCount() {
    return validRecordCount;
  }

  public long totalRecordCount() {
    return totalRecordCount;
  }

  public List<FileGdbField> fields() {
    if (physicalFields == null) {
      throw new IllegalStateException("Fields are not finalized yet");
    }
    return physicalFields;
  }

  public void addField(FileGdbField field) {
    if (field.defaultValue() != null
        && (!field.editable()
            || java.util.Set.of(
                    FileGdbFieldType.BINARY,
                    FileGdbFieldType.GUID,
                    FileGdbFieldType.GLOBALID,
                    FileGdbFieldType.XML)
                .contains(field.type())))
      throw new IllegalArgumentException("Unsupported constant default for field: " + field.name());
    if (fieldsWritten) {
      throw new IllegalStateException("Fields must be added before the first row");
    }
    if (!fieldNames.add(field.name().toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("Duplicate field name: " + field.name());
    }
    if (field.type() == FileGdbFieldType.GEOMETRY) {
      throw new IllegalArgumentException("Use addGeometryField() for geometry fields");
    }
    if (field.type() == FileGdbFieldType.OBJECTID) {
      throw new IllegalArgumentException("The object id field is created automatically");
    }
    attributeFields.add(field);
    if (field.nullable()) {
      nullableCount++;
    }
  }

  public void addGeometryField(FileGdbGeomField field) {
    if (fieldsWritten) {
      throw new IllegalStateException("Fields must be added before the first row");
    }
    if (geometryField != null) {
      throw new IllegalStateException("Only one geometry field is supported");
    }
    if (!fieldNames.add(field.name().toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("Duplicate field name: " + field.name());
    }
    field.geometry().precision().validateForWriting();
    geometryField = field;
    if (field.nullable()) {
      nullableCount++;
    }
  }

  /** Writes the field descriptor section and prepares the table for rows. */
  public void writeFieldDescriptors() throws IOException {
    if (fieldsWritten) {
      return;
    }
    physicalFields = new ArrayList<>(attributeFields.size() + 2);
    physicalFields.add(
        new FileGdbField(
            "OBJECTID", "", FileGdbFieldType.OBJECTID, false, true, false, 0, false, null));
    physicalFields.addAll(attributeFields);
    if (geometryField != null) {
      geometryFieldIndex = physicalFields.size();
      physicalFields.add(
          new FileGdbField(
              geometryField.name(),
              geometryField.alias(),
              FileGdbFieldType.GEOMETRY,
              geometryField.nullable(),
              false,
              false,
              0,
              false,
              null));
    }
    nullMaskSize = (nullableCount + 7) / 8;

    BinaryBuffer descriptor = new BinaryBuffer(1024);
    descriptor.u32(0); // patched size
    descriptor.u32(4); // secondary header version
    int layerFlags =
        geometryKind.id()
            | (1 << 8) // strings are UTF-8
            | ((geometryField != null ? 1 : 0) << 9)
            | ((hasM ? 1 : 0) << 30)
            | ((hasZ ? 1 : 0) << 31);
    descriptor.u32(layerFlags);
    descriptor.u16(physicalFields.size());
    for (FileGdbField field : physicalFields) {
      writeFieldDescriptor(descriptor, field);
    }
    descriptor.u8(0xDE);
    descriptor.u8(0xAD);
    descriptor.u8(0xBE);
    descriptor.u8(0xEF);
    descriptor.patchU32(0, descriptor.size() - 4);

    offsetFieldDesc = fileSize;
    byte[] bytes = descriptor.toByteArray();
    writeFully(table, offsetFieldDesc, bytes);
    fieldDescLength = bytes.length - 4;
    this.bboxFileOffset =
        geometryField != null && geometryFieldBBoxOffset >= 0
            ? offsetFieldDesc + geometryFieldBBoxOffset
            : -1;
    this.gridResFileOffset =
        geometryField != null && geometryFieldGridResOffset >= 0
            ? offsetFieldDesc + geometryFieldGridResOffset
            : -1;
    fileSize = offsetFieldDesc + bytes.length;
    headerBufferMaxSize = Math.max(fieldDescLength, rowBufferMaxSize);
    fieldsWritten = true;
    sync();
  }

  private int geometryFieldBBoxOffset = -1;
  private int geometryFieldGridResOffset = -1;

  /**
   * Writes one feature.
   *
   * @param attributeValues values aligned with the attribute fields added to this writer
   * @param geometry geometry for the geometry field, may be null when the field is nullable
   */
  public long writeRow(Object[] attributeValues, FileGdbGeometry geometry) throws IOException {
    if (!fieldsWritten) {
      writeFieldDescriptors();
    }
    if (attributeValues.length != attributeFields.size()) {
      throw new IllegalArgumentException(
          "Expected " + attributeFields.size() + " values, got " + attributeValues.length);
    }
    byte[] blob = encodeRow(attributeValues, geometry);

    if (fileSize + blob.length + 4 >= (1L << (offsetWidth * 8))
        || totalRecordCount >= Integer.MAX_VALUE) {
      throw new GdbException("Table exceeds supported 32-bit row index limits");
    }
    long objectId = totalRecordCount + 1;
    long rowIndex = objectId - 1;
    cancellation.run();
    int block = (int) (rowIndex / 1024);
    if (blockMap.length <= block / 8) blockMap = java.util.Arrays.copyOf(blockMap, block / 8 + 1);
    if ((blockMap[block / 8] & (1 << (block % 8))) == 0) {
      blockMap[block / 8] |= (byte) (1 << (block % 8));
      writeFully(tableX, indexPosition(rowIndex - rowIndex % 1024), new byte[offsetWidth * 1024]);
    }

    BinaryBuffer row = new BinaryBuffer(blob.length + 4);
    row.u32(blob.length);
    row.bytes(blob);
    long rowOffset = fileSize;
    writeFully(table, rowOffset, row.toByteArray());
    fileSize += 4 + blob.length;

    byte[] offset = new byte[offsetWidth];
    for (int i = 0; i < offsetWidth; i++) offset[i] = (byte) (rowOffset >>> (8 * i));
    writeFully(tableX, indexPosition(rowIndex), offset);
    changed = true;

    totalRecordCount = objectId;
    validRecordCount++;
    rowBufferMaxSize = Math.max(rowBufferMaxSize, blob.length);
    headerBufferMaxSize = Math.max(headerBufferMaxSize, blob.length);
    updateHeaders();
    return objectId;
  }

  private byte[] encodeRow(Object[] attributeValues, FileGdbGeometry geometry) {
    BinaryBuffer buffer = new BinaryBuffer(256);
    int maskPosition = buffer.size();
    for (int i = 0; i < nullMaskSize; i++) {
      buffer.u8(0xFF);
    }
    byte[] mask = new byte[nullMaskSize];
    java.util.Arrays.fill(mask, (byte) 0xFF);
    int nullableIndex = 0;

    int attributeIndex = 0;
    for (int i = 0; i < physicalFields.size(); i++) {
      FileGdbField field = physicalFields.get(i);
      if (field.type() == FileGdbFieldType.OBJECTID) {
        continue;
      }
      if (i == geometryFieldIndex) {
        if (geometry == null) {
          if (!field.nullable()) {
            throw new GdbException("Null geometry in non-nullable geometry field");
          }
          nullableIndex++;
          continue;
        }
        byte[] shape = GeometryCodec.encode(geometry, geometryField.geometry());
        buffer.varUInt(shape.length);
        buffer.bytes(shape);
        FileGdbGeometry stored = GeometryCodec.decode(shape, geometryField.geometry());
        updateExtent(stored);
        var bounds = ch.so.agi.filegdb.geometry.GeometryBounds.of(stored);
        if (bounds != null) {
          include(new FileGdbPoint(bounds.xMin(), bounds.yMin()));
          include(new FileGdbPoint(bounds.xMax(), bounds.yMax()));
        }
        if (field.nullable()) {
          clearBit(mask, nullableIndex);
          nullableIndex++;
        }
        continue;
      }

      Object value = attributeValues[attributeIndex++];
      if (value == null) {
        if (!field.nullable()) {
          throw new GdbException("Null value in non-nullable field " + field.name());
        }
        nullableIndex++;
        continue;
      }
      encodeAttribute(buffer, field, value);
      if (field.nullable()) {
        clearBit(mask, nullableIndex);
        nullableIndex++;
      }
    }
    for (int i = 0; i < nullMaskSize; i++) {
      buffer.patchU8(maskPosition + i, mask[i] & 0xFF);
    }
    return buffer.toByteArray();
  }

  private static void clearBit(byte[] mask, int bitIndex) {
    mask[bitIndex / 8] &= (byte) ~(1 << (bitIndex % 8));
  }

  private void encodeAttribute(BinaryBuffer buffer, FileGdbField field, Object value) {
    switch (field.type()) {
      case INT16 -> buffer.i16(((Number) value).shortValue());
      case INT32 -> buffer.i32(((Number) value).intValue());
      case INT64 -> buffer.i64(((Number) value).longValue());
      case FLOAT32 -> buffer.f32(((Number) value).floatValue());
      case FLOAT64 -> buffer.f64(((Number) value).doubleValue());
      case STRING, XML -> {
        byte[] bytes =
            value
                .toString()
                .getBytes(
                    field.type() == FileGdbFieldType.STRING && !stringsUtf8
                        ? StandardCharsets.UTF_16LE
                        : StandardCharsets.UTF_8);
        buffer.varUInt(bytes.length);
        buffer.bytes(bytes);
      }
      case BINARY -> {
        byte[] bytes = (byte[]) value;
        buffer.varUInt(bytes.length);
        buffer.bytes(bytes);
      }
      case GUID, GLOBALID -> buffer.bytes(uuidBytes(value));
      case DATETIME -> buffer.f64(dateTimeToDays(toLocalDateTime(value), field.highPrecision()));
      case DATE -> buffer.f64(dateTimeToDays(toLocalDate(value).atStartOfDay(), false));
      case TIME -> {
        LocalTime time = toLocalTime(value);
        buffer.f64((time.toSecondOfDay() + time.getNano() / 1e9) / 86400.0);
      }
      case DATETIME_WITH_OFFSET -> {
        OffsetDateTime dateTime = toOffsetDateTime(value);
        buffer.f64(dateTimeToDays(dateTime.toLocalDateTime(), true));
        buffer.i16((short) (dateTime.getOffset().getTotalSeconds() / 60));
      }
      default -> throw new GdbException("Unsupported field type for writing: " + field.type());
    }
  }

  private static double dateTimeToDays(LocalDateTime dateTime, boolean highPrecision) {
    double days = dateTime.toEpochSecond(ZoneOffset.UTC) / 86400.0 + 25569.0;
    if (highPrecision) {
      days += dateTime.getNano() / 1e9 / 86400.0;
    }
    return days;
  }

  private static LocalDateTime toLocalDateTime(Object value) {
    if (value instanceof LocalDateTime dateTime) {
      return dateTime;
    }
    if (value instanceof LocalDate date) {
      return date.atStartOfDay();
    }
    if (value instanceof OffsetDateTime dateTime) {
      return dateTime.toLocalDateTime();
    }
    if (value instanceof java.util.Date date) {
      return LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
    }
    throw new GdbException("Unsupported datetime value: " + value.getClass().getName());
  }

  private static LocalDate toLocalDate(Object value) {
    if (value instanceof LocalDate date) {
      return date;
    }
    if (value instanceof LocalDateTime dateTime) {
      return dateTime.toLocalDate();
    }
    if (value instanceof OffsetDateTime dateTime) {
      return dateTime.toLocalDate();
    }
    if (value instanceof java.util.Date date) {
      return LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC).toLocalDate();
    }
    throw new GdbException("Unsupported date value: " + value.getClass().getName());
  }

  private static LocalTime toLocalTime(Object value) {
    if (value instanceof LocalTime time) {
      return time;
    }
    if (value instanceof LocalDateTime dateTime) {
      return dateTime.toLocalTime();
    }
    if (value instanceof OffsetDateTime dateTime) {
      return dateTime.toLocalTime();
    }
    throw new GdbException("Unsupported time value: " + value.getClass().getName());
  }

  private static OffsetDateTime toOffsetDateTime(Object value) {
    if (value instanceof OffsetDateTime dateTime) {
      return dateTime;
    }
    if (value instanceof LocalDateTime dateTime) {
      return dateTime.atOffset(ZoneOffset.UTC);
    }
    throw new GdbException("Unsupported offset datetime value: " + value.getClass().getName());
  }

  private static byte[] uuidBytes(Object value) {
    UUID uuid;
    if (value instanceof UUID typed) {
      uuid = typed;
    } else {
      String text = value.toString().replace("{", "").replace("}", "").trim();
      uuid = UUID.fromString(text);
    }
    long most = uuid.getMostSignificantBits();
    long least = uuid.getLeastSignificantBits();
    byte[] bytes = new byte[16];
    bytes[0] = (byte) (most >>> 32);
    bytes[1] = (byte) (most >>> 40);
    bytes[2] = (byte) (most >>> 48);
    bytes[3] = (byte) (most >>> 56);
    bytes[4] = (byte) (most >>> 16);
    bytes[5] = (byte) (most >>> 24);
    bytes[6] = (byte) most;
    bytes[7] = (byte) (most >>> 8);
    for (int i = 0; i < 8; i++) {
      bytes[8 + i] = (byte) (least >>> (56 - 8 * i));
    }
    return bytes;
  }

  private void updateExtent(FileGdbGeometry geometry) {
    if (geometry instanceof FileGdbPoint point) {
      include(point);
    } else if (geometry instanceof FileGdbMultiPoint multiPoint) {
      for (FileGdbPoint point : multiPoint.points()) {
        include(point);
      }
    } else if (geometry instanceof FileGdbPolyline polyline) {
      for (FileGdbPart part : polyline.parts()) {
        for (FileGdbPoint point : part.points()) {
          include(point);
        }
      }
    } else if (geometry instanceof FileGdbPolygon polygon) {
      for (FileGdbPart part : polygon.parts()) {
        for (FileGdbPoint point : part.points()) {
          include(point);
        }
      }
    }
  }

  private void include(FileGdbPoint point) {
    if (Double.isNaN(point.x()) || Double.isNaN(point.y())) {
      return;
    }
    minX = Double.isNaN(minX) ? point.x() : Math.min(minX, point.x());
    minY = Double.isNaN(minY) ? point.y() : Math.min(minY, point.y());
    maxX = Double.isNaN(maxX) ? point.x() : Math.max(maxX, point.x());
    maxY = Double.isNaN(maxY) ? point.y() : Math.max(maxY, point.y());
    if (point.m() != null && Double.isFinite(point.m())) {
      minM = Double.isNaN(minM) ? point.m() : Math.min(minM, point.m());
      maxM = Double.isNaN(maxM) ? point.m() : Math.max(maxM, point.m());
    }
    if (point.z() != null && Double.isFinite(point.z())) {
      minZ = Double.isNaN(minZ) ? point.z() : Math.min(minZ, point.z());
      maxZ = Double.isNaN(maxZ) ? point.z() : Math.max(maxZ, point.z());
    }
  }

  private void writeFieldDescriptor(BinaryBuffer buffer, FileGdbField field) {
    writeShortString(buffer, field.name());
    writeShortString(buffer, field.alias() == null ? "" : field.alias());
    buffer.u8(field.type().id());
    int flags =
        (field.nullable() ? 1 : 0) | (field.required() ? 2 : 0) | (field.editable() ? 4 : 0);
    switch (field.type()) {
      case STRING -> {
        buffer.u32(field.maxWidth());
        buffer.u8(flags);
        byte[] defaults =
            field.defaultValue() == null
                ? new byte[0]
                : field
                    .defaultValue()
                    .toString()
                    .getBytes(stringsUtf8 ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16LE);
        buffer.varUInt(defaults.length);
        buffer.bytes(defaults);
      }
      case OBJECTID -> {
        buffer.u8(4);
        buffer.u8(2);
      }
      case BINARY, XML -> {
        buffer.u8(0);
        buffer.u8(flags);
      }
      case GUID, GLOBALID -> {
        buffer.u8(38);
        buffer.u8(flags);
      }
      case GEOMETRY -> {
        buffer.u8(0);
        buffer.u8(flags);
        GeometryFieldDefinition definition = geometryField.geometry();
        writeShortStringWithByteLength(buffer, definition.wkt() == null ? "" : definition.wkt());
        boolean hasZOriginScaleTolerance = hasZ;
        boolean hasMOriginScaleTolerance = hasM;
        buffer.u8(1 | (hasMOriginScaleTolerance ? 2 : 0) | (hasZOriginScaleTolerance ? 4 : 0));
        CoordinatePrecision precision = definition.precision();
        buffer.f64(precision.xOrigin());
        buffer.f64(precision.yOrigin());
        buffer.f64(precision.xyScale());
        if (hasMOriginScaleTolerance) {
          buffer.f64(precision.mOrigin());
          buffer.f64(precision.mScale());
        }
        if (hasZOriginScaleTolerance) {
          buffer.f64(precision.zOrigin());
          buffer.f64(precision.zScale());
        }
        buffer.f64(precision.xyTolerance());
        if (hasMOriginScaleTolerance) {
          buffer.f64(precision.mTolerance());
        }
        if (hasZOriginScaleTolerance) {
          buffer.f64(precision.zTolerance());
        }
        geometryFieldBBoxOffset = buffer.size();
        buffer.f64(Double.NaN);
        buffer.f64(Double.NaN);
        buffer.f64(Double.NaN);
        buffer.f64(Double.NaN);
        if (hasZ) {
          buffer.f64(Double.NaN);
          buffer.f64(Double.NaN);
        }
        if (hasM) {
          buffer.f64(Double.NaN);
          buffer.f64(Double.NaN);
        }
        buffer.u8(0);
        List<Double> gridResolution = definition.spatialIndexGridResolution();
        if (gridResolution.isEmpty()) {
          gridResolution = List.of(1000.0);
        }
        buffer.u32(gridResolution.size());
        geometryFieldGridResOffset = buffer.size();
        for (double grid : gridResolution) {
          buffer.f64(grid);
        }
      }
      default -> {
        int size =
            switch (field.type()) {
              case INT16 -> 2;
              case INT32, FLOAT32 -> 4;
              case FLOAT64, DATETIME, DATE, TIME -> 8;
              case INT64 -> 8;
              case DATETIME_WITH_OFFSET -> 10;
              default -> 0;
            };
        buffer.u8(size);
        buffer.u8(flags);
        BinaryBuffer defaults = new BinaryBuffer(16);
        if (field.defaultValue() != null) encodeAttribute(defaults, field, field.defaultValue());
        buffer.u8(defaults.size());
        buffer.bytes(defaults.toByteArray());
      }
    }
  }

  private void writeShortString(BinaryBuffer buffer, String value) {
    int characterCount = Math.min(255, value.length());
    buffer.u8(characterCount);
    buffer.utf16(value.substring(0, characterCount));
  }

  private void writeShortStringWithByteLength(BinaryBuffer buffer, String value) {
    byte[] utf16 = value.getBytes(StandardCharsets.UTF_16LE);
    int length = Math.min(65534, utf16.length);
    buffer.u16(length);
    buffer.bytes(java.util.Arrays.copyOf(utf16, length));
  }

  /** Patches the header and the row index trailer. */
  public void sync() throws IOException {
    updateHeaders();
    table.force(false);
    tableX.force(false);
  }

  private void updateHeaders() throws IOException {
    if (!fieldsWritten) {
      return;
    }
    BinaryBuffer validRecords = new BinaryBuffer(4);
    validRecords.u32(validRecordCount);
    writeFully(table, 4, validRecords.toByteArray());

    BinaryBuffer maxSize = new BinaryBuffer(4);
    maxSize.u32(headerBufferMaxSize);
    writeFully(table, 8, maxSize.toByteArray());

    BinaryBuffer sizes = new BinaryBuffer(16);
    sizes.i64(fileSize);
    sizes.i64(offsetFieldDesc);
    writeFully(table, 24, sizes.toByteArray());

    if (bboxFileOffset >= 0 && !Double.isNaN(minX)) {
      BinaryBuffer bbox = new BinaryBuffer(32);
      bbox.f64(minX);
      bbox.f64(minY);
      bbox.f64(maxX);
      bbox.f64(maxY);
      if (hasZ) {
        bbox.f64(Double.isNaN(minZ) ? 0 : minZ);
        bbox.f64(Double.isNaN(maxZ) ? 0 : maxZ);
      }
      if (hasM) {
        bbox.f64(minM);
        bbox.f64(maxM);
      }
      writeFully(table, bboxFileOffset, bbox.toByteArray());
    }

    long blocksPresent = (totalRecordCount + TABLX_FEATURES_PER_PAGE - 1) / TABLX_FEATURES_PER_PAGE;
    int neededBytes = (int) ((blocksPresent + 7) / 8);
    if (blockMap.length < neededBytes) {
      blockMap = new byte[neededBytes];
    }
    long physicalBlocks = 0;
    for (byte bits : blockMap) physicalBlocks += Integer.bitCount(bits & 255);

    BinaryBuffer tableXHeader = new BinaryBuffer(12);
    tableXHeader.u32(physicalBlocks);
    tableXHeader.u32(totalRecordCount);
    writeFully(tableX, 4, tableXHeader.toByteArray());

    long trailerOffset = TABLX_HEADER_SIZE + physicalBlocks * offsetWidth * TABLX_FEATURES_PER_PAGE;
    // Pad the block map to a multiple of 32 32-bit words, like the FileGDB SDK.
    int words = (blockMap.length + 3) / 4;
    int paddedWords = ((words + 31) / 32) * 32;
    byte[] padded = new byte[paddedWords * 4];
    System.arraycopy(blockMap, 0, padded, 0, blockMap.length);
    int trailingZeroWords = 0;
    for (int i = paddedWords - 1; i >= 0; i--) {
      boolean zero = true;
      for (int j = 0; j < 4; j++) {
        if (padded[i * 4 + j] != 0) {
          zero = false;
          break;
        }
      }
      if (!zero) {
        break;
      }
      trailingZeroWords++;
    }
    BinaryBuffer trailer = new BinaryBuffer(16 + padded.length);
    trailer.u32(paddedWords);
    trailer.u32(blocksPresent);
    trailer.u32(physicalBlocks);
    trailer.u32(paddedWords - trailingZeroWords);
    trailer.bytes(padded);
    writeFully(tableX, trailerOffset, trailer.toByteArray());
  }

  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    try {
      if (fieldsWritten && changed) {
        cancellation.run();
        sync();
        if (spatialIndex) {
          double grid = ch.so.agi.filegdb.index.SpatialIndex.build(path, cancellation);
          BinaryBuffer value = new BinaryBuffer(8);
          value.f64(grid);
          for (int i = 1; i < gridCount; i++) value.f64(0);
          writeFully(table, gridResFileOffset, value.toByteArray());
          table.force(false);
        }
      }
    } finally {
      try {
        tableX.close();
      } finally {
        table.close();
      }
    }
  }

  private static Path withExtension(Path path, String extension) {
    String name = path.getFileName().toString();
    int dot = name.lastIndexOf('.');
    if (dot >= 0) {
      name = name.substring(0, dot);
    }
    return path.resolveSibling(name + extension);
  }

  private static void writeFully(FileChannel channel, long position, byte[] data)
      throws IOException {
    ByteBuffer buffer = ByteBuffer.wrap(data);
    int total = 0;
    while (buffer.hasRemaining()) {
      total += channel.write(buffer, position + total);
    }
  }
}
