package org.toonjava;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ToonEncoderTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void encodeObjectNormalizesNestedStructures() {
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("id", 7);
    user.put("name", "Ada");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("user", user);
    payload.put("tags", List.of("ml", "ops"));

    ToonObject result = ToonEncoder.encodeObject(payload);
    assertEquals(2, result.size());
    ToonObject userObject = result.getObject("user");
    assertEquals(7, userObject.getInt("id"));
    assertEquals("Ada", userObject.getString("name"));

    ToonArray tags = result.getArray("tags");
    assertEquals(List.of("ml", "ops"), tags.toList());
  }

  @Test
  void toToonArrayAcceptsPrimitiveArrays() {
    int[] numbers = new int[] {1, 2, 3};
    ToonArray array = ToonEncoder.encodeArray(numbers);

    assertEquals(3, array.size());
    assertEquals(List.of(1, 2, 3), array.toList());
  }

  @Test
  void normalizesOptionalValues() {
    ToonEncoder encoder = new ToonEncoder();
    Object present = encoder.toToonValue(Optional.of("value"));
    Object empty = encoder.toToonValue(Optional.empty());

    assertEquals("value", present);
    assertNull(empty);
  }

  @Test
  void convertsJsonNodeToToonObject() {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("id", 123);
    node.put("name", "Ada");

    ToonObject object = ToonEncoder.encodeObject(node);

    assertEquals(123, object.getInt("id"));
    assertEquals("Ada", object.getString("name"));
  }

  @Test
  void throwsOnCircularReferencesInMap() {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("value", 1);
    root.put("self", root);

    assertThrows(ToonException.class, () -> ToonEncoder.encodeObject(root));
  }

  @Test
  void rejectsNonStringKeys() {
    Map<Object, Object> raw = new LinkedHashMap<>();
    raw.put(123, "value");

    assertThrows(ToonException.class, () -> ToonEncoder.encodeObject(raw));
  }

  @Test
  void rejectsUnsupportedTypes() {
    ToonEncoder encoder = new ToonEncoder();
    Object unsupported = new Object();

    assertThrows(ToonException.class, () -> encoder.toToonValue(unsupported));
  }
}
