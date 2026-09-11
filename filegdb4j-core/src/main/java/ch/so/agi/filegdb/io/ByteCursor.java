package ch.so.agi.filegdb.io;

import ch.so.agi.filegdb.GdbException;
import java.nio.charset.StandardCharsets;

/**
 * Little endian cursor over a byte array.
 *
 * <p>Provides the primitives used by the file geodatabase binary format:
 * fixed width integers, IEEE doubles and the LEB128 style varints.
 */
public final class ByteCursor {

  private final byte[] data;
  private final int limit;
  private int position;

  public ByteCursor(byte[] data) {
    this(data, 0, data.length);
  }

  public ByteCursor(byte[] data, int offset, int length) {
    this.data = data;
    this.position = offset;
    this.limit = offset + length;
  }

  public int position() {
    return position;
  }

  public int remaining() {
    return limit - position;
  }

  public void skip(int count) {
    require(count);
    position += count;
  }

  private void require(int count) {
    if (count < 0 || position + count > limit) {
      throw new GdbException("Unexpected end of buffer at offset " + position);
    }
  }

  public int u8() {
    require(1);
    return data[position++] & 0xFF;
  }

  public int u16() {
    require(2);
    int value = (data[position] & 0xFF) | ((data[position + 1] & 0xFF) << 8);
    position += 2;
    return value;
  }

  public short i16() {
    return (short) u16();
  }

  public long u32() {
    require(4);
    long value =
        (data[position] & 0xFFL)
            | ((data[position + 1] & 0xFFL) << 8)
            | ((data[position + 2] & 0xFFL) << 16)
            | ((data[position + 3] & 0xFFL) << 24);
    position += 4;
    return value;
  }

  public int i32() {
    return (int) u32();
  }

  public long i64() {
    require(8);
    long value = 0;
    for (int i = 0; i < 8; i++) {
      value |= (data[position + i] & 0xFFL) << (8 * i);
    }
    position += 8;
    return value;
  }

  public float f32() {
    return Float.intBitsToFloat((int) u32());
  }

  public double f64() {
    return Double.longBitsToDouble(i64());
  }

  public byte[] bytes(int count) {
    require(count);
    byte[] result = new byte[count];
    System.arraycopy(data, position, result, 0, count);
    position += count;
    return result;
  }

  public long varUInt32() {
    int b = u8();
    long value = b & 0x7FL;
    int shift = 7;
    while ((b & 0x80) != 0) {
      b = u8();
      value |= (long) (b & 0x7F) << shift;
      shift += 7;
      if (shift > 35) {
        throw new GdbException("Invalid varuint32 at offset " + position);
      }
    }
    return value;
  }

  public long varUInt64() {
    int b = u8();
    long value = b & 0x7FL;
    int shift = 7;
    while ((b & 0x80) != 0) {
      b = u8();
      value |= (long) (b & 0x7F) << shift;
      shift += 7;
      if (shift > 70) {
        throw new GdbException("Invalid varuint64 at offset " + position);
      }
    }
    return value;
  }

  /** Signed delta varint: low six bits magnitude, bit six sign, continuation bit seven. */
  public long varIntDelta() {
    int b = u8();
    long value = b & 0x3FL;
    boolean negative = (b & 0x40) != 0;
    if ((b & 0x80) == 0) {
      return negative ? -value : value;
    }
    int shift = 6;
    while (true) {
      b = u8();
      value |= (long) (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        break;
      }
      shift += 7;
      if (shift >= 70) {
        break;
      }
    }
    return negative ? -value : value;
  }

  /** Reads a UTF-16LE string with the given number of UTF-16 code units. */
  public String utf16(int characterCount) {
    return new String(bytes(characterCount * 2), StandardCharsets.UTF_16LE);
  }

  public String utf8(int byteCount) {
    return new String(bytes(byteCount), StandardCharsets.UTF_8);
  }
}
