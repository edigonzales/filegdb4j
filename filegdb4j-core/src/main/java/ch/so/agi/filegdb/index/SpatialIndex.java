package ch.so.agi.filegdb.index;

import ch.so.agi.filegdb.geometry.*;
import ch.so.agi.filegdb.table.FileGdbTableFile;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/**
 * Native FileGDB SPX pages. Layout based on GDAL 3.11.4 filegdbindex{,_write}.cpp, Copyright Even
 * Rouault, MIT. Index cells conservatively cover feature envelopes.
 */
public final class SpatialIndex {
  private static final int PAGE = 4096, CAP = 340, VALUE_OFFSET = 1372;
  private static final long SHIFT = 1L << 29;

  private SpatialIndex() {}

  public static Path path(Path table) {
    return table.resolveSibling(table.getFileName().toString().replace(".gdbtable", ".spx"));
  }

  private record Entry(long key, long id) implements Comparable<Entry> {
    public int compareTo(Entry e) {
      int c = Long.compare(key, e.key);
      return c == 0 ? Long.compare(id, e.id) : c;
    }
  }

  private static ByteBuffer page(int size) {
    return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
  }

  private static long cell(double v, double grid) {
    return (long) Math.floor(v / grid + SHIFT);
  }

  /** Builds a v1 index at close, using bounded sorted runs rather than retaining geometries. */
  public static double build(Path tablePath) throws IOException {
    return build(tablePath, () -> {});
  }

  public static double build(Path tablePath, Runnable cancellation) throws IOException {
    double grid = 1, span = 0, maxAbs = 0;
    long count = 0;
    try (var table = FileGdbTableFile.open(tablePath)) {
      for (long i = 0; i < table.totalRecordCount(); i++) {
        cancellation.run();
        Object[] row = table.readRow(i);
        if (row == null) continue;
        Envelope e = GeometryBounds.of((FileGdbGeometry) row[table.geomFieldIndex()]);
        if (e == null) continue;
        grid = Math.max(grid, Math.max(e.xMax() - e.xMin(), e.yMax() - e.yMin()));
        maxAbs =
            Math.max(
                maxAbs,
                Math.max(
                    Math.max(Math.abs(e.xMin()), Math.abs(e.xMax())),
                    Math.max(Math.abs(e.yMin()), Math.abs(e.yMax()))));
        count++;
      }
      Envelope extent = table.geomField().geometry().extent();
      if (extent != null)
        span = Math.max(extent.xMax() - extent.xMin(), extent.yMax() - extent.yMin());
    }
    // One grid; each envelope spans at most four cells. Keep signed coordinates in range.
    grid =
        Math.max(
            grid,
            Math.max(
                Double.isFinite(span) ? span / Math.sqrt(Math.max(1, count)) : 1,
                maxAbs / (1L << 28)));
    Path work = Files.createTempDirectory(tablePath.getParent(), ".spx-sort-");
    try {
      List<Path> runs = new ArrayList<>();
      List<Entry> entries = new ArrayList<>();
      try (var table = FileGdbTableFile.open(tablePath)) {
        for (long i = 0; i < table.totalRecordCount(); i++) {
          cancellation.run();
          Object[] row = table.readRow(i);
          if (row == null) continue;
          Envelope e = GeometryBounds.of((FileGdbGeometry) row[table.geomFieldIndex()]);
          if (e == null) continue;
          for (long x = cell(e.xMin(), grid); x <= cell(e.xMax(), grid); x++)
            for (long y = cell(e.yMin(), grid); y <= cell(e.yMax(), grid); y++)
              entries.add(new Entry((x << 31) | y, i + 1));
          if (entries.size() >= 65536) {
            runs.add(spill(work, entries, runs.size()));
            entries.clear();
          }
        }
      }
      if (!entries.isEmpty() || runs.isEmpty()) runs.add(spill(work, entries, runs.size()));
      int generation = 0;
      while (runs.size() > 64) {
        cancellation.run();
        List<Path> merged = new ArrayList<>();
        for (int start = 0; start < runs.size(); start += 64) {
          var batch = runs.subList(start, Math.min(start + 64, runs.size()));
          Path target = work.resolve("merge-" + generation + "-" + start);
          merge(batch, target, cancellation);
          merged.add(target);
          for (Path old : batch) Files.delete(old);
        }
        runs = merged;
        generation++;
      }
      Path sorted = work.resolve("sorted");
      long total = merge(runs, sorted, cancellation);
      if (total > Integer.MAX_VALUE) throw new IOException("Spatial index exceeds v1 entry limit");
      writePages(sorted, total, path(tablePath), cancellation);
      return grid;
    } finally {
      try (var files = Files.walk(work)) {
        for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
      }
    }
  }

  private static Path spill(Path work, List<Entry> values, int n) throws IOException {
    Collections.sort(values);
    Path p = work.resolve("run" + n);
    try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(p)))) {
      for (Entry e : values) {
        out.writeLong(e.key);
        out.writeLong(e.id);
      }
    }
    return p;
  }

  private record Head(Entry entry, int run) {}

  private static long merge(List<Path> runs, Path target, Runnable cancellation)
      throws IOException {
    List<DataInputStream> streams = new ArrayList<>();
    var queue = new PriorityQueue<Head>(Comparator.comparing(Head::entry));
    long count = 0;
    try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(target)))) {
      for (Path run : runs) {
        var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(run)));
        streams.add(in);
        Entry e = next(in);
        if (e != null) queue.add(new Head(e, streams.size() - 1));
      }
      while (!queue.isEmpty()) {
        cancellation.run();
        Head h = queue.remove();
        out.writeLong(h.entry.key);
        out.writeLong(h.entry.id);
        count++;
        Entry e = next(streams.get(h.run));
        if (e != null) queue.add(new Head(e, h.run));
      }
    } finally {
      for (var in : streams) in.close();
    }
    return count;
  }

  private static Entry next(DataInputStream in) throws IOException {
    try {
      return new Entry(in.readLong(), in.readLong());
    } catch (EOFException e) {
      return null;
    }
  }

  private static final class Node {
    int id;
    long first, count, max;
    List<Node> children;

    Node(long first, long count, long max) {
      this.first = first;
      this.count = count;
      this.max = max;
    }
  }

  private static void writePages(Path sorted, long total, Path output, Runnable cancellation)
      throws IOException {
    try (var data = new RandomAccessFile(sorted.toFile(), "r");
        var out = new RandomAccessFile(output.toFile(), "rw")) {
      out.setLength(0);
      List<Node> level = new ArrayList<>();
      for (long i = 0; i < Math.max(1, total); i += CAP) {
        long n = Math.min(CAP, total - i);
        long max = 0;
        if (n > 0) {
          data.seek((i + n - 1) * 16);
          max = data.readLong();
        }
        level.add(new Node(i, n, max));
      }
      int depth = 1;
      while (level.size() > 1) {
        List<Node> parent = new ArrayList<>();
        for (int i = 0; i < level.size(); i += CAP) {
          var children = new ArrayList<>(level.subList(i, Math.min(i + CAP, level.size())));
          var node = new Node(0, 0, children.getLast().max);
          node.children = children;
          parent.add(node);
        }
        level = parent;
        depth++;
      }
      if (depth > 4) throw new IOException("Spatial index exceeds supported depth");
      List<Node> all = new ArrayList<>();
      all.add(level.getFirst());
      for (int i = 0; i < all.size(); i++) {
        Node n = all.get(i);
        n.id = i + 1;
        if (n.children != null) all.addAll(n.children);
      }
      for (int ni = 0; ni < all.size(); ni++) {
        cancellation.run();
        Node n = all.get(ni);
        ByteBuffer b = page(PAGE);
        if (n.children == null) {
          b.putInt(0, ni + 1 < all.size() ? ni + 2 : 0);
          b.putInt(4, (int) n.count);
          data.seek(n.first * 16);
          for (int i = 0; i < n.count; i++) {
            long key = data.readLong(), id = data.readLong();
            b.putInt(12 + i * 4, (int) id);
            b.putLong(VALUE_OFFSET + i * 8, key);
          }
        } else {
          int size = n.children.size();
          b.putInt(4, Math.max(1, size - 1));
          for (int i = 0; i < size; i++) b.putInt(8 + i * 4, n.children.get(i).id);
          for (int i = 0; i < Math.max(1, size - 1); i++)
            b.putLong(VALUE_OFFSET + i * 8, n.children.get(i).max);
        }
        out.write(b.array());
      }
      ByteBuffer trailer = page(22);
      trailer
          .put((byte) 8)
          .put((byte) 0x40)
          .putInt(1)
          .putInt(depth)
          .putInt((int) total)
          .putInt(0)
          .putInt(1);
      out.write(trailer.array());
    }
  }

  /** Returns candidate object IDs; consumers must apply their final geometric predicate. */
  public static SortedSet<Long> candidates(Path index, List<Double> grids, Envelope filter)
      throws IOException {
    return scan(index, grids, filter, () -> {});
  }

  public static void validate(Path index, List<Double> grids, Runnable cancellation)
      throws IOException {
    scan(index, grids, null, cancellation);
  }

  private static SortedSet<Long> scan(
      Path index, List<Double> grids, Envelope filter, Runnable cancellation) throws IOException {
    try (var in = new RandomAccessFile(index.toFile(), "r")) {
      if (in.length() < 22) throw new IOException("Truncated spatial index");
      in.seek(in.length() - 4);
      int version = Integer.reverseBytes(in.readInt());
      if (version != 1 && version != 2)
        throw new UnsupportedOperationException("Unsupported SPX version: " + version);
      int size = version == 1 ? 4096 : 65536, trailerSize = version == 1 ? 22 : 30;
      if (in.length() < size + trailerSize || (in.length() - trailerSize) % size != 0)
        throw new IOException("Invalid SPX file size");
      byte[] raw = new byte[trailerSize];
      in.seek(in.length() - trailerSize);
      in.readFully(raw);
      ByteBuffer t = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
      int depth = t.getInt(6);
      if (raw[0] != 8 || t.getInt(2) != 1 || depth < 1 || depth > 4)
        throw new IOException("Invalid SPX trailer");
      SortedSet<Long> result = new TreeSet<>();
      readNode(
          in,
          1,
          depth,
          version,
          size,
          (in.length() - trailerSize) / size,
          grids,
          filter,
          result,
          new HashSet<>(),
          cancellation);
      return result;
    }
  }

  private static boolean cellIntersects(long key, List<Double> grids, Envelope f)
      throws IOException {
    int grid = (int) (key >>> 62);
    if (grid >= grids.size()) throw new IOException("SPX grid missing");
    double step = grids.get(grid), shift = SHIFT * grids.getFirst() / step;
    if (!Double.isFinite(step) || step <= 0) throw new IOException("Invalid spatial grid");
    long x = (key >>> 31) & 0x7fffffffL, y = key & 0x7fffffffL;
    return x >= Math.floor(f.xMin() / step + shift)
        && x <= Math.floor(f.xMax() / step + shift)
        && y >= Math.floor(f.yMin() / step + shift)
        && y <= Math.floor(f.yMax() / step + shift);
  }

  private static void readNode(
      RandomAccessFile in,
      long id,
      int depth,
      int version,
      int size,
      long pages,
      List<Double> grids,
      Envelope f,
      SortedSet<Long> result,
      Set<Long> visited,
      Runnable cancellation)
      throws IOException {
    cancellation.run();
    if (id < 1 || id > pages || !visited.add(id))
      throw new IOException("Invalid or cyclic SPX page reference");
    byte[] raw = new byte[size];
    in.seek((id - 1) * size);
    in.readFully(raw);
    ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
    int oid = version == 1 ? 4 : 8,
        header = version == 1 ? 12 : 20,
        cap = (size - header) / (oid + 8),
        valueOffset = header + cap * oid;
    int n = b.getInt(oid);
    if (n < 0 || n > cap) throw new IOException("Invalid SPX page count");
    if (depth == 1) {
      for (int i = 0; i < n; i++)
        if (f == null || cellIntersects(b.getLong(valueOffset + i * 8), grids, f)) {
          long feature =
              oid == 4
                  ? Integer.toUnsignedLong(b.getInt(header + i * oid))
                  : b.getLong(header + i * oid);
          if (feature < 1) throw new IOException("Invalid SPX object ID");
          if (f != null) result.add(feature);
        }
    } else {
      if (n == 0) throw new IOException("Empty non-leaf SPX page");
      int childOffset = version == 1 ? 8 : 12;
      // Separator ranges are inclusive because one cell key may span several leaves.
      for (int i = 0; i <= n; i++) {
        long child =
            oid == 4
                ? Integer.toUnsignedLong(b.getInt(childOffset + i * oid))
                : b.getLong(childOffset + i * oid);
        if (child == 0 && n == 1 && i == 1) continue;
        long low = i == 0 ? Long.MIN_VALUE : b.getLong(valueOffset + (i - 1) * 8);
        long high = i == n ? Long.MAX_VALUE : b.getLong(valueOffset + i * 8);
        if (f != null && grids.size() == 1) {
          double step = grids.getFirst();
          long xmin = Math.max(0, cell(f.xMin(), step)),
              xmax = Math.min(0x7fffffffL, cell(f.xMax(), step));
          long ymin = Math.max(0, cell(f.yMin(), step)),
              ymax = Math.min(0x7fffffffL, cell(f.yMax(), step));
          if (xmin > xmax
              || ymin > ymax
              || high < ((xmin << 31) | ymin)
              || low > ((xmax << 31) | ymax)) continue;
        }
        readNode(
            in, child, depth - 1, version, size, pages, grids, f, result, visited, cancellation);
      }
    }
  }
}
