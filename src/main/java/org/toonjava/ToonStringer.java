package org.toonjava;

/**
 * Facilita la construcción de cadenas TOON en memoria utilizando {@link ToonWriter}. Similar a
 * {@code JSONStringer}, pero orientado al formato TOON.
 */
public final class ToonStringer {
  private final StringBuilder builder;
  private final ToonWriter writer;

  public ToonStringer() {
    this(ToonWriterOptions.defaults());
  }

  public ToonStringer(ToonWriterOptions options) {
    this.builder = new StringBuilder();
    this.writer = new ToonWriter(builder, options);
  }

  /**
   * Añade un valor cualquiera (objeto, array o primitivo) tras normalizarlo con {@link
   * ToonEncoder}.
   */
  public ToonStringer value(Object value) {
    writer.write(ToonEncoder.encode(value));
    return this;
  }

  /** Escribe directamente un {@link ToonObject}. */
  public ToonStringer object(ToonObject object) {
    writer.write(object);
    return this;
  }

  /** Escribe directamente un {@link ToonArray}. */
  public ToonStringer array(ToonArray array) {
    writer.write(array);
    return this;
  }

  /** Reinicia el contenido acumulado. */
  public ToonStringer reset() {
    builder.setLength(0);
    return this;
  }

  @Override
  public String toString() {
    return builder.toString();
  }

  /** Conversión rápida de un valor a cadena usando las opciones por defecto. */
  public static String encode(Object value) {
    return encode(value, ToonWriterOptions.defaults());
  }

  /** Conversión rápida de un valor a cadena con opciones personalizadas. */
  public static String encode(Object value, ToonWriterOptions options) {
    ToonStringer stringer = new ToonStringer(options);
    stringer.value(value);
    return stringer.toString();
  }
}
