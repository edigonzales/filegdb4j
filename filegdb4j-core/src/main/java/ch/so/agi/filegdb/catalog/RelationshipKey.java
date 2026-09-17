package ch.so.agi.filegdb.catalog;

import java.util.Objects;

/**
 * Key of a relationship class.
 *
 * @param objectKeyName field name in the origin or destination class or in the
 *     relationship table
 * @param role key role
 */
public final class RelationshipKey {
  private final String objectKeyName;
  private final RelationshipKey.Role role;

  public RelationshipKey(String objectKeyName, RelationshipKey.Role role) {
    this.objectKeyName = objectKeyName;
    this.role = role;
  }

  public String objectKeyName() {
    return objectKeyName;
  }

  public RelationshipKey.Role role() {
    return role;
  }

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
      switch (value) {
        case "esriRelKeyRoleOriginPrimary":
          return ORIGIN_PRIMARY;
        case "esriRelKeyRoleOriginForeign":
          return ORIGIN_FOREIGN;
        case "esriRelKeyRoleDestinationPrimary":
          return DESTINATION_PRIMARY;
        case "esriRelKeyRoleDestinationForeign":
          return DESTINATION_FOREIGN;
        default:
          return UNKNOWN;
      }
    }
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RelationshipKey other = (RelationshipKey) o;
    return Objects.equals(objectKeyName, other.objectKeyName) && Objects.equals(role, other.role);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Objects.hashCode(objectKeyName);
    result = 31 * result + Objects.hashCode(role);
    return result;
  }

  @Override
  public final String toString() {
    return "RelationshipKey[objectKeyName=" + objectKeyName + ", role=" + role + "]";
  }
}
