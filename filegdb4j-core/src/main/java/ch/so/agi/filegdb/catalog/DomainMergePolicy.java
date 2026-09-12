package ch.so.agi.filegdb.catalog;

/** Policy stored in the geodatabase domain definition. */
public enum DomainMergePolicy {
  DEFAULT_VALUE("esriMPTDefaultValue"),
  SUM_VALUES("esriMPTSumValues"),
  AREA_WEIGHTED("esriMPTAreaWeighted");
  private final String xml;

  DomainMergePolicy(String xml) {
    this.xml = xml;
  }

  public String xml() {
    return xml;
  }

  public static DomainMergePolicy fromXml(String value) {
    if (value == null || value.isBlank()) return DEFAULT_VALUE;
    for (var policy : values()) if (policy.xml.equals(value)) return policy;
    throw new IllegalArgumentException("Unknown domain policy: " + value);
  }
}
