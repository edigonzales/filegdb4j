package ch.so.agi.filegdb.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.CodedValue;
import ch.so.agi.filegdb.catalog.CodedValueDomain;
import ch.so.agi.filegdb.catalog.Dataset;
import ch.so.agi.filegdb.catalog.Domain;
import ch.so.agi.filegdb.geometry.FileGdbPart;
import ch.so.agi.filegdb.geometry.FileGdbPoint;
import ch.so.agi.filegdb.geometry.FileGdbPolygon;
import ch.so.agi.filegdb.geometry.FileGdbPolyline;
import ch.so.agi.filegdb.geometry.GeometryKind;
import ch.so.agi.filegdb.jts.JtsGeometryReader;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.table.FileGdbRow;
import ch.so.agi.filegdb.table.FileGdbTable;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

/** Reads the GDAL generated NPL reference geodatabase (Kanton Solothurn). */
class NplFixtureTest {

  private static Path gdbPath;

  @BeforeAll
  static void setUp() {
    gdbPath = TestData.gdal("npl_2546.gdb");
    assumeTrue(TestData.available(gdbPath), "NPL fixture is not available");
  }

  @Test
  void listsDatasets() throws Exception {
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath)) {
      assertThat(gdb.datasets()).hasSize(21);
      Dataset grundnutzung = gdb.dataset("grundnutzung").orElseThrow();
      assertThat(grundnutzung.isFeatureClass()).isTrue();
      assertThat(grundnutzung.definition()).contains("DEFeatureClassInfo");
      assertThat(grundnutzung.crs().effectiveWkid()).isEqualTo(2056);
      assertThat(grundnutzung.crs().wkt()).contains("CH1903+");
      assertThat(gdb.featureClasses()).hasSize(8);
      assertThat(gdb.tables()).hasSize(13);
    }
  }

  @Test
  void readsAttributesAndPolygon() throws Exception {
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath);
        FileGdbTable table = gdb.featureClass("grundnutzung")) {
      assertThat(table.rowCount()).isEqualTo(1148);
      assertThat(table.geomField().geometry().kind()).isEqualTo(GeometryKind.POLYGON);
      assertThat(table.geomField().geometry().hasZ()).isFalse();
      assertThat(table.fields())
          .extracting(FileGdbField::name)
          .contains("geometrie", "T_Id", "typ_bezeichnung", "publiziertab");

      FileGdbRow row = table.read(1);
      assertThat(row).isNotNull();
      assertThat(row.objectId()).isEqualTo(1);
      assertThat(row.get("T_Id")).isEqualTo(1277L);
      assertThat(row.get("typ_bezeichnung"))
          .isEqualTo("Zone für öffentliche Bauten und Anlagen, Bauklasse 0");
      assertThat(row.get("typ_code_kommunal")).isEqualTo(1501L);
      assertThat(row.get("rechtsstatus")).isEqualTo("inKraft");
      assertThat(row.get("publiziertab"))
          .isEqualTo(OffsetDateTime.of(2003, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC));
      assertThat(row.get("erfasser")).isEqualTo("bsb");
      assertThat(row.get("publiziertbis")).isNull();
      assertThat(row.get("datum_erfassung"))
          .isEqualTo(OffsetDateTime.of(2018, 12, 14, 0, 0, 0, 0, ZoneOffset.UTC));
      assertThat(row.get("bfs_nr")).isEqualTo(2546L);
      assertThat(row.get("typ_code_kt")).isEqualTo(150L);
      assertThat(row.get("typ_code_ch")).isEqualTo(15L);

      FileGdbPolygon polygon = (FileGdbPolygon) row.geometry();
      assertThat(polygon.parts()).hasSize(1);
      FileGdbPart ring = polygon.parts().get(0);
      assertThat(ring.points()).hasSize(7);
      FileGdbPoint first = ring.points().get(0);
      assertThat(first.x()).isCloseTo(2597753.064, within(1e-3));
      assertThat(first.y()).isCloseTo(1227286.162, within(1e-3));
      FileGdbPoint last = ring.points().get(ring.points().size() - 1);
      assertThat(last.x()).isEqualTo(first.x());
      assertThat(last.y()).isEqualTo(first.y());
    }
  }

  @Test
  void readsPolylineFeatures() throws Exception {
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath);
        FileGdbTable table = gdb.featureClass("erschliessung_linienobjekt")) {
      assertThat(table.rowCount()).isEqualTo(23);
      assertThat(table.geomField().geometry().kind()).isEqualTo(GeometryKind.LINE);
      FileGdbRow row = table.read(1);
      assertThat(row.geometry()).isInstanceOf(FileGdbPolyline.class);
      FileGdbPolyline polyline = (FileGdbPolyline) row.geometry();
      assertThat(polyline.parts()).isNotEmpty();
    }
  }

  @Test
  void readsPlainTableWithoutGeometry() throws Exception {
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath);
        FileGdbTable table = gdb.table("T_ILI2DB_SETTINGS")) {
      assertThat(table.isFeatureClass()).isFalse();
      assertThat(table.geomField()).isNull();
      assertThat(table.rowCount()).isEqualTo(22);
      FileGdbRow row = table.read(1);
      assertThat(row).isNotNull();
      assertThat(row.get("tag")).isEqualTo("ch.interlis.ili2c.ilidirs");
      assertThat(row.get("setting")).isInstanceOf(String.class);
    }
  }

  @Test
  void resolvesDomainAssignments() throws Exception {
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath)) {
      assertThat(gdb.domains()).isNotEmpty();
      try (FileGdbTable table = gdb.featureClass("grundnutzung")) {
        assertThat(table.field("typ_verbindlichkeit").orElseThrow().domain())
            .isEqualTo(
                "SO_ARP_Nutzungsplanung_Publikation_20201005_Nutzungsplanung_Verbindlichkeit");
        Domain domain = table.domain("typ_verbindlichkeit").orElseThrow();
        assertThat(domain).isInstanceOf(CodedValueDomain.class);
        assertThat(domain.values())
            .extracting(CodedValue::name)
            .contains("Nutzungsplanfestlegung");
      }
    }
  }

  @Test
  void readsPointFeatures() throws Exception {
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath);
        FileGdbTable table = gdb.featureClass("ueberlagernd_punkt")) {
      assertThat(table.rowCount()).isEqualTo(184);
      assertThat(table.geomField().geometry().kind()).isEqualTo(GeometryKind.POINT);
      FileGdbRow row = table.read(1);
      assertThat(row.geometry()).isInstanceOf(FileGdbPoint.class);
      FileGdbPoint point = (FileGdbPoint) row.geometry();
      assertThat(point.x()).isNotNaN();
      assertThat(point.y()).isNotNaN();
    }
  }

  @Test
  void convertsPolygonToJts() throws Exception {
    try (FileGeodatabase gdb = FileGeodatabase.open(gdbPath);
        FileGdbTable table = gdb.featureClass("grundnutzung")) {
      FileGdbRow row = table.read(1);
      JtsGeometryReader reader = new JtsGeometryReader(table.crs().effectiveWkid());
      Geometry geometry = reader.read(row.geometry());
      assertThat(geometry).isInstanceOf(Polygon.class);
      assertThat(geometry.getSRID()).isEqualTo(2056);
      assertThat(geometry.getArea()).isGreaterThan(0);
      assertThat(((Polygon) geometry).getExteriorRing().isClosed()).isTrue();
    }
  }
}
