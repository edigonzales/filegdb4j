package ch.so.agi.filegdb;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Exclusive, recoverable directory transaction. Foreign applications must close the GDB first. */
public final class FileGdbEditSession implements AutoCloseable {
  private final Path target, work, backup, journal;
  private final Runnable cancellation;
  private FileChannel lockChannel;
  private FileLock lock;
  private FileGeodatabase database;
  private Map<String, String> original;
  private boolean committed, closed;

  public static FileGdbEditSession open(Path target, boolean create, Runnable cancellation)
      throws IOException {
    return new FileGdbEditSession(target, create, cancellation);
  }

  private FileGdbEditSession(Path requested, boolean create, Runnable cancellation)
      throws IOException {
    this.cancellation = Objects.requireNonNull(cancellation);
    Path normalized = requested.toAbsolutePath().normalize();
    if (Files.exists(normalized)) normalized = normalized.toRealPath();
    else normalized = normalized.getParent().toRealPath().resolve(normalized.getFileName());
    target = normalized;
    if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gdb"))
      throw new IOException("Target must end in .gdb: " + target);
    String prefix = "." + target.getFileName() + ".filegdb4j-";
    work = target.resolveSibling(prefix + "work.gdb");
    backup = target.resolveSibling(prefix + "backup.gdb");
    journal = target.resolveSibling(prefix + "journal");
    try {
      lockChannel =
          FileChannel.open(
              target.resolveSibling(prefix + "lock"),
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE);
      long deadline = System.nanoTime() + 5_000_000_000L;
      while (lock == null) {
        cancellation.run();
        try {
          lock = lockChannel.tryLock();
        } catch (OverlappingFileLockException ignored) {
        }
        if (lock != null) break;
        if (System.nanoTime() >= deadline)
          throw new IOException("FileGDB is already being written: " + target);
        try {
          Thread.sleep(25);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted waiting for FileGDB lock", e);
        }
      }
      recover();
      if (Files.exists(target) == create)
        throw new IOException(
            create ? "Target already exists: " + target : "Target does not exist: " + target);
      if (create) {
        database = FileGeodatabase.create(work);
      } else {
        rejectForeignLocks();
        original = fingerprint(target, cancellation);
        copy(target, work, cancellation);
        if (!original.equals(fingerprint(work, cancellation))
            || !original.equals(fingerprint(target, cancellation)))
          throw new IOException("FileGDB changed while copying: " + target);
        database = FileGeodatabase.openWritable(work, cancellation);
      }
    } catch (IOException | RuntimeException e) {
      try {
        close();
      } catch (Exception cleanup) {
        e.addSuppressed(cleanup);
      }
      throw e;
    }
  }

  public FileGeodatabase database() {
    if (closed) throw new IllegalStateException("Session closed");
    return database;
  }

  public void commit() throws IOException {
    if (closed || committed) throw new IllegalStateException("Session already completed");
    cancellation.run();
    database.close();
    database = null;
    rejectForeignLocks();
    if (original != null && !original.equals(fingerprint(target, cancellation)))
      throw new IOException("Original FileGDB changed during editing: " + target);
    if (original != null && original.equals(fingerprint(work, cancellation))) {
      remove(work);
      committed = true;
      return;
    }
    // Flush every changed file before publishing the commit intent.
    try (var files = Files.walk(work)) {
      for (Path p : files.filter(Files::isRegularFile).toList()) {
        cancellation.run();
        try (var c = FileChannel.open(p, StandardOpenOption.WRITE)) {
          c.force(true);
        }
      }
    }
    cancellation.run();
    state(original == null ? "PREPARED_NEW" : "PREPARED_EXISTING");
    try {
      if (original != null) Files.move(target, backup, StandardCopyOption.ATOMIC_MOVE);
      Files.move(work, target, StandardCopyOption.ATOMIC_MOVE);
      state("COMMITTED");
      committed = true;
    } catch (IOException | RuntimeException e) {
      try {
        recover();
      } catch (Exception recovery) {
        e.addSuppressed(recovery);
      }
      throw e;
    }
    // A cleanup failure does not roll back a durable commit; the next session retries cleanup.
    try {
      remove(backup);
      Files.deleteIfExists(journal);
    } catch (IOException ignored) {
    }
  }

  private void state(String text) throws IOException {
    Path next = journal.resolveSibling(journal.getFileName() + ".next");
    try (var c =
        FileChannel.open(
            next,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE)) {
      var bytes = ByteBuffer.wrap(text.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      while (bytes.hasRemaining()) c.write(bytes);
      c.force(true);
    }
    Files.move(next, journal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
  }

  private void recover() throws IOException {
    if (Files.exists(journal)) {
      String state = Files.readString(journal);
      switch (state) {
        case "COMMITTED" -> {
          if (!Files.isDirectory(target))
            throw new IOException(
                "Committed FileGDB missing; preserve backup for recovery: " + backup);
          remove(backup);
        }
        case "PREPARED_EXISTING" -> {
          if (Files.exists(backup)) {
            remove(target);
            Files.move(backup, target, StandardCopyOption.ATOMIC_MOVE);
          } else if (!Files.isDirectory(target))
            throw new IOException("Cannot recover FileGDB: original and backup missing");
        }
        case "PREPARED_NEW" -> remove(target);
        default -> throw new IOException("Unknown FileGDB recovery state: " + journal);
      }
      Files.delete(journal);
    } else if (Files.exists(backup))
      throw new IOException("Unjournaled FileGDB backup requires inspection: " + backup);
    remove(work);
    Files.deleteIfExists(journal.resolveSibling(journal.getFileName() + ".next"));
  }

  private void rejectForeignLocks() throws IOException {
    if (!Files.isDirectory(target)) return;
    try (var paths = Files.walk(target)) {
      for (Path p : paths.toList()) {
        cancellation.run();
        if (p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lock"))
          throw new IOException("Close other FileGDB users before editing; lock exists: " + p);
      }
    }
  }

  private static Map<String, String> fingerprint(Path root, Runnable cancellation)
      throws IOException {
    Map<String, String> values = new TreeMap<>();
    try (var paths = Files.walk(root)) {
      for (Path p : paths.toList()) {
        cancellation.run();
        if (Files.isSymbolicLink(p))
          throw new IOException("Symbolic links inside FileGDB are unsupported: " + p);
        String name = root.relativize(p).toString();
        if (Files.isDirectory(p)) {
          values.put(name, "directory");
          continue;
        }
        try {
          MessageDigest digest = MessageDigest.getInstance("SHA-256");
          try (var in = Files.newInputStream(p)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) >= 0) {
              cancellation.run();
              digest.update(buffer, 0, n);
            }
          }
          values.put(name, HexFormat.of().formatHex(digest.digest()));
        } catch (java.security.NoSuchAlgorithmException e) {
          throw new AssertionError(e);
        }
      }
    }
    return values;
  }

  private static void copy(Path root, Path target, Runnable cancellation) throws IOException {
    try (var paths = Files.walk(root)) {
      for (Path source : paths.toList()) {
        cancellation.run();
        Path dest = target.resolve(root.relativize(source));
        if (Files.isDirectory(source)) Files.createDirectory(dest);
        else
          try (var in = Files.newInputStream(source);
              var out = Files.newOutputStream(dest, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) >= 0) {
              cancellation.run();
              out.write(buffer, 0, n);
            }
          }
      }
    }
  }

  private static void remove(Path root) throws IOException {
    if (!Files.exists(root)) return;
    try (var paths = Files.walk(root)) {
      for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
    }
  }

  @Override
  public void close() throws IOException {
    if (closed) return;
    closed = true;
    IOException error = null;
    try {
      if (database != null) database.close();
    } catch (IOException | RuntimeException e) {
      error = new IOException("Cannot close edit workspace", e);
    }
    try {
      // Never touch another owner's staging if acquiring our lock failed.
      if (lock != null && !Files.exists(journal)) remove(work);
    } catch (IOException e) {
      if (error == null) error = e;
      else error.addSuppressed(e);
    } finally {
      try {
        if (lock != null) lock.release();
      } finally {
        if (lockChannel != null) lockChannel.close();
      }
    }
    if (error != null) throw error;
  }
}
