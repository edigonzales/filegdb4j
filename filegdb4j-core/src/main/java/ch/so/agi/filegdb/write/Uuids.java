package ch.so.agi.filegdb.write;

import java.util.UUID;

/** Generates the brace wrapped UUID strings used in file geodatabase catalogs. */
final class Uuids {

  private Uuids() {}

  static String generate() {
    return "{" + UUID.randomUUID().toString() + "}";
  }
}
