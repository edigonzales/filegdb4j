# filegdb4j

Pure Java access to the Esri file geodatabase format (`.gdb` directories).

No GDAL/OGR runtime, no FFM, no native libraries. The binary format
implementation is ported from the [GDAL](https://gdal.org) **OpenFileGDB**
driver (MIT licensed). Reference version for the port is **GDAL 3.13.3**
(`ogr/ogrsf_frmts/openfilegdb/`).

The library is developed standalone and free of Apache Hop and ili2db
dependencies. First consumer is the
[hop-vector-raster-plugin](https://github.com/edigonzales/hop-vector-raster-plugin)
Vector Reader/Writer, where the format is available as `FILEGEODATABASE`;
afterwards the library can serve as the backend of `ili2ofgdb`.
- Group id: `ch.so.agi`
- Packages: `ch.so.agi.filegdb`, `ch.so.agi.filegdb.geometry`,
  `ch.so.agi.filegdb.jts`, `ch.so.agi.filegdb.cli`
- Java 21, Gradle (Groovy DSL)

## Status

| Area | State |
|---|---|
| Catalog (`a00000001`, `GDB_Items`), dataset/CRS metadata | done |
| Tables: fields and rows (INT16/32/64, FLOAT32/64, STRING, XML, BINARY, GUID/GLOBALID, DATETIME, DATE, TIME, DATETIME_WITH_OFFSET, OBJECTID) | done |
| Geometry read: Point, MultiPoint, Polyline, Polygon (XY, Z, M), ring organisation | done |
| Geometry read: circular arcs (interior point and center), cubic Bezier, ellipse parameters | done (stroked for JTS) |
| Writer: circular arc segments, XY/XYZ/XYM/XYZM | done |
| Domains (coded value, range) and field domain assignment | read |
| Relationship classes (1:1, 1:n, n:m, composite, attributed, attachment) | read |
| Writer: new dataset, feature class, rows (attributes, Point/MultiPoint/Polyline/Polygon, XY/Z/M) | done |
| Writer: plain attribute tables | done |
| Writer: domains (coded/range) and relationship classes (1:1, 1:n, n:m with mapping table) | done |
| Writer: native spatial index (`.spx`) | built at close by default |
| Writer: attribute indexes, updates/deletes | not supported |
| Bezier and ellipse segments | read; writing not supported |
| MultiPatch | not supported |

The Java reader is verified against `ogrinfo` from GDAL 3.13.3: all layer
feature counts and the layer geometry classification of the reference
geodatabase match. Additional fixtures cover UTF-16 strings, sparse rows with
large object ids and 3D tables in the version 4 (ArcGIS Pro) format.

Created databases are verified with `ogrinfo` and `ogr2ogr` from the same GDAL
version: layers, field types, null values, dates, GUIDs, CRS and geometries are
recognised.

## Modules

| Module | Artifact | Contents |
|---|---|---|
| `filegdb4j-geometry` | `filegdb4j-geometry` | Geometry model and Esri shape buffer codec (pure JDK) |
| `filegdb4j-core` | `filegdb4j-core` | Tables, catalog, domains, relationships, public API |
| `filegdb4j-jts` | `filegdb4j-jts` | JTS adapter (`jts-core`) |
| `filegdb4j-test-support` | `filegdb4j-test-support` | Fixtures, `ogrinfo`/`ogr2ogr` helpers |
| `filegdb4j-cli` | `filegdb4j-cli` | Small inspection command line tool |

The dependency direction is `cli`/`jts`/`test-support` → `core` → `geometry`.
`filegdb4j-core` has no dependencies beyond the JDK.

## Build

```bash
./gradlew build          # compile and run tests
./gradlew publishToMavenLocal
```

Tests that need reference data or the GDAL command line tools are skipped when
they are not available. The fixture directory is passed through the
`filegdb.test.data` system property; the build sets it to `test-data/` when the
directory exists. `ogrinfo`/`ogr2ogr` are discovered through `gdal.prefix`,
`GDAL_PREFIX`, `PATH` or `~/miniforge3/envs/gdal/bin`.

## Usage

```java
try (FileGeodatabase gdb = FileGeodatabase.open(Path.of("npl_2546.gdb"))) {
  for (Dataset dataset : gdb.datasets()) {
    System.out.println(dataset.name() + " " + dataset.crs().effectiveWkid());
  }
  try (FileGdbTable table = gdb.featureClass("grundnutzung")) {
    for (FileGdbRow row : table) {
      System.out.println(row.get("T_Id") + ": " + row.geometry());
    }
  }
}
```

Writing:

```java
try (FileGeodatabase gdb = FileGeodatabase.create(Path.of("new.gdb"))) {
  FeatureClassDefinition definition =
      FeatureClassDefinition.builder("roads")
          .field(FileGdbField.string("name", 255).asRequired())
          .field(FileGdbField.real("length").asNullable())
          .geometry(GeometryFieldDefinition.of("shape", GeometryKind.POLYGON))
          .crs(new CrsDefinition(2056, 2056, ""))
          .build();
  try (GdbFeatureWriter writer = gdb.createFeatureClass(definition)) {
    writer.write(new Object[] {"A1", 12.5}, polygon);
    writer.write(new Object[] {"B2", null}, null);
  }
}
```

CLI:

```bash
./gradlew :filegdb4j-cli:run --args="info test-data/gdal/npl_2546.gdb"
./gradlew :filegdb4j-cli:run --args="dump test-data/gdal/npl_2546.gdb grundnutzung --limit 5"
./gradlew :filegdb4j-cli:run --args="domains test-data/gdal/Domains.gdb"
./gradlew :filegdb4j-cli:run --args="relationships test-data/gdal/relationships.gdb"
```

## Verification with GDAL

Create the reference environment once:

```bash
conda create -y -n gdal -c conda-forge gdal=3.13.3
```

The integration tests compare all feature counts and the geometry
classification with `ogrinfo -al -so`; additional reference tests can use
`ogr2ogr`.

## Reference and license

The port follows the GDAL OpenFileGDB implementation file by file and keeps the
MIT license and attribution of the original authors. Source files that port
logic carry a note with the corresponding GDAL file. GDAL is licensed under the
MIT license, see <https://github.com/OSGeo/gdal>.

Reference checkout for development:

```bash
git clone --depth 1 --branch v3.13.3 --filter=blob:none --sparse \
  https://github.com/OSGeo/gdal.git gdal-3.13.3-ref
cd gdal-3.13.3-ref && git sparse-checkout set ogr/ogrsf_frmts/openfilegdb
```

## Test data

- `test-data/gdal/npl_2546.gdb` is a GDAL/ili2db generated geodatabase of the
  Solothurn Nutzungsplanung reading
  (`SO_ARP_Nutzungsplanung_Publikation_20201005`), used as a realistic reference
  because it contains feature classes, plain tables, coded value domains and
  several DateTime fields.
- `test-data/gdal/Domains.gdb` and `test-data/gdal/relationships.gdb` are test
  data from the GDAL repository
  (`autotest/ogr/data/filegdb/`, MIT licensed) and cover coded value domains,
  range domains and relationship classes for all cardinalities.
- `test-data/gdal/test_utf16.gdb`, `testdatetimeutc.gdb` and
  `objectid64/3features.gdb` (also GDAL test data) cover UTF-16 string
  decoding, sparse rows with large object ids and 3D polygons in the version 4
  table format.
- `test-data/gdal/curves.gdb` and `test-data/gdal/curve_circle_by_center.gdb`
  cover curved segments with interior point arcs, center based arcs and full
  circles.

## XY precision, circular arcs and spatial queries

`GeometryFieldDefinition.withPrecision(...)` exposes `CoordinatePrecision.withXY(resolution,
tolerance, xOrigin, yOrigin)`. Resolution is the coordinate storage grid spacing; tolerance is
metadata used by downstream geometry operations and does not itself round coordinates. Values are
in CRS coordinate units. Resolution must be positive, tolerance at least twice the resolution,
and coordinates must fit the nonnegative grid domain (at most 9e15 grid units). Invalid values
fail before writing. Existing library defaults remain unchanged.

`GeometryCodec` writes native circular segments, specified by an interior point or a centre and
direction, including major arcs and full circles. Bounds include circular extrema. Polyline
endpoints and direction are retained; only polygon rings are oriented for FileGDB. The core
geometry model has no dependency on Hop. The Hop provider supplies the exact curve adapter;
the plain JTS adapter continues to expose a linear geometry view. Declared Z/M dimensions require
finite ordinates; missing values are rejected instead of silently becoming zero.

`FeatureClassDefinition.builder(name).spatialIndex(false)` disables index creation. Otherwise
closing the feature writer builds a native v1 `.spx` using sorted runs on disk and records the grid
size in the table header. Indexing covers envelopes conservatively; GDAL can use the resulting
index. The regression suite checks native-index filtering with GDAL 3.11.4 and its in-memory index
disabled, and covers row-index boundaries through 8,193 rows. Close/finish must succeed before a
dataset is used; an interrupted standalone write is not a committed database.

`table.query(new Envelope(xmin, ymin, xmax, ymax))` returns inclusive **geometry-envelope**
intersections in source coordinates. `QueryResult.indexUsed()` reports whether the index was used;
`geometriesRead()` counts decoded geometries. The overload with `false` forces a scan. Native v1/v2
candidate pages are supported, with separator pruning for single-grid trees. The high-level
query uses indexes produced by this writer and foreign point-layer indexes; other foreign geometry-cell indexes require a scan to
preserve envelope semantics (notably for filters inside polygon holes). Malformed compatible
indexes cause an error. Bezier/ellipse envelope queries currently fail explicitly.

Query results and candidate IDs are currently materialized. Index construction spills entry runs,
with at most 64 merge inputs open at once; page descriptors still grow with dataset size. This is not yet a fully
streaming large-dataset query API. Attribute indexes and updates/deletes remain outside this change.

Interop detail observed with GDAL 3.11.4: its WKT reconstruction of centre-defined measured
arcs may insert synthetic intermediate points with M=0 and a repeated start Z. Stored endpoint
ordinates remain present; the Hop adapter interpolates its reconstructed intermediate Z/M values.
A GDAL-mediated measured-curve round-trip therefore needs separate validation.
