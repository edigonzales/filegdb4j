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
    String domain,
    Object defaultValue) {

  public FileGdbField(
      String name,
      String alias,
      FileGdbFieldType type,
      boolean nullable,
      boolean required,
      boolean editable,
      int maxWidth,
      boolean highPrecision,
      String domain) {
    this(name, alias, type, nullable, required, editable, maxWidth, highPrecision, domain, null);
  }

  public FileGdbField withDefaultValue(Object value) {
    return new FileGdbField(
        name, alias, type, nullable, required, editable, maxWidth, highPrecision, domain, value);
  }

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

  public static FileGdbField string(String name, int maxWidth) {
    return new FileGdbField(name, "", FileGdbFieldType.STRING, false, false, true, maxWidth);
  }

  public static FileGdbField smallInteger(String name) {
    return new FileGdbField(name, "", FileGdbFieldType.INT16, false, false, true, 0);
  }

  public static FileGdbField integer(String name) {
    return new FileGdbField(name, "", FileGdbFieldType.INT32, false, false, true, 0);
  }

  public static FileGdbField bigInteger(String name) {
    return new FileGdbField(name, "", FileGdbFieldType.INT64, false, false, true, 0);
  }

  public static FileGdbField real(String name) {
    return new FileGdbField(name, "", FileGdbFieldType.FLOAT64, false, false, true, 0);
  }

  public static FileGdbField dateTime(String name) {
    return new FileGdbField(name, "", FileGdbFieldType.DATETIME, false, false, true, 0);
  }

  public static FileGdbField binary(String name) {
    return new FileGdbField(name, "", FileGdbFieldType.BINARY, false, false, true, 0);
  }

  public static FileGdbField xml(String name) {
    return new FileGdbField(name, "", FileGdbFieldType.XML, false, false, true, 0);
  }

  public static FileGdbField guid(String name) {
    return new FileGdbField(name, "", FileGdbFieldType.GUID, false, false, true, 0);
  }

  public static FileGdbField globalId(String name) {
    return new FileGdbField(name, "", FileGdbFieldType.GLOBALID, false, false, true, 0);
  }

  public FileGdbField withAlias(String alias) {
    return new FileGdbField(
        name,
        alias,
        type,
        nullable,
        required,
        editable,
        maxWidth,
        highPrecision,
        domain,
        defaultValue);
  }

  public FileGdbField asNullable() {
    return new FileGdbField(
        name, alias, type, true, required, editable, maxWidth, highPrecision, domain, defaultValue);
  }

  public FileGdbField asRequired() {
    return new FileGdbField(
        name, alias, type, nullable, true, editable, maxWidth, highPrecision, domain, defaultValue);
  }

  public FileGdbField notEditable() {
    return new FileGdbField(
        name,
        alias,
        type,
        nullable,
        required,
        false,
        maxWidth,
        highPrecision,
        domain,
        defaultValue);
  }

  public FileGdbField withDomain(String domain) {
    return new FileGdbField(
        name,
        alias,
        type,
        nullable,
        required,
        editable,
        maxWidth,
        highPrecision,
        domain,
        defaultValue);
  }

  public FileGdbField withHighPrecision() {
    return new FileGdbField(
        name, alias, type, nullable, required, editable, maxWidth, true, domain, defaultValue);
  }
}
