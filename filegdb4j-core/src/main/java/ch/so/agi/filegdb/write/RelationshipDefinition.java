package ch.so.agi.filegdb.write;

import ch.so.agi.filegdb.catalog.RelationshipCardinality;

/**
 * Definition of a relationship class to create.
 *
 * <pre>{@code
 * RelationshipDefinition.builder("building_entrance")
 *     .originClass("building").destinationClass("entrance")
 *     .cardinality(ONE_TO_MANY)
 *     .originPrimaryKey("globalid")
 *     .originForeignKey("building_id")
 *     .forwardLabel("entrances").backwardLabel("building")
 *     .build();
 * }</pre>
 */
public record RelationshipDefinition(
    String name,
    String originClassName,
    String destinationClassName,
    RelationshipCardinality cardinality,
    String originPrimaryKey,
    String originForeignKey,
    String destinationPrimaryKey,
    String destinationForeignKey,
    String forwardLabel,
    String backwardLabel,
    boolean composite) {

  public static Builder builder(String name) {
    return new Builder(name);
  }

  /** Fluent builder. */
  public static final class Builder {
    private final String name;
    private String originClassName;
    private String destinationClassName;
    private RelationshipCardinality cardinality = RelationshipCardinality.ONE_TO_MANY;
    private String originPrimaryKey;
    private String originForeignKey;
    private String destinationPrimaryKey;
    private String destinationForeignKey;
    private String forwardLabel = "";
    private String backwardLabel = "";
    private boolean composite;

    private Builder(String name) {
      this.name = name;
    }

    public Builder originClass(String originClassName) {
      this.originClassName = originClassName;
      return this;
    }

    public Builder destinationClass(String destinationClassName) {
      this.destinationClassName = destinationClassName;
      return this;
    }

    public Builder cardinality(RelationshipCardinality cardinality) {
      this.cardinality = cardinality;
      return this;
    }

    public Builder originPrimaryKey(String originPrimaryKey) {
      this.originPrimaryKey = originPrimaryKey;
      return this;
    }

    /**
     * For 1:1 and 1:n relationships this is the foreign key column of the
     * destination class, for n:m relationships the origin foreign key of the
     * mapping table.
     */
    public Builder originForeignKey(String originForeignKey) {
      this.originForeignKey = originForeignKey;
      return this;
    }

    public Builder destinationPrimaryKey(String destinationPrimaryKey) {
      this.destinationPrimaryKey = destinationPrimaryKey;
      return this;
    }

    public Builder destinationForeignKey(String destinationForeignKey) {
      this.destinationForeignKey = destinationForeignKey;
      return this;
    }

    public Builder labels(String forwardLabel, String backwardLabel) {
      this.forwardLabel = forwardLabel == null ? "" : forwardLabel;
      this.backwardLabel = backwardLabel == null ? "" : backwardLabel;
      return this;
    }

    public Builder composite(boolean composite) {
      this.composite = composite;
      return this;
    }

    public RelationshipDefinition build() {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Relationship name is required");
      }
      if (originClassName == null || destinationClassName == null) {
        throw new IllegalArgumentException("Origin and destination class are required");
      }
      if (originPrimaryKey == null || originPrimaryKey.isBlank()) {
        throw new IllegalArgumentException("Origin primary key is required");
      }
      if (cardinality == RelationshipCardinality.MANY_TO_MANY) {
        if (originForeignKey == null
            || destinationPrimaryKey == null
            || destinationForeignKey == null) {
          throw new IllegalArgumentException(
              "Many-to-many relationships need origin foreign key, destination primary and"
                  + " destination foreign key");
        }
      } else if (originForeignKey == null || originForeignKey.isBlank()) {
        throw new IllegalArgumentException("Origin foreign key is required");
      }
      return new RelationshipDefinition(
          name,
          originClassName,
          destinationClassName,
          cardinality,
          originPrimaryKey,
          originForeignKey == null ? "" : originForeignKey,
          destinationPrimaryKey == null ? "" : destinationPrimaryKey,
          destinationForeignKey == null ? "" : destinationForeignKey,
          forwardLabel,
          backwardLabel,
          composite);
    }
  }
}
