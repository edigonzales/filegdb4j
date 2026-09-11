package ch.so.agi.filegdb.geometry;

import java.util.List;

/**
 * Definition of the geometry field of a feature class.
 *
 * @param name field name
 * @param alias field alias
 * @param nullable whether null geometries are allowed
 * @param wkt spatial reference as WKT, may be empty
 * @param kind table geometry kind from the table header
 * @param hasZ whether coordinates carry a Z value
 * @param hasM whether coordinates carry a measure value
 * @param precision integer coordinate handling
 * @param extent feature extent declared in the table header, may be null
 * @param spatialIndexGridResolution spatial index grid sizes, may be empty
 */
public record GeometryFieldDefinition(
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

  public GeometryFieldDefinition {
    spatialIndexGridResolution =
        spatialIndexGridResolution == null ? List.of() : List.copyOf(spatialIndexGridResolution);
  }

  /** Creates a definition with the default FileGDB grid settings. */
  public static GeometryFieldDefinition of(String name, GeometryKind kind) {
    return new GeometryFieldDefinition(
        name, "", true, "", kind, false, false, ARCGIS_DEFAULT_PRECISION, null, List.of());
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
}
