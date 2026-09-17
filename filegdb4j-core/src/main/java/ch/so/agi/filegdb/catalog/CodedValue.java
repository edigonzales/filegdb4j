package ch.so.agi.filegdb.catalog;

import java.util.Objects;

/**
 * One name/code pair of a coded value domain.
 *
 * <p>Properties:
 * <ul>
 *   <li>{@code name}: display name</li>
 *   <li>{@code code}: stored code as text</li>
 * </ul>
 */
public final class CodedValue {
  private final String name;
  private final String code;

  public CodedValue(String name, String code) {
    this.name = name;
    this.code = code;
  }

  public String name() {
    return name;
  }

  public String code() {
    return code;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CodedValue other = (CodedValue) o;
    return Objects.equals(name, other.name) && Objects.equals(code, other.code);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(name);
    result = 31 * result + Objects.hashCode(code);
    return result;
  }

  @Override
  public final String toString() {
    return "CodedValue[name=" + name + ", code=" + code + "]";
  }
}
