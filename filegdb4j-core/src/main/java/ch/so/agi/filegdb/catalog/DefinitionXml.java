package ch.so.agi.filegdb.catalog;

import ch.so.agi.filegdb.table.FieldMetadata;
import ch.so.agi.filegdb.table.FileGdbFieldType;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * Tolerant reader for the XML definitions stored in {@code GDB_Items}.
 *
 * <p>The geodatabase stores spatial references, field metadata, domains and relationship classes as
 * XML documents. The parser matches element names without namespace prefixes, mirroring the
 * behaviour of GDAL's CPL XML parser.
 */
public final class DefinitionXml {

  private DefinitionXml() {}

  public static boolean contains(String xml, String marker) {
    return xml != null && xml.contains(marker);
  }

  /** Spatial reference of a dataset definition. */
  public static CrsDefinition crs(String xml) {
    Element root = parse(xml);
    if (root == null) {
      return CrsDefinition.UNKNOWN;
    }
    Element spatialReference = find(root, "SpatialReference");
    if (spatialReference == null) {
      return CrsDefinition.UNKNOWN;
    }
    String wkt = childText(spatialReference, "WKT");
    String wkid = childText(spatialReference, "WKID");
    String latestWkid = childText(spatialReference, "LatestWKID");
    return new CrsDefinition(
        parseInt(wkid),
        latestWkid == null || latestWkid.isBlank() ? null : parseInt(latestWkid),
        wkt == null ? "" : wkt);
  }

  /**
   * Field metadata from the {@code GPFieldInfoExs} section of a dataset definition, keyed by field
   * name.
   */
  public static Map<String, FieldMetadata> fieldInfo(String xml) {
    Element root = parse(xml);
    if (root == null) {
      return Map.of();
    }
    Element infos = find(root, "GPFieldInfoExs");
    if (infos == null) {
      return Map.of();
    }
    Map<String, FieldMetadata> result = new LinkedHashMap<>();
    for (Element info : children(infos, "GPFieldInfoEx")) {
      String name = childText(info, "Name");
      if (name == null || name.isBlank()) {
        continue;
      }
      String domain = childText(info, "DomainName");
      boolean highPrecision = "true".equalsIgnoreCase(trimmed(childText(info, "HighPrecision")));
      result.put(
          name,
          new FieldMetadata(domain == null || domain.isBlank() ? null : domain, highPrecision));
    }
    return result;
  }

  /** Parses a coded value or range domain definition, or returns {@code null}. */
  public static Domain domain(String xml) {
    Element root = parse(xml);
    if (root == null) {
      return null;
    }
    Element codedDomain = findContaining(root, "CodedValueDomain");
    if (codedDomain != null) {
      return codedValueDomain(codedDomain);
    }
    Element rangeDomain = findContaining(root, "RangeDomain");
    if (rangeDomain != null) {
      return rangeDomain(rangeDomain);
    }
    return null;
  }

  /** Parses a relationship class definition, or returns {@code null}. */
  public static RelationshipClass relationship(String xml) {
    Element root = parse(xml);
    if (root == null) {
      return null;
    }
    Element relationship = find(root, "DERelationshipClassInfo");
    if (relationship == null) {
      return null;
    }
    String name = childText(relationship, "Name");
    String origin = nestedName(relationship, "OriginClassNames");
    String destination = nestedName(relationship, "DestinationClassNames");
    RelationshipCardinality cardinality =
        RelationshipCardinality.fromEsri(childText(relationship, "Cardinality"));
    String forwardLabel = childText(relationship, "ForwardPathLabel");
    String backwardLabel = childText(relationship, "BackwardPathLabel");
    boolean composite = "true".equalsIgnoreCase(trimmed(childText(relationship, "IsComposite")));
    boolean attributed = "true".equalsIgnoreCase(trimmed(childText(relationship, "IsAttributed")));
    boolean attachment =
        "true".equalsIgnoreCase(trimmed(childText(relationship, "IsAttachmentRelationship")));
    List<RelationshipKey> originKeys = keys(relationship, "OriginClassKeys");
    List<RelationshipKey> destinationKeys = keys(relationship, "DestinationClassKeys");
    return new RelationshipClass(
        name,
        origin,
        destination,
        cardinality,
        forwardLabel == null ? "" : forwardLabel,
        backwardLabel == null ? "" : backwardLabel,
        composite,
        attributed,
        attachment,
        originKeys,
        destinationKeys);
  }

  static FileGdbFieldType fieldTypeFromEsri(String esriType) {
    if (esriType == null) {
      return FileGdbFieldType.UNDEFINED;
    }
    return switch (esriType) {
      case "esriFieldTypeSmallInteger" -> FileGdbFieldType.INT16;
      case "esriFieldTypeInteger" -> FileGdbFieldType.INT32;
      case "esriFieldTypeBigInteger" -> FileGdbFieldType.INT64;
      case "esriFieldTypeSingle" -> FileGdbFieldType.FLOAT32;
      case "esriFieldTypeDouble" -> FileGdbFieldType.FLOAT64;
      case "esriFieldTypeString" -> FileGdbFieldType.STRING;
      case "esriFieldTypeDate" -> FileGdbFieldType.DATETIME;
      case "esriFieldTypeDateOnly" -> FileGdbFieldType.DATE;
      case "esriFieldTypeTimeOnly" -> FileGdbFieldType.TIME;
      case "esriFieldTypeTimestampOffset" -> FileGdbFieldType.DATETIME_WITH_OFFSET;
      case "esriFieldTypeOID" -> FileGdbFieldType.OBJECTID;
      case "esriFieldTypeGeometry" -> FileGdbFieldType.GEOMETRY;
      case "esriFieldTypeBlob" -> FileGdbFieldType.BINARY;
      case "esriFieldTypeRaster" -> FileGdbFieldType.RASTER;
      case "esriFieldTypeGUID" -> FileGdbFieldType.GUID;
      case "esriFieldTypeGlobalID" -> FileGdbFieldType.GLOBALID;
      case "esriFieldTypeXML" -> FileGdbFieldType.XML;
      default -> FileGdbFieldType.UNDEFINED;
    };
  }

  private static CodedValueDomain codedValueDomain(Element domain) {
    String name = defaultString(childText(domain, "DomainName"));
    String description = defaultString(childText(domain, "Description"));
    FileGdbFieldType fieldType = fieldTypeFromEsri(childText(domain, "FieldType"));
    List<CodedValue> values = new ArrayList<>();
    Element codedValues = find(domain, "CodedValues");
    if (codedValues != null) {
      for (Element codedValue : children(codedValues, "CodedValue")) {
        String valueName = childText(codedValue, "Name");
        String code = childText(codedValue, "Code");
        values.add(new CodedValue(defaultString(valueName), defaultString(code)));
      }
    }
    return new CodedValueDomain(
        name,
        fieldType,
        description,
        values,
        DomainSplitPolicy.fromXml(childText(domain, "SplitPolicy")),
        DomainMergePolicy.fromXml(childText(domain, "MergePolicy")));
  }

  private static RangeDomain rangeDomain(Element domain) {
    String name = defaultString(childText(domain, "DomainName"));
    String description = defaultString(childText(domain, "Description"));
    FileGdbFieldType fieldType = fieldTypeFromEsri(childText(domain, "FieldType"));
    return new RangeDomain(
        name,
        fieldType,
        description,
        defaultString(childText(domain, "MinValue")),
        defaultString(childText(domain, "MaxValue")),
        DomainSplitPolicy.fromXml(childText(domain, "SplitPolicy")),
        DomainMergePolicy.fromXml(childText(domain, "MergePolicy")));
  }

  private static List<RelationshipKey> keys(Element relationship, String containerName) {
    Element container = find(relationship, containerName);
    if (container == null) {
      return List.of();
    }
    List<RelationshipKey> keys = new ArrayList<>();
    for (Element key : children(container, "RelationshipClassKey")) {
      String objectKeyName = defaultString(childText(key, "ObjectKeyName"));
      RelationshipKey.Role role = RelationshipKey.Role.fromEsri(childText(key, "KeyRole"));
      keys.add(new RelationshipKey(objectKeyName, role));
    }
    return keys;
  }

  private static String nestedName(Element element, String containerName) {
    Element container = find(element, containerName);
    if (container == null) {
      return null;
    }
    return childText(container, "Name");
  }

  private static String defaultString(String value) {
    return value == null ? "" : value;
  }

  private static String trimmed(String value) {
    return value == null ? "" : value.trim();
  }

  static Element parse(String xml) {
    if (xml == null || xml.isBlank()) {
      return null;
    }
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
      trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
      trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
      Document document =
          factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
      return document.getDocumentElement();
    } catch (Exception e) {
      return null;
    }
  }

  private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
    try {
      factory.setFeature(feature, value);
    } catch (Exception e) {
      // parser does not support the feature; the definition XML is local data
    }
  }

  /** Finds the first element with the exact local name. */
  static Element find(Element element, String localName) {
    if (element == null) {
      return null;
    }
    if (localName(element.getTagName()).equals(localName)) {
      return element;
    }
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element childElement) {
        Element result = find(childElement, localName);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  /** Finds the first element whose local name contains the given fragment. */
  static Element findContaining(Element element, String fragment) {
    if (element == null) {
      return null;
    }
    if (localName(element.getTagName()).contains(fragment)) {
      return element;
    }
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element childElement) {
        Element result = findContaining(childElement, fragment);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  static List<Element> children(Element element, String localName) {
    List<Element> result = new ArrayList<>();
    if (element == null) {
      return result;
    }
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element childElement
          && localName(childElement.getTagName()).equals(localName)) {
        result.add(childElement);
      }
    }
    return result;
  }

  static String childText(Element element, String localName) {
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element childElement
          && localName(childElement.getTagName()).equals(localName)) {
        return childElement.getTextContent();
      }
    }
    return null;
  }

  private static int parseInt(String value) {
    if (value == null || value.isBlank()) {
      return 0;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static String localName(String tagName) {
    int colon = tagName.indexOf(':');
    return colon < 0 ? tagName : tagName.substring(colon + 1);
  }
}
