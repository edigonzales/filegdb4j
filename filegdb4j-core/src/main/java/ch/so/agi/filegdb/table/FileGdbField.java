package ch.so.agi.filegdb.table;

/**
 * Attribute field of a file geodatabase table.
 *
 * @param name field name
 * @param alias field alias
 * @param type field type
 * @param nullable whether the field participates in the null bit mask
 * @param required whether the field is required
 * @param editable whether the field is editable
 * @param maxWidth maximum width in UTF-16 code units for string fields, otherwise zero
 * @param highPrecision whether datetime values keep sub second precision
 * @param domain name of the assigned catalog domain, may be null
 */
public record FileGdbField(
    String name,
    String alias,
    FileGdbFieldType type,
    boolean nullable,
    boolean required,
    boolean editable,
    int maxWidth,
    boolean highPrecision,
    String domain) {

  public FileGdbField(
      String name,
      String alias,
      FileGdbFieldType type,
      boolean nullable,
      boolean required,
      boolean editable,
      int maxWidth) {
    this(name, alias, type, nullable, required, editable, maxWidth, false, null);
  }
}
