package ch.so.agi.filegdb.catalog;

import ch.so.agi.filegdb.GdbException;
import ch.so.agi.filegdb.io.GdbPaths;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.table.FileGdbFieldType;
import ch.so.agi.filegdb.table.FileGdbTableFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the geodatabase catalog: the system catalog lists the physical tables,
 * {@code GDB_Items} describes the datasets, domains and relationships.
 *
 * <p>The system catalog maps the row order to the physical table number, see
 * GDAL OpenFileGDB {@code ogropenfilegdbdatasource.cpp}.
 */
public final class GdbCatalog {

  private final Path directory;
  private final List<String> tableNames;
  private final Map<String, Integer> tableNumberByName;
  private final List<GdbItem> items;

  private GdbCatalog(
      Path directory,
      List<String> tableNames,
      Map<String, Integer> tableNumberByName,
      List<GdbItem> items) {
    this.directory = directory;
    this.tableNames = tableNames;
    this.tableNumberByName = tableNumberByName;
    this.items = items;
  }

  public static GdbCatalog open(Path directory) throws IOException {
    if (!GdbPaths.isFileGeodatabase(directory)) {
      throw new GdbException("Not a file geodatabase directory: " + directory);
    }
    List<String> tableNames = readSystemCatalog(directory);
    Map<String, Integer> tableNumberByName = new HashMap<>();
    for (int i = 0; i < tableNames.size(); i++) {
      if (tableNames.get(i) != null && !tableNames.get(i).isEmpty()) {
        tableNumberByName.putIfAbsent(tableNames.get(i), i + 1);
      }
    }
    Integer itemsTableNumber = tableNumberByName.get("GDB_Items");
    if (itemsTableNumber == null) {
      throw new GdbException("File geodatabase has no GDB_Items table: " + directory);
    }
    List<GdbItem> items =
        readItems(directory.resolve(GdbPaths.tableFileName(itemsTableNumber)), tableNumberByName);
    return new GdbCatalog(directory, tableNames, tableNumberByName, items);
  }

  public List<GdbItem> items() {
    return items;
  }

  public List<String> tableNames() {
    return tableNames;
  }

  /** Returns the one based physical table number or zero if the name is unknown. */
  public int tableNumber(String name) {
    Integer number = tableNumberByName.get(name);
    return number == null ? 0 : number;
  }

  public Optional<Path> tableFile(String name) {
    int number = tableNumber(name);
    if (number == 0) {
      return Optional.empty();
    }
    Path file = GdbPaths.tableFile(directory, number);
    return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
  }

  public List<Dataset> datasets() {
    List<Dataset> datasets = new ArrayList<>();
    for (GdbItem item : items) {
      if (item.name() == null || item.name().isBlank() || item.tableNumber() == 0) {
        continue;
      }
      String definition = item.definition();
      DatasetKind kind = kindOf(definition);
      if (kind == null) {
        continue;
      }
      Path tableFile = GdbPaths.tableFile(directory, item.tableNumber());
      if (!Files.isRegularFile(tableFile)) {
        continue;
      }
      datasets.add(
          new Dataset(
              item.name(), kind, item.tableNumber(), tableFile, definition, DefinitionXml.crs(definition)));
    }
    return List.copyOf(datasets);
  }

  public Optional<Dataset> dataset(String name) {
    Dataset fallback = null;
    for (Dataset dataset : datasets()) {
      if (dataset.name().equals(name)) {
        return Optional.of(dataset);
      }
      if (fallback == null && dataset.name().equalsIgnoreCase(name)) {
        fallback = dataset;
      }
    }
    return Optional.ofNullable(fallback);
  }

  private static DatasetKind kindOf(String definition) {
    if (DefinitionXml.contains(definition, "DEFeatureClassInfo")) {
      return DatasetKind.FEATURE_CLASS;
    }
    if (DefinitionXml.contains(definition, "DETableInfo")) {
      return DatasetKind.TABLE;
    }
    return null;
  }

  private static List<String> readSystemCatalog(Path directory) throws IOException {
    Path systemCatalog = directory.resolve("a00000001.gdbtable");
    try (FileGdbTableFile table = FileGdbTableFile.open(systemCatalog)) {
      int nameIndex = indexOf(table, "Name");
      if (nameIndex < 0) {
        throw new GdbException("System catalog has no Name field");
      }
      List<String> names = new ArrayList<>((int) table.totalRecordCount());
      for (long row = 0; row < table.totalRecordCount(); row++) {
        Object[] values = table.readRow(row);
        Object name = values == null ? null : values[nameIndex];
        names.add(name == null ? "" : (String) name);
      }
      return List.copyOf(names);
    }
  }

  private static List<GdbItem> readItems(Path itemsFile, Map<String, Integer> tableNumberByName)
      throws IOException {
    try (FileGdbTableFile table = FileGdbTableFile.open(itemsFile)) {
      int uuidIndex = indexOf(table, "UUID");
      int typeIndex = indexOf(table, "Type");
      int nameIndex = indexOf(table, "Name");
      int pathIndex = indexOf(table, "Path");
      int definitionIndex = indexOf(table, "Definition");
      int documentationIndex = indexOf(table, "Documentation");
      if (uuidIndex < 0
          || typeIndex < 0
          || nameIndex < 0
          || pathIndex < 0
          || definitionIndex < 0
          || documentationIndex < 0) {
        throw new GdbException("File geodatabase has an invalid GDB_Items table");
      }
      checkType(table, uuidIndex, FileGdbFieldType.GLOBALID);
      checkType(table, typeIndex, FileGdbFieldType.GUID);
      checkType(table, nameIndex, FileGdbFieldType.STRING);
      checkType(table, pathIndex, FileGdbFieldType.STRING);

      List<GdbItem> items = new ArrayList<>((int) table.totalRecordCount());
      for (long row = 0; row < table.totalRecordCount(); row++) {
        Object[] values = table.readRow(row);
        if (values == null) {
          continue;
        }
        String name = (String) values[nameIndex];
        UUID uuid = (UUID) values[uuidIndex];
        Object type = values[typeIndex];
        int tableNumber =
            name == null ? 0 : tableNumberByName.getOrDefault(name, 0);
        items.add(
            new GdbItem(
                uuid,
                type == null ? "" : type.toString(),
                name,
                (String) values[pathIndex],
                (String) values[definitionIndex],
                (String) values[documentationIndex],
                tableNumber));
      }
      return List.copyOf(items);
    }
  }

  private static int indexOf(FileGdbTableFile table, String fieldName) {
    for (int i = 0; i < table.fields().size(); i++) {
      if (table.fields().get(i).name().equals(fieldName)) {
        return i;
      }
    }
    return -1;
  }

  private static void checkType(FileGdbTableFile table, int index, FileGdbFieldType type) {
    FileGdbField field = table.fields().get(index);
    if (field.type() != type) {
      throw new GdbException(
          "GDB_Items field " + field.name() + " has type " + field.type() + ", expected " + type);
    }
  }
}
