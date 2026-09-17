package ch.so.agi.filegdb.geometry;

/**
 * Geometry value as decoded from a file geodatabase shape buffer.
 *
 * <p>The model deliberately does not depend on JTS or on any Hop type. Curved
 * segments (arc, Bezier, ellipse) are part of the format and will be added to
 * {@link FileGdbPart} as explicit segments.
 */
public interface FileGdbGeometry {}
