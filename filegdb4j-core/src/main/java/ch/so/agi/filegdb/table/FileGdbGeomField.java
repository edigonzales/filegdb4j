package ch.so.agi.filegdb.table;

import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;

/**
 * Geometry field of a file geodatabase table.
 *
 * <p>{@link #geometry()} carries the structural information decoded from the
 * table header. The WKT usually lives in the catalog definition and is merged
 * by the catalog reader.
 */
public record FileGdbGeomField(
    String name, String alias, boolean nullable, String wkt, GeometryFieldDefinition geometry) {}
