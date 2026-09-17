package ch.so.agi.filegdb.table;

import java.util.Objects;

/**
 * Field metadata that lives in the catalog definition XML rather than in the
 * binary table header.
 *
 * <p>Properties:
 * <ul>
 *   <li>{@code domain}: name of the assigned domain, may be null</li>
 *   <li>{@code highPrecision}: whether datetime values keep sub second precision</li>
 * </ul>
 */
public final class FieldMetadata {
  private final String domain;
  private final boolean highPrecision;

  public static final FieldMetadata EMPTY = new FieldMetadata(null, false);

  public FieldMetadata(String domain, boolean highPrecision) {
    this.domain = domain;
    this.highPrecision = highPrecision;
  }

  public String domain() {
    return domain;
  }

  public boolean highPrecision() {
    return highPrecision;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FieldMetadata other = (FieldMetadata) o;
    return highPrecision == other.highPrecision && Objects.equals(domain, other.domain);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(domain);
    result = 31 * result + Boolean.hashCode(highPrecision);
    return result;
  }

  @Override
  public final String toString() {
    return "FieldMetadata[domain=" + domain + ", highPrecision=" + highPrecision + "]";
  }
}
