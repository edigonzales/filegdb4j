package ch.so.agi.filegdb.write;

import ch.so.agi.filegdb.GdbException;
import ch.so.agi.filegdb.geometry.CoordinatePrecision;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import ch.so.agi.filegdb.geometry.GeometryKind;
import ch.so.agi.filegdb.io.GdbPaths;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.table.FileGdbGeomField;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Creates a new file geodatabase with its system tables.
 *
 * <p>Ported from GDAL OpenFileGDB ({@code ogropenfilegdbdatasource_write.cpp}). The system catalog,
 * GDB_Items, GDB_ItemTypes, GDB_ItemRelationships and GDB_ItemRelationshipTypes tables are written
 * like the FileGDB SDK does.
 */
public final class GdbCreator implements AutoCloseable {

  private static final String FOLDER_TYPE_UUID = "{f3783e6f-65ca-4514-8315-ce3985dad3b1}";
  private static final String WORKSPACE_TYPE_UUID = "{c673fe0f-7280-404f-8532-20755dd8fc06}";
  private static final String FEATURE_CLASS_TYPE_UUID = "{70737809-852c-4a03-9e22-2cecea5b9bfa}";
  private static final String TABLE_TYPE_UUID = "{cd06bc3b-789d-4c51-aafa-a467912b8965}";
  private static final String RELATIONSHIP_TYPE_UUID = "{b606a7e1-fa5b-439c-849c-6e9c2481537b}";
  private static final String CODED_DOMAIN_TYPE_UUID = "{8c368b12-a12e-4c7e-9638-c9c64e69e98f}";
  private static final String RANGE_DOMAIN_TYPE_UUID = "{c29da988-8c3e-45f7-8b5c-18e51ee7beb4}";
  private static final String DATASET_IN_FOLDER_UUID = "{dc78f1ab-34e4-43ac-ba47-1c4eabd0e7c7}";
  private static final String DOMAIN_IN_DATASET_UUID = "{17e08adb-2b31-4dcd-8fdd-df529e88f843}";
  private static final String DATASETS_RELATED_THROUGH_UUID =
      "{725badab-3452-491b-a795-55f32d67229c}";

  private static final String WGS84_WKT =
      "GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,"
          + "298.257223563]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]]";

  private static final String WORKSPACE_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<DEWorkspace xmlns:typens=\"http://www.esri.com/schemas/ArcGIS/10.3\" "
          + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
          + "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
          + "xsi:type=\"typens:DEWorkspace\">\n"
          + "  <CatalogPath>\\</CatalogPath>\n"
          + "  <Name/>\n"
          + "  <ChildrenExpanded>false</ChildrenExpanded>\n"
          + "  <WorkspaceType>esriLocalDatabaseWorkspace</WorkspaceType>\n"
          + "  <WorkspaceFactoryProgID/>\n"
          + "  <ConnectionString/>\n"
          + "  <ConnectionInfo xsi:nil=\"true\"/>\n"
          + "  <Domains xsi:type=\"typens:ArrayOfDomain\"/>\n"
          + "  <MajorVersion>3</MajorVersion>\n"
          + "  <MinorVersion>0</MinorVersion>\n"
          + "  <BugfixVersion>0</BugfixVersion>\n"
          + "</DEWorkspace>";

  private final Path directory;
  private final TableFileWriter systemCatalog;
  private final TableFileWriter spatialRefs;
  private final TableFileWriter items;
  private final TableFileWriter itemRelationships;
  private final TableFileWriter dbtune;
  private final TableFileWriter itemTypes;
  private final TableFileWriter itemRelationshipTypes;
  private final String rootGuid;
  private final String workspaceGuid;
  private final Set<String> spatialRefTexts = new HashSet<>();
  private final java.util.Map<String, String> datasetUuids =
      new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private final java.util.Map<String, ch.so.agi.filegdb.catalog.Domain> domains =
      new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private final java.util.Map<String, String> domainUuids =
      new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private final java.util.Map<String, List<FileGdbField>> datasetFields =
      new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private final Set<String> names = new HashSet<>();
  private int nextTableNumber;

  private Runnable cancellation = () -> {};
  private final java.util.List<TableFileWriter> datasetWriters = new java.util.ArrayList<>();
  private final Set<String> appended = new HashSet<>();
  private ch.so.agi.filegdb.catalog.GdbCatalog existingCatalog;

  public static GdbCreator openExisting(Path directory, Runnable cancellation) throws IOException {
    return new GdbCreator(directory, cancellation);
  }

  private GdbCreator(Path directory, Runnable cancellation) throws IOException {
    this.directory = directory;
    this.cancellation = cancellation;
    existingCatalog = ch.so.agi.filegdb.catalog.GdbCatalog.open(directory);
    rootGuid =
        existingCatalog.items().stream()
            .filter(
                i ->
                    i.type()
                            .replace("{", "")
                            .replace("}", "")
                            .equalsIgnoreCase(FOLDER_TYPE_UUID.substring(1, 37))
                        && "\\".equals(i.path()))
            .map(i -> i.uuid().toString())
            .findFirst()
            .orElseThrow(() -> new IOException("FileGDB root folder is missing"));
    workspaceGuid =
        existingCatalog.items().stream()
            .filter(
                i ->
                    i.type()
                        .replace("{", "")
                        .replace("}", "")
                        .equalsIgnoreCase(WORKSPACE_TYPE_UUID.substring(1, 37)))
            .map(i -> i.uuid().toString())
            .findFirst()
            .orElse("");
    var opened = new java.util.ArrayList<TableFileWriter>();
    try {
      for (int i = 1; i <= 7; i++) {
        cancellation.run();
        var writer =
            TableFileWriter.append(
                directory.resolve(GdbPaths.tableFileName(i)),
                false,
                java.util.Map.of(),
                cancellation);
        writer.setCancellation(cancellation);
        opened.add(writer);
      }
    } catch (Exception e) {
      for (var w : opened)
        try {
          w.close();
        } catch (Exception c) {
          e.addSuppressed(c);
        }
      throw e;
    }
    systemCatalog = opened.get(0);
    dbtune = opened.get(1);
    spatialRefs = opened.get(2);
    items = opened.get(3);
    itemTypes = opened.get(4);
    itemRelationships = opened.get(5);
    itemRelationshipTypes = opened.get(6);
    nextTableNumber = Math.toIntExact(systemCatalog.totalRecordCount() + 1);
    for (var item : existingCatalog.items()) {
      if (item.name() != null && !item.name().isEmpty())
        names.add(item.name().toLowerCase(Locale.ROOT));
      if (item.tableNumber() > 0) datasetUuids.put(item.name(), item.uuid().toString());
      var domain = ch.so.agi.filegdb.catalog.DefinitionXml.domain(item.definition());
      if (domain != null) {
        domains.put(domain.name(), domain);
        domainUuids.put(domain.name(), item.uuid().toString());
      }
    }
  }

  public GdbFeatureWriter appendFeatures(String name, boolean createSpatialIndex)
      throws IOException {
    var dataset = requireAppendable(name, true);
    var writer = appendDataset(dataset, createSpatialIndex);
    return new GdbFeatureWriter(dataset.name(), writer);
  }

  public GdbTableWriter appendRows(String name) throws IOException {
    var dataset = requireAppendable(name, false);
    return new GdbTableWriter(dataset.name(), appendDataset(dataset, false));
  }

  private TableFileWriter appendDataset(ch.so.agi.filegdb.catalog.Dataset dataset, boolean index)
      throws IOException {
    if (appended.contains(dataset.name()))
      throw new IOException("Dataset already opened for append: " + dataset.name());
    var indexPath = ch.so.agi.filegdb.index.SpatialIndex.path(dataset.tableFile());
    if (Files.exists(indexPath))
      try (var source = ch.so.agi.filegdb.table.FileGdbTableFile.open(dataset.tableFile())) {
        ch.so.agi.filegdb.index.SpatialIndex.validate(
            indexPath, source.geomField().geometry().spatialIndexGridResolution(), cancellation);
      }
    var writer =
        TableFileWriter.append(
            dataset.tableFile(),
            index,
            ch.so.agi.filegdb.catalog.DefinitionXml.fieldInfo(dataset.definition()),
            cancellation);
    writer.setCancellation(cancellation);
    appended.add(dataset.name());
    datasetWriters.add(writer);
    return writer;
  }

  private ch.so.agi.filegdb.catalog.Dataset requireAppendable(String name, boolean geometry)
      throws IOException {
    if (closed) throw new IllegalStateException("File geodatabase is closed");
    if (existingCatalog == null) throw new IOException("Append requires an edit session");
    var dataset =
        existingCatalog.datasets().stream()
            .filter(d -> d.name().equalsIgnoreCase(name))
            .findFirst()
            .orElseThrow(() -> new IOException("Dataset does not exist: " + name));
    if (dataset.isFeatureClass() != geometry) throw new IOException("Wrong dataset kind: " + name);
    checkAppendSupport(existingCatalog, dataset, cancellation);
    return dataset;
  }

  /** Checks unsupported editing semantics before any dataset changes. */
  public static void checkAppendSupport(
      ch.so.agi.filegdb.catalog.GdbCatalog catalog,
      ch.so.agi.filegdb.catalog.Dataset dataset,
      Runnable cancellation)
      throws IOException {
    cancellation.run();
    rejectAttributeIndexes(dataset.tableFile());
    String unsupported =
        ch.so.agi.filegdb.catalog.DefinitionXml.unsupportedEditingReason(dataset.definition());
    if (unsupported != null)
      throw new IOException("Unsupported editing rules in " + dataset.name() + ": " + unsupported);
    try (var table = ch.so.agi.filegdb.table.FileGdbTableFile.open(dataset.tableFile())) {
      for (var field : table.fields()) {
        if (field.type() == ch.so.agi.filegdb.table.FileGdbFieldType.GLOBALID
            || (!field.editable()
                && field.type() != ch.so.agi.filegdb.table.FileGdbFieldType.OBJECTID
                && field.type() != ch.so.agi.filegdb.table.FileGdbFieldType.GEOMETRY))
          throw new IOException(
              "Unsupported automatic field: " + dataset.name() + "." + field.name());
      }
    }
    for (var relationship : catalog.relationships()) {
      if ((relationship.originClassName().equalsIgnoreCase(dataset.name())
              || relationship.destinationClassName().equalsIgnoreCase(dataset.name()))
          && (relationship.attributed()
              || relationship.attachment()
              || relationship.composite()
              || relationship.cardinality()
                  == ch.so.agi.filegdb.catalog.RelationshipCardinality.MANY_TO_MANY))
        throw new IOException("Unsupported complex relationship: " + relationship.name());
    }
  }

  private static void rejectAttributeIndexes(Path table) throws IOException {
    String prefix =
        table.getFileName().toString().replace(".gdbtable", ".").toLowerCase(Locale.ROOT);
    try (var files = Files.list(table.getParent())) {
      if (files.anyMatch(
          p ->
              p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(prefix)
                  && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".atx")))
        throw new IOException(
            "Attribute indexes are unsupported for editing: " + table.getFileName());
    }
  }

  private void checkCatalogWritable() throws IOException {
    for (int number : new int[] {1, 3, 4, 6})
      rejectAttributeIndexes(directory.resolve(GdbPaths.tableFileName(number)));
    cancellation.run();
  }

  private void checkName(String name) {
    if (name == null
        || !name.matches("[\\p{L}_][\\p{L}\\p{N}_]*")
        || name.length() > 160
        || name.toUpperCase(Locale.ROOT).startsWith("GDB_"))
      throw new IllegalArgumentException("Invalid catalog name: " + name);
    if (names.contains(name.toLowerCase(Locale.ROOT)))
      throw new IllegalArgumentException("Duplicate catalog name: " + name);
  }

  private void checkFields(List<FileGdbField> fields, String geometryName) {
    Set<String> used = new HashSet<>();
    used.add("objectid");
    if (geometryName != null) {
      if (!geometryName.matches("[\\p{L}_][\\p{L}\\p{N}_]*")
          || geometryName.length() > 64
          || !used.add(geometryName.toLowerCase(Locale.ROOT)))
        throw new IllegalArgumentException("Invalid geometry field: " + geometryName);
    }
    for (var field : fields) {
      if (field.name() == null
          || !field.name().matches("[\\p{L}_][\\p{L}\\p{N}_]*")
          || field.name().length() > 64
          || !used.add(field.name().toLowerCase(Locale.ROOT)))
        throw new IllegalArgumentException("Invalid or duplicate field: " + field.name());
      if (field.domain() != null) {
        var domain = domains.get(field.domain());
        if (domain == null || domain.fieldType() != field.type())
          throw new IllegalArgumentException(
              "Unknown or incompatible domain on field: " + field.name());
      }
    }
  }

  private void registerDataset(String name, String uuid, List<FileGdbField> fields)
      throws IOException {
    names.add(name.toLowerCase(Locale.ROOT));
    datasetFields.put(name, List.copyOf(fields));
    for (String domain :
        fields.stream()
            .map(FileGdbField::domain)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList())
      itemRelationships.writeRow(
          new Object[] {
            Uuids.generate(), uuid, domainUuids.get(domain), DOMAIN_IN_DATASET_UUID, null, null
          },
          null);
  }

  private ch.so.agi.filegdb.table.FileGdbFieldType keyType(String dataset, String name)
      throws IOException {
    if (!datasetFields.containsKey(dataset) && existingCatalog != null) {
      var d =
          existingCatalog
              .dataset(dataset)
              .orElseThrow(() -> new IOException("Dataset missing: " + dataset));
      try (var t = ch.so.agi.filegdb.table.FileGdbTableFile.open(d.tableFile())) {
        datasetFields.put(dataset, t.fields());
      }
    }
    if ("OBJECTID".equals(name)) return ch.so.agi.filegdb.table.FileGdbFieldType.INT32;
    return datasetFields.get(dataset).stream()
        .filter(f -> f.name().equals(name))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown relationship key: " + dataset + "." + name))
        .type();
  }

  private boolean closed;

  private GdbCreator(Path directory) throws IOException {
    this.directory = directory;
    this.rootGuid = Uuids.generate();
    this.workspaceGuid = Uuids.generate();

    Files.createDirectory(directory);
    Files.write(
        directory.resolve("gdb"),
        new byte[] {0x05, 0x00, 0x00, 0x00, (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF});
    byte[] timestamps = new byte[400];
    java.util.Arrays.fill(timestamps, (byte) 0xFF);
    Files.write(directory.resolve("timestamps"), timestamps);

    try {
      this.systemCatalog = createSystemCatalog();
      this.dbtune = createDbtune();
      this.spatialRefs = createSpatialRefs();
      this.items = createItems();
      this.itemTypes = createItemTypes();
      this.itemRelationships = createItemRelationships();
      this.itemRelationshipTypes = createItemRelationshipTypes();
    } catch (Exception e) {
      closeQuietly();
      throw e;
    }
    this.nextTableNumber = (int) systemCatalog.totalRecordCount() + 1;
  }

  public static GdbCreator create(Path directory) throws IOException {
    if (directory == null) {
      throw new IllegalArgumentException("Directory is required");
    }
    if (!directory.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gdb")) {
      throw new IllegalArgumentException("File geodatabase directory must end with .gdb");
    }
    if (Files.exists(directory)) {
      throw new GdbException("Directory already exists: " + directory);
    }
    Path parent = directory.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    return new GdbCreator(directory.toAbsolutePath().normalize());
  }

  /**
   * Creates a feature class table and registers it in the catalog.
   *
   * <p>The returned writer owns the physical table and must be closed before the database itself is
   * closed.
   */
  public GdbFeatureWriter createFeatureClass(FeatureClassDefinition definition) throws IOException {
    if (closed) {
      throw new IllegalStateException("File geodatabase is already closed");
    }
    checkCatalogWritable();
    checkName(definition.name());
    checkFields(definition.fields(), definition.geometry().name());
    GeometryFieldDefinition geometry = definition.geometry();
    Path tableFile = directory.resolve(GdbPaths.tableFileName(nextTableNumber));
    if (Files.exists(tableFile))
      throw new IOException("Physical table already exists: " + tableFile);
    TableFileWriter table =
        TableFileWriter.create(tableFile, geometry.kind(), geometry.hasZ(), geometry.hasM());
    try {
      for (FileGdbField field : definition.fields()) {
        table.addField(field);
      }
      FileGdbGeomField geomField =
          new FileGdbGeomField(
              geometry.name(), geometry.alias(), geometry.nullable(), geometry.wkt(), geometry);
      table.addGeometryField(geomField);
      table.setSpatialIndex(definition.spatialIndex());
      table.writeFieldDescriptors();

      String layerGuid = Uuids.generate();
      addSpatialRef(geometry.wkt(), geometry.precision());

      systemCatalog.writeRow(new Object[] {definition.name(), 0L}, null);
      itemRelationships.writeRow(
          new Object[] {Uuids.generate(), rootGuid, layerGuid, DATASET_IN_FOLDER_UUID, null, null},
          null);

      String xml = DefinitionXmlWriter.featureClass(definition, (int) items.totalRecordCount() + 1);
      String physicalName = definition.name().toUpperCase(Locale.ROOT);
      items.writeRow(
          new Object[] {
            layerGuid,
            FEATURE_CLASS_TYPE_UUID,
            definition.name(),
            physicalName,
            "\\" + definition.name(),
            1L,
            (long) geometry.kind().id(),
            geometry.name(),
            null,
            "",
            xml,
            null,
            null,
            1L,
            null
          },
          null);

      nextTableNumber++;
      datasetUuids.put(definition.name(), layerGuid);
      registerDataset(definition.name(), layerGuid, definition.fields());
      table.setCancellation(cancellation);
      datasetWriters.add(table);
      return new GdbFeatureWriter(definition.name(), table);
    } catch (Exception e) {
      table.close();
      throw e;
    }
  }

  /** Creates an attribute domain item in the catalog. */
  public void createDomain(ch.so.agi.filegdb.catalog.Domain domain) throws IOException {
    if (closed) {
      throw new IllegalStateException("File geodatabase is already closed");
    }
    if (existingCatalog != null) {
      var old =
          domains.values().stream()
              .filter(d -> d.name().equalsIgnoreCase(domain.name()))
              .findFirst();
      if (old.isPresent()) {
        if (!ch.so.agi.filegdb.catalog.DomainValues.equivalent(old.get(), domain))
          throw new IOException("Conflicting existing domain: " + domain.name());
        return;
      }
    }
    checkCatalogWritable();
    checkName(domain.name());
    ch.so.agi.filegdb.catalog.DomainValues.validate(domain);
    boolean coded = domain instanceof ch.so.agi.filegdb.catalog.CodedValueDomain;
    String typeUuid = coded ? CODED_DOMAIN_TYPE_UUID : RANGE_DOMAIN_TYPE_UUID;
    String definitionXml = DefinitionXmlWriter.domain(domain);
    String uuid = Uuids.generate();
    items.writeRow(
        new Object[] {
          uuid,
          typeUuid,
          domain.name(),
          "",
          "",
          null,
          null,
          null,
          null,
          "",
          definitionXml,
          null,
          null,
          null,
          null
        },
        null);
    domains.put(domain.name(), domain);
    domainUuids.put(domain.name(), uuid);
    names.add(domain.name().toLowerCase(Locale.ROOT));
  }

  /** Creates a relationship class item in the catalog. */
  public void createRelationship(RelationshipDefinition definition) throws IOException {
    if (closed) {
      throw new IllegalStateException("File geodatabase is already closed");
    }
    if (existingCatalog != null) {
      var old =
          existingCatalog.relationships().stream()
              .filter(r -> r.name().equalsIgnoreCase(definition.name()))
              .findFirst();
      if (old.isPresent()) {
        var expected =
            ch.so.agi.filegdb.catalog.DefinitionXml.relationship(
                DefinitionXmlWriter.relationship(definition, 0, ""));
        if (!old.get().equals(expected))
          throw new IOException("Conflicting existing relationship: " + definition.name());
        return;
      }
      if (definition.composite()
          || definition.cardinality()
              == ch.so.agi.filegdb.catalog.RelationshipCardinality.MANY_TO_MANY)
        throw new IOException("Only simple 1:1/1:n relationships can be added to existing GDBs");
      if ("OBJECTID".equalsIgnoreCase(definition.originPrimaryKey()))
        throw new IOException("New relationships must use explicit business keys, not OBJECTID");
    }
    checkCatalogWritable();
    checkName(definition.name());
    String originUuid = datasetUuids.get(definition.originClassName());
    String destinationUuid = datasetUuids.get(definition.destinationClassName());
    if (originUuid == null || destinationUuid == null) {
      throw new GdbException(
          "Relationship classes require existing or newly created datasets: "
              + definition.originClassName()
              + " -> "
              + definition.destinationClassName());
    }

    if (definition.cardinality() == null
        || definition.cardinality() == ch.so.agi.filegdb.catalog.RelationshipCardinality.UNKNOWN)
      throw new IllegalArgumentException("Known relationship cardinality required");
    var originType = keyType(definition.originClassName(), definition.originPrimaryKey());
    if (!java.util.Set.of(
            ch.so.agi.filegdb.table.FileGdbFieldType.INT16,
            ch.so.agi.filegdb.table.FileGdbFieldType.INT32,
            ch.so.agi.filegdb.table.FileGdbFieldType.INT64,
            ch.so.agi.filegdb.table.FileGdbFieldType.STRING,
            ch.so.agi.filegdb.table.FileGdbFieldType.GUID,
            ch.so.agi.filegdb.table.FileGdbFieldType.GLOBALID)
        .contains(originType))
      throw new IllegalArgumentException("Unsupported relationship key type: " + originType);
    if (definition.cardinality()
        != ch.so.agi.filegdb.catalog.RelationshipCardinality.MANY_TO_MANY) {
      var targetType = keyType(definition.destinationClassName(), definition.originForeignKey());
      boolean guids =
          (originType == ch.so.agi.filegdb.table.FileGdbFieldType.GLOBALID
              && targetType == ch.so.agi.filegdb.table.FileGdbFieldType.GUID);
      if (originType != targetType && !guids)
        throw new IllegalArgumentException("Incompatible relationship key types");
    }
    String mappingTableOidName = "";
    if (definition.cardinality()
        == ch.so.agi.filegdb.catalog.RelationshipCardinality.MANY_TO_MANY) {
      mappingTableOidName = "OBJECTID";
      createTable(
              TableDefinition.builder(definition.name())
                  .field(FileGdbField.string(definition.originForeignKey(), 255).asNullable())
                  .field(FileGdbField.string(definition.destinationForeignKey(), 255).asNullable())
                  .build())
          .close();
    }

    String xml =
        DefinitionXmlWriter.relationship(
            definition, (int) items.totalRecordCount() + 1, mappingTableOidName);
    long subtype =
        switch (definition.cardinality()) {
          case ONE_TO_ONE -> 1;
          case ONE_TO_MANY -> 2;
          case MANY_TO_MANY -> 3;
          case UNKNOWN -> 2;
        };
    String uuid = Uuids.generate();
    items.writeRow(
        new Object[] {
          uuid,
          RELATIONSHIP_TYPE_UUID,
          definition.name(),
          definition.name().toUpperCase(Locale.ROOT),
          "\\" + definition.name(),
          subtype,
          0L,
          null,
          null,
          "",
          xml,
          null,
          null,
          1L,
          null
        },
        null);
    names.add(definition.name().toLowerCase(Locale.ROOT));
    itemRelationships.writeRow(
        new Object[] {Uuids.generate(), rootGuid, uuid, DATASET_IN_FOLDER_UUID, null, 1L}, null);
    itemRelationships.writeRow(
        new Object[] {
          Uuids.generate(), originUuid, uuid, DATASETS_RELATED_THROUGH_UUID, null, null
        },
        null);
    itemRelationships.writeRow(
        new Object[] {
          Uuids.generate(), destinationUuid, uuid, DATASETS_RELATED_THROUGH_UUID, null, null
        },
        null);
  }

  /** Creates a plain attribute table without geometry and registers it. */
  public GdbTableWriter createTable(TableDefinition definition) throws IOException {
    if (closed) {
      throw new IllegalStateException("File geodatabase is already closed");
    }
    String name = definition.name();
    checkName(name);
    checkFields(definition.fields(), null);
    Path tableFile = directory.resolve(GdbPaths.tableFileName(nextTableNumber));
    if (Files.exists(tableFile))
      throw new IOException("Physical table already exists: " + tableFile);
    TableFileWriter table = TableFileWriter.create(tableFile, GeometryKind.NONE, false, false);
    try {
      for (FileGdbField field : definition.fields()) {
        table.addField(field);
      }
      table.writeFieldDescriptors();
      String uuid = Uuids.generate();
      systemCatalog.writeRow(new Object[] {name, 0L}, null);
      itemRelationships.writeRow(
          new Object[] {Uuids.generate(), rootGuid, uuid, DATASET_IN_FOLDER_UUID, null, null},
          null);
      String xml =
          DefinitionXmlWriter.table(name, definition.fields(), (int) items.totalRecordCount() + 1);
      items.writeRow(
          new Object[] {
            uuid,
            TABLE_TYPE_UUID,
            name,
            name.toUpperCase(Locale.ROOT),
            "\\" + name,
            0L,
            0L,
            null,
            null,
            "",
            xml,
            null,
            null,
            1L,
            null
          },
          null);
      nextTableNumber++;
      datasetUuids.put(name, uuid);
      registerDataset(name, uuid, definition.fields());
      table.setCancellation(cancellation);
      datasetWriters.add(table);
      return new GdbTableWriter(name, table);
    } catch (Exception e) {
      table.close();
      throw e;
    }
  }

  private void updateAppendedMetadata() throws IOException {
    if (existingCatalog == null || appended.isEmpty()) return;
    try (var source = ch.so.agi.filegdb.table.FileGdbTableFile.open(items.path())) {
      var fields = source.fields();
      int nameIndex = -1, definitionIndex = -1;
      for (int i = 0; i < fields.size(); i++) {
        if (fields.get(i).name().equals("Name")) nameIndex = i;
        if (fields.get(i).name().equals("Definition")) definitionIndex = i;
      }
      for (long i = 0; i < source.totalRecordCount(); i++) {
        cancellation.run();
        Object[] row = source.readRow(i);
        if (row == null || !appended.contains(row[nameIndex])) continue;
        var dataset = existingCatalog.dataset((String) row[nameIndex]).orElseThrow();
        if (!dataset.isFeatureClass()) continue;
        var writer =
            datasetWriters.stream()
                .filter(w -> w.path().equals(dataset.tableFile()))
                .findFirst()
                .orElseThrow();
        if (!writer.changed()) continue;
        rejectAttributeIndexes(items.path());
        try (var table = ch.so.agi.filegdb.table.FileGdbTableFile.open(dataset.tableFile())) {
          row[definitionIndex] =
              ch.so.agi.filegdb.catalog.DefinitionXml.withStorageExtent(
                  (String) row[definitionIndex],
                  table.geomField().geometry().extent(),
                  Files.exists(ch.so.agi.filegdb.index.SpatialIndex.path(dataset.tableFile())));
        }
        var values = new java.util.ArrayList<Object>();
        for (int f = 0; f < fields.size(); f++)
          if (f != source.objectIdFieldIndex() && f != source.geomFieldIndex()) values.add(row[f]);
        var geometry =
            source.geomFieldIndex() < 0
                ? null
                : (ch.so.agi.filegdb.geometry.FileGdbGeometry) row[source.geomFieldIndex()];
        items.replaceRow(i + 1, values.toArray(), geometry);
      }
    }
  }

  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    IOException error = null;
    for (var writer : datasetWriters) {
      try {
        writer.close();
      } catch (IOException | RuntimeException e) {
        error = new IOException("Cannot close dataset writer", e);
      }
    }
    if (error == null)
      try {
        updateAppendedMetadata();
      } catch (IOException | RuntimeException e) {
        error = new IOException("Cannot update FileGDB metadata", e);
      }
    for (TableFileWriter writer :
        List.of(
            itemRelationshipTypes,
            itemRelationships,
            itemTypes,
            items,
            spatialRefs,
            dbtune,
            systemCatalog)) {
      try {
        writer.close();
      } catch (IOException | RuntimeException e) {
        if (error == null) error = new IOException("Cannot close FileGDB", e);
        else error.addSuppressed(e);
      }
    }
    if (error != null) {
      throw error;
    }
  }

  private void closeQuietly() {
    try {
      close();
    } catch (IOException ignored) {
      // original exception wins
    }
  }

  private void addSpatialRef(String wkt, CoordinatePrecision precision) throws IOException {
    String text = wkt == null || wkt.isBlank() ? "{B286C06B-0879-11D2-AACA-00C04FA33C20}" : wkt;
    if (!spatialRefTexts.add(text)) {
      return;
    }
    spatialRefs.writeRow(
        new Object[] {
          text,
          precision.xOrigin(),
          precision.yOrigin(),
          precision.xyScale(),
          precision.zOrigin(),
          precision.zScale(),
          precision.mOrigin(),
          precision.mScale(),
          precision.xyTolerance(),
          precision.zTolerance(),
          precision.mTolerance()
        },
        null);
  }

  private TableFileWriter createSystemCatalog() throws IOException {
    TableFileWriter table =
        TableFileWriter.create(
            directory.resolve("a00000001.gdbtable"), GeometryKind.NONE, false, false);
    try {
      table.addField(FileGdbField.string("Name", 160));
      table.addField(FileGdbField.integer("FileFormat"));
      table.writeFieldDescriptors();
      for (String name :
          List.of(
              "GDB_SystemCatalog",
              "GDB_DBTune",
              "GDB_SpatialRefs",
              "GDB_Items",
              "GDB_ItemTypes",
              "GDB_ItemRelationships",
              "GDB_ItemRelationshipTypes",
              "GDB_ReplicaLog")) {
        long fileFormat = name.equals("GDB_ReplicaLog") ? 2 : 0;
        table.writeRow(new Object[] {name, fileFormat}, null);
      }
      return table;
    } catch (Exception e) {
      table.close();
      throw e;
    }
  }

  private TableFileWriter createDbtune() throws IOException {
    TableFileWriter table =
        TableFileWriter.create(
            directory.resolve("a00000002.gdbtable"), GeometryKind.NONE, false, false);
    try {
      table.addField(FileGdbField.string("Keyword", 32));
      table.addField(FileGdbField.string("ParameterName", 32));
      table.addField(FileGdbField.string("ConfigString", 2048).asNullable());
      table.writeFieldDescriptors();
      for (String[] row : DBTUNE_ROWS) {
        table.writeRow(new Object[] {row[0], row[1], row[2]}, null);
      }
      return table;
    } catch (Exception e) {
      table.close();
      throw e;
    }
  }

  private TableFileWriter createSpatialRefs() throws IOException {
    TableFileWriter table =
        TableFileWriter.create(
            directory.resolve("a00000003.gdbtable"), GeometryKind.NONE, false, false);
    try {
      table.addField(FileGdbField.string("SRTEXT", 2048));
      for (String name :
          List.of(
              "FalseX",
              "FalseY",
              "XYUnits",
              "FalseZ",
              "ZUnits",
              "FalseM",
              "MUnits",
              "XYTolerance",
              "ZTolerance",
              "MTolerance")) {
        table.addField(FileGdbField.real(name).asNullable());
      }
      table.writeFieldDescriptors();
      return table;
    } catch (Exception e) {
      table.close();
      throw e;
    }
  }

  private TableFileWriter createItems() throws IOException {
    TableFileWriter table =
        TableFileWriter.create(
            directory.resolve("a00000004.gdbtable"), GeometryKind.POLYGON, false, false);
    try {
      table.addField(FileGdbField.globalId("UUID").asRequired().notEditable());
      table.addField(FileGdbField.guid("Type"));
      table.addField(FileGdbField.string("Name", 160).asNullable());
      table.addField(FileGdbField.string("PhysicalName", 160).asNullable());
      table.addField(FileGdbField.string("Path", 260).asNullable());
      table.addField(FileGdbField.integer("DatasetSubtype1").asNullable());
      table.addField(FileGdbField.integer("DatasetSubtype2").asNullable());
      table.addField(FileGdbField.string("DatasetInfo1", 255).asNullable());
      table.addField(FileGdbField.string("DatasetInfo2", 255).asNullable());
      table.addField(FileGdbField.string("URL", 255).asNullable());
      table.addField(FileGdbField.xml("Definition").asNullable());
      table.addField(FileGdbField.xml("Documentation").asNullable());
      table.addField(FileGdbField.xml("ItemInfo").asNullable());
      table.addField(FileGdbField.integer("Properties").asNullable());
      table.addField(FileGdbField.binary("Defaults").asNullable());
      GeometryFieldDefinition shapeDefinition =
          GeometryFieldDefinition.of("Shape", GeometryKind.POLYGON).withWkt(WGS84_WKT);
      table.addGeometryField(new FileGdbGeomField("Shape", "", true, WGS84_WKT, shapeDefinition));
      table.writeFieldDescriptors();

      CoordinatePrecision wgs84Precision =
          new CoordinatePrecision(
              -180, -90, 1000000, 0.000002, -100000, 10000, 0.001, -100000, 10000, 0.001);
      addSpatialRef(WGS84_WKT, wgs84Precision);

      table.writeRow(
          new Object[] {
            rootGuid,
            FOLDER_TYPE_UUID,
            "",
            "",
            "\\",
            null,
            null,
            "",
            "",
            "",
            null,
            null,
            null,
            1L,
            null
          },
          null);
      table.writeRow(
          new Object[] {
            workspaceGuid,
            WORKSPACE_TYPE_UUID,
            "Workspace",
            "WORKSPACE",
            "",
            null,
            null,
            "",
            "",
            "",
            WORKSPACE_XML,
            null,
            null,
            0L,
            null
          },
          null);
      return table;
    } catch (Exception e) {
      table.close();
      throw e;
    }
  }

  private TableFileWriter createItemTypes() throws IOException {
    TableFileWriter table =
        TableFileWriter.create(
            directory.resolve("a00000005.gdbtable"), GeometryKind.NONE, false, false);
    try {
      table.addField(FileGdbField.guid("UUID"));
      table.addField(FileGdbField.guid("ParentTypeID"));
      table.addField(FileGdbField.string("Name", 160));
      table.writeFieldDescriptors();
      for (String[] row : ITEM_TYPES_ROWS) {
        table.writeRow(new Object[] {row[0], row[1], row[2]}, null);
      }
      return table;
    } catch (Exception e) {
      table.close();
      throw e;
    }
  }

  private TableFileWriter createItemRelationships() throws IOException {
    TableFileWriter table =
        TableFileWriter.create(
            directory.resolve("a00000006.gdbtable"), GeometryKind.NONE, false, false);
    try {
      table.addField(FileGdbField.globalId("UUID").asRequired().notEditable());
      table.addField(FileGdbField.guid("OriginID"));
      table.addField(FileGdbField.guid("DestID"));
      table.addField(FileGdbField.guid("Type"));
      table.addField(FileGdbField.xml("Attributes").asNullable());
      table.addField(FileGdbField.integer("Properties").asNullable());
      table.writeFieldDescriptors();
      return table;
    } catch (Exception e) {
      table.close();
      throw e;
    }
  }

  private TableFileWriter createItemRelationshipTypes() throws IOException {
    TableFileWriter table =
        TableFileWriter.create(
            directory.resolve("a00000007.gdbtable"), GeometryKind.NONE, false, false);
    try {
      table.addField(FileGdbField.guid("UUID"));
      table.addField(FileGdbField.guid("OrigItemTypeID"));
      table.addField(FileGdbField.guid("DestItemTypeID"));
      table.addField(FileGdbField.string("Name", 160).asNullable());
      table.addField(FileGdbField.string("ForwardLabel", 255).asNullable());
      table.addField(FileGdbField.string("BackwardLabel", 255).asNullable());
      table.addField(FileGdbField.smallInteger("IsContainment").asNullable());
      table.writeFieldDescriptors();
      for (String[] row : ITEM_RELATIONSHIP_TYPES_ROWS) {
        table.writeRow(
            new Object[] {row[0], row[1], row[2], row[3], row[4], row[5], Long.parseLong(row[6])},
            null);
      }
      return table;
    } catch (Exception e) {
      table.close();
      throw e;
    }
  }

  private static final String[][] DBTUNE_ROWS = {
    {"DEFAULTS", "UI_TEXT", "The default datafile configuration."},
    {"DEFAULTS", "CHARACTER_FORMAT", "UTF8"},
    {"DEFAULTS", "GEOMETRY_FORMAT", "Compressed"},
    {"DEFAULTS", "GEOMETRY_STORAGE", "InLine"},
    {"DEFAULTS", "BLOB_STORAGE", "InLine"},
    {"DEFAULTS", "MAX_FILE_SIZE", "1TB"},
    {"DEFAULTS", "RASTER_STORAGE", "InLine"},
    {"TEXT_UTF16", "UI_TEXT", "The UTF16 text format configuration."},
    {"TEXT_UTF16", "CHARACTER_FORMAT", "UTF16"},
    {"MAX_FILE_SIZE_4GB", "UI_TEXT", "The 4GB maximum datafile size configuration."},
    {"MAX_FILE_SIZE_4GB", "MAX_FILE_SIZE", "4GB"},
    {"MAX_FILE_SIZE_256TB", "UI_TEXT", "The 256TB maximum datafile size configuration."},
    {"MAX_FILE_SIZE_256TB", "MAX_FILE_SIZE", "256TB"},
    {"GEOMETRY_UNCOMPRESSED", "UI_TEXT", "The Uncompressed Geometry configuration."},
    {"GEOMETRY_UNCOMPRESSED", "GEOMETRY_FORMAT", "Uncompressed"},
    {"GEOMETRY_OUTOFLINE", "UI_TEXT", "The Outofline Geometry configuration."},
    {"GEOMETRY_OUTOFLINE", "GEOMETRY_STORAGE", "OutOfLine"},
    {"BLOB_OUTOFLINE", "UI_TEXT", "The Outofline Blob configuration."},
    {"BLOB_OUTOFLINE", "BLOB_STORAGE", "OutOfLine"},
    {"GEOMETRY_AND_BLOB_OUTOFLINE", "UI_TEXT", "The Outofline Geometry and Blob configuration."},
    {"GEOMETRY_AND_BLOB_OUTOFLINE", "GEOMETRY_STORAGE", "OutOfLine"},
    {"GEOMETRY_AND_BLOB_OUTOFLINE", "BLOB_STORAGE", "OutOfLine"},
    {"TERRAIN_DEFAULTS", "UI_TERRAIN_TEXT", "The terrains default configuration."},
    {"TERRAIN_DEFAULTS", "GEOMETRY_STORAGE", "OutOfLine"},
    {"TERRAIN_DEFAULTS", "BLOB_STORAGE", "OutOfLine"},
    {"MOSAICDATASET_DEFAULTS", "UI_MOSAIC_TEXT", "The Outofline Raster and Blob configuration."},
    {"MOSAICDATASET_DEFAULTS", "RASTER_STORAGE", "OutOfLine"},
    {"MOSAICDATASET_DEFAULTS", "BLOB_STORAGE", "OutOfLine"},
    {"MOSAICDATASET_INLINE", "UI_MOSAIC_TEXT", "The mosaic dataset inline configuration."},
    {"MOSAICDATASET_INLINE", "CHARACTER_FORMAT", "UTF8"},
    {"MOSAICDATASET_INLINE", "GEOMETRY_FORMAT", "Compressed"},
    {"MOSAICDATASET_INLINE", "GEOMETRY_STORAGE", "InLine"},
    {"MOSAICDATASET_INLINE", "BLOB_STORAGE", "InLine"},
    {"MOSAICDATASET_INLINE", "MAX_FILE_SIZE", "1TB"},
    {"MOSAICDATASET_INLINE", "RASTER_STORAGE", "InLine"}
  };

  private static final String[][] ITEM_TYPES_ROWS = {
    {"{8405add5-8df8-4227-8fac-3fcade073386}", "{00000000-0000-0000-0000-000000000000}", "Item"},
    {FOLDER_TYPE_UUID, "{8405add5-8df8-4227-8fac-3fcade073386}", "Folder"},
    {
      "{ffd09c28-fe70-4e25-907c-af8e8a5ec5f3}", "{8405add5-8df8-4227-8fac-3fcade073386}", "Resource"
    },
    {"{28da9e89-ff80-4d6d-8926-4ee2b161677d}", "{ffd09c28-fe70-4e25-907c-af8e8a5ec5f3}", "Dataset"},
    {"{fbdd7dd6-4a25-40b7-9a1a-ecc3d1172447}", "{28da9e89-ff80-4d6d-8926-4ee2b161677d}", "Tin"},
    {
      "{d4912162-3413-476e-9da4-2aefbbc16939}",
      "{28da9e89-ff80-4d6d-8926-4ee2b161677d}",
      "AbstractTable"
    },
    {RELATIONSHIP_TYPE_UUID, "{28da9e89-ff80-4d6d-8926-4ee2b161677d}", "Relationship Class"},
    {
      "{74737149-DCB5-4257-8904-B9724E32A530}",
      "{28da9e89-ff80-4d6d-8926-4ee2b161677d}",
      "Feature Dataset"
    },
    {
      "{73718a66-afb9-4b88-a551-cffa0ae12620}",
      "{28da9e89-ff80-4d6d-8926-4ee2b161677d}",
      "Geometric Network"
    },
    {
      "{767152d3-ed66-4325-8774-420d46674e07}", "{28da9e89-ff80-4d6d-8926-4ee2b161677d}", "Topology"
    },
    {
      "{e6302665-416b-44fa-be33-4e15916ba101}",
      "{28da9e89-ff80-4d6d-8926-4ee2b161677d}",
      "Survey Dataset"
    },
    {
      "{d5a40288-029e-4766-8c81-de3f61129371}",
      "{28da9e89-ff80-4d6d-8926-4ee2b161677d}",
      "Schematic Dataset"
    },
    {"{db1b697a-3bb6-426a-98a2-6ee7a4c6aed3}", "{28da9e89-ff80-4d6d-8926-4ee2b161677d}", "Toolbox"},
    {WORKSPACE_TYPE_UUID, "{28da9e89-ff80-4d6d-8926-4ee2b161677d}", "Workspace"},
    {
      "{dc9ef677-1aa3-45a7-8acd-303a5202d0dc}",
      "{28da9e89-ff80-4d6d-8926-4ee2b161677d}",
      "Workspace Extension"
    },
    {
      "{77292603-930f-475d-ae4f-b8970f42f394}",
      "{28da9e89-ff80-4d6d-8926-4ee2b161677d}",
      "Extension Dataset"
    },
    {"{8637f1ed-8c04-4866-a44a-1cb8288b3c63}", "{28da9e89-ff80-4d6d-8926-4ee2b161677d}", "Domain"},
    {"{4ed4a58e-621f-4043-95ed-850fba45fcbc}", "{28da9e89-ff80-4d6d-8926-4ee2b161677d}", "Replica"},
    {
      "{d98421eb-d582-4713-9484-43304d0810f6}",
      "{28da9e89-ff80-4d6d-8926-4ee2b161677d}",
      "Replica Dataset"
    },
    {
      "{dc64b6e4-dc0f-43bd-b4f5-f22385dcf055}",
      "{28da9e89-ff80-4d6d-8926-4ee2b161677d}",
      "Historical Marker"
    },
    {"{cd06bc3b-789d-4c51-aafa-a467912b8965}", "{d4912162-3413-476e-9da4-2aefbbc16939}", "Table"},
    {FEATURE_CLASS_TYPE_UUID, "{d4912162-3413-476e-9da4-2aefbbc16939}", "Feature Class"},
    {
      "{5ed667a3-9ca9-44a2-8029-d95bf23704b9}",
      "{d4912162-3413-476e-9da4-2aefbbc16939}",
      "Raster Dataset"
    },
    {
      "{35b601f7-45ce-4aff-adb7-7702d3839b12}",
      "{d4912162-3413-476e-9da4-2aefbbc16939}",
      "Raster Catalog"
    },
    {
      "{7771fc7d-a38b-4fd3-8225-639d17e9a131}",
      "{77292603-930f-475d-ae4f-b8970f42f394}",
      "Network Dataset"
    },
    {"{76357537-3364-48af-a4be-783c7c28b5cb}", "{77292603-930f-475d-ae4f-b8970f42f394}", "Terrain"},
    {
      "{a3803369-5fc2-4963-bae0-13effc09dd73}",
      "{77292603-930f-475d-ae4f-b8970f42f394}",
      "Parcel Fabric"
    },
    {
      "{a300008d-0cea-4f6a-9dfa-46af829a3df2}",
      "{77292603-930f-475d-ae4f-b8970f42f394}",
      "Representation Class"
    },
    {
      "{787bea35-4a86-494f-bb48-500b96145b58}",
      "{77292603-930f-475d-ae4f-b8970f42f394}",
      "Catalog Dataset"
    },
    {
      "{f8413dcb-2248-4935-bfe9-315f397e5110}",
      "{77292603-930f-475d-ae4f-b8970f42f394}",
      "Mosaic Dataset"
    },
    {
      "{c29da988-8c3e-45f7-8b5c-18e51ee7beb4}",
      "{8637f1ed-8c04-4866-a44a-1cb8288b3c63}",
      "Range Domain"
    },
    {
      "{8c368b12-a12e-4c7e-9638-c9c64e69e98f}",
      "{8637f1ed-8c04-4866-a44a-1cb8288b3c63}",
      "Coded Value Domain"
    }
  };

  private static final String[][] ITEM_RELATIONSHIP_TYPES_ROWS = {
    {
      "{0d10b3a7-2f64-45e6-b7ac-2fc27bf2133c}",
      FOLDER_TYPE_UUID,
      FOLDER_TYPE_UUID,
      "FolderInFolder",
      "Parent Folder Of",
      "Child Folder Of",
      "1"
    },
    {
      "{5dd0c1af-cb3d-4fea-8c51-cb3ba8d77cdb}",
      FOLDER_TYPE_UUID,
      "{8405add5-8df8-4227-8fac-3fcade073386}",
      "ItemInFolder",
      "Contains Item",
      "Contained In Folder",
      "1"
    },
    {
      "{a1633a59-46ba-4448-8706-d8abe2b2b02e}",
      "{74737149-DCB5-4257-8904-B9724E32A530}",
      "{28da9e89-ff80-4d6d-8926-4ee2b161677d}",
      "DatasetInFeatureDataset",
      "Contains Dataset",
      "Contained In FeatureDataset",
      "1"
    },
    {
      DATASET_IN_FOLDER_UUID,
      FOLDER_TYPE_UUID,
      "{28da9e89-ff80-4d6d-8926-4ee2b161677d}",
      "DatasetInFolder",
      "Contains Dataset",
      "Contained in Dataset",
      "1"
    },
    {
      "{17e08adb-2b31-4dcd-8fdd-df529e88f843}",
      "{28da9e89-ff80-4d6d-8926-4ee2b161677d}",
      "{8637f1ed-8c04-4866-a44a-1cb8288b3c63}",
      "DomainInDataset",
      "Contains Domain",
      "Contained in Dataset",
      "0"
    },
    {
      "{725badab-3452-491b-a795-55f32d67229c}",
      "{28da9e89-ff80-4d6d-8926-4ee2b161677d}",
      "{28da9e89-ff80-4d6d-8926-4ee2b161677d}",
      "DatasetsRelatedThrough",
      "Origin Of",
      "Destination Of",
      "0"
    },
    {
      "{d088b110-190b-4229-bdf7-89fddd14d1ea}",
      "{767152d3-ed66-4325-8774-420d46674e07}",
      FEATURE_CLASS_TYPE_UUID,
      "FeatureClassInTopology",
      "Spatially Manages Feature Class",
      "Participates In Topology",
      "0"
    },
    {
      "{dc739a70-9b71-41e8-868c-008cf46f16d7}",
      "{73718a66-afb9-4b88-a551-cffa0ae12620}",
      FEATURE_CLASS_TYPE_UUID,
      "FeatureClassInGeometricNetwork",
      "Spatially Manages Feature Class",
      "Participates In Geometric Network",
      "0"
    },
    {
      "{b32b8563-0b96-4d32-92c4-086423ae9962}",
      "{7771fc7d-a38b-4fd3-8225-639d17e9a131}",
      FEATURE_CLASS_TYPE_UUID,
      "FeatureClassInNetworkDataset",
      "Spatially Manages Feature Class",
      "Participates In Network Dataset",
      "0"
    },
    {
      "{908a4670-1111-48c6-8269-134fdd3fe617}",
      "{7771fc7d-a38b-4fd3-8225-639d17e9a131}",
      "{cd06bc3b-789d-4c51-aafa-a467912b8965}",
      "TableInNetworkDataset",
      "Manages Table",
      "Participates In Network Dataset",
      "0"
    },
    {
      "{55d2f4dc-cb17-4e32-a8c7-47591e8c71de}",
      "{76357537-3364-48af-a4be-783c7c28b5cb}",
      FEATURE_CLASS_TYPE_UUID,
      "FeatureClassInTerrain",
      "Spatially Manages Feature Class",
      "Participates In Terrain",
      "0"
    },
    {
      "{583a5baa-3551-41ae-8aa8-1185719f3889}",
      "{a3803369-5fc2-4963-bae0-13effc09dd73}",
      FEATURE_CLASS_TYPE_UUID,
      "FeatureClassInParcelFabric",
      "Spatially Manages Feature Class",
      "Participates In Parcel Fabric",
      "0"
    },
    {
      "{5f9085e0-788f-4354-ae3c-34c83a7ea784}",
      "{a3803369-5fc2-4963-bae0-13effc09dd73}",
      "{cd06bc3b-789d-4c51-aafa-a467912b8965}",
      "TableInParcelFabric",
      "Manages Table",
      "Participates In Parcel Fabric",
      "0"
    },
    {
      "{e79b44e3-f833-4b12-90a1-364ec4ddc43e}",
      FEATURE_CLASS_TYPE_UUID,
      "{a300008d-0cea-4f6a-9dfa-46af829a3df2}",
      "RepresentationOfFeatureClass",
      "Feature Class Representation",
      "Represented Feature Class",
      "0"
    },
    {
      "{8db31af1-df7c-4632-aa10-3cc44b0c6914}",
      "{4ed4a58e-621f-4043-95ed-850fba45fcbc}",
      "{d98421eb-d582-4713-9484-43304d0810f6}",
      "ReplicaDatasetInReplica",
      "Replicated Dataset",
      "Participates In Replica",
      "1"
    },
    {
      "{d022de33-45bd-424c-88bf-5b1b6b957bd3}",
      "{d98421eb-d582-4713-9484-43304d0810f6}",
      "{28da9e89-ff80-4d6d-8926-4ee2b161677d}",
      "DatasetOfReplicaDataset",
      "Replicated Dataset",
      "Dataset of Replicated Dataset",
      "0"
    }
  };
}
