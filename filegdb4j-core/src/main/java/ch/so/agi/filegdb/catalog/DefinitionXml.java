package ch.so.agi.filegdb.catalog;

import java.io.StringReader;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/** Tolerant helper for the XML definitions stored in {@code GDB_Items}. */
final class DefinitionXml {

  private DefinitionXml() {}

  static boolean contains(String xml, String marker) {
    return xml != null && xml.contains(marker);
  }

  static CrsDefinition crs(String xml) {
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

  static String childText(Element element, String localName) {
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element childElement
          && localName(childElement.getTagName()).equals(localName)) {
        return childElement.getTextContent();
      }
    }
    return null;
  }

  private static String localName(String tagName) {
    int colon = tagName.indexOf(':');
    return colon < 0 ? tagName : tagName.substring(colon + 1);
  }
}
