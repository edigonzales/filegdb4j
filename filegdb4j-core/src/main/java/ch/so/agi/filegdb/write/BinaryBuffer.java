package ch.so.agi.filegdb.write;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Growable little endian buffer with the file geodatabase encodings. */
public final class BinaryBuffer {

  private byte[] data;
  private int size;

  public BinaryBuffer() {
    this(256);
  }

  public BinaryBuffer(int initialCapacity) {
    this.data = new byte[Math.max(16, initialCapacity)];
  }

  public int size() {
    return size;
  }

  public void u8(int value) {
    ensure(1);
    data[size++] = (byte) value;
  }

  public void u16(int value) {
    ensure(2);
    data[size++] = (byte) value;
    data[size++] = (byte) (value >>> 8);
  }

  public void i16(short value) {
    u16(value & 0xFFFF);
  }

  public void u32(long value) {
    ensure(4);
    data[size++] = (byte) value;
    data[size++] = (byte) (value >>> 8);
    data[size++] = (byte) (value >>> 16);
    data[size++] = (byte) (value >>> 24);
  }

  public void i32(int value) {
    u32(value & 0xFFFFFFFFL);
  }

  public void i64(long value) {
    ensure(8);
    for (int i = 0; i < 8; i++) {
      data[size++] = (byte) (value >>> (8 * i));
    }
  }

  public void f32(float value) {
    i32(Float.floatToIntBits(value));
  }

  public void f64(double value) {
    i64(Double.doubleToLongBits(value));
  }

  public void bytes(byte[] value) {
    ensure(value.length);
    System.arraycopy(value, 0, data, size, value.length);
    size += value.length;
  }

  public void ascii(String value) {
    bytes(value.getBytes(StandardCharsets.US_ASCII));
  }

  public void utf8(String value) {
    bytes(value.getBytes(StandardCharsets.UTF_8));
  }

  public void utf16(String value) {
    bytes(value.getBytes(StandardCharsets.UTF_16LE));
  }

  /** Unsigned LEB128 varint. */
  public void varUInt(long value) {
    if (value < 0) {
      throw new IllegalArgumentException("Negative unsigned varint: " + value);
    }
    while (true) {
      if (value >= 0x80) {
        u8((int) (0x80 | (value & 0x7F)));
        value >>>= 7;
      } else {
        u8((int) value);
        return;
      }
    }
  }

  /** Signed delta varint: low six bits magnitude, bit six sign, continuation bit seven. */
  public void varInt(long value) {
    boolean negative = value < 0;
    long magnitude = Math.abs(value);
    if (magnitude >= 0x40) {
      u8((int) (magnitude & 0x3F) | (negative ? 0x40 : 0) | 0x80);
      varUInt(magnitude >>> 6);
    } else {
      u8((int) magnitude | (negative ? 0x40 : 0));
    }
  }

  public void patchU8(int position, int value) {
    data[position] = (byte) value;
  }

  public void patchU32(int position, long value) {
    data[position] = (byte) value;
    data[position + 1] = (byte) (value >>> 8);
    data[position + 2] = (byte) (value >>> 16);
    data[position + 3] = (byte) (value >>> 24);
  }

  public byte[] toByteArray() {
    return Arrays.copyOf(data, size);
  }

  private void ensure(int additional) {
    if (size + additional > data.length) {
      data = Arrays.copyOf(data, Math.max(data.length * 2, size + additional));
    }
  }
}
