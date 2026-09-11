package ch.so.agi.filegdb.catalog;

import ch.so.agi.filegdb.table.FileGdbFieldType;
import java.util.List;

/** Coded value domain with a list of code/description pairs. */
public record CodedValueDomain(
    String name, FileGdbFieldType fieldType, String description, List<CodedValue> values)
    implements Domain {

  public CodedValueDomain {
    values = List.copyOf(values);
  }
}
