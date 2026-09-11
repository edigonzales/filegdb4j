package ch.so.agi.filegdb.catalog;

/**
 * Key of a relationship class.
 *
 * @param objectKeyName field name in the origin or destination class or in the
 *     relationship table
 * @param role key role
 */
public record RelationshipKey(String objectKeyName, RelationshipKey.Role role) {

  public enum Role {
    ORIGIN_PRIMARY,
    ORIGIN_FOREIGN,
    DESTINATION_PRIMARY,
    DESTINATION_FOREIGN,
    UNKNOWN;

    static Role fromEsri(String value) {
      if (value == null) {
        return UNKNOWN;
      }
      return switch (value) {
        case "esriRelKeyRoleOriginPrimary" -> ORIGIN_PRIMARY;
        case "esriRelKeyRoleOriginForeign" -> ORIGIN_FOREIGN;
        case "esriRelKeyRoleDestinationPrimary" -> DESTINATION_PRIMARY;
        case "esriRelKeyRoleDestinationForeign" -> DESTINATION_FOREIGN;
        default -> UNKNOWN;
      };
    }
  }
}
