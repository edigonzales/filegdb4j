package ch.so.agi.filegdb.test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs the reference GDAL command line tools for independent verification. */
public final class Ogr {

  private Ogr() {}

  public static Path ogrInfo() {
    return findExecutable("ogrinfo");
  }

  public static Path ogr2Ogr() {
    return findExecutable("ogr2ogr");
  }

  public static Path findExecutable(String name) {
    String prefix = System.getProperty("gdal.prefix", System.getenv("GDAL_PREFIX"));
    if (prefix != null && !prefix.isBlank()) {
      Path candidate = Path.of(prefix).resolve("bin").resolve(name);
      if (Files.isExecutable(candidate)) {
        return candidate;
      }
    }
    String pathEnvironment = System.getenv("PATH");
    if (pathEnvironment != null) {
      for (String directory : pathEnvironment.split(File.pathSeparator)) {
        if (directory.isBlank()) {
          continue;
        }
        Path candidate = Path.of(directory, name);
        if (Files.isExecutable(candidate)) {
          return candidate;
        }
      }
    }
    Path conda =
        Path.of(System.getProperty("user.home"), "miniforge3", "envs", "gdal", "bin", name);
    if (Files.isExecutable(conda)) {
      return conda;
    }
    Path miniconda =
        Path.of(System.getProperty("user.home"), "miniconda3", "envs", "gdal", "bin", name);
    if (Files.isExecutable(miniconda)) {
      return miniconda;
    }
    Path anaconda =
        Path.of(System.getProperty("user.home"), "anaconda3", "envs", "gdal", "bin", name);
    if (Files.isExecutable(anaconda)) {
      return anaconda;
    }
    Path homebrew = Path.of("/opt/homebrew/bin", name);
    if (Files.isExecutable(homebrew)) {
      return homebrew;
    }
    return null;
  }

  public static String run(Path executable, String... arguments)
      throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    command.add(executable.toString());
    command.addAll(List.of(arguments));
    ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IllegalStateException(
          executable.getFileName() + " exited with " + exitCode + ":\n" + output);
    }
    return output;
  }

  /** Parses {@code ogrinfo -al -so} output into layer name to feature count. */
  public static Map<String, Long> featureCounts(Path gdb) throws IOException, InterruptedException {
    String output = run(ogrInfo(), "-al", "-so", gdb.toString());
    Map<String, Long> result = new LinkedHashMap<>();
    String layer = null;
    for (String line : output.split("\\R")) {
      if (line.startsWith("Layer name: ")) {
        layer = line.substring("Layer name: ".length()).trim();
      } else if (line.startsWith("Feature Count: ") && layer != null) {
        result.put(layer, Long.parseLong(line.substring("Feature Count: ".length()).trim()));
        layer = null;
      }
    }
    return result;
  }

  /** Parses {@code ogrinfo -al -so} output into layer name to geometry type text. */
  public static Map<String, String> geometryTypes(Path gdb)
      throws IOException, InterruptedException {
    String output = run(ogrInfo(), "-al", "-so", gdb.toString());
    Map<String, String> result = new LinkedHashMap<>();
    String layer = null;
    for (String line : output.split("\\R")) {
      if (line.startsWith("Layer name: ")) {
        layer = line.substring("Layer name: ".length()).trim();
      } else if (line.startsWith("Geometry: ") && layer != null) {
        result.put(layer, line.substring("Geometry: ".length()).trim());
        layer = null;
      }
    }
    return result;
  }
}
