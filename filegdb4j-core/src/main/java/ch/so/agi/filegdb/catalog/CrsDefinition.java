package ch.so.agi.filegdb.catalog;

import java.util.Objects;

/**
 * Spatial reference of a dataset as declared in its catalog definition XML.
 *
 * @param wkid well known id from the {@code WKID} element, zero if absent
 * @param latestWkid newer well known id from {@code LatestWKID}, null if absent
 * @param wkt WKT text from the {@code WKT} element, empty if absent
 */
public final class CrsDefinition {
  private final int wkid;
  private final Integer latestWkid;
  private final String wkt;

  public static final CrsDefinition UNKNOWN = new CrsDefinition(0, null, "");

  public CrsDefinition(int wkid, Integer latestWkid, String wkt) {
    this.wkid = wkid;
    this.latestWkid = latestWkid;
    this.wkt = wkt;
  }

  public int wkid() {
    return wkid;
  }

  public Integer latestWkid() {
    return latestWkid;
  }

  public String wkt() {
    return wkt;
  }

  public boolean isDefined() {
    return wkid > 0
        || (latestWkid != null && latestWkid > 0)
        || (wkt != null && !wkt.trim().isEmpty());
  }

  /** Returns the most useful well known id, zero if none is present. */
  public int effectiveWkid() {
    if (latestWkid != null && latestWkid > 0) {
      return latestWkid;
    }
    return wkid;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CrsDefinition other = (CrsDefinition) o;
    return wkid == other.wkid
        && Objects.equals(latestWkid, other.latestWkid)
        && Objects.equals(wkt, other.wkt);
  }

  @Override
  public final int hashCode() {
    int result = 0;
    result = 31 * result + Integer.hashCode(wkid);
    result = 31 * result + Objects.hashCode(latestWkid);
    result = 31 * result + Objects.hashCode(wkt);
    return result;
  }

  @Override
  public final String toString() {
    return "CrsDefinition[wkid=" + wkid + ", latestWkid=" + latestWkid + ", wkt=" + wkt + "]";
  }
}
