package org.toonjava;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToonWriterTest {

  @Test
  void writesObjectsWithNestedStructuresAndArrays() {
    Map<String, Object> input = new LinkedHashMap<>();
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("id", 123);
    user.put("name", "Ada");
    input.put("user", user);
    input.put("tags", List.of("ml", "ops"));

    ToonObject object = ToonEncoder.encodeObject(input);
    StringBuilder buffer = new StringBuilder();
    new ToonWriter(buffer).write(object);

    assertEquals(
        """
        user:
          id: 123
          name: Ada
        tags[2]: ml,ops""",
        buffer.toString(),
        () -> "Resultado:\n" + buffer);
  }

  @Test
  void quotesAmbiguousAndNonBareValues() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("order:id", "true");
    input.put("note", "a,b");
    input.put("plain", "ok");

    ToonObject object = ToonEncoder.encodeObject(input);
    StringBuilder buffer = new StringBuilder();
    new ToonWriter(buffer).write(object);

    assertEquals(
        """
        "order:id": "true"
        note: "a,b"
        plain: ok""", buffer.toString());
  }

  @Test
  void writesTabularArraysWhenObjectsShareSchema() {
    Map<String, Object> row1 = new LinkedHashMap<>();
    row1.put("id", 1);
    row1.put("name", "Ada");
    row1.put("score", 9.9);
    Map<String, Object> row2 = new LinkedHashMap<>();
    row2.put("id", 2);
    row2.put("name", "Bob");
    row2.put("score", 8.5);
    List<Map<String, Object>> rows = List.of(row1, row2);
    Map<String, Object> input = Map.of("results", rows);

    ToonObject object = ToonEncoder.encodeObject(input);
    StringBuilder buffer = new StringBuilder();
    new ToonWriter(buffer).write(object);

    assertEquals(
        """
        results[2]{id,name,score}:
          1,Ada,9.9
          2,Bob,8.5""",
        buffer.toString(),
        () -> "Resultado:\n" + buffer);
  }

  @Test
  void honorsCustomDelimiterAndLengthMarker() {
    ToonArray array = ToonEncoder.encodeArray(List.of("a", "b|c", "d"));
    ToonWriterOptions options =
        ToonWriterOptions.defaults().withDelimiter("|").withLengthMarker("#");

    StringBuilder buffer = new StringBuilder();
    new ToonWriter(buffer, options).write(array);

    assertEquals("[#3|]: a|\"b|c\"|d", buffer.toString());
  }
}
