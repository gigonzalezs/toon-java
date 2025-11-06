package org.toonjava;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Generador de texto TOON a partir de estructuras dinámicas ({@link ToonObject}, {@link
 * ToonArray}). Gestiona indentación, delimitadores y detección de encabezados tabulares.
 */
public final class ToonWriter {
  private static final Pattern NUMBER_PATTERN =
      Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

  private final Appendable out;
  private final ToonWriterOptions options;
  private final int indentSize;
  private final String delimiter;
  private final String lengthMarker;

  public ToonWriter(Appendable out) {
    this(out, ToonWriterOptions.defaults());
  }

  public ToonWriter(Appendable out, ToonWriterOptions options) {
    this.out = Objects.requireNonNull(out, "out");
    this.options = Objects.requireNonNull(options, "options");
    this.indentSize = options.indent();
    this.delimiter = options.delimiter();
    this.lengthMarker = options.lengthMarker();
  }

  /** Escribe un valor en la salida asociada. */
  public void write(Object value) {
    Object normalized = normalize(value);
    if (normalized instanceof ToonObject object) {
      writeObject(object, 0);
    } else if (normalized instanceof ToonArray array) {
      writeArrayRoot(array);
    } else {
      append(formatPrimitive(normalized));
    }
  }

  private void writeObject(ToonObject object, int indentLevel) {
    if (object.isEmpty()) {
      // Objeto vacío en raíz → salida vacía; en niveles anidados se imprime solo la cabecera.
      return;
    }
    Iterator<String> keys = object.keySet().iterator();
    boolean first = true;
    while (keys.hasNext()) {
      String key = keys.next();
      Object value = object.opt(key);
      if (!first) {
        newline();
      }
      appendIndent(indentLevel);
      writeKeyValue(key, value, indentLevel);
      first = false;
    }
  }

  private void writeKeyValue(String key, Object value, int indentLevel) {
    append(formatKey(key));
    if (value instanceof ToonObject object) {
      append(':');
      if (!object.isEmpty()) {
        newline();
        writeObject(object, indentLevel + 1);
      }
      return;
    }
    if (value instanceof ToonArray array) {
      writeArrayEntry(array, indentLevel);
      return;
    }
    append(": ");
    append(formatPrimitive(value));
  }

  private void writeArrayEntry(ToonArray array, int indentLevel) {
    ArrayAnalysis analysis = analyzeArray(array);
    append(arrayHeader(array, analysis));
    append(':');
    switch (analysis.kind) {
      case EMPTY -> {}
      case PRIMITIVE_INLINE -> {
        append(' ');
        append(joinInline(array));
      }
      case TABULAR -> {
        newline();
        writeTabularRows(array, indentLevel + 1, analysis.columns);
      }
      case LIST -> {
        newline();
        writeListItems(array, indentLevel + 1);
      }
    }
  }

  private void writeArrayRoot(ToonArray array) {
    ArrayAnalysis analysis = analyzeArray(array);
    append(arrayHeader(array, analysis));
    append(':');
    switch (analysis.kind) {
      case EMPTY -> {}
      case PRIMITIVE_INLINE -> {
        append(' ');
        append(joinInline(array));
      }
      case TABULAR -> {
        newline();
        writeTabularRows(array, 1, analysis.columns);
      }
      case LIST -> {
        newline();
        writeListItems(array, 1);
      }
    }
  }

  private void writeListItems(ToonArray array, int indentLevel) {
    int size = array.size();
    for (int index = 0; index < size; index++) {
      Object item = array.get(index);
      if (index > 0) {
        newline();
      }
      appendIndent(indentLevel);
      append('-');
      Object normalized = normalize(item);
      if (normalized == null) {
        append(' ');
        append("null");
        continue;
      }
      if (normalized instanceof ToonObject object) {
        if (object.isEmpty()) {
          continue;
        }
        append(' ');
        writeObjectListEntry(object, indentLevel);
        continue;
      }
      if (normalized instanceof ToonArray nestedArray) {
        append(' ');
        writeArrayListItem(nestedArray, indentLevel);
        continue;
      }
      append(' ');
      append(formatPrimitive(normalized));
    }
  }

  private void writeObjectListEntry(ToonObject object, int indentLevel) {
    Iterator<String> keys = object.keySet().iterator();
    if (!keys.hasNext()) {
      return;
    }
    String firstKey = keys.next();
    Object firstValue = object.opt(firstKey);
    writeKeyValue(firstKey, firstValue, indentLevel);
    while (keys.hasNext()) {
      newline();
      appendIndent(indentLevel + 1);
      String key = keys.next();
      Object value = object.opt(key);
      writeKeyValue(key, value, indentLevel + 1);
    }
  }

  private void writeArrayListItem(ToonArray array, int indentLevel) {
    ArrayAnalysis analysis = analyzeArray(array);
    append(arrayHeader(array, analysis));
    append(':');
    switch (analysis.kind) {
      case EMPTY -> {}
      case PRIMITIVE_INLINE -> {
        append(' ');
        append(joinInline(array));
      }
      case TABULAR -> {
        newline();
        writeTabularRows(array, indentLevel + 1, analysis.columns);
      }
      case LIST -> {
        newline();
        writeListItems(array, indentLevel + 1);
      }
    }
  }

  private void writeTabularRows(ToonArray array, int indentLevel, List<String> columns) {
    for (int rowIndex = 0; rowIndex < array.size(); rowIndex++) {
      if (rowIndex > 0) {
        newline();
      }
      ToonObject row = (ToonObject) array.get(rowIndex);
      appendIndent(indentLevel);
      for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
        if (colIndex > 0) {
          append(delimiter);
        }
        String column = columns.get(colIndex);
        Object cell = row.opt(column);
        append(formatPrimitive(cell));
      }
    }
  }

  private String joinInline(ToonArray array) {
    StringBuilder builder = new StringBuilder();
    for (int index = 0; index < array.size(); index++) {
      if (index > 0) {
        builder.append(delimiter);
      }
      Object value = normalize(array.get(index));
      builder.append(formatPrimitive(value));
    }
    return builder.toString();
  }

  private ArrayAnalysis analyzeArray(ToonArray array) {
    if (array.isEmpty()) {
      return ArrayAnalysis.empty();
    }
    boolean primitivesOnly = true;
    for (int i = 0; i < array.size(); i++) {
      Object value = normalize(array.get(i));
      if (!isPrimitive(value)) {
        primitivesOnly = false;
        break;
      }
    }
    if (primitivesOnly) {
      return ArrayAnalysis.inline();
    }

    List<String> columns = null;
    for (int i = 0; i < array.size(); i++) {
      Object value = normalize(array.get(i));
      if (!(value instanceof ToonObject object)) {
        return ArrayAnalysis.list();
      }
      List<String> keyList = new ArrayList<>();
      for (String key : object.keySet()) {
        keyList.add(key);
      }
      if (columns == null) {
        columns = keyList;
      } else if (!columns.equals(keyList)) {
        return ArrayAnalysis.list();
      }
      for (String key : columns) {
        Object cell = object.opt(key);
        if (!isPrimitive(normalize(cell))) {
          return ArrayAnalysis.list();
        }
      }
    }
    if (columns == null) {
      columns = Collections.emptyList();
    }
    return ArrayAnalysis.tabular(columns);
  }

  private String arrayHeader(ToonArray array, ArrayAnalysis analysis) {
    StringBuilder builder = new StringBuilder();
    builder.append('[');
    if (!lengthMarker.isEmpty()) {
      builder.append(lengthMarker);
    }
    builder.append(array.size());
    if (!",".equals(delimiter)) {
      builder.append(delimiter);
    }
    builder.append(']');
    if (analysis.kind == ArrayAnalysis.Kind.TABULAR) {
      builder.append('{');
      for (int index = 0; index < analysis.columns.size(); index++) {
        if (index > 0) {
          builder.append(delimiter);
        }
        builder.append(formatKey(analysis.columns.get(index)));
      }
      builder.append('}');
    }
    return builder.toString();
  }

  private String formatPrimitive(Object value) {
    Object normalized = normalize(value);
    if (normalized == null) {
      return "null";
    }
    if (normalized instanceof Boolean b) {
      return Boolean.toString(b);
    }
    if (normalized instanceof Number number) {
      return formatNumber(number);
    }
    if (normalized instanceof String s) {
      return formatString(s);
    }
    if (normalized instanceof ToonNull) {
      return "null";
    }
    throw new ToonException(
        "Tipo no soportado para valor primitivo: " + normalized.getClass().getName());
  }

  private String formatString(String value) {
    if (!requiresQuotes(value)) {
      return value;
    }
    return '"' + escape(value) + '"';
  }

  private String formatKey(String key) {
    Objects.requireNonNull(key, "key");
    if (isBareKey(key) && !key.contains(delimiter)) {
      return key;
    }
    return '"' + escape(key) + '"';
  }

  private boolean isBareKey(String key) {
    if (key.isEmpty()) {
      return false;
    }
    char first = key.charAt(0);
    if (!(Character.isLetter(first) || first == '_')) {
      return false;
    }
    for (int i = 1; i < key.length(); i++) {
      char ch = key.charAt(i);
      if (!(Character.isLetterOrDigit(ch) || ch == '_')) {
        return false;
      }
    }
    return true;
  }

  private boolean requiresQuotes(String value) {
    if (value.isEmpty()) {
      return true;
    }
    int length = value.length();
    if (Character.isWhitespace(value.charAt(0))
        || Character.isWhitespace(value.charAt(length - 1))) {
      return true;
    }
    if (value.indexOf('"') >= 0 || value.indexOf('\\') >= 0) {
      return true;
    }
    if (containsControl(value)) {
      return true;
    }
    if (value.startsWith("- ")) {
      return true;
    }
    if (value.startsWith("[") || value.startsWith("{")) {
      return true;
    }
    if (value.indexOf(':') >= 0) {
      return true;
    }
    if (!delimiter.isEmpty() && value.contains(delimiter)) {
      return true;
    }
    if ("true".equals(value) || "false".equals(value) || "null".equals(value)) {
      return true;
    }
    if (NUMBER_PATTERN.matcher(value).matches()) {
      return true;
    }
    return false;
  }

  private boolean containsControl(String value) {
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch == '\n' || ch == '\r' || ch == '\t') {
        return true;
      }
      if (ch < 0x20) {
        return true;
      }
    }
    return false;
  }

  private String escape(String value) {
    StringBuilder builder = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      switch (ch) {
        case '"' -> builder.append("\\\"");
        case '\\' -> builder.append("\\\\");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        case '\t' -> builder.append("\\t");
        default -> {
          if (ch < 0x20) {
            builder.append(String.format("\\u%04x", (int) ch));
          } else {
            builder.append(ch);
          }
        }
      }
    }
    return builder.toString();
  }

  private String formatNumber(Number number) {
    if (number instanceof Byte
        || number instanceof Short
        || number instanceof Integer
        || number instanceof Long) {
      return number.toString();
    }
    if (number instanceof java.math.BigInteger bigInteger) {
      return bigInteger.toString();
    }
    if (number instanceof java.math.BigDecimal bigDecimal) {
      return normalizeDecimal(bigDecimal);
    }
    if (number instanceof Float || number instanceof Double) {
      if (number.doubleValue() == 0d) {
        return "0";
      }
      try {
        BigDecimal decimal = new BigDecimal(number.toString());
        return normalizeDecimal(decimal);
      } catch (NumberFormatException ex) {
        BigDecimal decimal = BigDecimal.valueOf(number.doubleValue());
        return normalizeDecimal(decimal);
      }
    }
    return number.toString();
  }

  private String normalizeDecimal(BigDecimal decimal) {
    String text = decimal.stripTrailingZeros().toPlainString();
    if (text.indexOf('E') >= 0 || text.indexOf('e') >= 0) {
      text = decimal.toPlainString();
    }
    if (text.indexOf('.') >= 0) {
      int end = text.length();
      while (end > 0 && text.charAt(end - 1) == '0') {
        end--;
      }
      if (end > 0 && text.charAt(end - 1) == '.') {
        end--;
      }
      text = text.substring(0, end);
    }
    if (text.equals("-0")) {
      return "0";
    }
    return text;
  }

  private boolean isPrimitive(Object value) {
    return value == null
        || value == ToonNull.INSTANCE
        || value instanceof String
        || value instanceof Number
        || value instanceof Boolean;
  }

  private Object normalize(Object value) {
    if (value == ToonNull.INSTANCE) {
      return null;
    }
    return value;
  }

  private void append(CharSequence sequence) {
    try {
      out.append(sequence);
    } catch (IOException ex) {
      throw new ToonException("Error escribiendo salida TOON", ex);
    }
  }

  private void append(char ch) {
    try {
      out.append(ch);
    } catch (IOException ex) {
      throw new ToonException("Error escribiendo salida TOON", ex);
    }
  }

  private void newline() {
    append('\n');
  }

  private void appendIndent(int level) {
    int spaces = level * indentSize;
    for (int i = 0; i < spaces; i++) {
      append(' ');
    }
  }

  private static final class ArrayAnalysis {
    enum Kind {
      EMPTY,
      PRIMITIVE_INLINE,
      TABULAR,
      LIST
    }

    final Kind kind;
    final List<String> columns;

    private ArrayAnalysis(Kind kind, List<String> columns) {
      this.kind = kind;
      this.columns = columns;
    }

    static ArrayAnalysis empty() {
      return new ArrayAnalysis(Kind.EMPTY, Collections.emptyList());
    }

    static ArrayAnalysis inline() {
      return new ArrayAnalysis(Kind.PRIMITIVE_INLINE, Collections.emptyList());
    }

    static ArrayAnalysis list() {
      return new ArrayAnalysis(Kind.LIST, Collections.emptyList());
    }

    static ArrayAnalysis tabular(List<String> columns) {
      return new ArrayAnalysis(Kind.TABULAR, columns);
    }
  }
}
