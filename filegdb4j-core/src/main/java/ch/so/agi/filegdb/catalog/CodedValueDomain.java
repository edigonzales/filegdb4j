package ch.so.agi.filegdb.catalog;

import ch.so.agi.filegdb.table.FileGdbFieldType;
import java.util.List;

/** Coded value domain with a list of code/description pairs. */
public record CodedValueDomain(
    String name,
    FileGdbFieldType fieldType,
    String description,
    List<CodedValue> values,
    DomainSplitPolicy splitPolicy,
    DomainMergePolicy mergePolicy)
    implements Domain {

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

  public CodedValueDomain {
    java.util.Objects.requireNonNull(splitPolicy);
    java.util.Objects.requireNonNull(mergePolicy);
    values = List.copyOf(values);
  }
}
