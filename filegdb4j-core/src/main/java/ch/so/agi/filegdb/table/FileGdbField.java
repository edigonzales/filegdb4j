package ch.so.agi.filegdb.table;

import java.util.Objects;

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
public final class FileGdbField {
  private final String name;
  private final String alias;
  private final FileGdbFieldType type;
  private final boolean nullable;
  private final boolean required;
  private final boolean editable;
  private final int maxWidth;
  private final boolean highPrecision;
  private final String domain;
  private final Object defaultValue;

  public FileGdbField(
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
    this.name = name;
    this.alias = alias;
    this.type = type;
    this.nullable = nullable;
    this.required = required;
    this.editable = editable;
    this.maxWidth = maxWidth;
    this.highPrecision = highPrecision;
    this.domain = domain;
    this.defaultValue = defaultValue;
  }

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

  public String name() {
    return name;
  }

  public String alias() {
    return alias;
  }

  public FileGdbFieldType type() {
    return type;
  }

  public boolean nullable() {
    return nullable;
  }

  public boolean required() {
    return required;
  }

  public boolean editable() {
    return editable;
  }

  public int maxWidth() {
    return maxWidth;
  }

  public boolean highPrecision() {
    return highPrecision;
  }

  public String domain() {
    return domain;
  }

  public Object defaultValue() {
    return defaultValue;
  }

  public FileGdbField withDefaultValue(Object value) {
    return new FileGdbField(
        name, alias, type, nullable, required, editable, maxWidth, highPrecision, domain, value);
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

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FileGdbField other = (FileGdbField) o;
    return nullable == other.nullable
        && required == other.required
        && editable == other.editable
        && maxWidth == other.maxWidth
        && highPrecision == other.highPrecision
        && Objects.equals(name, other.name)
        && Objects.equals(alias, other.alias)
        && type == other.type
        && Objects.equals(domain, other.domain)
        && Objects.equals(defaultValue, other.defaultValue);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(name);
    result = 31 * result + Objects.hashCode(alias);
    result = 31 * result + Objects.hashCode(type);
    result = 31 * result + Boolean.hashCode(nullable);
    result = 31 * result + Boolean.hashCode(required);
    result = 31 * result + Boolean.hashCode(editable);
    result = 31 * result + Integer.hashCode(maxWidth);
    result = 31 * result + Boolean.hashCode(highPrecision);
    result = 31 * result + Objects.hashCode(domain);
    result = 31 * result + Objects.hashCode(defaultValue);
    return result;
  }

  @Override
  public final String toString() {
    return "FileGdbField[name="
        + name
        + ", alias="
        + alias
        + ", type="
        + type
        + ", nullable="
        + nullable
        + ", required="
        + required
        + ", editable="
        + editable
        + ", maxWidth="
        + maxWidth
        + ", highPrecision="
        + highPrecision
        + ", domain="
        + domain
        + ", defaultValue="
        + defaultValue
        + "]";
  }
}
