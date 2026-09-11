package ch.so.agi.filegdb.write;

import ch.so.agi.filegdb.catalog.CrsDefinition;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import ch.so.agi.filegdb.geometry.GeometryKind;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.table.FileGdbFieldType;
import java.util.Locale;

/**
 * Builds the catalog XML definitions written to {@code GDB_Items}.
 *
 * <p>The layout mirrors the definitions GDAL writes, see
 * {@code ogropenfilegdblayer_write.cpp} ({@code RefreshXMLDefinitionInMemory},
 * {@code CreateXMLFieldDefinition}, {@code XMLSerializeGeomFieldBase}).
 */
final class DefinitionXmlWriter {

  private DefinitionXmlWriter() {}

  static String featureClass(FeatureClassDefinition definition, int dsid) {    GeometryFieldDefinition geometry = definition.geometry();
    StringBuilder xml = new StringBuilder(2048);
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<DEFeatureClassInfo xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
    xml.append(" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"");
    xml.append(" xmlns:typens=\"http://www.esri.com/schemas/ArcGIS/10.3\"");
    xml.append(" xsi:type=\"typens:DEFeatureClassInfo\">\n");
    element(xml, 1, "CatalogPath", "\\" + definition.name());
    element(xml, 1, "Name", definition.name());
    element(xml, 1, "ChildrenExpanded", "false");
    element(xml, 1, "DatasetType", "esriDTFeatureClass");
    element(xml, 1, "DSID", Integer.toString(dsid));
    element(xml, 1, "Versioned", "false");
    element(xml, 1, "CanVersion", "false");
    element(xml, 1, "HasOID", "true");
    element(xml, 1, "OIDFieldName", "OBJECTID");

    xml.append("  <GPFieldInfoExs xsi:type=\"typens:ArrayOfGPFieldInfoEx\">\n");
    xml.append("    <GPFieldInfoEx xsi:type=\"typens:GPFieldInfoEx\">\n");
    element(xml, 3, "Name", "OBJECTID");
    element(xml, 3, "FieldType", "esriFieldTypeOID");
    element(xml, 3, "IsNullable", "false");
    element(xml, 3, "Length", "4");
    element(xml, 3, "Precision", "0");
    element(xml, 3, "Scale", "0");
    element(xml, 3, "Required", "true");
    xml.append("    </GPFieldInfoEx>\n");
    for (FileGdbField field : definition.fields()) {
      writeField(xml, field);
    }
    writeGeometryField(xml, geometry);
    xml.append("  </GPFieldInfoExs>\n");

    element(xml, 1, "CLSID", "{52353152-891A-11D0-BEC6-00805F7C4268}");
    element(xml, 1, "EXTCLSID", "");
    if (definition.alias() != null && !definition.alias().isBlank()) {
      element(xml, 1, "AliasName", definition.alias());
    }
    element(xml, 1, "IsTimeInUTC", "false");
    element(xml, 1, "FeatureType", "esriFTSimple");
    element(xml, 1, "ShapeType", shapeType(geometry.kind()));
    element(xml, 1, "ShapeFieldName", geometry.name());
    element(xml, 1, "HasM", geometry.hasM() ? "true" : "false");
    element(xml, 1, "HasZ", geometry.hasZ() ? "true" : "false");
    element(xml, 1, "HasSpatialIndex", "false");
    element(xml, 1, "AreaFieldName", "");
    element(xml, 1, "LengthFieldName", "");
    xml.append("  <Extent xsi:nil=\"true\"/>\n");
    writeSpatialReference(xml, geometry, definition.crs());
    xml.append("</DEFeatureClassInfo>");
    return xml.toString();
  }

  private static void writeField(StringBuilder xml, FileGdbField field) {
    xml.append("    <GPFieldInfoEx xsi:type=\"typens:GPFieldInfoEx\">\n");
    element(xml, 3, "Name", field.name());
    if (field.alias() != null && !field.alias().isBlank()) {
      element(xml, 3, "AliasName", field.alias());
    }
    element(xml, 3, "FieldType", esriType(field.type()));
    if (field.nullable()) {
      element(xml, 3, "IsNullable", "true");
    }
    if (field.required()) {
      element(xml, 3, "Required", "true");
    }
    if (!field.editable()) {
      element(xml, 3, "Editable", "false");
    }
    if (field.highPrecision()) {
      element(xml, 3, "HighPrecision", "true");
    }
    element(xml, 3, "Length", Integer.toString(fieldLength(field)));
    element(xml, 3, "Precision", "0");
    element(xml, 3, "Scale", "0");
    if (field.domain() != null && !field.domain().isBlank()) {
      element(xml, 3, "DomainName", field.domain());
    }
    xml.append("    </GPFieldInfoEx>\n");
  }

  private static void writeGeometryField(StringBuilder xml, GeometryFieldDefinition geometry) {
    xml.append("    <GPFieldInfoEx xsi:type=\"typens:GPFieldInfoEx\">\n");
    element(xml, 3, "Name", geometry.name());
    element(xml, 3, "FieldType", "esriFieldTypeGeometry");
    element(xml, 3, "IsNullable", geometry.nullable() ? "true" : "false");
    element(xml, 3, "Length", "0");
    element(xml, 3, "Precision", "0");
    element(xml, 3, "Scale", "0");
    element(xml, 3, "Required", "true");
    xml.append("    </GPFieldInfoEx>\n");
  }

  static String table(String name, java.util.List<FileGdbField> fields, int dsid) {
    StringBuilder xml = new StringBuilder(1024);
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<DETableInfo xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
    xml.append(" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"");
    xml.append(" xmlns:typens=\"http://www.esri.com/schemas/ArcGIS/10.3\"");
    xml.append(" xsi:type=\"typens:DETableInfo\">\n");
    element(xml, 1, "CatalogPath", "\\" + name);
    element(xml, 1, "Name", name);
    element(xml, 1, "ChildrenExpanded", "false");
    element(xml, 1, "DatasetType", "esriDTTable");
    element(xml, 1, "DSID", Integer.toString(dsid));
    element(xml, 1, "Versioned", "false");
    element(xml, 1, "CanVersion", "false");
    element(xml, 1, "HasOID", "true");
    element(xml, 1, "OIDFieldName", "OBJECTID");
    xml.append("  <GPFieldInfoExs xsi:type=\"typens:ArrayOfGPFieldInfoEx\">\n");
    xml.append("    <GPFieldInfoEx xsi:type=\"typens:GPFieldInfoEx\">\n");
    element(xml, 3, "Name", "OBJECTID");
    element(xml, 3, "FieldType", "esriFieldTypeOID");
    element(xml, 3, "IsNullable", "false");
    element(xml, 3, "Length", "4");
    element(xml, 3, "Precision", "0");
    element(xml, 3, "Scale", "0");
    element(xml, 3, "Required", "true");
    xml.append("    </GPFieldInfoEx>\n");
    for (FileGdbField field : fields) {
      writeField(xml, field);
    }
    xml.append("  </GPFieldInfoExs>\n");
    element(xml, 1, "CLSID", "{7A566981-C114-11D2-8A28-006097AFF44E}");
    element(xml, 1, "EXTCLSID", "");
    element(xml, 1, "IsTimeInUTC", "false");
    xml.append("</DETableInfo>");
    return xml.toString();
  }

  static String domain(ch.so.agi.filegdb.catalog.Domain domain) {
    boolean coded = domain instanceof ch.so.agi.filegdb.catalog.CodedValueDomain;
    StringBuilder xml = new StringBuilder(512);
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append('<')
        .append(coded ? "GPCodedValueDomain2" : "GPRangeDomain2")
        .append(" xsi:type=\"typens:")
        .append(coded ? "GPCodedValueDomain2" : "GPRangeDomain2")
        .append("\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
        .append(" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"")
        .append(" xmlns:typens=\"http://www.esri.com/schemas/ArcGIS/10.1\">\n");
    element(xml, 1, "DomainName", domain.name());
    element(xml, 1, "FieldType", esriType(domain.fieldType()));
    element(xml, 1, "MergePolicy", "esriMPTDefaultValue");
    element(xml, 1, "SplitPolicy", "esriSPTDefaultValue");
    element(xml, 1, "Description", domain.description());
    element(xml, 1, "Owner", "");
    if (domain instanceof ch.so.agi.filegdb.catalog.CodedValueDomain codedDomain) {
      xml.append("  <CodedValues xsi:type=\"typens:ArrayOfCodedValue\">\n");
      for (var value : codedDomain.values()) {
        xml.append("    <CodedValue xsi:type=\"typens:CodedValue\">\n");
        element(xml, 3, "Name", value.name());
        xml.append("      <Code xsi:type=\"")
            .append(codeType(domain.fieldType()))
            .append("\">")
            .append(escape(value.code()))
            .append("</Code>\n");
        xml.append("    </CodedValue>\n");
      }
      xml.append("  </CodedValues>\n");
    } else if (domain instanceof ch.so.agi.filegdb.catalog.RangeDomain rangeDomain) {
      element(xml, 1, "MinValue", rangeDomain.minValue());
      element(xml, 1, "MaxValue", rangeDomain.maxValue());
    }
    xml.append("</").append(coded ? "GPCodedValueDomain2" : "GPRangeDomain2").append('>');
    return xml.toString();
  }

  static String relationship(
      RelationshipDefinition definition, int dsid, String mappingTableOidName) {
    boolean manyToMany = definition.cardinality() == ch.so.agi.filegdb.catalog.RelationshipCardinality.MANY_TO_MANY;
    StringBuilder xml = new StringBuilder(1536);
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<DERelationshipClassInfo xsi:type=\"typens:DERelationshipClassInfo\"");
    xml.append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
    xml.append(" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"");
    xml.append(" xmlns:typens=\"http://www.esri.com/schemas/ArcGIS/10.1\">\n");
    element(xml, 1, "CatalogPath", "\\" + definition.name());
    element(xml, 1, "Name", definition.name());
    element(xml, 1, "ChildrenExpanded", "false");
    element(xml, 1, "DatasetType", "esriDTRelationshipClass");
    element(xml, 1, "DSID", Integer.toString(dsid));
    element(xml, 1, "Versioned", "false");
    element(xml, 1, "CanVersion", "false");
    element(xml, 1, "ConfigurationKeyword", "");
    element(xml, 1, "RequiredGeodatabaseClientVersion", "10.0");
    element(xml, 1, "HasOID", "false");
    xml.append("  <GPFieldInfoExs xsi:type=\"typens:ArrayOfGPFieldInfoEx\">\n");
    if (manyToMany) {
      writeFieldName(xml, mappingTableOidName);
      writeFieldName(xml, definition.originForeignKey());
      writeFieldName(xml, definition.destinationForeignKey());
      element(xml, 1, "OIDFieldName", mappingTableOidName);
    } else {
      element(xml, 1, "OIDFieldName", "");
    }
    xml.append("  </GPFieldInfoExs>\n");
    element(xml, 1, "CLSID", "");
    element(xml, 1, "EXTCLSID", "");
    xml.append("  <RelationshipClassNames xsi:type=\"typens:Names\"/>\n");
    element(xml, 1, "AliasName", "");
    element(xml, 1, "ModelName", "");
    element(xml, 1, "HasGlobalID", "false");
    element(xml, 1, "GlobalIDFieldName", "");
    element(xml, 1, "RasterFieldName", "");
    xml.append("  <ExtensionProperties xsi:type=\"typens:PropertySet\">\n");
    xml.append("    <PropertyArray xsi:type=\"typens:ArrayOfPropertySetProperty\"/>\n");
    xml.append("  </ExtensionProperties>\n");
    xml.append("  <ControllerMemberships xsi:type=\"typens:ArrayOfControllerMembership\"/>\n");
    element(xml, 1, "EditorTrackingEnabled", "false");
    element(xml, 1, "CreatorFieldName", "");
    element(xml, 1, "CreatedAtFieldName", "");
    element(xml, 1, "EditorFieldName", "");
    element(xml, 1, "EditedAtFieldName", "");
    element(xml, 1, "IsTimeInUTC", "true");
    element(xml, 1, "Cardinality", cardinality(definition.cardinality()));
    element(xml, 1, "Notification", "esriRelNotificationNone");
    element(xml, 1, "IsAttributed", "false");
    element(xml, 1, "IsComposite", definition.composite() ? "true" : "false");
    xml.append("  <OriginClassNames xsi:type=\"typens:Names\">\n");
    element(xml, 2, "Name", definition.originClassName());
    xml.append("  </OriginClassNames>\n");
    xml.append("  <DestinationClassNames xsi:type=\"typens:Names\">\n");
    element(xml, 2, "Name", definition.destinationClassName());
    xml.append("  </DestinationClassNames>\n");
    element(xml, 1, "KeyType", "esriRelKeyTypeSingle");
    element(xml, 1, "ClassKey", "esriRelClassKeyUndefined");
    element(xml, 1, "ForwardPathLabel", definition.forwardLabel());
    element(xml, 1, "BackwardPathLabel", definition.backwardLabel());
    element(xml, 1, "IsReflexive", "false");
    xml.append("  <OriginClassKeys xsi:type=\"typens:ArrayOfRelationshipClassKey\">\n");
    writeKey(xml, definition.originPrimaryKey(), "esriRelKeyRoleOriginPrimary");
    if (manyToMany) {
      writeKey(xml, definition.originForeignKey(), "esriRelKeyRoleOriginForeign");
    } else {
      writeKey(xml, definition.originForeignKey(), "esriRelKeyRoleOriginForeign");
    }
    xml.append("  </OriginClassKeys>\n");
    if (manyToMany) {
      xml.append("  <DestinationClassKeys xsi:type=\"typens:ArrayOfRelationshipClassKey\">\n");
      writeKey(xml, definition.destinationPrimaryKey(), "esriRelKeyRoleDestinationPrimary");
      writeKey(xml, definition.destinationForeignKey(), "esriRelKeyRoleDestinationForeign");
      xml.append("  </DestinationClassKeys>\n");
    }
    xml.append("  <RelationshipRules xsi:type=\"typens:ArrayOfRelationshipRule\"/>\n");
    element(xml, 1, "IsAttachmentRelationship", "false");
    element(xml, 1, "ChangeTracked", "false");
    element(xml, 1, "ReplicaTracked", "false");
    xml.append("</DERelationshipClassInfo>");
    return xml.toString();
  }

  private static void writeFieldName(StringBuilder xml, String name) {
    xml.append("    <GPFieldInfoEx xsi:type=\"typens:GPFieldInfoEx\">\n");
    element(xml, 3, "Name", name);
    xml.append("    </GPFieldInfoEx>\n");
  }

  private static void writeKey(StringBuilder xml, String objectKeyName, String role) {
    xml.append("    <RelationshipClassKey xsi:type=\"typens:RelationshipClassKey\">\n");
    element(xml, 3, "ObjectKeyName", objectKeyName);
    element(xml, 3, "ClassKeyName", "");
    element(xml, 3, "KeyRole", role);
    xml.append("    </RelationshipClassKey>\n");
  }

  private static String cardinality(ch.so.agi.filegdb.catalog.RelationshipCardinality cardinality) {
    return switch (cardinality) {
      case ONE_TO_ONE -> "esriRelCardinalityOneToOne";
      case ONE_TO_MANY -> "esriRelCardinalityOneToMany";
      case MANY_TO_MANY -> "esriRelCardinalityManyToMany";
      case UNKNOWN -> "esriRelCardinalityOneToMany";
    };
  }

  private static String codeType(ch.so.agi.filegdb.table.FileGdbFieldType type) {
    return switch (type) {
      case INT16 -> "xs:short";
      case INT32 -> "xs:int";
      case INT64 -> "xs:long";
      case FLOAT32 -> "xs:float";
      case FLOAT64 -> "xs:double";
      case DATETIME, DATE, TIME, DATETIME_WITH_OFFSET -> "xs:dateTime";
      default -> "xs:string";
    };
  }

  private static void writeSpatialReference(
      StringBuilder xml, GeometryFieldDefinition geometry, CrsDefinition crs) {
    boolean hasWkt = geometry.wkt() != null && !geometry.wkt().isBlank();
    String type;
    if (hasWkt) {
      type = crs.effectiveWkid() > 0 && crs.effectiveWkid() < 2000
          ? "typens:GeographicCoordinateSystem"
          : "typens:ProjectedCoordinateSystem";
    } else {
      type = "typens:UnknownCoordinateSystem";
    }
    xml.append("  <SpatialReference xsi:type=\"").append(type).append("\">\n");
    if (hasWkt) {
      element(xml, 2, "WKT", geometry.wkt());
    }
    element(xml, 2, "XOrigin", number(geometry.precision().xOrigin()));
    element(xml, 2, "YOrigin", number(geometry.precision().yOrigin()));
    element(xml, 2, "XYScale", number(geometry.precision().xyScale()));
    element(xml, 2, "ZOrigin", number(geometry.precision().zOrigin()));
    element(xml, 2, "ZScale", number(geometry.precision().zScale()));
    element(xml, 2, "MOrigin", number(geometry.precision().mOrigin()));
    element(xml, 2, "MScale", number(geometry.precision().mScale()));
    element(xml, 2, "XYTolerance", number(geometry.precision().xyTolerance()));
    element(xml, 2, "ZTolerance", number(geometry.precision().zTolerance()));
    element(xml, 2, "MTolerance", number(geometry.precision().mTolerance()));
    element(xml, 2, "HighPrecision", "true");
    int wkid = crs.effectiveWkid();
    if (wkid > 0) {
      element(xml, 2, "WKID", Integer.toString(wkid));
      element(xml, 2, "LatestWKID", Integer.toString(wkid));
    }
    xml.append("  </SpatialReference>\n");
  }

  private static String number(double value) {
    return String.format(Locale.ROOT, "%.17g", value);
  }

  private static String shapeType(GeometryKind kind) {
    return switch (kind) {
      case POINT -> "esriGeometryPoint";
      case MULTIPOINT -> "esriGeometryMultipoint";
      case LINE -> "esriGeometryPolyline";
      case POLYGON -> "esriGeometryPolygon";
      case MULTIPATCH -> "esriGeometryMultiPatch";
      case NONE -> "";
    };
  }

  private static String esriType(FileGdbFieldType type) {
    return switch (type) {
      case INT16 -> "esriFieldTypeSmallInteger";
      case INT32 -> "esriFieldTypeInteger";
      case INT64 -> "esriFieldTypeBigInteger";
      case FLOAT32 -> "esriFieldTypeSingle";
      case FLOAT64 -> "esriFieldTypeDouble";
      case STRING -> "esriFieldTypeString";
      case DATETIME -> "esriFieldTypeDate";
      case DATE -> "esriFieldTypeDateOnly";
      case TIME -> "esriFieldTypeTimeOnly";
      case DATETIME_WITH_OFFSET -> "esriFieldTypeTimestampOffset";
      case OBJECTID -> "esriFieldTypeOID";
      case GEOMETRY -> "esriFieldTypeGeometry";
      case BINARY -> "esriFieldTypeBlob";
      case RASTER -> "esriFieldTypeRaster";
      case GUID -> "esriFieldTypeGUID";
      case GLOBALID -> "esriFieldTypeGlobalID";
      case XML -> "esriFieldTypeXML";
      case UNDEFINED -> "";
    };
  }

  private static int fieldLength(FileGdbField field) {
    return switch (field.type()) {
      case INT16 -> 2;
      case INT32, FLOAT32 -> 4;
      case INT64, FLOAT64, DATETIME, DATE, TIME -> 8;
      case DATETIME_WITH_OFFSET -> 10;
      case STRING -> field.maxWidth();
      default -> 0;
    };
  }

  private static void element(StringBuilder xml, int indent, String name, String value) {
    xml.append("  ".repeat(indent))
        .append('<')
        .append(name)
        .append('>')
        .append(escape(value))
        .append("</")
        .append(name)
        .append(">\n");
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
