package org.toonjava;

/**
 * Opciones de decodificación para controlar tolerancias de indentación y validaciones estrictas.
 */
public record ToonDecoderOptions(int indent, boolean strict) {
  public static final ToonDecoderOptions DEFAULT = new ToonDecoderOptions(2, true);

  public ToonDecoderOptions {
    if (indent <= 0) {
      throw new IllegalArgumentException("El tamaño de indentación debe ser mayor a cero");
    }
  }

  public static ToonDecoderOptions defaults() {
    return DEFAULT;
  }

  public ToonDecoderOptions withIndent(int indent) {
    return new ToonDecoderOptions(indent, strict);
  }

  public ToonDecoderOptions withStrict(boolean strict) {
    return new ToonDecoderOptions(indent, strict);
  }
}
