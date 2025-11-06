package org.toonjava;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToonStringerTest {

  @Test
  void valueWritesNormalizedContentAndResetClearsBuffer() {
    Map<String, Object> object = new LinkedHashMap<>();
    object.put("id", 1);
    object.put("name", "Ada");

    ToonStringer stringer = new ToonStringer();
    stringer.value(object);

    assertEquals("""
        id: 1
        name: Ada""", stringer.toString());

    stringer.reset().value(List.of("x", "y"));
    assertEquals("[2]: x,y", stringer.toString());
  }

  @Test
  void objectAndArrayShortcutMethodsDelegateToWriter() {
    ToonObject object = ToonEncoder.encodeObject(Map.of("msg", "hi"));
    ToonArray array = ToonEncoder.encodeArray(List.of(1, 2));

    ToonStringer stringer = new ToonStringer();
    stringer.object(object);
    assertEquals("msg: hi", stringer.toString());

    stringer.reset().array(array);
    assertEquals("[2]: 1,2", stringer.toString());
  }

  @Test
  void staticEncodeRespectsOptions() {
    ToonWriterOptions options =
        ToonWriterOptions.defaults().withDelimiter("|").withLengthMarker("#");
    String encoded = ToonStringer.encode(List.of("a", "b|c"), options);

    assertEquals("[#2|]: a|\"b|c\"", encoded);
  }
}
