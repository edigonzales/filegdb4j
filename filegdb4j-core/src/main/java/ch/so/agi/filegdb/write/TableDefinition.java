package ch.so.agi.filegdb.write;

import ch.so.agi.filegdb.table.FileGdbField;
import java.util.ArrayList;
import java.util.List;

/** Definition of a plain attribute table without geometry. */
public record TableDefinition(String name, List<FileGdbField> fields) {

  public TableDefinition {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Table name is required");
    }
    fields = List.copyOf(fields);
  }

  public static Builder builder(String name) {
    return new Builder(name);
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
