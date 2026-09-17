package ch.so.agi.filegdb.write;

import ch.so.agi.filegdb.catalog.CrsDefinition;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import ch.so.agi.filegdb.table.FileGdbField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
 * <p>The object id field is created automatically; the attribute list must not contain geometry or
 * object id fields.
 */
public final class FeatureClassDefinition {
  private final String name;
  private final String alias;
  private final List<FileGdbField> fields;
  private final GeometryFieldDefinition geometry;
  private final CrsDefinition crs;
  private final boolean spatialIndex;

  public FeatureClassDefinition(
      String name,
      String alias,
      List<FileGdbField> fields,
      GeometryFieldDefinition geometry,
      CrsDefinition crs) {
    this(name, alias, fields, geometry, crs, true);
  }

  public FeatureClassDefinition(
      String name,
      String alias,
      List<FileGdbField> fields,
      GeometryFieldDefinition geometry,
      CrsDefinition crs,
      boolean spatialIndex) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Feature class name is required");
    }
    this.name = name;
    this.alias = alias;
    this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
    this.geometry = geometry;
    this.crs = crs == null ? CrsDefinition.UNKNOWN : crs;
    this.spatialIndex = spatialIndex;
  }

  public String name() {
    return name;
  }

  public String alias() {
    return alias;
  }

  public List<FileGdbField> fields() {
    return fields;
  }

  public GeometryFieldDefinition geometry() {
    return geometry;
  }

  public CrsDefinition crs() {
    return crs;
  }

  public boolean spatialIndex() {
    return spatialIndex;
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
    FeatureClassDefinition other = (FeatureClassDefinition) o;
    return spatialIndex == other.spatialIndex
        && Objects.equals(name, other.name)
        && Objects.equals(alias, other.alias)
        && Objects.equals(fields, other.fields)
        && Objects.equals(geometry, other.geometry)
        && Objects.equals(crs, other.crs);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(name);
    result = 31 * result + Objects.hashCode(alias);
    result = 31 * result + Objects.hashCode(fields);
    result = 31 * result + Objects.hashCode(geometry);
    result = 31 * result + Objects.hashCode(crs);
    result = 31 * result + Boolean.hashCode(spatialIndex);
    return result;
  }

  @Override
  public final String toString() {
    return "FeatureClassDefinition[name="
        + name
        + ", alias="
        + alias
        + ", fields="
        + fields
        + ", geometry="
        + geometry
        + ", crs="
        + crs
        + ", spatialIndex="
        + spatialIndex
        + "]";
  }

  /** Fluent builder. */
  public static final class Builder {
    private final String name;
    private String alias = "";
    private final List<FileGdbField> fields = new ArrayList<>();
    private GeometryFieldDefinition geometry;
    private CrsDefinition crs = CrsDefinition.UNKNOWN;
    private boolean spatialIndex = true;

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

    public Builder spatialIndex(boolean enabled) {
      this.spatialIndex = enabled;
      return this;
    }

    public FeatureClassDefinition build() {
      if (geometry == null) {
        throw new IllegalStateException("A geometry definition is required for a feature class");
      }
      return new FeatureClassDefinition(name, alias, fields, geometry, crs, spatialIndex);
    }
  }
}
