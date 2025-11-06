package org.toonjava;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ToonEncoderFixtureTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FIXTURE_BASE =
      Path.of(".", "src", "test", "resources", "fixtures", "encode");

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtureCases")
  void encodeFixtures(
      String displayName, Object input, String expected, ToonWriterOptions options) {
    String actual = ToonStringer.encode(input, options);
    assertEquals(expected, actual, () -> describeMismatch(displayName, expected, actual));
  }

  private static Stream<Arguments> fixtureCases() throws IOException {
    if (!Files.isDirectory(FIXTURE_BASE)) {
      throw new IllegalStateException("No se encontró el directorio de fixtures: " + FIXTURE_BASE);
    }

    return Files.list(FIXTURE_BASE)
        .filter(Files::isRegularFile)
        .sorted(Comparator.comparing(Path::getFileName))
        .flatMap(ToonEncoderFixtureTest::readFixture);
  }

  private static Stream<Arguments> readFixture(Path path) {
    try {
      JsonNode root = MAPPER.readTree(Files.readString(path));
      JsonNode tests = root.get("tests");
      if (tests == null || !tests.isArray()) {
        throw new IllegalStateException("Fixture sin array de tests: " + path);
      }
      return StreamSupport.stream(tests.spliterator(), false).map(test -> toArguments(path, test));
    } catch (IOException ex) {
      throw new IllegalStateException("No se pudo leer el fixture " + path, ex);
    }
  }

  private static Arguments toArguments(Path path, JsonNode testNode) {
    String name = testNode.path("name").asText("(sin nombre)");
    String displayName = path.getFileName() + " :: " + name;
    Object input = toJavaValue(testNode.get("input"));
    String expected = testNode.path("expected").asText();
    ToonWriterOptions options = parseOptions(testNode.get("options"));
    return Arguments.of(displayName, input, expected, options);
  }

  private static Object toJavaValue(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    try {
      return MAPPER.treeToValue(node, Object.class);
    } catch (IOException ex) {
      throw new IllegalStateException("No se pudo convertir el nodo JSON a valor Java", ex);
    }
  }

  private static ToonWriterOptions parseOptions(JsonNode node) {
    ToonWriterOptions options = ToonWriterOptions.defaults();
    if (node == null || node.isNull()) {
      return options;
    }
    if (node.has("indent")) {
      int indent = node.path("indent").asInt(options.indent());
      options = options.withIndent(indent);
    }
    if (node.has("delimiter")) {
      String delimiter = node.path("delimiter").asText(options.delimiter());
      options = options.withDelimiter(delimiter);
    }
    if (node.has("lengthMarker")) {
      String lengthMarker = node.path("lengthMarker").asText(options.lengthMarker());
      options = options.withLengthMarker(lengthMarker);
    }
    return options;
  }

  private static String describeMismatch(String displayName, String expected, String actual) {
    return displayName + " -> esperado:\n" + expected + "\nobtenido:\n" + actual;
  }

  private ToonEncoderFixtureTest() {}
}
