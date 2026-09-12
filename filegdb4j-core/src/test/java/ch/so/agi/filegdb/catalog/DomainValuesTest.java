package ch.so.agi.filegdb.catalog;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.filegdb.table.FileGdbFieldType;
import java.util.List;
import org.junit.jupiter.api.Test;

class DomainValuesTest {
  @Test
  void comparesAtDeclaredPrecisionWithoutRoundingIntegerCodes() {
    var integers =
        new CodedValueDomain(
            "ids",
            FileGdbFieldType.INT64,
            "",
            List.of(new CodedValue("large", "9007199254740993")));
    assertThat(DomainValues.contains(integers, "9007199254740993")).isTrue();
    assertThat(DomainValues.contains(integers, "9007199254740992")).isFalse();
    var floats =
        new CodedValueDomain(
            "floats", FileGdbFieldType.FLOAT32, "", List.of(new CodedValue("tenth", "0.1")));
    assertThat(DomainValues.contains(floats, Double.toString((double) 0.1f))).isTrue();
    assertThatThrownBy(() -> DomainValues.parse(FileGdbFieldType.INT32, "2147483648"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DomainValues.parse(FileGdbFieldType.FLOAT64, "NaN"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void checksDateRangeAndDuplicateNumericCodes() {
    var range = new RangeDomain("dates", FileGdbFieldType.DATE, "", "2025-01-01", "2026-01-01");
    assertThat(DomainValues.contains(range, "2026-01-01")).isTrue();
    assertThat(DomainValues.contains(range, "2026-01-02")).isFalse();
    assertThatThrownBy(
            () ->
                DomainValues.validate(
                    new CodedValueDomain(
                        "codes",
                        FileGdbFieldType.INT32,
                        "",
                        List.of(new CodedValue("one", "1"), new CodedValue("also one", "01")))))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
