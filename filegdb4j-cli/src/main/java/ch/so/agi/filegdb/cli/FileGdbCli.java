package ch.so.agi.filegdb.cli;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.CrsDefinition;
import ch.so.agi.filegdb.catalog.Dataset;
import ch.so.agi.filegdb.geometry.FileGdbGeometry;
import ch.so.agi.filegdb.geometry.GeometryKind;
import ch.so.agi.filegdb.jts.JtsGeometryReader;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.table.FileGdbRow;
import ch.so.agi.filegdb.table.FileGdbTable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.io.WKTWriter;

/** Small command line front end for inspecting a file geodatabase. */
public final class FileGdbCli {

  private static final String USAGE =
      """
      Usage:
        filegdb4j info <database.gdb>
        filegdb4j dump <database.gdb> <layer> [--limit N]

      Commands:
        info   list datasets with geometry type, row count, CRS and field count
        dump   print decoded rows, geometry as WKT
      """;

  private FileGdbCli() {}

  public static void main(String[] args) {
    try {
      System.exit(run(args));
    } catch (Exception e) {
      System.err.println("error: " + e.getMessage());
      System.exit(1);
    }
  }

  static int run(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.print(USAGE);
      return 2;
    }
    Path database = Path.of(args[1]);
    switch (args[0]) {
      case "info" -> info(database);
      case "dump" -> dump(database, args);
      default -> {
        System.err.print(USAGE);
        return 2;
      }
    }
    return 0;
  }

  private static void info(Path database) throws Exception {
    try (FileGeodatabase gdb = FileGeodatabase.open(database)) {
      List<Dataset> datasets = gdb.datasets();
      System.out.println("File geodatabase: " + gdb.path());
      System.out.println("Datasets: " + datasets.size());
      System.out.printf(
          "%-32s %-14s %-12s %8s %-8s %6s%n",
          "NAME", "KIND", "GEOMETRY", "ROWS", "CRS", "FIELDS");
      for (Dataset dataset : datasets) {
        try (FileGdbTable table = gdb.table(dataset.name())) {
          GeometryKind kind = table.geomField() == null ? null : table.geomField().geometry().kind();
          CrsDefinition crs = dataset.crs();
          String crsText = crs.isDefined() ? Integer.toString(crs.effectiveWkid()) : "-";
          System.out.printf(
              "%-32s %-14s %-12s %8d %-8s %6d%n",
              dataset.name(),
              dataset.isFeatureClass() ? "feature class" : "table",
              kind == null ? "-" : kind.name(),
              table.rowCount(),
              crsText.isBlank() || crsText.equals("0") ? "-" : crsText,
              table.fields().size());
        }
      }
    }
  }

  private static void dump(Path database, String[] args) throws Exception {
    if (args.length < 3) {
      System.err.print(USAGE);
      throw new IllegalArgumentException("Missing layer name");
    }
    String layerName = args[2];
    long limit = Long.MAX_VALUE;
    for (int i = 3; i < args.length; i++) {
      if (args[i].equals("--limit") && i + 1 < args.length) {
        limit = Long.parseLong(args[++i]);
      } else {
        throw new IllegalArgumentException("Unknown option: " + args[i]);
      }
    }

    try (FileGeodatabase gdb = FileGeodatabase.open(database);
        FileGdbTable table = gdb.table(layerName)) {
      List<FileGdbField> fields = table.fields();
      List<String> names = new ArrayList<>(fields.size());
      for (FileGdbField field : fields) {
        names.add(field.name());
      }
      System.out.println("layer\t" + table.name());
      System.out.println("rows\t" + table.rowCount());
      System.out.println("fields\t" + String.join(",", names));
      System.out.println();

      JtsGeometryReader jts = new JtsGeometryReader(table.crs().effectiveWkid());
      WKTWriter wkt = new WKTWriter();
      long count = 0;
      for (FileGdbRow row : table) {
        if (count >= limit) {
          break;
        }
        List<String> cells = new ArrayList<>(fields.size() + 1);
        cells.add(Long.toString(row.objectId()));
        for (int i = 0; i < fields.size(); i++) {
          Object value = row.get(i);
          if (value instanceof FileGdbGeometry) {
            cells.add(wkt.write(jts.read((FileGdbGeometry) value)));
          } else {
            cells.add(value == null ? "" : value.toString());
          }
        }
        System.out.println(String.join("\t", cells));
        count++;
      }
      System.out.println();
      System.out.println("dumped\t" + count);
    }
  }
}
