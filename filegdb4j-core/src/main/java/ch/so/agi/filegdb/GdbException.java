package ch.so.agi.filegdb;

/** Signals a malformed file geodatabase or an unsupported format construct. */
public class GdbException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public GdbException(String message) {
    super(message);
  }

  public GdbException(String message, Throwable cause) {
    super(message, cause);
  }
}
