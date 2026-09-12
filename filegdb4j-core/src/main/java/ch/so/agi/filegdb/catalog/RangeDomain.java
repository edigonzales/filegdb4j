package ch.so.agi.filegdb.catalog;

import ch.so.agi.filegdb.table.FileGdbFieldType;

/** Numeric or date range domain. */
public record RangeDomain(
    String name,
    FileGdbFieldType fieldType,
    String description,
    String minValue,
    String maxValue,
    DomainSplitPolicy splitPolicy,
    DomainMergePolicy mergePolicy)
    implements Domain {
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

  public RangeDomain {
    java.util.Objects.requireNonNull(splitPolicy);
    java.util.Objects.requireNonNull(mergePolicy);
  }
}
