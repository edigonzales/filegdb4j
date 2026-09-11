package ch.so.agi.filegdb.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.Dataset;
import ch.so.agi.filegdb.table.FileGdbTable;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Compares the Java reader against the GDAL reference tools. */
class OgrCrossCheckTest {

  private static Path gdbPath;
  private static Path ogrInfo;

  @BeforeAll
  static void setUp() {
    gdbPath = TestData.gdal("npl_2546.gdb");
    ogrInfo = Ogr.ogrInfo();
    assumeTrue(TestData.available(gdbPath), "NPL fixture is not available");
    assumeTrue(ogrInfo != null, "ogrinfo is not available");
  }

  @Test
  void featureCountsMatchOgrInfo() throws Exception {
    Map<String, Long> expected = Ogr.featureCounts(gdbPath);
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath)) {
      assertThat(gdb.datasets()).hasSize(expected.size());
      for (Dataset dataset : gdb.datasets()) {
        try (FileGdbTable table = gdb.table(dataset.name())) {
          assertThat(table.rowCount()).as(dataset.name()).isEqualTo(expected.get(dataset.name()));
        }
      }
    }
  }

  @Test
  void geometryClassificationMatchesOgrInfo() throws Exception {
    Map<String, String> expected = Ogr.geometryTypes(gdbPath);
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath)) {
      for (Dataset dataset : gdb.datasets()) {
        String geometry = expected.get(dataset.name());
        assertThat(geometry).as(dataset.name()).isNotNull();
        if (dataset.isFeatureClass()) {
          assertThat(geometry).as(dataset.name()).isNotEqualTo("None");
        } else {
          assertThat(geometry).as(dataset.name()).isEqualTo("None");
        }
      }
    }
  }
}
