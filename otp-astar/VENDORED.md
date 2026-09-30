# Vendored: OpenTripPlanner `astar`

## 1. Upstream

- Repository: https://github.com/opentripplanner/OpenTripPlanner
- Commit: `61a3af67983e5776d8c0fced23839086bb543a59`
- Describe: `v2.9.0-2454-g61a3af6798`
- Module: `astar` (Maven artifact `astar`, part of `otp-root` `2.10.0-SNAPSHOT`)

## 2. Copy command

Run from the `app/` directory, with the OTP checkout at `../OpenTripPlanner`:

```bash
mkdir -p otp-astar/src/main otp-astar/src/test
cp -r ../OpenTripPlanner/astar/src/main/java otp-astar/src/main/java
cp -r ../OpenTripPlanner/astar/src/test/java otp-astar/src/test/java
cp -r ../OpenTripPlanner/astar/src/test-fixtures otp-astar/src/test-fixtures
```

24 `.java` files copied under `src/main/java`; 7 under `src/test/java`; 4
under `src/test-fixtures/java`.

## 3. Deleted files

None.

## 4. Edited files

All three edits are mechanical ports of Java 21/22 syntax the module used,
down to Java 17 (this project's `options.release`). No behavior change.

- `src/main/java/org/opentripplanner/astar/model/GraphPath.java`:
  - Line 54 (constructor): `states.addFirst(cur)` -> `states.add(0, cur)`.
    `List.addFirst()` is a JDK 21 `SequencedCollection` method; `states` is a
    public field typed `List<State>` (backed by a `LinkedList` instance at
    construction time), so insertion at index 0 keeps the field's public
    type unchanged rather than narrowing it to `LinkedList`.
  - Line 58 (constructor): `edges.addFirst(cur.getBackEdge())` ->
    `edges.add(0, cur.getBackEdge())`. Same reason as above; `edges` is
    `List<Edge>`.
  - Line 72 (`getStartTime`): `states.getFirst()` -> `states.get(0)`.
    `List.getFirst()` is the same JDK 21 `SequencedCollection` method.
  - Line 79 (`getEndTime`): `states.getLast()` ->
    `states.get(states.size() - 1)`. Same reason as above.
  - Line 87 (`getDuration`): `states.getLast()` ->
    `states.get(states.size() - 1)`. Same reason as above.
  - Line 91 (`getWeight`): `states.getLast()` ->
    `states.get(states.size() - 1)`. Same reason as above.
- `src/test/java/org/opentripplanner/astar/AStarTest.java`:
  - Lines 26, 49, 74: `states.getFirst()` -> `states.get(0)` (three call
    sites, one per test method: `simple`, `twoOptions`,
    `moreEdgesButLowerCost`). Same `SequencedCollection` reason as above;
    `states` here is `path.states`, the same `List<State>` field from
    `GraphPath`.
- `src/test/java/org/opentripplanner/astar/model/ShortestPathTreeTest.java`:
  - Line 22: `private static final DominanceFunction<TestState> NONE =
    (_, _) -> false;` -> parameters renamed to `(a, b) -> false`. Java 22
    unnamed lambda parameters (`_`) are not legal under `--release 17`
    (`_` is a reserved keyword since Java 9 and cannot be used as an
    ordinary identifier). `a`/`b` were chosen to mirror the sibling field
    `BY_WEIGHT` immediately above, which already names its two lambda
    parameters `a`/`b`.

### Build-file note (not a source edit)

`app/otp-astar/build.gradle.kts` declares
`implementation("com.google.code.findbugs:jsr305:3.0.2")` directly, even
though `app/otp-utils/build.gradle.kts` also declares it. Upstream, `astar`'s
`pom.xml` does not redeclare `jsr305` (only `utils`, `slf4j-api`, and test
deps) because Maven's default `compile` scope is transitive: any module
depending on `utils` automatically gets `jsr305` on its compile classpath.
Gradle's `implementation` configuration is deliberately **not** transitive
to consumers (only `api` is), so `:otp-astar`'s own use of
`javax.annotation.Nullable` (in `AStar.java` and `ShortestPathTree.java`)
needs `jsr305` declared directly in this module. `otp-utils` is unchanged
(stays `implementation`, not `api`); each module declares what it uses.

## 5. How to re-sync

1. Re-run the copy command in section 2 against a newer OTP checkout,
   replacing `src/main/java`, `src/test/java`, and `src/test-fixtures/java`
   wholesale.
2. Re-apply the edit list in section 4 to `GraphPath.java`, `AStarTest.java`,
   and `ShortestPathTreeTest.java` (or re-derive it: compile with
   `./gradlew :otp-astar:compileJava` and `:otp-astar:compileTestJava`, and
   fix whatever fails to compile under `--release 17`, keeping surrounding
   logic identical). Note: a syntax-level error (such as the unnamed-lambda-
   parameter error) in one test file can suppress semantic-error reporting
   for other test files compiled in the same batch; if a compile run shows
   fewer errors than expected, fix the reported error(s) first, recompile,
   and check again before concluding no further errors exist.
3. Run `./gradlew :otp-astar:test` and confirm `BUILD SUCCESSFUL` with no
   failing OTP tests. Do not add, delete, or disable tests to make this pass.

Note: `app/otp-astar/build.gradle.kts` sets `options.encoding = "UTF-8"` on
every `JavaCompile` task, matching `app/otp-utils/build.gradle.kts`, because
the upstream sources are UTF-8; on Windows, `javac`'s default platform
charset (`windows-1252`) cannot read them otherwise. This is a build-file
setting, not a source edit, so it is not listed in section 4.
