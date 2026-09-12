package ch.so.agi.filegdb.catalog;

import ch.so.agi.filegdb.table.FileGdbFieldType;
import java.time.LocalDateTime;

/** Lossless conversion of domain literals for schema and row validation. */
public final class DomainValues {
  private DomainValues() {}

  public static Comparable<?> parse(FileGdbFieldType type, String value) {
    if (value == null) throw new IllegalArgumentException("Domain value is required");
    return switch (type) {
      case STRING -> value;
      case INT16 -> Short.valueOf(value);
      case INT32 -> Integer.valueOf(value);
      case INT64 -> Long.valueOf(value);
      case FLOAT32 -> {
        float number = Float.parseFloat(value);
        if (!Float.isFinite(number))
          throw new IllegalArgumentException("Domain number outside field range: " + value);
        yield number == 0 ? 0.0f : number;
      }
      case FLOAT64 -> {
        double number = Double.parseDouble(value);
        if (!Double.isFinite(number))
          throw new IllegalArgumentException("Domain number outside field range: " + value);
        yield number == 0 ? 0.0 : number;
      }
      case DATETIME -> LocalDateTime.parse(value.replace(' ', 'T'));
      case DATE -> java.time.LocalDate.parse(value);
      case TIME -> java.time.LocalTime.parse(value);
      default -> throw new IllegalArgumentException("Unsupported domain field type: " + type);
    };
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compare(Comparable a, Comparable b) {
    return a.compareTo(b);
  }

  public static void validate(Domain domain) {
    if (domain.fieldType() == null
        || !java.util.Set.of(
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
    if (domain.name() == null || domain.name().isBlank())
      throw new IllegalArgumentException("Domain name is required");
    if (domain instanceof CodedValueDomain coded) {
      var codes = new java.util.HashSet<Comparable<?>>();
      for (var value : coded.values()) {
        if (!codes.add(parse(domain.fieldType(), value.code())))
          throw new IllegalArgumentException(
              "Duplicate domain code in " + domain.name() + ": " + value.code());
      }
    } else if (domain instanceof RangeDomain range) {
      if (domain.fieldType() == FileGdbFieldType.STRING)
        throw new IllegalArgumentException("Range domain requires numeric or date values");
      if (compare(
              parse(domain.fieldType(), range.minValue()),
              parse(domain.fieldType(), range.maxValue()))
          > 0) throw new IllegalArgumentException("Reversed domain range: " + domain.name());
    }
  }

  public static boolean contains(Domain domain, String literal) {
    var value = parse(domain.fieldType(), literal);
    if (domain instanceof CodedValueDomain coded)
      return coded.values().stream()
          .anyMatch(v -> parse(domain.fieldType(), v.code()).equals(value));
    var range = (RangeDomain) domain;
    return compare(value, parse(domain.fieldType(), range.minValue())) >= 0
        && compare(value, parse(domain.fieldType(), range.maxValue())) <= 0;
  }
}
