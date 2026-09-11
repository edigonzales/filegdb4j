package ch.so.agi.filegdb.catalog;

/**
 * One name/code pair of a coded value domain.
 *
 * @param name display name
 * @param code stored code as text
 */
public record CodedValue(String name, String code) {}
