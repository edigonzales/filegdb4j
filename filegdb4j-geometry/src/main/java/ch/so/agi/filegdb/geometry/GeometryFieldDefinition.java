package ch.so.agi.filegdb.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Definition of the geometry field of a feature class.
 *
 * <p>Properties:
 * <ul>
 *   <li>{@code name}: field name</li>
 *   <li>{@code alias}: field alias</li>
 *   <li>{@code nullable}: whether null geometries are allowed</li>
 *   <li>{@code wkt}: spatial reference as WKT, may be empty</li>
 *   <li>{@code kind}: table geometry kind from the table header</li>
 *   <li>{@code hasZ}: whether coordinates carry a Z value</li>
 *   <li>{@code hasM}: whether coordinates carry a measure value</li>
 *   <li>{@code precision}: integer coordinate handling</li>
 *   <li>{@code extent}: feature extent declared in the table header, may be null</li>
 *   <li>{@code spatialIndexGridResolution}: spatial index grid sizes, may be empty</li>
 * </ul>
 */
public final class GeometryFieldDefinition {
  private final String name;
  private final String alias;
  private final boolean nullable;
  private final String wkt;
  private final GeometryKind kind;
  private final boolean hasZ;
  private final boolean hasM;
  private final CoordinatePrecision precision;
  private final Envelope extent;
  private final List<Double> spatialIndexGridResolution;

  /** Default grid settings used by the Esri FileGDB SDK. */
  public static final CoordinatePrecision ARCGIS_DEFAULT_PRECISION =
      new CoordinatePrecision(
          -2147483647,
          -2147483647,
          10000,
          0.001,
          -100000,
          10000,
          0.001,
          -100000,
          10000,
          0.001);

  public GeometryFieldDefinition(
      String name,
      String alias,
      boolean nullable,
      String wkt,
      GeometryKind kind,
      boolean hasZ,
      boolean hasM,
      CoordinatePrecision precision,
      Envelope extent,
      List<Double> spatialIndexGridResolution) {
    this.name = name;
    this.alias = alias;
    this.nullable = nullable;
    this.wkt = wkt;
    this.kind = kind;
    this.hasZ = hasZ;
    this.hasM = hasM;
    this.precision = precision;
    this.extent = extent;
    this.spatialIndexGridResolution =
        spatialIndexGridResolution == null
            ? Collections.<Double>emptyList()
            : Collections.unmodifiableList(new ArrayList<>(spatialIndexGridResolution));
  }

  /** Creates a definition with the default FileGDB grid settings. */
  public static GeometryFieldDefinition of(String name, GeometryKind kind) {
    return new GeometryFieldDefinition(
        name, "", true, "", kind, false, false, ARCGIS_DEFAULT_PRECISION, null, Collections.<Double>emptyList());
  }

  public GeometryFieldDefinition withWkt(String wkt) {
    return new GeometryFieldDefinition(
        name, alias, nullable, wkt, kind, hasZ, hasM, precision, extent,
        spatialIndexGridResolution);
  }

  public GeometryFieldDefinition withZ() {
    return new GeometryFieldDefinition(
        name, alias, nullable, wkt, kind, true, hasM, precision, extent,
        spatialIndexGridResolution);
  }

  public GeometryFieldDefinition withM() {
    return new GeometryFieldDefinition(
        name, alias, nullable, wkt, kind, hasZ, true, precision, extent,
        spatialIndexGridResolution);
  }

  public GeometryFieldDefinition withNullable(boolean nullable) {
    return new GeometryFieldDefinition(
        name, alias, nullable, wkt, kind, hasZ, hasM, precision, extent,
        spatialIndexGridResolution);
  }

  public GeometryFieldDefinition withPrecision(CoordinatePrecision precision) {
    return new GeometryFieldDefinition(
        name, alias, nullable, wkt, kind, hasZ, hasM, precision, extent,
        spatialIndexGridResolution);
  }

  public GeometryFieldDefinition withGridResolution(List<Double> gridResolution) {
    return new GeometryFieldDefinition(
        name, alias, nullable, wkt, kind, hasZ, hasM, precision, extent, gridResolution);
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

  public GeometryKind kind() {
    return kind;
  }

  public boolean hasZ() {
    return hasZ;
  }

  public boolean hasM() {
    return hasM;
  }

  public CoordinatePrecision precision() {
    return precision;
  }

  public Envelope extent() {
    return extent;
  }

  public List<Double> spatialIndexGridResolution() {
    return spatialIndexGridResolution;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GeometryFieldDefinition other = (GeometryFieldDefinition) o;
    return nullable == other.nullable
        && hasZ == other.hasZ
        && hasM == other.hasM
        && Objects.equals(name, other.name)
        && Objects.equals(alias, other.alias)
        && Objects.equals(wkt, other.wkt)
        && kind == other.kind
        && Objects.equals(precision, other.precision)
        && Objects.equals(extent, other.extent)
        && Objects.equals(spatialIndexGridResolution, other.spatialIndexGridResolution);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(name);
    result = 31 * result + Objects.hashCode(alias);
    result = 31 * result + Boolean.hashCode(nullable);
    result = 31 * result + Objects.hashCode(wkt);
    result = 31 * result + Objects.hashCode(kind);
    result = 31 * result + Boolean.hashCode(hasZ);
    result = 31 * result + Boolean.hashCode(hasM);
    result = 31 * result + Objects.hashCode(precision);
    result = 31 * result + Objects.hashCode(extent);
    result = 31 * result + Objects.hashCode(spatialIndexGridResolution);
    return result;
  }

  @Override
  public final String toString() {
    return "GeometryFieldDefinition[name="
        + name
        + ", alias="
        + alias
        + ", nullable="
        + nullable
        + ", wkt="
        + wkt
        + ", kind="
        + kind
        + ", hasZ="
        + hasZ
        + ", hasM="
        + hasM
        + ", precision="
        + precision
        + ", extent="
        + extent
        + ", spatialIndexGridResolution="
        + spatialIndexGridResolution
        + "]";
  }
}
