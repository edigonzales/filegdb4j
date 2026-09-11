package ch.so.agi.filegdb.table;

/**
 * Field metadata that lives in the catalog definition XML rather than in the
 * binary table header.
 *
 * @param domain name of the assigned domain, may be null
 * @param highPrecision whether datetime values keep sub second precision
 */
public record FieldMetadata(String domain, boolean highPrecision) {

  public static final FieldMetadata EMPTY = new FieldMetadata(null, false);
}
