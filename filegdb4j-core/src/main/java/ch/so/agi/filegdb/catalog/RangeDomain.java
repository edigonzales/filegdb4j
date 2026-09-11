package ch.so.agi.filegdb.catalog;

import ch.so.agi.filegdb.table.FileGdbFieldType;

/** Numeric or date range domain. */
public record RangeDomain(
    String name, FileGdbFieldType fieldType, String description, String minValue, String maxValue)
    implements Domain {}
