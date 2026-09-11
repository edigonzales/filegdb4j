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

  public GeometryFieldDefinition {
    spatialIndexGridResolution =
        spatialIndexGridResolution == null ? List.of() : List.copyOf(spatialIndexGridResolution);
  }
}
