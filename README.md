# TOON-JAVA Library

Java implementation of the [Token-Oriented Object Notation (TOON)](SPEC.md) specification, inspired by the minimalist `org.json` API. The goal is to provide dynamic containers (`ToonObject`, `ToonArray`) and lightweight parsing/serialization utilities that remain auditable and free of mandatory runtime dependencies.

> 📚 For a deeper architectural overview, check out `PROJECT_OVERVIEW.md` and `ARCHITECTURE.md`.

## Features

- Decoder (`ToonDecoder`) built on `ToonTokener`, turning TOON text into dynamic Java structures.
- API familiar to `org.json` developers: typed accessors, null handling through `ToonNull`, and unchecked exceptions via `ToonException`.
- Optional conversion to Jackson types (`JsonNode`) without hard-coupling the library to that dependency.
- Lean toolchain: only requires JDK 17+ and Gradle; the parser is generated via ANTLR at build time.
- “No global configuration” philosophy: direct methods for parsing and rendering TOON.

## Requirements

- JDK 17 or newer (configured through Gradle Toolchains).
- Access to Maven Central (to resolve ANTLR and test dependencies).

## Getting Started

```bash
./gradlew build
```

This command generates the ANTLR lexer/parser, compiles the sources, and runs the test suite (`JUnit 5`). To apply code formatting manually:

```bash
./gradlew spotlessApply
```

If you only need to verify compilation:

```bash
./gradlew assemble
```

## Quick Usage

```java
import org.toonjava.ToonDecoder;
import org.toonjava.ToonObject;

String toonText = """
name: "Toon Java"
version: 1.0
features:
  - decoder
  - tokener
  - jackson-bridge
""";

ToonObject root = ToonDecoder.decodeObject(toonText);
String name = root.getString("name");
int version = root.getInt("version");
```

To produce structures compatible with common JSON libraries:

```java
Map<String, Object> map = ToonDecoder.decodeToMap(toonText);
```

If `jackson-databind` is on the classpath, you can also obtain a `JsonNode`:

```java
Object jsonNode = ToonDecoder.toJsonNode(root);
```

## Project Layout

- Main source code: `src/main/java/org/toonjava/`
- Grammars and generated sources: `src/main/antlr/` and `build/generated-src/`
- Tests: `src/test/java/`
- Supplementary documentation: `SPEC.md`, `ARCHITECTURE.md`, `PROJECT_OVERVIEW.md`, `CONTRIBUTING.md`

## Roadmap

Planned tasks and their status live in `TASKS.md`. Upcoming milestones include:

- Encoder (`ToonEncoder`, `ToonWriter`, `ToonStringer`) for exporting Java structures as TOON text.
- Configurable options (`ToonOptions`) covering delimiters, indentation, and strict mode.
- JSON ↔ TOON integration fixtures.

## Contributing

1. Read `CONTRIBUTING.md` and `SPEC.md` before proposing changes.
2. Run `./gradlew check` to validate the project.
3. Keep the API minimalist and free from new runtime dependencies unless justified in the issue.

Questions about the specification can be discussed via issues or PRs following the contribution guide.

## License

This project is distributed under the MIT license; see `LICENSE`.
