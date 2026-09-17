package ch.so.agi.filegdb.table;

import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import java.util.Objects;

/**
 * Geometry field of a file geodatabase table.
 *
 * <p>{@link #geometry()} carries the structural information decoded from the
 * table header. The WKT usually lives in the catalog definition and is merged
 * by the catalog reader.
 */
public final class FileGdbGeomField {
  private final String name;
  private final String alias;
  private final boolean nullable;
  private final String wkt;
  private final GeometryFieldDefinition geometry;

  public FileGdbGeomField(
      String name, String alias, boolean nullable, String wkt, GeometryFieldDefinition geometry) {
    this.name = name;
    this.alias = alias;
    this.nullable = nullable;
    this.wkt = wkt;
    this.geometry = geometry;
  }

  public String name() {
    return name;
  }

  public String alias() {
    return alias;
  }

  public boolean nullable() {
    return nullable;
  }

  public String wkt() {
    return wkt;
  }

  public GeometryFieldDefinition geometry() {
    return geometry;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FileGdbGeomField other = (FileGdbGeomField) o;
    return nullable == other.nullable
        && Objects.equals(name, other.name)
        && Objects.equals(alias, other.alias)
        && Objects.equals(wkt, other.wkt)
        && Objects.equals(geometry, other.geometry);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(name);
    result = 31 * result + Objects.hashCode(alias);
    result = 31 * result + Boolean.hashCode(nullable);
    result = 31 * result + Objects.hashCode(wkt);
    result = 31 * result + Objects.hashCode(geometry);
    return result;
  }

  @Override
  public final String toString() {
    return "FileGdbGeomField[name="
        + name
        + ", alias="
        + alias
        + ", nullable="
        + nullable
        + ", wkt="
        + wkt
        + ", geometry="
        + geometry
        + "]";
  }
}
