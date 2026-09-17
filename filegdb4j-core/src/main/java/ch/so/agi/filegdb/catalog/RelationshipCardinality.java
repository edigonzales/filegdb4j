package ch.so.agi.filegdb.catalog;

/** Cardinality of a relationship class. */
public enum RelationshipCardinality {
  ONE_TO_ONE,
  ONE_TO_MANY,
  MANY_TO_MANY,
  UNKNOWN;

  static RelationshipCardinality fromEsri(String value) {
    if (value == null) {
      return UNKNOWN;
    }
    if ("esriRelCardinalityOneToOne".equals(value)) {
      return ONE_TO_ONE;
    }
    if ("esriRelCardinalityOneToMany".equals(value)) {
      return ONE_TO_MANY;
    }
    if ("esriRelCardinalityManyToMany".equals(value)) {
      return MANY_TO_MANY;
    }
    return UNKNOWN;
  }
}
