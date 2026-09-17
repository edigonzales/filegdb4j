package ch.so.agi.filegdb.catalog;

import ch.so.agi.filegdb.table.FileGdbFieldType;
import java.util.Objects;

/** Numeric or date range domain. */
public final class RangeDomain implements Domain {
  private final String name;
  private final FileGdbFieldType fieldType;
  private final String description;
  private final String minValue;
  private final String maxValue;
  private final DomainSplitPolicy splitPolicy;
  private final DomainMergePolicy mergePolicy;

  public RangeDomain(
      String name,
      FileGdbFieldType fieldType,
      String description,
      String minValue,
      String maxValue) {
    this(
        name,
        fieldType,
        description,
        minValue,
        maxValue,
        DomainSplitPolicy.DEFAULT_VALUE,
        DomainMergePolicy.DEFAULT_VALUE);
  }

  public RangeDomain(
      String name,
      FileGdbFieldType fieldType,
      String description,
      String minValue,
      String maxValue,
      DomainSplitPolicy splitPolicy,
      DomainMergePolicy mergePolicy) {
    Objects.requireNonNull(splitPolicy);
    Objects.requireNonNull(mergePolicy);
    this.name = name;
    this.fieldType = fieldType;
    this.description = description;
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.splitPolicy = splitPolicy;
    this.mergePolicy = mergePolicy;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public FileGdbFieldType fieldType() {
    return fieldType;
  }

  @Override
  public String description() {
    return description;
  }

  public String minValue() {
    return minValue;
  }

  public String maxValue() {
    return maxValue;
  }

  @Override
  public DomainSplitPolicy splitPolicy() {
    return splitPolicy;
  }

  @Override
  public DomainMergePolicy mergePolicy() {
    return mergePolicy;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RangeDomain other = (RangeDomain) o;
    return Objects.equals(name, other.name)
        && Objects.equals(fieldType, other.fieldType)
        && Objects.equals(description, other.description)
        && Objects.equals(minValue, other.minValue)
        && Objects.equals(maxValue, other.maxValue)
        && Objects.equals(splitPolicy, other.splitPolicy)
        && Objects.equals(mergePolicy, other.mergePolicy);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(name);
    result = 31 * result + Objects.hashCode(fieldType);
    result = 31 * result + Objects.hashCode(description);
    result = 31 * result + Objects.hashCode(minValue);
    result = 31 * result + Objects.hashCode(maxValue);
    result = 31 * result + Objects.hashCode(splitPolicy);
    result = 31 * result + Objects.hashCode(mergePolicy);
    return result;
  }

  @Override
  public final String toString() {
    return "RangeDomain[name="
        + name
        + ", fieldType="
        + fieldType
        + ", description="
        + description
        + ", minValue="
        + minValue
        + ", maxValue="
        + maxValue
        + ", splitPolicy="
        + splitPolicy
        + ", mergePolicy="
        + mergePolicy
        + "]";
  }
}
