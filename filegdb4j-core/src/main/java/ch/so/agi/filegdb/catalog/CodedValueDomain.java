package ch.so.agi.filegdb.catalog;

import ch.so.agi.filegdb.table.FileGdbFieldType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Coded value domain with a list of code/description pairs. */
public final class CodedValueDomain implements Domain {
  private final String name;
  private final FileGdbFieldType fieldType;
  private final String description;
  private final List<CodedValue> values;
  private final DomainSplitPolicy splitPolicy;
  private final DomainMergePolicy mergePolicy;

  public CodedValueDomain(
      String name, FileGdbFieldType fieldType, String description, List<CodedValue> values) {
    this(
        name,
        fieldType,
        description,
        values,
        DomainSplitPolicy.DEFAULT_VALUE,
        DomainMergePolicy.DEFAULT_VALUE);
  }

  public CodedValueDomain(
      String name,
      FileGdbFieldType fieldType,
      String description,
      List<CodedValue> values,
      DomainSplitPolicy splitPolicy,
      DomainMergePolicy mergePolicy) {
    Objects.requireNonNull(splitPolicy);
    Objects.requireNonNull(mergePolicy);
    this.name = name;
    this.fieldType = fieldType;
    this.description = description;
    this.values = Collections.unmodifiableList(new ArrayList<>(values));
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

  @Override
  public List<CodedValue> values() {
    return values;
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
    CodedValueDomain other = (CodedValueDomain) o;
    return Objects.equals(name, other.name)
        && Objects.equals(fieldType, other.fieldType)
        && Objects.equals(description, other.description)
        && Objects.equals(values, other.values)
        && Objects.equals(splitPolicy, other.splitPolicy)
        && Objects.equals(mergePolicy, other.mergePolicy);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(name);
    result = 31 * result + Objects.hashCode(fieldType);
    result = 31 * result + Objects.hashCode(description);
    result = 31 * result + Objects.hashCode(values);
    result = 31 * result + Objects.hashCode(splitPolicy);
    result = 31 * result + Objects.hashCode(mergePolicy);
    return result;
  }

  @Override
  public final String toString() {
    return "CodedValueDomain[name="
        + name
        + ", fieldType="
        + fieldType
        + ", description="
        + description
        + ", values="
        + values
        + ", splitPolicy="
        + splitPolicy
        + ", mergePolicy="
        + mergePolicy
        + "]";
  }
}
