package ch.so.agi.filegdb.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
public final class RelationshipClass {
  private final String name;
  private final String originClassName;
  private final String destinationClassName;
  private final RelationshipCardinality cardinality;
  private final String forwardLabel;
  private final String backwardLabel;
  private final boolean composite;
  private final boolean attributed;
  private final boolean attachment;
  private final List<RelationshipKey> originKeys;
  private final List<RelationshipKey> destinationKeys;

  public RelationshipClass(
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
    this.name = name;
    this.originClassName = originClassName;
    this.destinationClassName = destinationClassName;
    this.cardinality = cardinality;
    this.forwardLabel = forwardLabel;
    this.backwardLabel = backwardLabel;
    this.composite = composite;
    this.attributed = attributed;
    this.attachment = attachment;
    this.originKeys = Collections.unmodifiableList(new ArrayList<>(originKeys));
    this.destinationKeys = Collections.unmodifiableList(new ArrayList<>(destinationKeys));
  }

  public String name() {
    return name;
  }

  public String originClassName() {
    return originClassName;
  }

  public String destinationClassName() {
    return destinationClassName;
  }

  public RelationshipCardinality cardinality() {
    return cardinality;
  }

  public String forwardLabel() {
    return forwardLabel;
  }

  public String backwardLabel() {
    return backwardLabel;
  }

  public boolean composite() {
    return composite;
  }

  public boolean attributed() {
    return attributed;
  }

  public boolean attachment() {
    return attachment;
  }

  public List<RelationshipKey> originKeys() {
    return originKeys;
  }

  public List<RelationshipKey> destinationKeys() {
    return destinationKeys;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RelationshipClass other = (RelationshipClass) o;
    return composite == other.composite
        && attributed == other.attributed
        && attachment == other.attachment
        && Objects.equals(name, other.name)
        && Objects.equals(originClassName, other.originClassName)
        && Objects.equals(destinationClassName, other.destinationClassName)
        && Objects.equals(cardinality, other.cardinality)
        && Objects.equals(forwardLabel, other.forwardLabel)
        && Objects.equals(backwardLabel, other.backwardLabel)
        && Objects.equals(originKeys, other.originKeys)
        && Objects.equals(destinationKeys, other.destinationKeys);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(name);
    result = 31 * result + Objects.hashCode(originClassName);
    result = 31 * result + Objects.hashCode(destinationClassName);
    result = 31 * result + Objects.hashCode(cardinality);
    result = 31 * result + Objects.hashCode(forwardLabel);
    result = 31 * result + Objects.hashCode(backwardLabel);
    result = 31 * result + Boolean.hashCode(composite);
    result = 31 * result + Boolean.hashCode(attributed);
    result = 31 * result + Boolean.hashCode(attachment);
    result = 31 * result + Objects.hashCode(originKeys);
    result = 31 * result + Objects.hashCode(destinationKeys);
    return result;
  }

  @Override
  public final String toString() {
    return "RelationshipClass[name="
        + name
        + ", originClassName="
        + originClassName
        + ", destinationClassName="
        + destinationClassName
        + ", cardinality="
        + cardinality
        + ", forwardLabel="
        + forwardLabel
        + ", backwardLabel="
        + backwardLabel
        + ", composite="
        + composite
        + ", attributed="
        + attributed
        + ", attachment="
        + attachment
        + ", originKeys="
        + originKeys
        + ", destinationKeys="
        + destinationKeys
        + "]";
  }
}
