package ch.so.agi.filegdb.catalog;

import ch.so.agi.filegdb.table.FileGdbFieldType;
import java.util.Collections;
import java.util.List;

/**
 * Attribute domain declared in the geodatabase catalog.
 *
 * @see CodedValueDomain
 * @see RangeDomain
 */
public interface Domain {

  String name();

  FileGdbFieldType fieldType();

  String description();

  DomainSplitPolicy splitPolicy();

  DomainMergePolicy mergePolicy();

  /** Coded values of a {@link CodedValueDomain}, empty for range domains. */
  default List<CodedValue> values() {
    return Collections.emptyList();
  }
}
