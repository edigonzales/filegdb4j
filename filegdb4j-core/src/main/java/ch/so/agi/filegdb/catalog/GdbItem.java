package ch.so.agi.filegdb.catalog;

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
public record GdbItem(
    UUID uuid,
    String type,
    String name,
    String path,
    String definition,
    String documentation,
    int tableNumber) {}
