# Vendored: OpenTripPlanner `raptor`

## 1. Upstream

- Repository: https://github.com/opentripplanner/OpenTripPlanner
- Commit: `61a3af67983e5776d8c0fced23839086bb543a59`
- Describe: `v2.9.0-2454-g61a3af6798`
- Module: `raptor` (Maven artifact `raptor`, part of `otp-root` `2.10.0-SNAPSHOT`)

## 2. Copy command

Run from the `app/` directory, with the OTP checkout at `../OpenTripPlanner`:

```bash
mkdir -p otp-raptor/src/main otp-raptor/src/test
cp -r ../OpenTripPlanner/raptor/src/main/java otp-raptor/src/main/java
cp -r ../OpenTripPlanner/raptor/src/test/java otp-raptor/src/test/java
cp -r ../OpenTripPlanner/raptor/src/test-fixtures otp-raptor/src/test-fixtures
```

224 `.java` files copied under `src/main/java`; 161 under `src/test/java`
(before deletions, see section 3); 4 under `src/test-fixtures/java`.

## 3. Deleted files

- `src/test/java/org/opentripplanner/raptor/RaptorArchitectureTest.java` —
  this is OTP's ArchUnit-based architecture/layering test. `archunit` is
  deliberately not one of this module's dependencies, so this test cannot
  compile against this vendored module.

- `src/test/java/org/opentripplanner/raptor/_support/arch/` (3 files:
  `ArchComponent.java`, `Module.java`, `Package.java`) — deleted as a
  necessary consequence of the deletion above. These three files exist
  solely to support `RaptorArchitectureTest.java`; a repo-wide check
  (`grep -rl "_support\.arch\|ArchComponent\b"` over `src/test/java`)
  confirmed no other file references them. Two of the three
  (`ArchComponent.java`, `Package.java`) directly `import
  com.tngtech.archunit....` and would fail to compile with "package
  com.tngtech.archunit does not exist" once `RaptorArchitectureTest.java`
  is gone and archunit is absent from the classpath — an error outside the
  authorized JDK-17-porting edit forms (getFirst/getLast/reversed, unnamed
  `_`, the one named pattern-matching switch), so it could not be fixed by
  a source edit under this task's rules; deletion was the only option.
  This also confirms no remaining file imports `com.tngtech.archunit`,
  which is only satisfiable by removing these three files along with the
  architecture test itself. The resulting test file count is **157**, not
  160, since these three support files exist solely for the deleted test
  and were not anticipated when the file count was first estimated. No
  other file, test, or behavior is affected (verified: `diff -rq` against
  upstream shows no other file missing, and `:otp-raptor:test` is BUILD
  SUCCESSFUL).

## 4. Edited files

All edits are mechanical ports of Java 21/22 syntax the module used, down
to Java 17 (this project's `options.release`), or unnamed-lambda-parameter
renames required because `_` became a reserved identifier in Java 9+/is a
preview-only pattern-matching binding in this JDK. No behavior change,
except where noted for the `switch` rewrite (behaviorally identical, with
an added explicit-failure branch, see below). One additional note applies
throughout: the `getFirst()`/`getLast()` rewrites (`list.get(0)` and
`list.get(list.size() - 1)`) change the exception thrown on an empty list
from `NoSuchElementException` to `IndexOutOfBoundsException`; every such
site here is guarded or is itself a precondition violation, so this is
unobservable, and the same choice was made in the `otp-utils` and
`otp-astar` modules.

- `src/main/java/org/opentripplanner/raptor/configure/RaptorConfig.java`:
  - Added imports `java.util.ArrayList`, `java.util.Collections`.
  - Line 57 (`createRangeRaptorWithStdWorker`): `context.segments().getFirst()`
    -> `context.segments().get(0)`.
  - Line 100 (`createRangeRaptorWithMcWorker`, via-search branch): `for
    (SearchContextViaSegments<T> ctxSegment : context.segments().reversed())`
    -> build `var reversedSegments = new ArrayList<>(context.segments());
    Collections.reverse(reversedSegments);` then iterate `for
    (SearchContextViaSegments<T> ctxSegment : reversedSegments)`. The loop
    body is unchanged. The loop links via-segments back to front (see the
    comment immediately above it, "we start with the last segment to be
    able to link the segments together"), so the iteration order must stay
    reversed; iterating a reversed copy preserves that behavior exactly.
    `List.reversed()` is a JDK 21
    `SequencedCollection` method, not available under `--release 17`.
  - Line 109 (same method, non-via-search branch): `context.segments().getFirst()`
    -> `context.segments().get(0)`.
  - Line 180 (`createRangeRaptor`): `ctx.segments().getFirst().accessPaths()`
    -> `ctx.segments().get(0).accessPaths()`.
- `src/main/java/org/opentripplanner/raptor/path/Path.java`:
  - Line 289 (`findEgressLeg`): `.reduce((_, b) -> b)` -> `.reduce((ignored,
    b) -> b)`. Unnamed lambda parameters (`_`) are a Java 21+ feature not
    available under `--release 17`, so the parameter was renamed.
- `src/main/java/org/opentripplanner/raptor/rangeraptor/multicriteria/McRangeRaptorWorkerState.java`:
  - Line 97: `lifeCycle.onSetupIteration(_ -> setupIteration())` ->
    `lifeCycle.onSetupIteration(ignored -> setupIteration())`. Same reason
    as above.
- `src/main/java/org/opentripplanner/raptor/rangeraptor/standard/besttimes/BestTimes.java`:
  - Lines 56-57: `lifeCycle.onSetupIteration(_ -> setupIteration())` and
    `lifeCycle.onPrepareForNextRound(_ -> prepareForNextRound())` ->
    parameters renamed to `ignored`. Same reason as above.
- `src/main/java/org/opentripplanner/raptor/rangeraptor/standard/stoparrivals/path/EgressArrivalToPathAdapter.java`:
  - Lines 57-58: `lifeCycle.onSetupIteration(_ -> setupIteration())` and
    `lifeCycle.onRoundComplete(_ -> roundComplete())` -> parameters renamed
    to `ignored`. Same reason as above.
- `src/main/java/org/opentripplanner/raptor/rangeraptor/multicriteria/ViaConnectionStopArrivalEventListener.java`:
  - Lines 124-137 (`notifyElementAccepted`): rewrote a pattern-matching
    `switch` over the sealed type `ViaConnection` into an `if`/`else if`
    chain using `instanceof` with binding patterns. Java pattern-matching
    `switch` (`case Type name -> ...` with type patterns) requires
    `--release 21`+ (or `--enable-preview` on 17); not available under
    `--release 17`. See section "Switch rewrite" below for the full
    before/after and the fall-through-safety decision.

  Also on this same file: line 125's `case RaptorPassThroughViaConnection _
  ->` used an unnamed pattern-match binding, which is folded into the
  `switch` rewrite above rather than listed as a separate `_`-rename (there
  is no longer a `case` or binding at that site after the rewrite).

- `src/main/java/org/opentripplanner/raptor/util/composite/CompositeUtil.java`:
  - Line 50 (`of`): `return list.getFirst();` -> `return list.get(0);`.
- `src/main/java/org/opentripplanner/raptor/rangeraptor/context/SearchContext.java`:
  - Line 219 (`createTimeBasedBoardingSupport`): `segments.getFirst()...`
    -> `segments.get(0)...`.
- `src/main/java/org/opentripplanner/raptor/rangeraptor/standard/configure/StdRangeRaptorConfig.java`:
  - Line 272 (`egressPaths`): `ctx.segments().getLast().egressPaths()` ->
    `ctx.segments().get(ctx.segments().size() - 1).egressPaths()`.
- `src/test/java/org/opentripplanner/raptor/rangeraptor/transit/AccessEgressFunctionsTest.java`:
  - Lines 196 and 201 (`groupByRoundTest`): both `_ -> true` lambda
    parameters -> `ignored -> true`. Same reason as the main-source `_`
    renames above.
- `src/test/java/org/opentripplanner/raptor/_data/transit/TestTransitData.java`:
  - Line 169: `return list.getFirst();` -> `return list.get(0);`.

### Switch rewrite: `ViaConnectionStopArrivalEventListener.java`

`ViaConnection` (`org.opentripplanner.raptor.api.request.via.ViaConnection`)
is `public abstract sealed class ViaConnection permits
RaptorPassThroughViaConnection, RaptorTransferViaConnection,
RaptorVisitStopViaConnection`. The original `switch` had **no `default`
branch** and relied on the compiler's exhaustiveness check over the sealed
type's three permitted subtypes.

Before:

```java
    for (ViaConnection connection : connections) {
      switch (connection) {
        case RaptorPassThroughViaConnection _ -> handlePassThroughViaConnection(arrival);
        case RaptorVisitStopViaConnection visitStop -> {
          continueFromSameStopArrival(arrival, visitStop);
        }
        case RaptorTransferViaConnection transfer -> {
          if (arrival.arrivedOnBoard()) {
            continueWithTransfer(arrival, transfer);
          }
          // Silently ignore arrive-on-foot + via-transfer. Two transfers are
          // not allowed after each other, and we can safely skip it here.
        }
      }
    }
```

After:

```java
    for (ViaConnection connection : connections) {
      if (connection instanceof RaptorPassThroughViaConnection) {
        handlePassThroughViaConnection(arrival);
      } else if (connection instanceof RaptorVisitStopViaConnection visitStop) {
        continueFromSameStopArrival(arrival, visitStop);
      } else if (connection instanceof RaptorTransferViaConnection transfer) {
        if (arrival.arrivedOnBoard()) {
          continueWithTransfer(arrival, transfer);
        }
        // Silently ignore arrive-on-foot + via-transfer. Two transfers are
        // not allowed after each other, and we can safely skip it here.
      } else {
        throw new IllegalStateException(
          "Unexpected ViaConnection type: " + connection.getClass()
        );
      }
    }
```

Each branch body is preserved verbatim (the unnamed `_` binding in the
first `case` becomes a plain `instanceof` type check with no binding
variable, since the original branch body never used it). Because the
sealed type has exactly three permitted subtypes and the original had no
`default`/relied on exhaustiveness, an explicit final `else` was added
that throws `IllegalStateException` naming the unexpected runtime type,
so silent fall-through for a fourth, currently-impossible subtype is not
possible. This is a defensive addition, not a behavior
change for any of the three real subtypes: `:otp-raptor:test` passes
(`BUILD SUCCESSFUL`), confirming all exercised branches behave as before.

### Build-file note (not a source edit)

`app/otp-raptor/build.gradle.kts` declares
`implementation("net.sf.trove4j:trove4j:3.0.3")`,
`implementation("org.slf4j:slf4j-api:2.0.19")`, and
`implementation("com.google.code.findbugs:jsr305:3.0.2")` directly,
mirroring upstream `raptor/pom.xml`'s three declared 3rd-party
dependencies (`jsr305`, `trove4j`, `slf4j-api`) plus its
one project dependency (`utils`, vendored as `:otp-utils`). `archunit` is
upstream's fourth dependency (test scope only) but is intentionally **not**
declared here — see section 3 for why, and its consequence (deleting the
architecture test and its dedicated support files).

Note: `app/otp-raptor/build.gradle.kts` sets `options.encoding = "UTF-8"`
on every `JavaCompile` task, matching `app/otp-utils/build.gradle.kts` and
`app/otp-astar/build.gradle.kts`, because the upstream sources are UTF-8; on
Windows, `javac`'s default platform charset (`windows-1252`) cannot read
them otherwise. This is a build-file setting, not a source edit.

No `maxHeapSize` override was needed: `:otp-raptor:test` completed with the
default test-task heap and no `OutOfMemoryError`.

## 5. How to re-sync

1. Re-run the copy command in section 2 against a newer OTP checkout,
   replacing `src/main/java`, `src/test/java`, and `src/test-fixtures/java`
   wholesale.
2. Delete `src/test/java/org/opentripplanner/raptor/RaptorArchitectureTest.java`
   and `src/test/java/org/opentripplanner/raptor/_support/arch/` (see
   section 3 for why both go together). Re-check with `grep -rl
   "com.tngtech.archunit" src/test/java` that nothing remains.
3. Re-apply the edit list in section 4 (or re-derive it: compile with
   `./gradlew :otp-raptor:compileJava` and `:otp-raptor:compileTestJava`,
   and fix whatever fails to compile under `--release 17`, keeping
   surrounding logic identical). Note: a syntax-level error (such as the
   unnamed-lambda-parameter error, or the pattern-matching-switch error) in
   one file can suppress semantic-error reporting (such as `getFirst()`)
   for other files compiled in the same batch; if a compile run shows fewer
   errors than expected, fix the reported error(s) first, recompile, and
   check again before concluding no further errors exist.
4. Re-check the sealed-type `switch` rewrite in
   `ViaConnectionStopArrivalEventListener.java`: confirm `ViaConnection`'s
   permitted subtypes haven't changed, and if they have, add/remove
   `instanceof` branches accordingly, keeping the final `else` that throws
   `IllegalStateException`.
5. Run `./gradlew :otp-raptor:test` and confirm `BUILD SUCCESSFUL` with no
   failing OTP tests. Do not add, delete, or disable tests to make this
   pass. If it fails with `OutOfMemoryError`, add `maxHeapSize = "1g"` to
   this module's `test` task in `build.gradle.kts` and rerun.
