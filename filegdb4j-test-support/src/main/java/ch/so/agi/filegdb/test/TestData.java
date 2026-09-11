package ch.so.agi.filegdb.test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates the repositories test fixtures.
 *
 * <p>The directory is passed by the Gradle build through the
 * {@code filegdb.test.data} system property.
 */
public final class TestData {

  private TestData() {}

  public static Path root() {
    String configured = System.getProperty("filegdb.test.data");
    if (configured == null || configured.isBlank()) {
      return null;
    }
    return Path.of(configured);
  }

  public static Path gdal(String name) {
    Path root = root();
    return root == null ? null : root.resolve("gdal").resolve(name);
  }

  public static boolean available(Path path) {
    return path != null && Files.exists(path);
  }
}
