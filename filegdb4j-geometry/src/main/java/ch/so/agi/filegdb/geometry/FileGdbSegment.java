package ch.so.agi.filegdb.geometry;

/**
 * Curved segment of a {@link FileGdbPart}.
 *
 * <p>The segment replaces the straight connection between the point at
 * {@link #startPointIndex()} and the following point of the part.
 */
public interface FileGdbSegment {

  int startPointIndex();
}
