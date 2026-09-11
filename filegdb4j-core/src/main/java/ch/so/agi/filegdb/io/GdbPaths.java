package ch.so.agi.filegdb.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Physical layout of a {@code .gdb} directory. */
public final class GdbPaths {

  private GdbPaths() {}

  /** Checks whether the path looks like a file geodatabase directory. */
  public static boolean isFileGeodatabase(Path path) {
    return path != null
        && Files.isDirectory(path)
        && Files.isRegularFile(path.resolve("a00000001.gdbtable"));
  }

  /** Table file name for the one based table number used by the system catalog. */
  public static String tableFileName(int tableNumber) {
    return String.format(Locale.ROOT, "a%08x.gdbtable", tableNumber);
  }

  public static Path tableFile(Path directory, int tableNumber) {
    return directory.resolve(tableFileName(tableNumber));
  }

  /** Returns the companion file of a {@code .gdbtable} file, for example the {@code .gdbtablx}. */
  public static Path companion(Path tableFile, String extension) {
    String name = tableFile.getFileName().toString();
    if (name.toLowerCase(Locale.ROOT).endsWith(".gdbtable")) {
      name = name.substring(0, name.length() - ".gdbtable".length());
    }
    return tableFile.resolveSibling(name + "." + extension);
  }
}
