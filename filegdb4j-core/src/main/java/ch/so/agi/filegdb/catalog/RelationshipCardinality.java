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
    return switch (value) {
      case "esriRelCardinalityOneToOne" -> ONE_TO_ONE;
      case "esriRelCardinalityOneToMany" -> ONE_TO_MANY;
      case "esriRelCardinalityManyToMany" -> MANY_TO_MANY;
      default -> UNKNOWN;
    };
  }
}
