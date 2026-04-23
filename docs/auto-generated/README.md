# Auto-generated UML

This diagram was generated from source using
[vincentmacri/JavaToUML](https://github.com/vincentmacri/JavaToUML),
adapted to modern JavaParser (3.25.10, `LanguageLevel.JAVA_17`) so all
92 source files under `src/` parse cleanly.

## Files

- `auto-class-diagram.puml` — PlantUML output listing every class with
  its fields, constructors, and methods (visibility encoded with
  `+`/`-`/`#`; `{static}` markers applied).
- `auto-class-diagram.png` — rendered PNG.
- `files.txt` — the list of `.java` files fed to the converter.

## Regenerate

```bash
# Prereqs: plantuml.jar at /tmp/puml/plantuml.jar,
# javaparser-core-3.25.10.jar at /tmp/javauml-lib/javaparser-core.jar,
# JavaToUML compiled to /tmp/JavaToUML/out (patched fork)

cd docs/auto-generated
find "$(git rev-parse --show-toplevel)/src" -name '*.java' > files.txt
java -cp "/tmp/JavaToUML/out:/tmp/javauml-lib/javaparser-core.jar" \
     ca.vincemacri.javauml.Converter $(cat files.txt | tr '\n' ' ')
mv UMLOutput.txt auto-class-diagram.puml
java -jar /tmp/puml/plantuml.jar -tpng auto-class-diagram.puml
```

## Notes

JavaToUML only emits class definitions (fields / constructors /
methods); it does not infer inheritance, composition, or association
edges. For the hand-curated diagrams that include relationships and
design-pattern annotations, see `../good-design-class-diagram.puml`
and `../bad-design-class-diagram.puml`.
