package ch.so.agi.filegdb.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.CodedValue;
import ch.so.agi.filegdb.catalog.CodedValueDomain;
import ch.so.agi.filegdb.catalog.Domain;
import ch.so.agi.filegdb.catalog.RangeDomain;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.table.FileGdbTable;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Domain support, using the GDAL OpenFileGDB test data. */
class DomainsFixtureTest {

  private static Path gdbPath;

  @BeforeAll
  static void setUp() {
    gdbPath = TestData.gdal("Domains.gdb");
    assumeTrue(TestData.available(gdbPath), "Domains.gdb fixture is not available");
  }

  @Test
  void readsCodedAndRangeDomains() throws Exception {
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath)) {
      assertThat(gdb.domains())
          .extracting(Domain::name)
          .containsExactlyInAnyOrder("MedianType", "SpeedLimit", "RoadSurfaceType");

      CodedValueDomain medianType =
          (CodedValueDomain) gdb.domain("MedianType").orElseThrow();
      assertThat(medianType.values())
          .containsExactly(new CodedValue("None", "0"), new CodedValue("Cement", "1"));
      assertThat(medianType.description()).isEqualTo("Road median types.");

      RangeDomain speedLimit = (RangeDomain) gdb.domain("SpeedLimit").orElseThrow();
      assertThat(speedLimit.minValue()).isEqualTo("40");
      assertThat(speedLimit.maxValue()).isEqualTo("100");

      CodedValueDomain roadSurface = (CodedValueDomain) gdb.domain("RoadSurfaceType").orElseThrow();
      assertThat(roadSurface.values()).hasSize(4);
      assertThat(roadSurface.values().get(0)).isEqualTo(new CodedValue("Asphalt", "1"));
    }
  }

  @Test
  void assignsDomainsToFields() throws Exception {
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath);
        FileGdbTable roads = gdb.featureClass("Roads")) {
      assertThat(roads.fields())
          .filteredOn(field -> field.domain() != null)
          .extracting(FileGdbField::name)
          .containsExactlyInAnyOrder("MaxSpeed", "MedianType", "SurfaceType");

      assertThat(roads.domain("MedianType")).contains(gdb.domain("MedianType").orElseThrow());
      assertThat(roads.domain("MaxSpeed")).isPresent();
      assertThat(roads.domain("Name")).isEmpty();
      assertThat(roads.field("MedianType").orElseThrow().domain()).isEqualTo("MedianType");
    }
  }
}
