package ch.so.agi.filegdb.catalog;

import java.util.Objects;
import java.util.UUID;

/**
 * One row of the {@code GDB_Items} catalog table.
 *
 * @param uuid catalog item UUID
 * @param type item type UUID as string
 * @param name item name
 * @param path catalog path of the item
 * @param definition definition XML
 * @param documentation documentation XML
 * @param tableNumber physical table number resolved through the system catalog, zero if none
 */
public final class GdbItem {
  private final UUID uuid;
  private final String type;
  private final String name;
  private final String path;
  private final String definition;
  private final String documentation;
  private final int tableNumber;

  public GdbItem(
      UUID uuid,
      String type,
      String name,
      String path,
      String definition,
      String documentation,
      int tableNumber) {
    this.uuid = uuid;
    this.type = type;
    this.name = name;
    this.path = path;
    this.definition = definition;
    this.documentation = documentation;
    this.tableNumber = tableNumber;
  }

  public UUID uuid() {
    return uuid;
  }

  public String type() {
    return type;
  }

  public String name() {
    return name;
  }

  public String path() {
    return path;
  }

  public String definition() {
    return definition;
  }

  public String documentation() {
    return documentation;
  }

  public int tableNumber() {
    return tableNumber;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GdbItem other = (GdbItem) o;
    return tableNumber == other.tableNumber
        && Objects.equals(uuid, other.uuid)
        && Objects.equals(type, other.type)
        && Objects.equals(name, other.name)
        && Objects.equals(path, other.path)
        && Objects.equals(definition, other.definition)
        && Objects.equals(documentation, other.documentation);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(uuid);
    result = 31 * result + Objects.hashCode(type);
    result = 31 * result + Objects.hashCode(name);
    result = 31 * result + Objects.hashCode(path);
    result = 31 * result + Objects.hashCode(definition);
    result = 31 * result + Objects.hashCode(documentation);
    result = 31 * result + Integer.hashCode(tableNumber);
    return result;
  }

  @Override
  public final String toString() {
    return "GdbItem[uuid="
        + uuid
        + ", type="
        + type
        + ", name="
        + name
        + ", path="
        + path
        + ", definition="
        + definition
        + ", documentation="
        + documentation
        + ", tableNumber="
        + tableNumber
        + "]";
  }
}
