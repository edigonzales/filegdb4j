package ch.so.agi.filegdb.catalog;

import java.util.List;

/**
 * Relationship class declared in the geodatabase catalog.
 *
 * @param name relationship class name
 * @param originClassName origin class name
 * @param destinationClassName destination class name
 * @param cardinality relationship cardinality
 * @param forwardLabel forward path label
 * @param backwardLabel backward path label
 * @param composite whether the relationship is composite
 * @param attributed whether the relationship carries attributes
 * @param attachment whether this is an attachment relationship
 * @param originKeys keys in the origin class
 * @param destinationKeys keys in the destination class
 */
public record RelationshipClass(
    String name,
    String originClassName,
    String destinationClassName,
    RelationshipCardinality cardinality,
    String forwardLabel,
    String backwardLabel,
    boolean composite,
    boolean attributed,
    boolean attachment,
    List<RelationshipKey> originKeys,
    List<RelationshipKey> destinationKeys) {

  public RelationshipClass {
    originKeys = List.copyOf(originKeys);
    destinationKeys = List.copyOf(destinationKeys);
  }
}
