package org.toonjava;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Prepara estructuras con forma JSON para su serialización en formato TOON. Acepta mapas, listas,
 * arreglos y nodos de Jackson sin requerir dependencias adicionales en tiempo de ejecución.
 *
 * <p>La clase normaliza los valores de entrada a {@link ToonObject}, {@link ToonArray} y tipos
 * primitivos compatibles, validando referencias circulares y tipos no soportados.
 */
public final class ToonEncoder {
  private final IdentityHashMap<Object, Boolean> seen;

  public ToonEncoder() {
    this.seen = new IdentityHashMap<>();
  }

  /**
   * Normaliza el valor recibido a una representación válida para TOON. Los objetos se convierten a
   * {@link ToonObject}, las colecciones a {@link ToonArray} y los valores primitivos se devuelven
   * tal cual.
   *
   * @param value valor a normalizar.
   * @return representación lista para ser escrita como TOON.
   */
  public Object toToonValue(Object value) {
    return normalize(value);
  }

  /**
   * Normaliza el valor recibido y asegura que resulte en un {@link ToonObject}.
   *
   * @param value valor de entrada con forma de objeto.
   * @return instancia de {@link ToonObject}.
   */
  public ToonObject toToonObject(Object value) {
    Object normalized = toToonValue(value);
    if (normalized instanceof ToonObject object) {
      return object;
    }
    throw new ToonException("Se esperaba un objeto TOON pero se obtuvo " + describe(normalized));
  }

  /**
   * Normaliza el valor recibido y asegura que resulte en un {@link ToonArray}.
   *
   * @param value valor de entrada con forma de array.
   * @return instancia de {@link ToonArray}.
   */
  public ToonArray toToonArray(Object value) {
    Object normalized = toToonValue(value);
    if (normalized instanceof ToonArray array) {
      return array;
    }
    throw new ToonException("Se esperaba un array TOON pero se obtuvo " + describe(normalized));
  }

  /**
   * Normaliza un valor usando una instancia temporal del encoder. Es un atajo para casos donde no
   * se requiere reutilizar estado.
   */
  public static Object encode(Object value) {
    return new ToonEncoder().toToonValue(value);
  }

  /** Normaliza un objeto y garantiza que el resultado sea un {@link ToonObject}. */
  public static ToonObject encodeObject(Object value) {
    return new ToonEncoder().toToonObject(value);
  }

  /** Normaliza un array y garantiza que el resultado sea un {@link ToonArray}. */
  public static ToonArray encodeArray(Object value) {
    return new ToonEncoder().toToonArray(value);
  }

  private Object normalize(Object value) {
    if (value == null || value == ToonNull.INSTANCE) {
      return null;
    }
    if (value instanceof ToonObject || value instanceof ToonArray) {
      return value;
    }
    if (value instanceof ToonNull) {
      return null;
    }
    if (value instanceof Optional<?> optional) {
      return normalize(optional.orElse(null));
    }
    if (JacksonBridge.isJsonNode(value)) {
      Object bridged = JacksonBridge.toJavaValue(value);
      return normalize(bridged);
    }
    if (value instanceof Map<?, ?> map) {
      return mapToObject(map);
    }
    if (value instanceof Iterable<?> iterable) {
      return iterableToArray(iterable);
    }
    if (value.getClass().isArray()) {
      return arrayToArray(value);
    }
    if (value instanceof CharSequence sequence) {
      return sequence.toString();
    }
    if (value instanceof Character character) {
      return character.toString();
    }
    if (value instanceof Number number) {
      validateNumber(number);
      return number;
    }
    if (value instanceof Boolean) {
      return value;
    }
    if (value instanceof Enum<?> enumeration) {
      return enumeration.name();
    }
    throw new ToonException(
        "Tipo de valor no soportado para ToonEncoder: " + value.getClass().getName());
  }

  private ToonObject mapToObject(Map<?, ?> map) {
    if (seen.put(map, Boolean.TRUE) != null) {
      throw new ToonException("Se detectó una referencia circular en un mapa");
    }
    try {
      LinkedHashMap<String, Object> copy = new LinkedHashMap<>(map.size());
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = normalizeKey(entry.getKey());
        copy.put(key, normalize(entry.getValue()));
      }
      return new ToonObject(copy);
    } finally {
      seen.remove(map);
    }
  }

  private ToonArray iterableToArray(Iterable<?> iterable) {
    if (seen.put(iterable, Boolean.TRUE) != null) {
      throw new ToonException("Se detectó una referencia circular en una colección");
    }
    try {
      List<Object> values = new ArrayList<>();
      for (Object item : iterable) {
        values.add(normalize(item));
      }
      return new ToonArray(values);
    } finally {
      seen.remove(iterable);
    }
  }

  private ToonArray arrayToArray(Object array) {
    if (seen.put(array, Boolean.TRUE) != null) {
      throw new ToonException("Se detectó una referencia circular en un arreglo");
    }
    try {
      int length = Array.getLength(array);
      List<Object> values = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        values.add(normalize(Array.get(array, i)));
      }
      return new ToonArray(values);
    } finally {
      seen.remove(array);
    }
  }

  private static String normalizeKey(Object key) {
    if (key == null) {
      throw new ToonException("Las claves de un objeto no pueden ser null");
    }
    if (key instanceof String string) {
      return string;
    }
    if (key instanceof CharSequence sequence) {
      return sequence.toString();
    }
    throw new ToonException(
        "Las claves de un objeto deben ser String o CharSequence. Se recibió: "
            + key.getClass().getName());
  }

  private static void validateNumber(Number number) {
    if (number instanceof Double d && (d.isNaN() || d.isInfinite())) {
      throw new ToonException("Los números NaN o infinitos no están permitidos: " + number);
    }
    if (number instanceof Float f && (f.isNaN() || f.isInfinite())) {
      throw new ToonException("Los números NaN o infinitos no están permitidos: " + number);
    }
  }

  private static String describe(Object value) {
    if (value == null) {
      return "null";
    }
    if (value == ToonNull.INSTANCE) {
      return "ToonNull";
    }
    return value.getClass().getSimpleName();
  }

  private static final class JacksonBridge {
    private static final Class<?> JSON_NODE_CLASS;
    private static final Method IS_OBJECT;
    private static final Method IS_ARRAY;
    private static final Method IS_NULL;
    private static final Method IS_MISSING_NODE;
    private static final Method IS_BOOLEAN;
    private static final Method BOOLEAN_VALUE;
    private static final Method IS_NUMBER;
    private static final Method NUMBER_VALUE;
    private static final Method IS_TEXTUAL;
    private static final Method TEXT_VALUE;
    private static final Method IS_BINARY;
    private static final Method BINARY_VALUE;
    private static final Method FIELDS;
    private static final Method ELEMENTS;

    static {
      Class<?> jsonNodeClass = null;
      Method isObject = null;
      Method isArray = null;
      Method isNull = null;
      Method isMissingNode = null;
      Method isBoolean = null;
      Method booleanValue = null;
      Method isNumber = null;
      Method numberValue = null;
      Method isTextual = null;
      Method textValue = null;
      Method isBinary = null;
      Method binaryValue = null;
      Method fields = null;
      Method elements = null;
      try {
        jsonNodeClass = Class.forName("com.fasterxml.jackson.databind.JsonNode");
        isObject = jsonNodeClass.getMethod("isObject");
        isArray = jsonNodeClass.getMethod("isArray");
        isNull = jsonNodeClass.getMethod("isNull");
        isMissingNode = jsonNodeClass.getMethod("isMissingNode");
        isBoolean = jsonNodeClass.getMethod("isBoolean");
        booleanValue = jsonNodeClass.getMethod("booleanValue");
        isNumber = jsonNodeClass.getMethod("isNumber");
        numberValue = jsonNodeClass.getMethod("numberValue");
        isTextual = jsonNodeClass.getMethod("isTextual");
        textValue = jsonNodeClass.getMethod("textValue");
        isBinary = findMethod(jsonNodeClass, "isBinary");
        binaryValue = findMethod(jsonNodeClass, "binaryValue");
        fields = jsonNodeClass.getMethod("fields");
        elements = jsonNodeClass.getMethod("elements");
      } catch (ClassNotFoundException ignored) {
        jsonNodeClass = null;
      } catch (NoSuchMethodException ex) {
        throw new ExceptionInInitializerError(ex);
      }
      JSON_NODE_CLASS = jsonNodeClass;
      IS_OBJECT = isObject;
      IS_ARRAY = isArray;
      IS_NULL = isNull;
      IS_MISSING_NODE = isMissingNode;
      IS_BOOLEAN = isBoolean;
      BOOLEAN_VALUE = booleanValue;
      IS_NUMBER = isNumber;
      NUMBER_VALUE = numberValue;
      IS_TEXTUAL = isTextual;
      TEXT_VALUE = textValue;
      IS_BINARY = isBinary;
      BINARY_VALUE = binaryValue;
      FIELDS = fields;
      ELEMENTS = elements;
    }

    private JacksonBridge() {}

    static boolean isJsonNode(Object value) {
      return JSON_NODE_CLASS != null && JSON_NODE_CLASS.isInstance(value);
    }

    static Object toJavaValue(Object jsonNode) {
      if (!isJsonNode(jsonNode)) {
        throw new IllegalArgumentException("El valor proporcionado no es un JsonNode");
      }
      try {
        if (isNull(jsonNode)) {
          return null;
        }
        if ((boolean) IS_OBJECT.invoke(jsonNode)) {
          return readObject(jsonNode);
        }
        if ((boolean) IS_ARRAY.invoke(jsonNode)) {
          return readArray(jsonNode);
        }
        if ((boolean) IS_BOOLEAN.invoke(jsonNode)) {
          return BOOLEAN_VALUE.invoke(jsonNode);
        }
        if ((boolean) IS_NUMBER.invoke(jsonNode)) {
          return NUMBER_VALUE.invoke(jsonNode);
        }
        if ((boolean) IS_TEXTUAL.invoke(jsonNode)) {
          return TEXT_VALUE.invoke(jsonNode);
        }
        if (IS_BINARY != null && (boolean) IS_BINARY.invoke(jsonNode)) {
          return BINARY_VALUE.invoke(jsonNode);
        }
        throw new ToonException(
            "Tipo de JsonNode no soportado para encoding: " + jsonNode.getClass().getName());
      } catch (IllegalAccessException ex) {
        throw new ToonException("No se pudo acceder a JsonNode mediante reflexión", ex);
      } catch (InvocationTargetException ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        throw new ToonException("Falló la conversión de JsonNode a valor Java", cause);
      }
    }

    private static boolean isNull(Object jsonNode)
        throws InvocationTargetException, IllegalAccessException {
      boolean nullNode = IS_NULL != null && (boolean) IS_NULL.invoke(jsonNode);
      boolean missingNode = IS_MISSING_NODE != null && (boolean) IS_MISSING_NODE.invoke(jsonNode);
      return nullNode || missingNode;
    }

    private static Object readObject(Object jsonNode)
        throws InvocationTargetException, IllegalAccessException {
      @SuppressWarnings("unchecked")
      Iterator<Map.Entry<String, Object>> iterator =
          (Iterator<Map.Entry<String, Object>>) FIELDS.invoke(jsonNode);
      LinkedHashMap<String, Object> result = new LinkedHashMap<>();
      while (iterator.hasNext()) {
        Map.Entry<String, Object> entry = iterator.next();
        String key = Objects.requireNonNull(entry.getKey(), "JsonNode field key");
        Object child = entry.getValue();
        result.put(key, toJavaValue(child));
      }
      return result;
    }

    private static Object readArray(Object jsonNode)
        throws InvocationTargetException, IllegalAccessException {
      @SuppressWarnings("unchecked")
      Iterator<Object> iterator = (Iterator<Object>) ELEMENTS.invoke(jsonNode);
      List<Object> result = new ArrayList<>();
      while (iterator.hasNext()) {
        Object child = iterator.next();
        result.add(toJavaValue(child));
      }
      return result;
    }

    private static Method findMethod(Class<?> type, String name) {
      try {
        return type.getMethod(name);
      } catch (NoSuchMethodException ignored) {
        return null;
      }
    }
  }
}
