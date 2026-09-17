package ch.so.agi.filegdb.write;

import ch.so.agi.filegdb.catalog.RelationshipCardinality;
import java.util.Objects;

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
public final class RelationshipDefinition {
  private final String name;
  private final String originClassName;
  private final String destinationClassName;
  private final RelationshipCardinality cardinality;
  private final String originPrimaryKey;
  private final String originForeignKey;
  private final String destinationPrimaryKey;
  private final String destinationForeignKey;
  private final String forwardLabel;
  private final String backwardLabel;
  private final boolean composite;
  private final String mappingTable;
  private final boolean attributed;

  public RelationshipDefinition(
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
    this(
        name,
        originClassName,
        destinationClassName,
        cardinality,
        originPrimaryKey,
        originForeignKey,
        destinationPrimaryKey,
        destinationForeignKey,
        forwardLabel,
        backwardLabel,
        composite,
        null,
        false);
  }

  /**
   * Creates a relationship definition.
   *
   * @param mappingTable existing table used as n:m mapping table; when set, no mapping table is
   *     created and the relationship name must equal the table name (as expected by GDAL/ArcGIS)
   * @param attributed whether the relationship class is attributed (n:m with attributes)
   */
  public RelationshipDefinition(
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
      boolean composite,
      String mappingTable,
      boolean attributed) {
    this.mappingTable = mappingTable;
    this.attributed = attributed;
    this.name = name;
    this.originClassName = originClassName;
    this.destinationClassName = destinationClassName;
    this.cardinality = cardinality;
    this.originPrimaryKey = originPrimaryKey;
    this.originForeignKey = originForeignKey;
    this.destinationPrimaryKey = destinationPrimaryKey;
    this.destinationForeignKey = destinationForeignKey;
    this.forwardLabel = forwardLabel;
    this.backwardLabel = backwardLabel;
    this.composite = composite;
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

  public String originPrimaryKey() {
    return originPrimaryKey;
  }

  public String originForeignKey() {
    return originForeignKey;
  }

  public String destinationPrimaryKey() {
    return destinationPrimaryKey;
  }

  public String destinationForeignKey() {
    return destinationForeignKey;
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

  /** Existing table used as n:m mapping table, or null when filegdb4j creates it. */
  public String mappingTable() {
    return mappingTable;
  }

  /** Whether the relationship class is attributed (n:m with attribute columns). */
  public boolean attributed() {
    return attributed;
  }

  public static Builder builder(String name) {
    return new Builder(name);
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RelationshipDefinition other = (RelationshipDefinition) o;
    return composite == other.composite
        && attributed == other.attributed
        && Objects.equals(mappingTable, other.mappingTable)
        && Objects.equals(name, other.name)
        && Objects.equals(originClassName, other.originClassName)
        && Objects.equals(destinationClassName, other.destinationClassName)
        && Objects.equals(cardinality, other.cardinality)
        && Objects.equals(originPrimaryKey, other.originPrimaryKey)
        && Objects.equals(originForeignKey, other.originForeignKey)
        && Objects.equals(destinationPrimaryKey, other.destinationPrimaryKey)
        && Objects.equals(destinationForeignKey, other.destinationForeignKey)
        && Objects.equals(forwardLabel, other.forwardLabel)
        && Objects.equals(backwardLabel, other.backwardLabel);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(name);
    result = 31 * result + Objects.hashCode(originClassName);
    result = 31 * result + Objects.hashCode(destinationClassName);
    result = 31 * result + Objects.hashCode(cardinality);
    result = 31 * result + Objects.hashCode(originPrimaryKey);
    result = 31 * result + Objects.hashCode(originForeignKey);
    result = 31 * result + Objects.hashCode(destinationPrimaryKey);
    result = 31 * result + Objects.hashCode(destinationForeignKey);
    result = 31 * result + Objects.hashCode(forwardLabel);
    result = 31 * result + Objects.hashCode(backwardLabel);
    result = 31 * result + Boolean.hashCode(composite);
    result = 31 * result + Objects.hashCode(mappingTable);
    result = 31 * result + Boolean.hashCode(attributed);
    return result;
  }

  @Override
  public final String toString() {
    return "RelationshipDefinition[name="
        + name
        + ", originClassName="
        + originClassName
        + ", destinationClassName="
        + destinationClassName
        + ", cardinality="
        + cardinality
        + ", originPrimaryKey="
        + originPrimaryKey
        + ", originForeignKey="
        + originForeignKey
        + ", destinationPrimaryKey="
        + destinationPrimaryKey
        + ", destinationForeignKey="
        + destinationForeignKey
        + ", forwardLabel="
        + forwardLabel
        + ", backwardLabel="
        + backwardLabel
        + ", composite="
        + composite
        + ", mappingTable="
        + mappingTable
        + ", attributed="
        + attributed
        + "]";
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
    private String mappingTable;
    private boolean attributed;

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

    /**
     * Uses an existing table as n:m mapping table instead of creating one. The relationship name
     * must equal the table name.
     */
    public Builder mappingTable(String mappingTable) {
      this.mappingTable = mappingTable;
      return this;
    }

    /** Marks the relationship class as attributed (n:m with attribute columns). */
    public Builder attributed(boolean attributed) {
      this.attributed = attributed;
      return this;
    }

    public RelationshipDefinition build() {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Relationship name is required");
      }
      if (originClassName == null || destinationClassName == null) {
        throw new IllegalArgumentException("Origin and destination class are required");
      }
      if (originPrimaryKey == null || originPrimaryKey.trim().isEmpty()) {
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
      } else if (originForeignKey == null || originForeignKey.trim().isEmpty()) {
        throw new IllegalArgumentException("Origin foreign key is required");
      }
      if (mappingTable != null) {
        if (cardinality != RelationshipCardinality.MANY_TO_MANY) {
          throw new IllegalArgumentException("A mapping table is only supported for n:m relationships");
        }
        if (mappingTable.trim().isEmpty()) {
          throw new IllegalArgumentException("Mapping table name is empty");
        }
        if (!name.equalsIgnoreCase(mappingTable)) {
          throw new IllegalArgumentException(
              "The relationship name must equal the mapping table name: " + name + " != " + mappingTable);
        }
      }
      if (attributed && mappingTable == null) {
        throw new IllegalArgumentException("Attributed relationships require a mapping table");
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
          composite,
          mappingTable,
          attributed);
    }
  }
}
