package ch.so.agi.filegdb.catalog;

import ch.so.agi.filegdb.table.FileGdbFieldType;
import java.time.LocalDateTime;

/** Lossless conversion of domain literals for schema and row validation. */
public final class DomainValues {
  private DomainValues() {}

  public static Comparable<?> parse(FileGdbFieldType type, String value) {
    if (value == null) throw new IllegalArgumentException("Domain value is required");
    switch (type) {
      case STRING:
        return value;
      case INT16:
        return Short.valueOf(value);
      case INT32:
        return Integer.valueOf(value);
      case INT64:
        return Long.valueOf(value);
      case FLOAT32:
        {
          float number = Float.parseFloat(value);
          if (!Float.isFinite(number))
            throw new IllegalArgumentException("Domain number outside field range: " + value);
          return number == 0 ? 0.0f : number;
        }
      case FLOAT64:
        {
          double number = Double.parseDouble(value);
          if (!Double.isFinite(number))
            throw new IllegalArgumentException("Domain number outside field range: " + value);
          return number == 0 ? 0.0 : number;
        }
      case DATETIME:
        return LocalDateTime.parse(value.replace(' ', 'T'));
      case DATE:
        return java.time.LocalDate.parse(value);
      case TIME:
        return java.time.LocalTime.parse(value);
      default:
        throw new IllegalArgumentException("Unsupported domain field type: " + type);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compare(Comparable a, Comparable b) {
    return a.compareTo(b);
  }

  public static boolean equivalent(Domain a, Domain b) {
    validate(a);
    validate(b);
    if (a.getClass() != b.getClass()
        || !a.name().equalsIgnoreCase(b.name())
        || a.fieldType() != b.fieldType()
        || !java.util.Objects.equals(a.description(), b.description())
        || a.splitPolicy() != b.splitPolicy()
        || a.mergePolicy() != b.mergePolicy()) return false;
    if (a instanceof RangeDomain && b instanceof RangeDomain) {
      RangeDomain x = (RangeDomain) a;
      RangeDomain y = (RangeDomain) b;
      return parse(a.fieldType(), x.minValue()).equals(parse(b.fieldType(), y.minValue()))
          && parse(a.fieldType(), x.maxValue()).equals(parse(b.fieldType(), y.maxValue()));
    }
    java.util.HashMap<Comparable<?>, String> x = new java.util.HashMap<>();
    java.util.HashMap<Comparable<?>, String> y = new java.util.HashMap<>();
    for (CodedValue v : a.values()) x.put(parse(a.fieldType(), v.code()), v.name());
    for (CodedValue v : b.values()) y.put(parse(b.fieldType(), v.code()), v.name());
    return x.equals(y);
  }

  public static void validate(Domain domain) {
    if (domain.fieldType() == null
        || !java.util.Arrays.asList(
                FileGdbFieldType.STRING,
                FileGdbFieldType.INT16,
                FileGdbFieldType.INT32,
                FileGdbFieldType.INT64,
                FileGdbFieldType.FLOAT32,
                FileGdbFieldType.FLOAT64,
                FileGdbFieldType.DATETIME,
                FileGdbFieldType.DATE,
                FileGdbFieldType.TIME)
            .contains(domain.fieldType()))
      throw new IllegalArgumentException("Unsupported domain field type: " + domain.fieldType());
    if (domain.name() == null || domain.name().trim().isEmpty())
      throw new IllegalArgumentException("Domain name is required");
    if (domain instanceof CodedValueDomain) {
      CodedValueDomain coded = (CodedValueDomain) domain;
      java.util.HashSet<Comparable<?>> codes = new java.util.HashSet<>();
      for (CodedValue value : coded.values()) {
        if (!codes.add(parse(domain.fieldType(), value.code())))
          throw new IllegalArgumentException(
              "Duplicate domain code in " + domain.name() + ": " + value.code());
      }
    } else if (domain instanceof RangeDomain) {
      RangeDomain range = (RangeDomain) domain;
      if (domain.fieldType() == FileGdbFieldType.STRING)
        throw new IllegalArgumentException("Range domain requires numeric or date values");
      if (compare(
              parse(domain.fieldType(), range.minValue()),
              parse(domain.fieldType(), range.maxValue()))
          > 0) throw new IllegalArgumentException("Reversed domain range: " + domain.name());
    }
  }

  public static boolean contains(Domain domain, String literal) {
    Comparable<?> value = parse(domain.fieldType(), literal);
    if (domain instanceof CodedValueDomain) {
      CodedValueDomain coded = (CodedValueDomain) domain;
      return coded.values().stream()
          .anyMatch(v -> parse(domain.fieldType(), v.code()).equals(value));
    }
    RangeDomain range = (RangeDomain) domain;
    return compare(value, parse(domain.fieldType(), range.minValue())) >= 0
        && compare(value, parse(domain.fieldType(), range.maxValue())) <= 0;
  }
}
