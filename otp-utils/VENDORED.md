# Vendored: OpenTripPlanner `utils`

## 1. Upstream

- Repository: https://github.com/opentripplanner/OpenTripPlanner
- Commit: `61a3af67983e5776d8c0fced23839086bb543a59`
- Describe: `v2.9.0-2454-g61a3af6798`
- Module: `utils` (Maven artifact `utils`, part of `otp-root` `2.10.0-SNAPSHOT`)

## 2. Copy command

Run from the `app/` directory, with the OTP checkout at `../OpenTripPlanner`:

```bash
mkdir -p otp-utils/src/main otp-utils/src/test
cp -r ../OpenTripPlanner/utils/src/main/java otp-utils/src/main/java
cp -r ../OpenTripPlanner/utils/src/test/java otp-utils/src/test/java
```

52 `.java` files copied under `src/main/java`; 46 under `src/test/java`.

## 3. Deleted files

None.

## 4. Edited files

Both edits are mechanical ports of Java 21/22 syntax the module used, down to
Java 17 (this project's `options.release`). No behavior change.

- `src/main/java/org/opentripplanner/utils/collection/ListUtils.java`:
  - Line 19 (method `first`): `list.getFirst()` -> `list.get(0)`. `List.getFirst()`
    is a JDK 21 `SequencedCollection` method; the surrounding null/empty
    check is unchanged.
  - Line 27 (method `last`): `list.getLast()` -> `list.get(list.size() - 1)`.
    Same reason as above.
  - Line 46 (method `countIterable`): unnamed variable `_` (Java 22) renamed
    to `ignored`. `for (var _ : iterable)` uses the unnamed-variable pattern,
    which requires a newer language level than 17 (`_` is a reserved keyword
    since Java 9 and cannot be used as an ordinary identifier under
    `--release 17`).
  - Line 152 (method `partitionIntoOverlappingPairs`): `input.getFirst()` ->
    `input.get(0)`, `input.getLast()` -> `input.get(input.size() - 1)`. Same
    `SequencedCollection` reason as above.
  - Line 184 (method `partitionIntoSplit`, private): `list.getFirst()` ->
    `list.get(0)`. Same reason as above.
- `src/main/java/org/opentripplanner/utils/collection/SetUtils.java`:
  - Line 29 (method `intersection`): `list.getFirst()` -> `list.get(0)`.
    Same `SequencedCollection` reason as above.
  - Line 34 (method `intersection`): `list.getFirst()` -> `list.get(0)`
    (inside `new HashSet<>(list.getFirst())`). Same reason as above.

### Android runtime compatibility

- `src/main/java/org/opentripplanner/utils/text/TextVariablesSubstitution.java`:
  - Line 18: `}` escaped in the regex literal (`Pattern.compile("\\$\\{([.\\w]+)}")`
    -> `Pattern.compile("\\$\\{([.\\w]+)\\}")`) because Android's ICU-backed
    `java.util.regex` rejects an unescaped closing brace that OpenJDK
    accepts; found by the on-device ToyNetwork test.

## 5. How to re-sync

1. Re-run the copy command in section 2 against a newer OTP checkout,
   replacing `src/main/java` and `src/test/java` wholesale.
2. Re-apply the edit list in section 4 to `ListUtils.java` and `SetUtils.java`
   (or re-derive it: compile with `./gradlew :otp-utils:compileJava` and
   `:otp-utils:compileTestJava`, and fix whatever fails to compile under
   `--release 17`, keeping surrounding logic identical).
3. Run `./gradlew :otp-utils:test` and confirm `BUILD SUCCESSFUL` with no
   failing OTP tests. Do not add, delete, or disable tests to make this pass.

Note: `app/otp-utils/build.gradle.kts` sets `options.encoding = "UTF-8"` on
every `JavaCompile` task, because the upstream sources are UTF-8 and several
files contain non-ASCII characters; on Windows, `javac`'s default platform
charset (`windows-1252`) cannot read them otherwise. This is a build-file
setting, not a source edit, so it is not listed in section 4.
