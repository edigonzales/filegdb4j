package ch.so.agi.filegdb.catalog;

/**
 * Spatial reference of a dataset as declared in its catalog definition XML.
 *
 * @param wkid well known id from the {@code WKID} element, zero if absent
 * @param latestWkid newer well known id from {@code LatestWKID}, null if absent
 * @param wkt WKT text from the {@code WKT} element, empty if absent
 */
public record CrsDefinition(int wkid, Integer latestWkid, String wkt) {

  public static final CrsDefinition UNKNOWN = new CrsDefinition(0, null, "");

  public boolean isDefined() {
    return wkid > 0 || (latestWkid != null && latestWkid > 0) || (wkt != null && !wkt.isBlank());
  }

  /** Returns the most useful well known id, zero if none is present. */
  public int effectiveWkid() {
    if (latestWkid != null && latestWkid > 0) {
      return latestWkid;
    }
    return wkid;
  }
}
