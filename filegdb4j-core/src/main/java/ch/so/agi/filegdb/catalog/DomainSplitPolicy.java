package ch.so.agi.filegdb.catalog;

/** Policy stored in the geodatabase domain definition. */
public enum DomainSplitPolicy {
  DEFAULT_VALUE("esriSPTDefaultValue"),
  DUPLICATE("esriSPTDuplicate"),
  GEOMETRY_RATIO("esriSPTGeometryRatio");
  private final String xml;

  DomainSplitPolicy(String xml) {
    this.xml = xml;
  }

  public String xml() {
    return xml;
  }

  public static DomainSplitPolicy fromXml(String value) {
    if (value == null || value.trim().isEmpty()) return DEFAULT_VALUE;
    for (DomainSplitPolicy policy : values()) if (policy.xml.equals(value)) return policy;
    throw new IllegalArgumentException("Unknown domain policy: " + value);
  }
}
