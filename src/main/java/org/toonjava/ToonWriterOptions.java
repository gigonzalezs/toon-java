package org.toonjava;

import java.util.Objects;

/**
 * Configuración de formato para la escritura en TOON. Permite ajustar la indentación, el
 * delimitador usado en arrays y el marcador de longitud opcional.
 */
public record ToonWriterOptions(int indent, String delimiter, String lengthMarker) {
  public static final ToonWriterOptions DEFAULT = new ToonWriterOptions(2, ",", "");

  public ToonWriterOptions {
    if (indent <= 0) {
      throw new IllegalArgumentException("La indentación debe ser mayor que cero");
    }
    Objects.requireNonNull(delimiter, "delimiter");
    Objects.requireNonNull(lengthMarker, "lengthMarker");
    if (delimiter.isEmpty()) {
      throw new IllegalArgumentException("El delimitador no puede ser vacío");
    }
  }

  public static ToonWriterOptions defaults() {
    return DEFAULT;
  }

  public ToonWriterOptions withIndent(int indent) {
    return new ToonWriterOptions(indent, delimiter, lengthMarker);
  }

  public ToonWriterOptions withDelimiter(String delimiter) {
    return new ToonWriterOptions(indent, delimiter, lengthMarker);
  }

  public ToonWriterOptions withLengthMarker(String lengthMarker) {
    return new ToonWriterOptions(indent, delimiter, lengthMarker);
  }
}
