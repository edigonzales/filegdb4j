package ch.so.agi.filegdb.write;

import ch.so.agi.filegdb.table.FileGdbField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Definition of a plain attribute table without geometry. */
public final class TableDefinition {
  private final String name;
  private final List<FileGdbField> fields;

  public TableDefinition(String name, List<FileGdbField> fields) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Table name is required");
    }
    this.name = name;
    this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
  }

  public String name() {
    return name;
  }

  public List<FileGdbField> fields() {
    return fields;
  }

  public static Builder builder(String name) {
    return new Builder(name);
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TableDefinition other = (TableDefinition) o;
    return Objects.equals(name, other.name) && Objects.equals(fields, other.fields);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(name);
    result = 31 * result + Objects.hashCode(fields);
    return result;
  }

  @Override
  public final String toString() {
    return "TableDefinition[name=" + name + ", fields=" + fields + "]";
  }

  /** Fluent builder. */
  public static final class Builder {
    private final String name;
    private final List<FileGdbField> fields = new ArrayList<>();

    private Builder(String name) {
      this.name = name;
    }

    public Builder field(FileGdbField field) {
      fields.add(field);
      return this;
    }

    public TableDefinition build() {
      return new TableDefinition(name, fields);
    }
  }
}
