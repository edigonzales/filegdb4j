package ch.so.agi.filegdb.write;

import ch.so.agi.filegdb.catalog.CrsDefinition;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import ch.so.agi.filegdb.table.FileGdbField;
import java.util.ArrayList;
import java.util.List;

/**
 * Definition of a feature class to create.
 *
 * <pre>{@code
 * FeatureClassDefinition.builder("roads")
 *     .field(FileGdbField.string("name", 255))
 *     .geometry(GeometryFieldDefinition.of("shape", GeometryKind.POLYGON))
 *     .crs(new CrsDefinition(2056, 2056, ""))
 *     .build();
 * }</pre>
 *
 * <p>The object id field is created automatically; the attribute list must not
 * contain geometry or object id fields.
 */
public record FeatureClassDefinition(
    String name,
    String alias,
    List<FileGdbField> fields,
    GeometryFieldDefinition geometry,
    CrsDefinition crs) {

  public FeatureClassDefinition {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Feature class name is required");
    }
    fields = List.copyOf(fields);
    if (crs == null) {
      crs = CrsDefinition.UNKNOWN;
    }
  }

  public static Builder builder(String name) {
    return new Builder(name);
  }

  /** Fluent builder. */
  public static final class Builder {
    private final String name;
    private String alias = "";
    private final List<FileGdbField> fields = new ArrayList<>();
    private GeometryFieldDefinition geometry;
    private CrsDefinition crs = CrsDefinition.UNKNOWN;

    private Builder(String name) {
      this.name = name;
    }

    public Builder alias(String alias) {
      this.alias = alias == null ? "" : alias;
      return this;
    }

    public Builder field(FileGdbField field) {
      fields.add(field);
      return this;
    }

    public Builder geometry(GeometryFieldDefinition geometry) {
      this.geometry = geometry;
      return this;
    }

    public Builder crs(CrsDefinition crs) {
      this.crs = crs;
      return this;
    }

    public FeatureClassDefinition build() {
      if (geometry == null) {
        throw new IllegalStateException("A geometry definition is required for a feature class");
      }
      return new FeatureClassDefinition(name, alias, fields, geometry, crs);
    }
  }
}
