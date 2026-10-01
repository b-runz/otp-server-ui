# otp-server-ui Backend JSON API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a complete, independently-testable Kotlin/Ktor backend that serves
Park & Ride, Bring Bike, and Drop-me-off trip planning as a plain JSON API,
loading a real OTP graph once at startup and holding it in memory for the
process's lifetime.

**Architecture:** vendor bikebus's existing OTP runtime modules and routing
logic verbatim (they already support eager whole-network loading via an
existing fallback path — see Task 6), add a small newly-vendored
graph-*loading* slice (not graph-building), and wrap it all in a thin Ktor
JSON API. No frontend work is in this plan — see the spec's own note that
the frontend is a natural follow-up plan once this backend is real and
testable.

**Tech Stack:** Kotlin, Gradle, Ktor, JUnit 5, Google Truth (matching
bikebus's own test-assertion style), Kryo (graph deserialization).

**Spec:** `docs/superpowers/specs/2026-09-30-otp-server-ui-design.md`

## Global Constraints

- No heap-constrained or latency benchmarks as recurring task-level unit
  tests — functionality/golden-value correctness only (spec's own
  "Testing" section; matches the convention `bikebus`'s own
  `random-access-routing-data` plan used).
- Every vendored/ported file's origin is bikebus commit `39655a6`
  (`embedded-otp-real-data` branch) unless a task says otherwise — always
  the current tip of that branch at plan-writing time, not some other
  revision.
- The vendored OTP source commit for anything cut fresh from upstream
  (Task 3's `SerializedGraphObject`/`kryosupport`) is `61a3af6798`, from the
  local checkout at `C:\Users\bru\spare-source\bikebus\OpenTripPlanner`
  (verified: exact HEAD of that checkout, clean tree).
- Never vendor `graph_builder.*`/`gtfs.*`/`osm.*` from upstream OTP (spec's
  own Non-Goals) — graph *building* stays entirely external to this repo.
- Money/duration/coordinate types follow whatever the copied bikebus code
  already uses (`java.time.Duration`/`Instant`, `WgsCoordinate`) — no new
  conventions invented for ported code.

## Review Focus

- **A destination whose nearest transit stop is a long walk away (Park &
  Ride):** must return a route flagged as a long walk, not "no route" —
  already fixed in the source bikebus code (`ParkAndRideFinder`'s two-tier
  egress); Task 8's test proves the ported copy still does this.
- **An origin/destination pair with an empty access or egress street
  reach (Bring Bike):** must return the direct-route itineraries it still
  has, not crash — already fixed in the source bikebus code
  (`planEmbeddedItineraries`'s empty-reach guard); Task 9's test proves the
  ported copy still does this.
- **A destination genuinely outside the loaded graph's coverage:** the
  `/search` endpoint must return the typed `no_coverage` error shape, not a
  generic 500 or an unhandled exception — Task 13's own test.
- **Malformed or missing `/search` request fields** (e.g. an unparseable
  datetime, an unknown mode string): a typed 400-shaped JSON error, not a
  stack trace leaking to the client — Task 13's own test (added
  specifically for this, see its Step 2's third test case).
- **`/geocode` with no results** (a nonsense query string): an empty
  `candidates: []` array, not a null/missing field the frontend would have
  to special-case — Task 15's own test.

---

### Task 1: Gradle/Kotlin project skeleton with a health-check endpoint

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle.properties`
- Create: `backend/build.gradle.kts`
- Create: `backend/src/main/kotlin/one/otpserverui/Main.kt`
- Test: `backend/src/test/kotlin/one/otpserverui/HealthCheckTest.kt`

**Interfaces:**
- Produces: `fun main()` (Ktor server entry point, listens on port `8080`),
  a `GET /health` route returning `200 OK` with body `ok`.

- [ ] **Step 1: Create the root Gradle files**

`settings.gradle.kts`:
```kotlin
rootProject.name = "otp-server-ui"
include(":backend")
```

`gradle.properties`:
```properties
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2g
```

`build.gradle.kts` (root, empty — per-module config lives in each module):
```kotlin
// Intentionally empty: all real configuration lives in backend/build.gradle.kts.
```

- [ ] **Step 2: Create `backend/build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-netty:3.0.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("org.slf4j:slf4j-simple:2.0.16")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("io.ktor:ktor-server-test-host:3.0.3")
}

application {
    mainClass.set("one.otpserverui.MainKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Write the failing test**

```kotlin
package one.otpserverui

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class HealthCheckTest {
    @Test
    fun `GET health returns 200 ok`() = runTest {
        testApplication {
            application { module() }
            val response = client.get("/health")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).isEqualTo("ok")
        }
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "*.HealthCheckTest"` (from the repo
root)
Expected: FAIL — `module` is unresolved (doesn't exist yet).

- [ ] **Step 5: Write `Main.kt`**

```kotlin
package one.otpserverui

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json() }
    routing {
        get("/health") { call.respondText("ok") }
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "*.HealthCheckTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties backend/
git commit -m "Add Ktor project skeleton with a health-check endpoint"
```

---

### Task 2: Vendor bikebus's existing OTP JVM modules

**Files:**
- Create: `otp-utils/` (copied from `bikebus/app/otp-utils/`)
- Create: `otp-astar/` (copied from `bikebus/app/otp-astar/`)
- Create: `otp-domain-core/` (copied from `bikebus/app/otp-domain-core/`)
- Create: `otp-street/` (copied from `bikebus/app/otp-street/`)
- Create: `otp-routing/` (copied from `bikebus/app/otp-routing/`)
- Create: `otp-raptor/` (copied from `bikebus/app/otp-raptor/`)
- Modify: `settings.gradle.kts` (add the six new modules)

**Interfaces:**
- Produces: every class these six modules already expose in bikebus,
  unchanged (`org.opentripplanner.street.graph.Graph`,
  `org.opentripplanner.transit.service.TransitRepository`,
  `org.opentripplanner.transfer.regular.TransferRepository`,
  `org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.RaptorTransitDataMapper`,
  everything `RoutingEngine`/`BuildingBlocks`/`ParkAndRideFinder`/
  `HubRouting` need in Tasks 6-10).

- [ ] **Step 1: Copy the six module directories verbatim**

From the repo root:
```bash
for m in otp-utils otp-astar otp-domain-core otp-street otp-routing otp-raptor; do
  cp -r "/c/Users/bru/spare-source/bikebus/app/$m" "./$m"
done
```

Each copied module keeps its own `build.gradle.kts`, `VENDORED.md`, and
(where present) `STUBS.md` exactly as bikebus has them — these files
document real, upstream-verified vendoring decisions that still apply here
unchanged (this project vendors the *same* commit, `61a3af6798`).

- [ ] **Step 2: Wire the six modules into `settings.gradle.kts`**

```kotlin
rootProject.name = "otp-server-ui"
include(":backend")
include(":otp-utils")
include(":otp-astar")
include(":otp-domain-core")
include(":otp-street")
include(":otp-routing")
include(":otp-raptor")
```

- [ ] **Step 3: Run each vendored module's own existing test suite**

Run: `./gradlew :otp-utils:test :otp-astar:test :otp-domain-core:test :otp-street:test :otp-routing:test :otp-raptor:test`
Expected: BUILD SUCCESSFUL, with the exact same test counts bikebus's own
`app/` reports for these modules today (confirm via
`./gradlew :otp-utils:test :otp-astar:test :otp-domain-core:test :otp-street:test :otp-routing:test :otp-raptor:test`
run inside `bikebus/app` for a side-by-side count — they must match
exactly, since nothing in these six modules was touched).

- [ ] **Step 4: Commit**

```bash
git add otp-utils/ otp-astar/ otp-domain-core/ otp-street/ otp-routing/ otp-raptor/ settings.gradle.kts
git commit -m "Vendor bikebus's existing OTP runtime modules verbatim"
```

---

### Task 3: Vendor graph-loading code (SerializedGraphObject + kryosupport)

**Files:**
- Create: `otp-routing/src/main/java/org/opentripplanner/routing/graph/SerializedGraphObject.java`
  (copied from `C:\Users\bru\spare-source\bikebus\OpenTripPlanner\application\src\main\java\org\opentripplanner\routing\graph\SerializedGraphObject.java`)
- Create: `otp-routing/src/main/java/org/opentripplanner/routing/graph/kryosupport/*.java`
  (all 7 files, copied verbatim from the same OpenTripPlanner checkout's
  `application/src/main/java/org/opentripplanner/routing/graph/kryosupport/`)
- Create (stubs, real no-op implementations, not copies): 
  `otp-routing/src/main/java/org/opentripplanner/service/worldenvelope/WorldEnvelopeRepository.java`,
  `otp-routing/src/main/java/org/opentripplanner/service/vehicleparking/VehicleParkingRepository.java`,
  `otp-routing/src/main/java/org/opentripplanner/service/osminfo/OsmInfoGraphBuildRepository.java`,
  `otp-routing/src/main/java/org/opentripplanner/ext/stopconsolidation/StopConsolidationRepository.java`,
  `otp-routing/src/main/java/org/opentripplanner/ext/emission/EmissionRepository.java`,
  `otp-routing/src/main/java/org/opentripplanner/ext/empiricaldelay/EmpiricalDelayRepository.java`,
  `otp-routing/src/main/java/org/opentripplanner/routing/fares/FareServiceFactory.java`
- Modify: `otp-routing/build.gradle.kts` (add the Kryo dependency)
- Modify: `otp-routing/VENDORED.md` (append a new section documenting this
  addition — see Step 5)
- Test: `otp-routing/src/test/java/org/opentripplanner/routing/graph/SerializedGraphObjectRoundTripTest.java`

**Interfaces:**
- Consumes: `org.opentripplanner.street.graph.Graph`,
  `org.opentripplanner.transit.service.TransitRepository`,
  `org.opentripplanner.transfer.regular.TransferRepository` (all already
  present from Task 2).
- Produces: `SerializedGraphObject.load(InputStream): SerializedGraphObject`
  (static factory) and its `.graph`, `.transitLayer` (real
  `TransitRepository`)-equivalent fields — the exact field names and
  accessors are whatever the real copied `SerializedGraphObject.java`
  declares; read that file once copied (Step 1) and use its real API in
  Task 5, don't guess a different shape.

- [ ] **Step 1: Copy the real source files**

```bash
mkdir -p otp-routing/src/main/java/org/opentripplanner/routing/graph/kryosupport
OTP="C:/Users/bru/spare-source/bikebus/OpenTripPlanner/application/src/main/java/org/opentripplanner/routing/graph"
cp "$OTP/SerializedGraphObject.java" otp-routing/src/main/java/org/opentripplanner/routing/graph/
cp "$OTP/kryosupport/"*.java otp-routing/src/main/java/org/opentripplanner/routing/graph/kryosupport/
```

- [ ] **Step 2: Read the copied `SerializedGraphObject.java` and list every
  unresolved import**

Run: `./gradlew :otp-routing:compileJava 2>&1 | grep "cannot find symbol\|package .* does not exist"`
Expected: a list of missing types — cross-reference against the "Files"
section above (`WorldEnvelopeRepository`, `VehicleParkingRepository`,
`OsmInfoGraphBuildRepository`, `StopConsolidationRepository`,
`EmissionRepository`, `EmpiricalDelayRepository`, `FareServiceFactory`, and
possibly `graph_builder.issue.api.DataImportIssueSummary` — if that last
one appears, copy it too, verbatim, from the same OpenTripPlanner checkout;
it's a small data-holder class in the `issue.api` package, not part of the
excluded `graph_builder` ingestion modules proper).

- [ ] **Step 3: Write a minimal, real (not throwing) stub for each missing
  repository type**

For each of `WorldEnvelopeRepository`, `VehicleParkingRepository`,
`OsmInfoGraphBuildRepository`, `StopConsolidationRepository`,
`EmissionRepository`, `EmpiricalDelayRepository`, `FareServiceFactory`:
read the real interface/class definition from the OpenTripPlanner checkout
first (`grep -rl "interface WorldEnvelopeRepository" "$OTP/../../../.."`
-style search under `application/src/main/java`), then write the smallest
real Java class/interface in this repo satisfying `SerializedGraphObject`'s
own usage of it — a `Serializable` marker interface with only the methods
`SerializedGraphObject` itself calls, each returning an empty/default value
(e.g. `Optional.empty()`, an empty list, `null` where the real type
permits it). Do not copy these classes' real bodies from upstream — they
back features (fares, emissions, empirical delay, stop consolidation,
vehicle parking, world envelope, OSM build info) this project doesn't use;
a real implementation would need Task 2's excluded `graph_builder`/`ext.*`
slice.

- [ ] **Step 4: Add the Kryo dependency**

In `otp-routing/build.gradle.kts`, add to the `dependencies` block:
```kotlin
implementation("com.esotericsoftware:kryo:5.6.2")
```

- [ ] **Step 5: Document this addition in `otp-routing/VENDORED.md`**

Append a new top-level section:
```markdown
## otp-server-ui addition: graph loading

`routing/graph/SerializedGraphObject.java` and `routing/graph/kryosupport/*`
were added on top of the original vendoring above, copied verbatim from the
same upstream commit (`61a3af6798`) this module's other files were cut
from. `WorldEnvelopeRepository`/`VehicleParkingRepository`/
`OsmInfoGraphBuildRepository`/`StopConsolidationRepository`/
`EmissionRepository`/`EmpiricalDelayRepository`/`FareServiceFactory` are
newly-written stubs, not copies — see this repo's own
`docs/superpowers/specs/2026-09-30-otp-server-ui-design.md` for why.
```

- [ ] **Step 6: Write the failing test**

This test needs a real serialized graph file. Use the smallest possible
real fixture: build one with real, standalone OTP from the OpenTripPlanner
checkout, over a tiny synthetic GTFS+OSM pair (details of *how* to invoke
standalone OTP's build step are Task 4's job, not this task's — for this
task, write the test against whatever tiny fixture file Task 4 will place
at `otp-routing/src/test/resources/tiny-fixture-graph.obj`, and mark this
test `@Disabled("enabled by Task 4 once the fixture file exists")` for now):

```java
package org.opentripplanner.routing.graph;

import static com.google.common.truth.Truth.assertThat;

import java.io.FileInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class SerializedGraphObjectRoundTripTest {

  @Test
  @Disabled("enabled by Task 4 once tiny-fixture-graph.obj exists")
  void loadsARealSerializedGraph() throws Exception {
    try (InputStream in = new FileInputStream("src/test/resources/tiny-fixture-graph.obj")) {
      SerializedGraphObject loaded = SerializedGraphObject.load(in);
      assertThat(loaded.graph).isNotNull();
    }
  }
}
```

- [ ] **Step 7: Run test to verify it compiles and is skipped**

Run: `./gradlew :otp-routing:test --tests "*.SerializedGraphObjectRoundTripTest"`
Expected: PASS with 1 test skipped (disabled), 0 failures — this confirms
`SerializedGraphObject`/the stubs compile cleanly, which is this task's
real deliverable; the disabled test becomes real in Task 4.

- [ ] **Step 8: Commit**

```bash
git add otp-routing/
git commit -m "Vendor OTP's graph-loading code (SerializedGraphObject + kryosupport)"
```

---

### Task 4: Build a real test-fixture serialized graph (throwaway builder)

**Files:**
- Create: `.tools/graph-builder/` (a small, standalone, throwaway Gradle
  project — **never committed to this repo**, see `.gitignore` note
  below; its only job is producing a real, version-compatible
  `graph.obj`, then it can be discarded)
- Create: `otp-routing/src/test/resources/tiny-fixture-graph.obj` (binary,
  the throwaway builder's real output, copied in — not hand-written)
- Modify: `otp-routing/src/test/java/org/opentripplanner/routing/graph/SerializedGraphObjectRoundTripTest.java`
  (remove the `@Disabled` from Task 3)
- Modify: `.gitignore` (add `.tools/` if not already covered — it already
  is, from Task 1/3's own additions; confirm rather than duplicate)
- Create: `docs/graph-build.md` (documents what the throwaway builder does
  and how to re-run it, so this is reproducible without needing to
  re-derive any of this task's own investigation)

**Interfaces:**
- Produces: `otp-routing/src/test/resources/tiny-fixture-graph.obj`, a
  real serialized graph — used by this task's own test, and by every
  routing-logic test from Task 6 onward.

**Why a throwaway builder, not the real `application`/`otp-shaded` Maven
build (corrects an earlier draft of this task):** real OTP's own `pom.xml`
unconditionally depends on `google-cloud-storage`/`google-cloud-pubsub`
(for an optional GCS `DataSource` backend and an optional SIRI/Pubsub
real-time updater — neither used by this project), which pulls in
`com.google.cloud:libraries-bom` — a version-alignment BOM covering
literally every GCP service (400+ sub-BOMs). Resolving its full POM graph
took over 30 minutes even through the JetBrains mirror (confirmed: this
plan's own pre-dispatch research got roughly a third of the way through
in ~20 minutes across several real, independently-verified attempts)
purely for two dependencies this project never calls. Verified directly:
neither `GraphBuilder.java` nor the real `graph_builder.*`/`gtfs.*`/
`osm.*` packages reference Google Cloud at all — their own real
dependencies are `com.beust:jcommander`, `com.csvreader:javacsv`,
`com.google.guava:guava`, `org.apache.commons:commons-*`,
`org.onebusaway:onebusaway-gtfs`, `org.locationtech.jts:jts-core`
(already vendored), `org.slf4j:slf4j-api` (already vendored) — none of
which touch `com.google.cloud:*` either. A small, separate project
vendoring just the graph-building slice avoids the whole GCP BOM
resolution entirely.

**Also verified: elevation/DEM support (`graph_builder.module.ned.*`,
which pulls in GeoTools/JAI — a second, separate large dependency
surface) is genuinely optional, not a hard dependency of graph building.**
`GraphBuilder.java` itself never references it; only the config-driven
wiring layer (`GraphBuilderFactory`/`GraphBuilderModules`, which parses
`build-config.json` and conditionally adds modules) does. Since this
task's own driver code constructs the module list directly (see Step 3),
elevation is simply never added — skip `graph_builder.module.ned.*`, its
`services.ned.*` counterpart, and their GeoTools/JAI dependencies
entirely. Denmark is flat; this project has no use for elevation profiles
regardless.

**The throwaway builder is genuinely throwaway:** it lives under
`.tools/graph-builder/` (already gitignored via `.tools/`), is never
referenced by `settings.gradle.kts`'s own `include(...)` list, and never
gets a task-review the way `otp-server-ui`'s own real modules do — its
only deliverable is the `tiny-fixture-graph.obj` binary it produces. Feel
free to structure its own internals pragmatically (this task's own
judgment) rather than to this plan's usual code-quality bar.

- [ ] **Step 1: Vendor the graph-building source slice**

From the same exact commit (`61a3af6798`) already used for everything
else in this plan, copy these real upstream packages verbatim into
`.tools/graph-builder/src/main/java/org/opentripplanner/`:
`graph_builder/**` (144 files per this plan's own earlier research)
EXCEPT `graph_builder/module/ned/**` and `graph_builder/services/ned/**`
(elevation, skipped — see above), `gtfs/**` (55 files), `osm/**` (55
files, note this excludes `graph_builder/module/osm/**`, which stays —
that's the street-graph-building orchestration, a different package from
the raw `osm/**` OSM-parsing one). Compile (`./gradlew` from this
throwaway project's own root, once Step 2's `build.gradle.kts` exists)
and fix whatever additional real upstream files the compiler surfaces as
missing (same "copy the real error's missing type from the same upstream
checkout" pattern Task 3 already used) — expect a genuine iteration loop
here, not a one-shot clean compile; this is real, substantial vendoring,
size-comparable to Task 2's own 6-module effort.

- [ ] **Step 2: Write the throwaway project's own `build.gradle.kts`**

A minimal Gradle project (own `settings.gradle.kts`/`build.gradle.kts`
under `.tools/graph-builder/`, NOT part of `otp-server-ui`'s own Gradle
reactor) depending on:
- This repo's own already-built `otp-utils`/`otp-domain-core`/`otp-astar`/
  `otp-street`/`otp-raptor`/`otp-routing` modules (reference their real
  built jars directly, e.g. `files("../../otp-utils/build/libs/otp-utils.jar")`
  for each, or a Gradle composite build via `includeBuild("../..")` if
  that proves simpler — your judgment on which is less friction).
- The real dependencies named above (`jcommander`, `javacsv`, `guava`,
  `commons-lang3`/`commons-io` — check the real imports for exact
  `commons-*` artifacts needed, `onebusaway-gtfs`, plus whatever else the
  compiler surfaces in Step 1).
- Route dependency resolution through the same JetBrains mirror this
  whole plan already uses (`https://cache-redirector.jetbrains.com/maven-central`)
  — copy the exact `dependencyResolutionManagement` pattern from this
  repo's own root `settings.gradle.kts`.

- [ ] **Step 3: Write a small driver `main()`**

Construct the needed `GraphBuilder` modules directly (read
`graph_builder/GraphBuilder.java`'s real public API first — its own
constructor/module-list shape, confirmed to exist from Step 1's vendored
copy — to get the exact real method names, don't guess): a GTFS module
reading a local GTFS zip, an OSM/street module reading a local `.osm.pbf`
file, run the real `GraphBuilder`, then call
`SerializedGraphObject.save(...)` (already-vendored, Task 3) to write the
result to a local path. This driver only needs to support exactly the
inputs this task needs — a hardcoded local file path is fine, this is
throwaway code.

- [ ] **Step 4: Source a small real GTFS + OSM pair**

The real, raw, full-Denmark source files already exist locally:
`C:\Users\bru\spare-source\bikebus\pipeline\build\sources\denmark-latest.osm.pbf`
(494 MB) and `...\pipeline\build\sources\GTFS.zip` (53 MB). Clip both down
to a small Aarhus-area extract before feeding them to the Step 3 driver,
using the exact same bounding box `bikebus`'s own pipeline already uses
for its own Aarhus dev fixture (reuse verbatim so this plan's ported tests
can reuse `bikebus`'s own known-good expected values):

```
min_lon=10.05  min_lat=56.08  max_lon=10.30  max_lat=56.25
```

**OSM clipping:** `bikebus`'s own pipeline venv
(`C:\Users\bru\spare-source\bikebus\pipeline\.venv\Scripts\python.exe`)
already has the real `osmium` Python package (pyosmium) installed — no
external CLI tool needed. Use `osmium.ForwardReferenceWriter` (real,
already-installed API — run
`.venv/Scripts/python.exe -c "import osmium; help(osmium.ForwardReferenceWriter.__init__)"`
yourself to see its real constructor signature before writing your own
extraction script) to write a real, valid, reference-complete `.osm.pbf`
extract covering the bbox above.

**GTFS clipping:** no ready-made "clip a GTFS zip to a bbox and write a
new valid zip" tool exists yet (`bikebus`'s own `gtfs_reader.py` filters
into its own in-memory bundle representation, not back out to a real GTFS
zip). Write a small, real Python script: read `stops.txt` and keep stops
within the bbox (plus their `parent_station`, if any); read
`stop_times.txt`/`trips.txt` and keep only rows referencing kept stops (a
trip that touches at least one kept stop is fine to keep in full, even if
some of its other stops fall outside the bbox); keep only the
`routes.txt`/`agency.txt`/`calendar.txt`/`calendar_dates.txt` rows those
kept trips actually reference. Write a real, valid GTFS zip (same file
set, filtered rows) as the result. Given `stop_times.txt` (220 MB) and
`shapes.txt` (110 MB) are large, read them by streaming (`csv.DictReader`
row-by-row), not by loading the whole file into memory at once.

- [ ] **Step 5: Run the throwaway builder and confirm the output loads**

Run the Step 3 driver against the Step 4 clipped inputs, producing a real
`graph.obj`. Then confirm it actually loads via THIS repo's own vendored
`SerializedGraphObject` (a quick, temporary test in `otp-routing` pointed
at the produced file is fine to prove this before doing Step 6's real,
permanent copy — delete the temporary test afterward). This is the real
acceptance criterion for this whole task: a version-277 file (confirmed:
`OpenTripPlanner/pom.xml`'s own `<otp.serialization.version.id>` property
— re-check it hasn't drifted from 277 since this plan's own research, the
version used for everything else this plan vendors) that this project's
own code can genuinely deserialize.

- [ ] **Step 6: Copy the resulting graph file into the test resources**

```bash
cp /path/to/your/output/graph.obj otp-routing/src/test/resources/tiny-fixture-graph.obj
```

- [ ] **Step 7: Remove `@Disabled` from `SerializedGraphObjectRoundTripTest`**

Delete the `@Disabled("enabled by Task 4 once tiny-fixture-graph.obj exists")`
line and its import if now unused.

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew :otp-routing:test --tests "*.SerializedGraphObjectRoundTripTest"`
Expected: PASS — `loaded.graph` is non-null and real.

- [ ] **Step 9: Document in `docs/graph-build.md`**

Write what the throwaway builder actually needed (the real vendored file
list, the real dependencies, the real driver code's shape, the real bbox
and clipping approach) — this becomes the reference for the eventual
production Denmark-wide graph-building flow (explicitly deferred, not
this task's own job — a full production pipeline needs real config
handling, error reporting, and probably belongs back in the real
`application` module's own build once this project is far enough along to
justify paying that dependency cost once, in CI/deployment rather than
per-developer-machine).

- [ ] **Step 10: Commit (the fixture and docs only — never the throwaway builder itself)**

```bash
git add otp-routing/src/test/resources/tiny-fixture-graph.obj otp-routing/src/test/java/org/opentripplanner/routing/graph/SerializedGraphObjectRoundTripTest.java docs/graph-build.md
git commit -m "Add a real test-fixture serialized graph (Aarhus area)"
```

---

### Task 5: Load the fixture graph into a real RoutingEngine

**Files:**
- Create: `backend/src/main/kotlin/one/otpserverui/GraphLoader.kt`
- Test: `backend/src/test/kotlin/one/otpserverui/GraphLoaderTest.kt`

**Interfaces:**
- Consumes: `org.opentripplanner.routing.graph.SerializedGraphObject.load`
  (Task 3), the real `tiny-fixture-graph.obj` (Task 4).
- Produces: `object GraphLoader { fun load(path: java.nio.file.Path): LoadedGraph }`
  and `data class LoadedGraph(val graph: Graph, val transitRepository: TransitRepository, val transferRepository: TransferRepository)`
  — Task 6 builds a `RoutingEngine` directly from this.

- [ ] **Step 1: Write the failing test**

First, copy the fixture into a location the backend module's tests can read
(the backend module doesn't share `otp-routing`'s test resources
directory):

```bash
mkdir -p backend/src/test/resources
cp otp-routing/src/test/resources/tiny-fixture-graph.obj backend/src/test/resources/
```

```kotlin
package one.otpserverui

import com.google.common.truth.Truth.assertThat
import java.nio.file.Path
import kotlin.io.path.toPath
import org.junit.jupiter.api.Test

class GraphLoaderTest {
    @Test
    fun `loads a real graph with a non-empty transit repository`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)

        assertThat(loaded.graph).isNotNull()
        assertThat(loaded.transitRepository).isNotNull()
        assertThat(loaded.transferRepository).isNotNull()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "*.GraphLoaderTest"`
Expected: FAIL — `GraphLoader` is unresolved.

- [ ] **Step 3: Add `otp-routing` as a backend dependency**

In `backend/build.gradle.kts`, add to `dependencies`:
```kotlin
implementation(project(":otp-routing"))
implementation(project(":otp-street"))
implementation(project(":otp-domain-core"))
implementation(project(":otp-raptor"))
implementation(project(":otp-astar"))
implementation(project(":otp-utils"))
```

- [ ] **Step 4: Write `GraphLoader.kt`**

Read the real `SerializedGraphObject` API (from Task 3's copied source)
before writing this — the exact field/accessor names for the graph,
transit repository, and transfer repository come from that real file, not
from a guess. The shape below assumes fields named `graph`/`transitLayer`/
`transferService`-equivalent; replace with whatever `SerializedGraphObject.java`
actually declares once you've read it:

```kotlin
package one.otpserverui

import java.io.FileInputStream
import java.nio.file.Path
import org.opentripplanner.routing.graph.SerializedGraphObject
import org.opentripplanner.street.graph.Graph
import org.opentripplanner.transfer.regular.TransferRepository
import org.opentripplanner.transit.service.TransitRepository

data class LoadedGraph(
    val graph: Graph,
    val transitRepository: TransitRepository,
    val transferRepository: TransferRepository,
)

object GraphLoader {
    fun load(path: Path): LoadedGraph {
        val serialized = FileInputStream(path.toFile()).use { SerializedGraphObject.load(it) }
        // Replace the three field/accessor names below with SerializedGraphObject's real API,
        // confirmed by reading otp-routing/src/main/java/.../SerializedGraphObject.java directly.
        return LoadedGraph(
            graph = serialized.graph,
            transitRepository = serialized.transitLayer,
            transferRepository = serialized.transferRepository,
        )
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "*.GraphLoaderTest"`
Expected: PASS. If the field names in Step 4 were wrong, fix them against
the real `SerializedGraphObject.java` source now, not by guessing again.

- [ ] **Step 6: Commit**

```bash
git add backend/ otp-routing/build.gradle.kts
git commit -m "Load the real fixture graph into GraphLoader"
```

---

### Task 6: Port RoutingEngine and BuildingBlocks, dropping the dead lazy-bridge branch

**Files:**
- Create: `backend/src/main/kotlin/one/otpserverui/routing/RoutingEngine.kt`
  (copied verbatim from `bikebus/app/routing/src/main/kotlin/one/brj/bikebus/routing/RoutingEngine.kt`,
  package renamed `one.brj.bikebus.routing` -> `one.otpserverui.routing`,
  no other changes — its `transitIndexPaths` parameter already defaults to
  `emptyList()` and its constructor already installs whole-network
  `RaptorTransitData` via `RaptorTransitDataMapper` when nothing is
  installed yet; see this plan's own Global Constraints and the spec's
  "Data Pipeline and Graph Loading" section for why no lazy-bridge code is
  needed here at all)
- Create: `backend/src/main/kotlin/one/otpserverui/routing/BuildingBlocks.kt`
  (copied from the same bikebus path, package renamed, **with the edit in
  Step 1 below** — this is the one file that needs a real change, not a
  verbatim copy)
- Test: `backend/src/test/kotlin/one/otpserverui/routing/AarhusRealDataTest.kt`

**Interfaces:**
- Consumes: `LoadedGraph` (Task 5).
- Produces: `class RoutingEngine(graph: Graph, transitRepository: TransitRepository, transferRepository: TransferRepository)`
  (same public shape as bikebus's own, minus the now-removed
  `transitIndexPaths` parameter — see Step 1), `fun RoutingEngine.directRoute(...)`,
  `fun RoutingEngine.streetReach(...)`, `fun RoutingEngine.transitSearch(...)`,
  `fun RoutingEngine.toItineraries(...)`, `fun RoutingEngine.filter(...)` —
  every later task (7-10) consumes these exact function names, unchanged
  from bikebus's own.

- [ ] **Step 1: Copy `BuildingBlocks.kt`, then remove the dead lazy-bridge branch**

Copy the file verbatim first, then make this one, precise edit. The two
functions `scopedOrInstalledRaptorTransitDataForSearch` and
`scopedOrInstalledRaptorTransitDataForPaths` each currently branch on
`services.transitIndexPaths.firstOrNull()`: a real path (the scoped
lazy-bridge, calling `LazyTransitIndexReader`/`buildScopedRaptorTransitData`)
when non-null, and `services.transitService.raptorTransitData` when null.
Since this project's `RoutingEngine` (Step nothing above — copied
unchanged) never has a `transitIndexPaths` list to populate in the first
place, replace both functions' bodies with the unconditional fallback only:

```kotlin
private fun RoutingEngine.scopedOrInstalledRaptorTransitDataForSearch(
    access: Collection<RoutingAccessEgress>,
    egress: Collection<RoutingAccessEgress>,
    request: RouteRequest,
    transitSearchTimeZero: ZonedDateTime,
): RaptorTransitData = services.transitService.raptorTransitData

private fun RoutingEngine.scopedOrInstalledRaptorTransitDataForPaths(
    paths: Collection<RaptorPath<TripSchedule>>,
    transitSearchTimeZero: ZonedDateTime,
): RaptorTransitData = services.transitService.raptorTransitData
```

Then delete the now-unreachable `buildScopedRaptorTransitData` and
`patternIndexOf` private functions (nothing else calls them once the two
functions above no longer do), and remove every now-unused import this
leaves behind (`one.brj.bikebus.loader.LazyTransitIndexReader`,
`one.brj.bikebus.loader.ScopedRaptorTransitDataBuilder`, `java.nio.file.Path`
if nothing else in the file needs it, `RaptorTransitData`'s own import stays
— it's still the return type). Run
`./gradlew :backend:compileKotlin` after this edit and fix any remaining
unused-import warnings-as-errors before moving on. Every other function in
this file (`transitSearch`, `toItineraries`, `directRoute`, `streetReach`,
`filter`, `buildRaptorRequest`) is copied unchanged — they don't reference
`transitIndexPaths` at all.

- [ ] **Step 2: Copy `RoutingEngine.kt` verbatim (package rename only)**

```bash
mkdir -p backend/src/main/kotlin/one/otpserverui/routing
cp /c/Users/bru/spare-source/bikebus/app/routing/src/main/kotlin/one/brj/bikebus/routing/RoutingEngine.kt backend/src/main/kotlin/one/otpserverui/routing/
sed -i 's/one\.brj\.bikebus\.routing/one.otpserverui.routing/g' backend/src/main/kotlin/one/otpserverui/routing/RoutingEngine.kt
```

Since this project never populates `transitIndexPaths`, and Step 1 already
removed the only code that reads it, simplify the constructor by deleting
the `transitIndexPaths: List<Path> = emptyList()` parameter and its KDoc
block entirely (keep the rest of the class body unchanged) — this makes
the removal from Step 1 fully consistent rather than leaving a dead,
unused constructor parameter around.

- [ ] **Step 3: Write the failing test**

Reuse the exact real query and expected values `bikebus`'s own
`AarhusRealDataTest`'s `directRoute` test already established (same
fixture area, since Task 4 built this project's fixture graph over the
same real Aarhus data):

```kotlin
package one.otpserverui.routing

import com.google.common.truth.Truth.assertThat
import java.nio.file.Path
import kotlin.io.path.toPath
import one.otpserverui.GraphLoader
import org.junit.jupiter.api.Test
import org.opentripplanner.model.plan.leg.StreetLeg
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.street.model.StreetMode

class AarhusRealDataTest {
    @Test
    fun `directRoute finds the known real Aarhus bike route`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        val origin = WgsCoordinate(56.171798, 10.172087).moveEastMeters(100.0).moveNorthMeters(100.0)
        val destination = WgsCoordinate(56.165518, 10.185262).moveEastMeters(200.0).moveNorthMeters(-200.0)
        val request = engine.requestBuilder()
            .withFrom(org.opentripplanner.model.GenericLocation.fromCoordinate(origin))
            .withTo(org.opentripplanner.model.GenericLocation.fromCoordinate(destination))
            .withDateTime(java.time.ZonedDateTime.of(2026, 9, 13, 16, 0, 0, 0, java.time.ZoneId.of("Europe/Copenhagen")).toInstant())
            .buildRequest()

        val itineraries = engine.directRoute(origin, destination, StreetMode.BIKE, request)

        assertThat(itineraries).hasSize(1)
        val leg = itineraries.single().legs().single() as StreetLeg
        assertThat(leg.mode.name).isEqualTo("BICYCLE")
        assertThat(leg.distanceMeters()).isWithin(1.0).of(1820.95)
        assertThat(leg.duration().seconds).isEqualTo(474L)
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "*.AarhusRealDataTest"`
Expected: FAIL — compile error until Steps 1-2's files are both in place,
or (once they compile) a real assertion failure if Task 4's fixture graph
doesn't cover this exact origin/destination the same way bikebus's own
fixture does — if so, fall back to Step 4a below.

- [ ] **Step 4a (only if Step 4's exact bikebus expected values don't
  match): measure real values from this project's own fixture**

Run the same query with a placeholder assertion (e.g.
`assertThat(leg.distanceMeters()).isWithin(1.0).of(0.0)`), read the real
failure output's actual value, and use that as this test's real expected
value instead — document in a one-line comment that this project's fixture
graph build (Task 4) produced a slightly different result than bikebus's
own pipeline output, and why that's expected (different graph-build tool
chain, real OTP's own OSM/GTFS parsing vs. bikebus's custom pipeline, can
give a legitimately different shortest path).

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "*.AarhusRealDataTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/one/otpserverui/routing/ backend/src/test/kotlin/one/otpserverui/routing/
git commit -m "Port RoutingEngine and BuildingBlocks, dropping the dead lazy-bridge branch"
```

---

### Task 7: Port ParkAndRideFinder

**Files:**
- Create: `backend/src/main/kotlin/one/otpserverui/routing/parkandride/ParkAndRideFinder.kt`
  (copied verbatim from `bikebus/app/routing/src/main/kotlin/one/brj/bikebus/routing/parkandride/ParkAndRideFinder.kt`,
  package renamed only — this file already has the two-tier egress fallback
  and needs no other changes)
- Test: `backend/src/test/kotlin/one/otpserverui/routing/parkandride/ParkAndRideFinderTest.kt`

**Interfaces:**
- Consumes: `RoutingEngine`, `streetReach`, `transitSearch`, `toItineraries`,
  `filter` (Task 6).
- Produces: `object ParkAndRideFinder { fun search(engine: RoutingEngine, origin: WgsCoordinate, destination: WgsCoordinate, departureTime: Instant): Itinerary? }`.

- [ ] **Step 1: Copy the file verbatim**

```bash
mkdir -p backend/src/main/kotlin/one/otpserverui/routing/parkandride
cp /c/Users/bru/spare-source/bikebus/app/routing/src/main/kotlin/one/brj/bikebus/routing/parkandride/ParkAndRideFinder.kt backend/src/main/kotlin/one/otpserverui/routing/parkandride/
sed -i 's/one\.brj\.bikebus\.routing/one.otpserverui.routing/g' backend/src/main/kotlin/one/otpserverui/routing/parkandride/ParkAndRideFinder.kt
```

- [ ] **Step 2: Write the failing test**

Reuse bikebus's own `ParkAndRideFinderTest`'s exact real query/expected
values (same fixture area):

```kotlin
package one.otpserverui.routing.parkandride

import com.google.common.truth.Truth.assertThat
import kotlin.io.path.toPath
import one.otpserverui.GraphLoader
import one.otpserverui.routing.RoutingEngine
import org.junit.jupiter.api.Test
import org.opentripplanner.model.plan.leg.StreetLeg
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.street.search.TraverseMode

class ParkAndRideFinderTest {
    @Test
    fun `real bike-then-transit itinerary for the known Aarhus trip`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        val origin = WgsCoordinate(56.171798, 10.172087)
        val destination = WgsCoordinate(56.102216, 10.17293)
        val departure = java.time.Instant.parse("2026-09-13T14:00:00Z")

        val itinerary = ParkAndRideFinder.search(engine, origin, destination, departure)

        assertThat(itinerary).isNotNull()
        assertThat(itinerary!!.legs().any { it.isTransitLeg }).isTrue()
        val accessLeg = itinerary.legs().first()
        assertThat(accessLeg.isTransitLeg).isFalse()
        assertThat((accessLeg as StreetLeg).mode).isEqualTo(TraverseMode.BICYCLE)
    }
}
```

- [ ] **Step 3: Run test to verify it fails or passes**

Run: `./gradlew :backend:test --tests "*.ParkAndRideFinderTest"`
Expected: PASS directly (this file was already correct in bikebus, this
task is a pure port) — if it fails on the specific origin/destination
above (this project's own fixture graph might cover a slightly different
real area than bikebus's `ParkAndRideFixture`), pick a different, real
origin/destination pair known to work against *this* project's own fixture
graph instead, following the same placeholder-then-measure technique as
Task 6 Step 4a.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/one/otpserverui/routing/parkandride/ backend/src/test/kotlin/one/otpserverui/routing/parkandride/
git commit -m "Port ParkAndRideFinder"
```

---

### Task 8: Prove the two-tier egress fallback still works after porting

**Files:**
- Test: `backend/src/test/kotlin/one/otpserverui/routing/parkandride/ParkAndRideFinderTest.kt` (extend)

**Interfaces:**
- Consumes: `ParkAndRideFinder.search` (Task 7).

- [ ] **Step 1: Find a real, long-walk-egress destination in this
  project's own fixture graph**

Pick a real coordinate within the fixture area whose nearest transit stop
requires a longer-than-15-minute walk (the same shape of case bikebus's own
`MorkeSkovstienRealDataTest` covers) — if the fixture area doesn't
naturally contain such a case, widen the fixture's bounding box (Task 4)
enough that it does, since this is one of this plan's Review Focus items
and needs a real repro, not a skipped test.

- [ ] **Step 2: Write the failing test**

```kotlin
@Test
fun `a destination whose nearest stop is a long walk still returns a route`() {
    val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
    val loaded = GraphLoader.load(fixturePath)
    val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

    val origin = WgsCoordinate(56.171798, 10.172087) // replace with the real Step 1 origin
    val longWalkDestination = WgsCoordinate(0.0, 0.0) // replace with the real Step 1 destination
    val departure = java.time.Instant.parse("2026-09-13T14:00:00Z")

    val itinerary = ParkAndRideFinder.search(engine, origin, longWalkDestination, departure)

    assertThat(itinerary).isNotNull()
    assertThat(itinerary!!.legs().last().isTransitLeg).isFalse()
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "*.ParkAndRideFinderTest"`
Expected: FAIL until the real Step 1 coordinates are filled in — this
confirms the test genuinely exercises the fallback path (a wrong
coordinate pair that resolves within the strict 15-minute cap would pass
vacuously, so verify by temporarily setting `FALLBACK_WALK_EGRESS` in the
copied `ParkAndRideFinder.kt` to equal `MAX_WALK_EGRESS` and confirming
this specific test then fails — restore the real value afterward).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "*.ParkAndRideFinderTest"`
Expected: PASS, with `FALLBACK_WALK_EGRESS` restored to its real (large)
value.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/kotlin/one/otpserverui/routing/parkandride/
git commit -m "Prove the two-tier egress fallback still works after porting"
```

---

### Task 9: Port the Bring Bike composition, with its empty-reach guard

**Files:**
- Create: `backend/src/main/kotlin/one/otpserverui/routing/BringBike.kt`
  (adapted from `bikebus/app/src/main/java/one/brj/bikebus/TripViewModel.kt`'s
  `planEmbeddedItineraries` function — see Step 1; this is an *extraction*,
  not a verbatim copy, since the source function lives inside an
  Android-specific file this project has no equivalent of)
- Test: `backend/src/test/kotlin/one/otpserverui/routing/BringBikeTest.kt`

**Interfaces:**
- Consumes: `RoutingEngine`, `streetReach`, `transitSearch`,
  `routingToItineraries` (`BuildingBlocks.toItineraries`, Task 6),
  `directRoute`, `filter`.
- Produces: `fun bringBike(engine: RoutingEngine, origin: WgsCoordinate, destination: WgsCoordinate, timeMode: TimeMode, dateTime: Instant): List<Itinerary>`
  (real OTP `org.opentripplanner.model.plan.Itinerary`, not yet the app-level
  DTO — Task 10 adds that mapping layer).

- [ ] **Step 1: Read the real source function first**

Read `bikebus/app/src/main/java/one/brj/bikebus/TripViewModel.kt`'s
`planEmbeddedItineraries` (currently around lines 107-154 on that branch's
tip, `39655a6` — confirm the real current line numbers before copying,
they may have shifted). It is pure routing logic with zero Android
dependencies (no `Context`, no `ViewModel`, no Compose types) apart from
living inside an Android-annotated file — safe to extract as a plain
top-level function.

- [ ] **Step 2: Write the failing test (empty-reach guard)**

Reuse bikebus's own `MorkeSkovstienRealDataTest`'s disconnected-footway
repro shape — a real coordinate in this project's fixture whose access or
egress street reach is empty:

```kotlin
package one.otpserverui.routing

import com.google.common.truth.Truth.assertThat
import kotlin.io.path.toPath
import one.otpserverui.GraphLoader
import one.otpserverui.model.TimeMode
import org.junit.jupiter.api.Test
import org.opentripplanner.street.geometry.WgsCoordinate

class BringBikeTest {
    @Test
    fun `real bike-plus-transit itinerary for the known Aarhus trip`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        val origin = WgsCoordinate(56.171798, 10.172087)
        val destination = WgsCoordinate(56.102216, 10.17293)
        val itineraries = bringBike(engine, origin, destination, TimeMode.DEPART_AT, java.time.Instant.parse("2026-09-13T14:00:00Z"))

        assertThat(itineraries).isNotEmpty()
    }

    @Test
    fun `an empty access or egress reach returns the direct route, not a crash`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        val origin = WgsCoordinate(56.171798, 10.172087)
        val disconnectedDestination = WgsCoordinate(0.0, 0.0) // replace with a real disconnected point in this project's fixture

        val itineraries = bringBike(engine, origin, disconnectedDestination, TimeMode.DEPART_AT, java.time.Instant.parse("2026-09-13T14:00:00Z"))

        // No exception thrown is the primary assertion here; a real fixture-specific
        // disconnected point may or may not still have a direct route, so assert only
        // that this call completed without throwing.
        assertThat(itineraries).isNotNull()
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "*.BringBikeTest"`
Expected: FAIL — `bringBike`/`TimeMode` unresolved.

- [ ] **Step 4: Write `TimeMode` and `BringBike.kt`**

```kotlin
package one.otpserverui.model

enum class TimeMode { DEPART_AT, ARRIVE_BY }
```

```kotlin
package one.otpserverui.routing

import java.time.Instant
import one.otpserverui.model.TimeMode
import org.opentripplanner.model.GenericLocation
import org.opentripplanner.model.plan.Itinerary
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.street.model.StreetMode

fun bringBike(
    engine: RoutingEngine,
    origin: WgsCoordinate,
    destination: WgsCoordinate,
    timeMode: TimeMode,
    dateTime: Instant,
): List<Itinerary> {
    val request = engine.requestBuilder()
        .withFrom(GenericLocation.fromCoordinate(origin))
        .withTo(GenericLocation.fromCoordinate(destination))
        .withDateTime(dateTime)
        .apply { if (timeMode == TimeMode.ARRIVE_BY) withArriveBy(true) }
        .buildRequest()

    val accessEgressDuration = request.preferences().street().accessEgress().maxDuration().valueOf(StreetMode.BIKE)
    val access = engine.streetReach(origin, StreetMode.BIKE, accessEgressDuration, ReachDirection.ACCESS, request)
    val egress = engine.streetReach(destination, StreetMode.BIKE, accessEgressDuration, ReachDirection.EGRESS, request)

    val transitItineraries: List<Itinerary> = if (access.isEmpty() || egress.isEmpty()) {
        emptyList()
    } else {
        val paths = engine.transitSearch(access, egress, request)
        engine.toItineraries(paths, request)
    }

    val directItineraries = engine.directRoute(origin, destination, StreetMode.BIKE, request)

    return engine.filter(directItineraries + transitItineraries, request)
}
```

Confirm `RouteRequestBuilder.withArriveBy(Boolean)` is the real method name
by reading `otp-routing`'s vendored `RouteRequestBuilder.java` first — if
the real name differs, use the real one.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "*.BringBikeTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/one/otpserverui/routing/BringBike.kt backend/src/main/kotlin/one/otpserverui/model/ backend/src/test/kotlin/one/otpserverui/routing/BringBikeTest.kt
git commit -m "Port the Bring Bike composition, with its empty-reach guard"
```

---

### Task 10: Port HubRouting (prefer transit hubs)

**Files:**
- Create: `backend/src/main/kotlin/one/otpserverui/routing/HubRouting.kt`
  (copied from `bikebus/app/src/main/java/one/brj/bikebus/domain/HubRouting.kt`,
  package renamed)
- Create: `backend/src/main/kotlin/one/otpserverui/routing/HubCatalog.kt`
  (adapted — bikebus's own `HubCatalog.load` reads from
  `android.content.res.AssetManager`; this project reads the same real hub
  data from a plain classpath resource instead, see Step 1)
- Create: `backend/src/main/resources/hubs.json` (copied verbatim from
  bikebus's own bundled hub catalog asset — find its real path with
  `find /c/Users/bru/spare-source/bikebus/app/src/main/assets -iname "*hub*"`
  and copy whatever real file that finds)
- Test: `backend/src/test/kotlin/one/otpserverui/routing/HubRoutingTest.kt`

**Interfaces:**
- Consumes: a baseline `List<Itinerary>` (from `bringBike`, Task 9, or
  `ParkAndRideFinder.search`, Task 7), `List<Hub>` (from `HubCatalog.load`).
- Produces: `object HubRouting { fun findHubSplit(hubs: List<Hub>, baseline: Itinerary): RouteHubSplit? }`
  and `fun choose(baseline: List<Itinerary>, stitched: Itinerary?, hubName: String): Pair<List<Itinerary>, String?>`
  — same real signatures as bikebus's own, confirm by reading that file
  directly before copying (this plan doesn't reproduce its whole body here
  since it's copied verbatim).

- [ ] **Step 1: Read bikebus's real `HubCatalog.load` and adapt it**

```bash
grep -n "fun load" /c/Users/bru/spare-source/bikebus/app/src/main/java/one/brj/bikebus/domain/HubCatalog.kt
```

Read the real function body at that line. It almost certainly parses a
JSON asset via `AssetManager.open(...)`. Replace only the file-opening
call with `object {}.javaClass.classLoader.getResourceAsStream("hubs.json")`
(a plain classpath resource read) — keep every other line (the actual JSON
parsing/`Hub` construction logic) unchanged.

- [ ] **Step 2: Copy the hub data and `HubRouting.kt` verbatim**

```bash
find /c/Users/bru/spare-source/bikebus/app/src/main/assets -iname "*hub*"
# copy whatever real file that finds to backend/src/main/resources/hubs.json
cp /c/Users/bru/spare-source/bikebus/app/src/main/java/one/brj/bikebus/domain/HubRouting.kt backend/src/main/kotlin/one/otpserverui/routing/
sed -i 's/one\.brj\.bikebus\.domain/one.otpserverui.routing/g; s/one\.brj\.bikebus\.routing/one.otpserverui.routing/g' backend/src/main/kotlin/one/otpserverui/routing/HubRouting.kt
```

- [ ] **Step 3: Write the failing test**

Reuse bikebus's own `HubRouting`/`HubCatalog` test's real hub name and
known-good split, if one exists (`find /c/Users/bru/spare-source/bikebus/app -iname "*HubRouting*Test*" -o -iname "*HubCatalog*Test*"`)
— copy its real assertions rather than inventing new ones:

```kotlin
package one.otpserverui.routing

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HubRoutingTest {
    @Test
    fun `loads the real bundled hub catalog`() {
        val hubs = HubCatalog.load()
        assertThat(hubs).isNotEmpty()
    }
}
```

(Expand this test with the real known-good hub-split assertions found by
the `find` command above, once you've read that real existing test file —
don't invent a hub name or split behavior that isn't already proven
correct in bikebus's own test suite.)

- [ ] **Step 4: Run test to verify it fails, then passes**

Run: `./gradlew :backend:test --tests "*.HubRoutingTest"`
Expected: FAIL until Steps 1-2 are complete, then PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/one/otpserverui/routing/HubRouting.kt backend/src/main/kotlin/one/otpserverui/routing/HubCatalog.kt backend/src/main/resources/hubs.json backend/src/test/kotlin/one/otpserverui/routing/HubRoutingTest.kt
git commit -m "Port HubRouting (prefer transit hubs)"
```

---

### Task 11: Drop-me-off (nearby routes + direct-route flag-stop connect)

**Files:**
- Create: `backend/src/main/kotlin/one/otpserverui/routing/NearbyStops.kt`
  (copied verbatim from `bikebus/app/routing/src/main/kotlin/one/brj/bikebus/routing/NearbyStops.kt`,
  package renamed — defines `RoutingEngine.stopsNear` and `NearbyPattern`)
- Create: `backend/src/main/kotlin/one/otpserverui/domain/NearbyRoutesFinder.kt`
  (copied verbatim from `bikebus/app/src/main/java/one/brj/bikebus/domain/NearbyRoutesFinder.kt`,
  package renamed — already pure decision logic, no network/Android
  dependency)
- Create: `backend/src/main/kotlin/one/otpserverui/domain/NearbyPatternMapper.kt`
  (copied verbatim from `bikebus/app/src/main/java/one/brj/bikebus/domain/NearbyPatternMapper.kt`,
  package renamed)
- Create: `backend/src/main/kotlin/one/otpserverui/model/PatternDto.kt`
  (the plain data classes `PatternDto`/`PatternRouteDto`/`LegGeometryDto`/
  `StopRefDto` from `bikebus/app/src/main/java/one/brj/bikebus/network/PatternDto.kt`
  — copied verbatim; despite living under bikebus's own `network` package
  there, they are plain `@Serializable` data classes with zero
  Retrofit/GraphQL annotations, used here purely as `NearbyRoutesFinder`'s
  own input shape, exactly as bikebus's own embedded-engine path already
  uses them — see `NearbyPatternMapper.kt`'s own KDoc: "the embedded
  engine's `findNearbyRoutes` path can feed `rankCandidates` exactly like
  the retired GraphQL path did")
- Create: `backend/src/main/kotlin/one/otpserverui/routing/DropMeOff.kt`
- Test: `backend/src/test/kotlin/one/otpserverui/routing/DropMeOffTest.kt`

**Interfaces:**
- Consumes: `RoutingEngine.stopsNear(coordinate: WgsCoordinate, radiusMeters: Double): List<NearbyPattern>`
  (copied in this task), `RoutingEngine.directRoute` (Task 6).
- Produces: `fun nearbyRoutes(engine: RoutingEngine, destination: WgsCoordinate, radiusMeters: Double): List<NearbyRoute>`
  and `fun connectByFlaggingABus(engine: RoutingEngine, flagPoint: WgsCoordinate, destination: WgsCoordinate, departureTime: Instant): Itinerary?`
  — the latter replaces bikebus's own `OtpQueryBuilder`/`otpApi` GraphQL
  call with a direct `directRoute` call, per the spec's explicit "no
  GraphQL dependency survives into this project" requirement.

- [ ] **Step 1: Copy the four files verbatim**

```bash
cp /c/Users/bru/spare-source/bikebus/app/routing/src/main/kotlin/one/brj/bikebus/routing/NearbyStops.kt backend/src/main/kotlin/one/otpserverui/routing/
cp /c/Users/bru/spare-source/bikebus/app/src/main/java/one/brj/bikebus/domain/NearbyRoutesFinder.kt backend/src/main/kotlin/one/otpserverui/domain/
cp /c/Users/bru/spare-source/bikebus/app/src/main/java/one/brj/bikebus/domain/NearbyPatternMapper.kt backend/src/main/kotlin/one/otpserverui/domain/
mkdir -p backend/src/main/kotlin/one/otpserverui/model
cp /c/Users/bru/spare-source/bikebus/app/src/main/java/one/brj/bikebus/network/PatternDto.kt backend/src/main/kotlin/one/otpserverui/model/
sed -i 's/one\.brj\.bikebus\.routing/one.otpserverui.routing/g; s/one\.brj\.bikebus\.domain/one.otpserverui.domain/g; s/one\.brj\.bikebus\.network/one.otpserverui.model/g; s/one\.brj\.bikebus\.model/one.otpserverui.model/g' \
  backend/src/main/kotlin/one/otpserverui/routing/NearbyStops.kt \
  backend/src/main/kotlin/one/otpserverui/domain/NearbyRoutesFinder.kt \
  backend/src/main/kotlin/one/otpserverui/domain/NearbyPatternMapper.kt \
  backend/src/main/kotlin/one/otpserverui/model/PatternDto.kt
```

Run `./gradlew :backend:compileKotlin` after this copy; fix any remaining
unresolved-import errors by locating the same real type in bikebus's own
source (most likely `NearbyRoute`/`FlagStopInfo`/`TransitHub` — small,
plain data classes under `one.brj.bikebus.model`, copy those too, same
package-rename treatment) before moving on.

- [ ] **Step 2: Read `closestPointOnPolyline`/`decodePolyline` and copy them**

```bash
grep -rn "fun closestPointOnPolyline\|fun decodePolyline" /c/Users/bru/spare-source/bikebus/app/src/main/java/one/brj/bikebus/domain/
```

Copy whatever real file(s) that finds into
`backend/src/main/kotlin/one/otpserverui/domain/`, same package-rename
treatment as Step 1 — this is pure polyline geometry math with no
Android/network dependency.

- [ ] **Step 3: Write the failing test**

```kotlin
package one.otpserverui.routing

import com.google.common.truth.Truth.assertThat
import kotlin.io.path.toPath
import one.otpserverui.GraphLoader
import org.junit.jupiter.api.Test
import org.opentripplanner.street.geometry.WgsCoordinate

class DropMeOffTest {
    @Test
    fun `nearbyRoutes finds real routes near a known Aarhus stop`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        val destination = WgsCoordinate(56.171798, 10.172087)
        val routes = nearbyRoutes(engine, destination, radiusMeters = 500.0)

        assertThat(routes).isNotEmpty()
    }

    @Test
    fun `connectByFlaggingABus finds a real direct route to the flag point`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)

        val flagPoint = WgsCoordinate(56.171798, 10.172087)
        val destination = WgsCoordinate(56.165518, 10.185262)
        val itinerary = connectByFlaggingABus(engine, flagPoint, destination, java.time.Instant.parse("2026-09-13T14:00:00Z"))

        assertThat(itinerary).isNotNull()
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "*.DropMeOffTest"`
Expected: FAIL — `nearbyRoutes`/`connectByFlaggingABus` unresolved.

- [ ] **Step 5: Write `DropMeOff.kt`**

```kotlin
package one.otpserverui.routing

import java.time.Instant
import one.otpserverui.domain.NearbyRoutesFinder
import one.otpserverui.domain.toPatternDto
import one.otpserverui.model.NearbyRoute
import org.opentripplanner.model.GenericLocation
import org.opentripplanner.model.plan.Itinerary
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.street.model.StreetMode

fun nearbyRoutes(engine: RoutingEngine, destination: WgsCoordinate, radiusMeters: Double): List<NearbyRoute> {
    val patterns = engine.stopsNear(destination, radiusMeters).map { it.toPatternDto() }
    return NearbyRoutesFinder.rankCandidates(patterns, destination.latitude(), destination.longitude())
}

fun connectByFlaggingABus(
    engine: RoutingEngine,
    flagPoint: WgsCoordinate,
    destination: WgsCoordinate,
    departureTime: Instant,
): Itinerary? {
    val request = engine.requestBuilder()
        .withFrom(GenericLocation.fromCoordinate(flagPoint))
        .withTo(GenericLocation.fromCoordinate(destination))
        .withDateTime(departureTime)
        .buildRequest()
    return engine.directRoute(flagPoint, destination, StreetMode.WALK, request).firstOrNull()
}
```

Confirm `NearbyRoutesFinder.rankCandidates`'s exact parameter order/names
against the real copied file from Step 1 — the call above assumes
`rankCandidates(patterns: List<PatternDto>, destinationLat: Double, destinationLon: Double)`,
matching what was read earlier in this plan's own research; if the real
copied signature differs, use the real one, not this one.

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "*.DropMeOffTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/one/otpserverui/routing/ backend/src/main/kotlin/one/otpserverui/domain/ backend/src/main/kotlin/one/otpserverui/model/ backend/src/test/kotlin/one/otpserverui/routing/DropMeOffTest.kt
git commit -m "Port Drop-me-off (nearby routes + direct-route flag-stop connect)"
```

---

### Task 12: Itinerary/Leg response model with badges

**Files:**
- Create: `backend/src/main/kotlin/one/otpserverui/model/Itinerary.kt`
  (adapted from `bikebus/app/src/main/java/one/brj/bikebus/model/Itinerary.kt`
  — that file is already plain Kotlin with zero Android dependencies, copy
  verbatim, package renamed)
- Create: `backend/src/main/kotlin/one/otpserverui/domain/OtpItineraryMapper.kt`
  (copied from `bikebus/app/src/main/java/one/brj/bikebus/domain/OtpItineraryMapper.kt`,
  package renamed)
- Test: `backend/src/test/kotlin/one/otpserverui/model/ItineraryTest.kt`
  (copied verbatim from bikebus's own `app/src/test/kotlin/one/brj/bikebus/model/ItineraryTest.kt`
  — that test has zero Android dependencies either)

**Interfaces:**
- Consumes: real OTP `org.opentripplanner.model.plan.Itinerary` (from Tasks
  7/9/11's return types).
- Produces: `data class Itinerary(val legs: List<Leg>)` with
  `exceedsBikeLimit`/`hasLongWalkEgress`, `fun OtpItinerary.toAppItinerary(): Itinerary`
  — every JSON response in Task 13 serializes this exact type.

- [ ] **Step 1: Copy both files and the test verbatim**

```bash
mkdir -p backend/src/main/kotlin/one/otpserverui/model backend/src/main/kotlin/one/otpserverui/domain backend/src/test/kotlin/one/otpserverui/model
cp /c/Users/bru/spare-source/bikebus/app/src/main/java/one/brj/bikebus/model/Itinerary.kt backend/src/main/kotlin/one/otpserverui/model/
cp /c/Users/bru/spare-source/bikebus/app/src/main/java/one/brj/bikebus/domain/OtpItineraryMapper.kt backend/src/main/kotlin/one/otpserverui/domain/
cp /c/Users/bru/spare-source/bikebus/app/src/test/kotlin/one/brj/bikebus/model/ItineraryTest.kt backend/src/test/kotlin/one/otpserverui/model/
sed -i 's/one\.brj\.bikebus/one.otpserverui/g' backend/src/main/kotlin/one/otpserverui/model/Itinerary.kt backend/src/main/kotlin/one/otpserverui/domain/OtpItineraryMapper.kt backend/src/test/kotlin/one/otpserverui/model/ItineraryTest.kt
```

- [ ] **Step 2: Run the copied test to verify it fails or passes**

Run: `./gradlew :backend:test --tests "*.ItineraryTest"`
Expected: FAIL only if `OtpItineraryMapper.kt` references a bikebus type
not yet ported (check the compile error) — if so, port that one dependency
too (most likely `Leg`, already covered by `Itinerary.kt` in the same
file). Otherwise PASS directly, since this is a verbatim, already-correct
copy.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/one/otpserverui/model/ backend/src/main/kotlin/one/otpserverui/domain/ backend/src/test/kotlin/one/otpserverui/model/
git commit -m "Port the Itinerary/Leg response model with badges"
```

---

### Task 13: POST /search — request/response DTOs and mode dispatch

**Files:**
- Create: `backend/src/main/kotlin/one/otpserverui/api/SearchRoute.kt`
- Create: `backend/src/main/kotlin/one/otpserverui/api/SearchDto.kt`
- Test: `backend/src/test/kotlin/one/otpserverui/api/SearchRouteTest.kt`

**Interfaces:**
- Consumes: `bringBike` (Task 9), `ParkAndRideFinder.search` (Task 7),
  `HubRouting` (Task 10), `toAppItinerary` (Task 12).
- Produces: `fun Routing.searchRoute(engine: RoutingEngine, hubs: List<Hub>)`
  — registers `POST /search`; wired into the real, complete production
  application only in Task 16 (see that task for why), not here.

- [ ] **Step 1: Write the DTOs**

```kotlin
package one.otpserverui.api

import kotlinx.serialization.Serializable

@Serializable
data class SearchRequest(
    val mode: String, // "park_and_ride" | "bring_bike"
    val timeMode: String, // "depart_at" | "arrive_by"
    val originLat: Double,
    val originLon: Double,
    val destinationLat: Double,
    val destinationLon: Double,
    val dateTimeIso: String,
    val preferHubs: Boolean = false,
)

@Serializable
data class LegDto(
    val mode: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val fromLat: Double,
    val fromLon: Double,
    val toLat: Double,
    val toLon: Double,
    val routeShortName: String?,
    val departureEpochSecond: Long,
)

@Serializable
data class ItineraryDto(
    val legs: List<LegDto>,
    val exceedsBikeLimit: Boolean,
    val hasLongWalkEgress: Boolean,
)

@Serializable
data class SearchResponse(
    val itineraries: List<ItineraryDto>,
    val notice: String? = null,
)

@Serializable
data class SearchErrorResponse(val error: String)
```

- [ ] **Step 2: Write the failing test**

This task's test does not depend on `Main.kt`'s shared `module()` function
(that function's final, complete shape isn't settled until Task 16 —
having this task's test import it now would create exactly the kind of
cross-task signature drift a pre-flight review would flag). Instead, build
a minimal, self-contained Ktor application directly in the test, wiring
only `searchRoute` — the one route this task owns:

```kotlin
package one.otpserverui.api

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.io.path.toPath
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import one.otpserverui.GraphLoader
import one.otpserverui.routing.HubCatalog
import one.otpserverui.routing.RoutingEngine
import org.junit.jupiter.api.Test

private fun testEngine(): RoutingEngine {
    val fixturePath = checkNotNull(object {}.javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
    val loaded = GraphLoader.load(fixturePath)
    return RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)
}

class SearchRouteTest {
    @Test
    fun `POST search bring_bike returns a real itinerary`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { searchRoute(testEngine(), HubCatalog.load()) }
            }
            val response = client.post("/search") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"mode":"bring_bike","timeMode":"depart_at",
                     "originLat":56.171798,"originLon":10.172087,
                     "destinationLat":56.102216,"destinationLon":10.17293,
                     "dateTimeIso":"2026-09-13T14:00:00Z","preferHubs":false}
                    """.trimIndent()
                )
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val body = Json.decodeFromString<SearchResponse>(response.bodyAsText())
            assertThat(body.itineraries).isNotEmpty()
        }
    }

    @Test
    fun `POST search with an out-of-coverage destination returns the typed no_coverage error`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { searchRoute(testEngine(), HubCatalog.load()) }
            }
            val response = client.post("/search") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"mode":"bring_bike","timeMode":"depart_at",
                     "originLat":56.171798,"originLon":10.172087,
                     "destinationLat":59.9,"destinationLon":10.7,
                     "dateTimeIso":"2026-09-13T14:00:00Z","preferHubs":false}
                    """.trimIndent()
                )
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
            val body = Json.decodeFromString<SearchErrorResponse>(response.bodyAsText())
            assertThat(body.error).isEqualTo("no_coverage")
        }
    }

    @Test
    fun `POST search with an unknown mode returns a typed 400 error`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { searchRoute(testEngine(), HubCatalog.load()) }
            }
            val response = client.post("/search") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"mode":"teleport","timeMode":"depart_at",
                     "originLat":56.171798,"originLon":10.172087,
                     "destinationLat":56.102216,"destinationLon":10.17293,
                     "dateTimeIso":"2026-09-13T14:00:00Z","preferHubs":false}
                    """.trimIndent()
                )
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
            val body = Json.decodeFromString<SearchErrorResponse>(response.bodyAsText())
            assertThat(body.error).isEqualTo("unknown_mode")
        }
    }

    @Test
    fun `POST search with an unparseable datetime returns a typed 400 error`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { searchRoute(testEngine(), HubCatalog.load()) }
            }
            val response = client.post("/search") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"mode":"bring_bike","timeMode":"depart_at",
                     "originLat":56.171798,"originLon":10.172087,
                     "destinationLat":56.102216,"destinationLon":10.17293,
                     "dateTimeIso":"not-a-real-datetime","preferHubs":false}
                    """.trimIndent()
                )
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
            val body = Json.decodeFromString<SearchErrorResponse>(response.bodyAsText())
            assertThat(body.error).isEqualTo("invalid_request")
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "*.SearchRouteTest"`
Expected: FAIL — `/search` route doesn't exist yet.

- [ ] **Step 4: Write `SearchRoute.kt` and wire it into `Main.kt`**

Bring Bike's own hub-stitching (per bikebus's own `TripViewModel.fetchItineraries`/
`buildStitchedItinerary` — read those functions once more from
`bikebus/app/src/main/java/one/brj/bikebus/TripViewModel.kt` if any detail
below doesn't match) only applies when `preferHubs` is set and a hub split
exists on the best baseline itinerary: it re-plans the trip as two
independent `bringBike` calls (origin -> hub, hub -> destination, chained
by arrival/departure time) and stitches the two legs together via
`HubRouting.trimHubConnector`, keeping the stitched result only if
`HubRouting.choose` prefers it over the baseline. Park & Ride never applies
hub-stitching (mirrors bikebus's own `fetchItineraries`, which returns
immediately for that mode before reaching the hub-preference check).

```kotlin
package one.otpserverui.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import java.time.Instant
import java.time.OffsetDateTime
import one.otpserverui.domain.toAppItinerary
import one.otpserverui.model.TimeMode
import one.otpserverui.routing.Hub
import one.otpserverui.routing.HubRouting
import one.otpserverui.routing.RoutingEngine
import one.otpserverui.routing.bringBike
import one.otpserverui.routing.parkandride.ParkAndRideFinder
import org.opentripplanner.model.plan.Itinerary
import org.opentripplanner.routing.error.RoutingValidationException
import org.opentripplanner.street.geometry.WgsCoordinate

private fun bringBikeWithHubPreference(
    engine: RoutingEngine,
    hubs: List<Hub>,
    origin: WgsCoordinate,
    destination: WgsCoordinate,
    timeMode: TimeMode,
    dateTime: Instant,
    preferHubs: Boolean,
): Pair<List<Itinerary>, String?> {
    val baseline = bringBike(engine, origin, destination, timeMode, dateTime)
    if (!preferHubs) return baseline to null
    val bestBaseline = baseline.firstOrNull() ?: return baseline to null
    val hub = HubRouting.findHubSplit(hubs, bestBaseline) ?: return baseline to null

    val stitched = runCatching {
        when (timeMode) {
            TimeMode.DEPART_AT -> {
                val legA = bringBike(engine, origin, hub.coordinate, TimeMode.DEPART_AT, dateTime).firstOrNull() ?: return@runCatching null
                val legB = bringBike(engine, hub.coordinate, destination, TimeMode.DEPART_AT, legA.endTimeAsInstant()).firstOrNull() ?: return@runCatching null
                HubRouting.trimHubConnector(legA, legB)
            }
            TimeMode.ARRIVE_BY -> {
                val legB = bringBike(engine, hub.coordinate, destination, TimeMode.ARRIVE_BY, dateTime).firstOrNull() ?: return@runCatching null
                val legA = bringBike(engine, origin, hub.coordinate, TimeMode.ARRIVE_BY, legB.startTimeAsInstant()).firstOrNull() ?: return@runCatching null
                HubRouting.trimHubConnector(legA, legB)
            }
        }
    }.getOrNull()

    return HubRouting.choose(baseline, stitched, hub.name)
}

fun Routing.searchRoute(engine: RoutingEngine, hubs: List<Hub>) {
    post("/search") {
        val request = call.receive<SearchRequest>()

        val dateTime = try {
            Instant.parse(request.dateTimeIso)
        } catch (e: java.time.format.DateTimeParseException) {
            call.respond(HttpStatusCode.BadRequest, SearchErrorResponse("invalid_request"))
            return@post
        }
        if (request.mode != "park_and_ride" && request.mode != "bring_bike") {
            call.respond(HttpStatusCode.BadRequest, SearchErrorResponse("unknown_mode"))
            return@post
        }

        val origin = WgsCoordinate(request.originLat, request.originLon)
        val destination = WgsCoordinate(request.destinationLat, request.destinationLon)
        val timeMode = if (request.timeMode == "arrive_by") TimeMode.ARRIVE_BY else TimeMode.DEPART_AT

        val (itineraries, notice) = try {
            when (request.mode) {
                "park_and_ride" -> listOfNotNull(ParkAndRideFinder.search(engine, origin, destination, dateTime)) to null
                else -> bringBikeWithHubPreference(engine, hubs, origin, destination, timeMode, dateTime, request.preferHubs)
            }
        } catch (e: RoutingValidationException) {
            call.respond(HttpStatusCode.UnprocessableEntity, SearchErrorResponse("no_coverage"))
            return@post
        }

        val dtos = itineraries.map { it.toAppItinerary() }.map { app ->
            ItineraryDto(
                legs = app.legs.map {
                    LegDto(it.mode, it.distanceMeters, it.durationSeconds, it.fromLat, it.fromLon, it.toLat, it.toLon, it.routeShortName, it.departureTime.toEpochSecond())
                },
                exceedsBikeLimit = app.exceedsBikeLimit,
                hasLongWalkEgress = app.hasLongWalkEgress,
            )
        }
        call.respond(SearchResponse(dtos, notice))
    }
}
```

Confirm the following against `HubRouting.kt`'s real, just-copied (Task 10)
source before treating the above as final, and adjust any name/signature
that differs: `Hub`'s real field name for its coordinate (assumed
`.coordinate: WgsCoordinate` above — bikebus's own `Hub`/`TransitHub` type
may name it differently, e.g. separate `lat`/`lon` fields), and
`HubRouting.trimHubConnector`'s real signature (assumed
`trimHubConnector(legA: Itinerary, legB: Itinerary): Itinerary` above,
matching this task's own earlier research into
`TripViewModel.buildStitchedItinerary`'s `stitchLegs` helper, which calls
`HubRouting.trimHubConnector(legA.legs, fromEnd = true)` on each side
separately, not on a whole `Itinerary` at once — if that's the real shape,
adapt `bringBikeWithHubPreference` to match it exactly rather than the
single-call form assumed here). Also confirm `Itinerary`'s own real
accessor for "arrival/departure time as an `Instant`" (`endTimeAsInstant`/
`startTimeAsInstant` above are placeholders for whatever real OTP
`Itinerary` method exists — likely `.endTime().toInstant()`/
`.startTime().toInstant()` or similar; read the real
`org.opentripplanner.model.plan.Itinerary` class from `otp-routing`,
vendored in Task 2, to get the exact real method name).

This task does not modify `Main.kt`'s production `module()`/`main()` at
all — Task 16 is where `searchRoute`/`dropMeOffRoutes`/`geocodeRoute` all
get registered together into the one real, complete production
application. Keeping that assembly in a single later task avoids each of
Tasks 13-15 repeatedly touching the same shared function and drifting out
of sync with each other, the way an earlier draft of this plan did before
its own pre-flight review caught it.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "*.SearchRouteTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/one/otpserverui/api/ backend/src/test/kotlin/one/otpserverui/api/
git commit -m "Add POST /search with mode dispatch and typed error responses"
```

---

### Task 14: Drop-me-off HTTP endpoints (nearby routes + connect)

**Files:**
- Create: `backend/src/main/kotlin/one/otpserverui/api/DropMeOffRoute.kt`
- Test: `backend/src/test/kotlin/one/otpserverui/api/DropMeOffRouteTest.kt`

**Interfaces:**
- Consumes: `nearbyRoutes`, `connectByFlaggingABus` (Task 11).
- Produces: `fun Routing.dropMeOffRoutes(engine: RoutingEngine)` —
  registers `GET /nearby-routes?lat=...&lon=...&radiusMeters=...` returning
  `{"routes": [{"routeGtfsId": "...", "routeShortName": "...", "distanceMeters": ...}]}`,
  and `POST /connect` (body: flag point lat/lon, destination lat/lon,
  departure datetime) returning a single `ItineraryDto` or a typed
  `unreachable` error — Drop-me-off is a genuinely two-step flow (browse
  nearby routes, then connect to the chosen one), unlike Park & Ride/Bring
  Bike's single-call `/search`, so it gets its own two endpoints rather
  than being folded into `SearchRequest`'s `mode` field. Wired into the
  real, complete production application only in Task 16, same reasoning as
  Task 13's own `searchRoute` — not here.

- [ ] **Step 1: Write the DTOs**

```kotlin
package one.otpserverui.api

import kotlinx.serialization.Serializable

@Serializable
data class NearbyRouteDto(val routeGtfsId: String, val routeShortName: String, val distanceMeters: Double)

@Serializable
data class NearbyRoutesResponse(val routes: List<NearbyRouteDto>)

@Serializable
data class ConnectRequest(
    val flagLat: Double,
    val flagLon: Double,
    val destinationLat: Double,
    val destinationLon: Double,
    val dateTimeIso: String,
)
```

- [ ] **Step 2: Write the failing test**

Same as Task 13's own test: this task's test does not depend on the
shared, not-yet-final `module()` — it builds a minimal application wiring
only `dropMeOffRoutes`:

```kotlin
package one.otpserverui.api

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.io.path.toPath
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import one.otpserverui.GraphLoader
import one.otpserverui.routing.RoutingEngine
import org.junit.jupiter.api.Test

private fun testEngine(): RoutingEngine {
    val fixturePath = checkNotNull(object {}.javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
    val loaded = GraphLoader.load(fixturePath)
    return RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)
}

class DropMeOffRouteTest {
    @Test
    fun `GET nearby-routes returns real routes near a known Aarhus stop`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { dropMeOffRoutes(testEngine()) }
            }
            val response = client.get("/nearby-routes?lat=56.171798&lon=10.172087&radiusMeters=500")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val body = Json.decodeFromString<NearbyRoutesResponse>(response.bodyAsText())
            assertThat(body.routes).isNotEmpty()
        }
    }

    @Test
    fun `POST connect returns a real itinerary to the flag point`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { dropMeOffRoutes(testEngine()) }
            }
            val response = client.post("/connect") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {"flagLat":56.171798,"flagLon":10.172087,
                     "destinationLat":56.165518,"destinationLon":10.185262,
                     "dateTimeIso":"2026-09-13T14:00:00Z"}
                    """.trimIndent()
                )
            }
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val body = Json.decodeFromString<ItineraryDto>(response.bodyAsText())
            assertThat(body.legs).isNotEmpty()
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "*.DropMeOffRouteTest"`
Expected: FAIL — neither route exists yet.

- [ ] **Step 4: Write `DropMeOffRoute.kt`**

```kotlin
package one.otpserverui.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.Instant
import one.otpserverui.domain.toAppItinerary
import one.otpserverui.routing.RoutingEngine
import one.otpserverui.routing.connectByFlaggingABus
import one.otpserverui.routing.nearbyRoutes
import org.opentripplanner.street.geometry.WgsCoordinate

fun Routing.dropMeOffRoutes(engine: RoutingEngine) {
    get("/nearby-routes") {
        val lat = checkNotNull(call.request.queryParameters["lat"]).toDouble()
        val lon = checkNotNull(call.request.queryParameters["lon"]).toDouble()
        val radiusMeters = call.request.queryParameters["radiusMeters"]?.toDouble() ?: 500.0
        val routes = nearbyRoutes(engine, WgsCoordinate(lat, lon), radiusMeters)
        call.respond(NearbyRoutesResponse(routes.map { NearbyRouteDto(it.routeGtfsId, it.routeShortName, it.distanceMeters) }))
    }

    post("/connect") {
        val request = call.receive<ConnectRequest>()
        val flagPoint = WgsCoordinate(request.flagLat, request.flagLon)
        val destination = WgsCoordinate(request.destinationLat, request.destinationLon)
        val itinerary = connectByFlaggingABus(engine, flagPoint, destination, Instant.parse(request.dateTimeIso))
        if (itinerary == null) {
            call.respond(HttpStatusCode.UnprocessableEntity, SearchErrorResponse("unreachable"))
            return@post
        }
        val app = itinerary.toAppItinerary()
        call.respond(
            ItineraryDto(
                legs = app.legs.map {
                    LegDto(it.mode, it.distanceMeters, it.durationSeconds, it.fromLat, it.fromLon, it.toLat, it.toLon, it.routeShortName, it.departureTime.toEpochSecond())
                },
                exceedsBikeLimit = app.exceedsBikeLimit,
                hasLongWalkEgress = app.hasLongWalkEgress,
            )
        )
    }
}
```

Confirm `NearbyRoute`'s real field names (`routeGtfsId`/`routeShortName`/
`distanceMeters` assumed above) against `NearbyRoutesFinder.kt`'s real,
just-copied (Task 11) `NearbyRoute` data class before treating this as
final. This task does not modify `Main.kt` — Task 16 registers
`dropMeOffRoutes` alongside `searchRoute`/`geocodeRoute` in the one real
production application (see Task 13's own note for why).

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "*.DropMeOffRouteTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/one/otpserverui/api/DropMeOffRoute.kt backend/src/test/kotlin/one/otpserverui/api/DropMeOffRouteTest.kt
git commit -m "Add Drop-me-off HTTP endpoints (nearby routes + connect)"
```

---

### Task 15: GET /geocode — Google Places proxy

**Files:**
- Create: `backend/src/main/kotlin/one/otpserverui/api/GeocodeRoute.kt`
- Test: `backend/src/test/kotlin/one/otpserverui/api/GeocodeRouteTest.kt`

**Interfaces:**
- Produces: `fun Routing.geocodeRoute(client: GeocodeClient)` — registers
  `GET /geocode?q=...` returning
  `{"candidates": [{"label": "...", "lat": ..., "lon": ...}]}`. Wired into
  the real, complete production application only in Task 16, same
  reasoning as Task 13's own `searchRoute` — not here.

- [ ] **Step 1: Write the failing test**

Use a fake/mocked Places client (an injected `GeocodeClient` interface),
not a real network call, so this test is fast and deterministic — same
self-contained-application pattern as Tasks 13/14's own tests, wiring only
`geocodeRoute`:

```kotlin
package one.otpserverui.api

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class GeocodeRouteTest {
    @Test
    fun `GET geocode returns real candidates from the injected client`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { geocodeRoute(FakeGeocodeClient(listOf(GeocodeCandidate("Langelandsgade, Aarhus, Danmark", 56.1638, 10.1979)))) }
            }
            val response = client.get("/geocode?q=Langelandsg")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val body = Json.decodeFromString<GeocodeResponse>(response.bodyAsText())
            assertThat(body.candidates).hasSize(1)
            assertThat(body.candidates.first().label).isEqualTo("Langelandsgade, Aarhus, Danmark")
        }
    }

    @Test
    fun `GET geocode with no results returns an empty candidates array`() = runTest {
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { geocodeRoute(FakeGeocodeClient(emptyList())) }
            }
            val response = client.get("/geocode?q=zzzznonsense")
            val body = Json.decodeFromString<GeocodeResponse>(response.bodyAsText())
            assertThat(body.candidates).isEmpty()
        }
    }
}

private class FakeGeocodeClient(private val results: List<GeocodeCandidate>) : GeocodeClient {
    override suspend fun search(query: String): List<GeocodeCandidate> = results
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "*.GeocodeRouteTest"`
Expected: FAIL — none of this exists yet.

- [ ] **Step 3: Write `GeocodeRoute.kt`**

```kotlin
package one.otpserverui.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.request.queryParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class GeocodeCandidate(val label: String, val lat: Double, val lon: Double)

@Serializable
data class GeocodeResponse(val candidates: List<GeocodeCandidate>)

interface GeocodeClient {
    suspend fun search(query: String): List<GeocodeCandidate>
}

@kotlinx.serialization.Serializable
private data class AutocompleteRequest(val input: String, val includedRegionCodes: List<String> = listOf("dk"), val languageCode: String = "da")

@kotlinx.serialization.Serializable
private data class AutocompleteResponse(val suggestions: List<SuggestionDto> = emptyList())

@kotlinx.serialization.Serializable
private data class SuggestionDto(val placePrediction: PlacePredictionDto? = null)

@kotlinx.serialization.Serializable
private data class PlacePredictionDto(val placeId: String, val text: FormattedTextDto)

@kotlinx.serialization.Serializable
private data class FormattedTextDto(val text: String)

@kotlinx.serialization.Serializable
private data class PlaceDetailsResponse(val location: LatLngDto)

@kotlinx.serialization.Serializable
private data class LatLngDto(val latitude: Double, val longitude: Double)

/**
 * Real Google Places (New) API — same endpoints/headers bikebus's own
 * `PlacesApi.kt` already calls (Retrofit there, plain Ktor HttpClient here;
 * same wire contract): `POST /v1/places:autocomplete` for suggestions, then
 * `GET /v1/places/{placeId}` with `X-Goog-FieldMask: location` to resolve
 * one candidate's real lat/lon.
 */
class GooglePlacesGeocodeClient(private val httpClient: HttpClient, private val apiKey: String) : GeocodeClient {
    override suspend fun search(query: String): List<GeocodeCandidate> {
        val autocomplete = httpClient.post("https://places.googleapis.com/v1/places:autocomplete") {
            header("X-Goog-Api-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(AutocompleteRequest(input = query))
        }.body<AutocompleteResponse>()

        return autocomplete.suggestions.mapNotNull { it.placePrediction }.map { prediction ->
            val details = httpClient.get("https://places.googleapis.com/v1/places/${prediction.placeId}") {
                header("X-Goog-Api-Key", apiKey)
                header("X-Goog-FieldMask", "location")
            }.body<PlaceDetailsResponse>()
            GeocodeCandidate(prediction.text.text, details.location.latitude, details.location.longitude)
        }
    }
}

fun Routing.geocodeRoute(client: GeocodeClient) {
    get("/geocode") {
        val query = call.queryParameters["q"] ?: ""
        call.respond(GeocodeResponse(client.search(query)))
    }
}
```

`GeocodeRouteTest` (Step 2) exercises the route/DTO contract against
`FakeGeocodeClient`, not `GooglePlacesGeocodeClient` — the real Google call
needs a real, live API key to test against an actual endpoint, which isn't
something a unit test should depend on. `GooglePlacesGeocodeClient` itself
is real, complete code (same wire contract as bikebus's own working
`PlacesApi.kt`), wired into production via Task 16's `main()`, not test
code — its correctness is verified once, manually, against a real key
during deployment (see this plan's own Deployment section, or note it as a
manual verification step when this task is executed).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "*.GeocodeRouteTest"`
Expected: PASS (the fake client, not the real Google one, is exercised).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/one/otpserverui/api/GeocodeRoute.kt backend/src/test/kotlin/one/otpserverui/api/GeocodeRouteTest.kt
git commit -m "Add GET /geocode Google Places proxy (fake-client-tested)"
```

---

### Task 16: Assemble the real production application

**Files:**
- Modify: `backend/src/main/kotlin/one/otpserverui/Main.kt`
- Test: `backend/src/test/kotlin/one/otpserverui/MainStartupTest.kt`

**Interfaces:**
- Consumes: `searchRoute` (Task 13), `dropMeOffRoutes` (Task 14),
  `geocodeRoute`/`GooglePlacesGeocodeClient` (Task 15), `HubCatalog.load`
  (Task 10), `GraphLoader.load` (Task 5).
- Produces: `fun Application.module(engine: RoutingEngine, hubs: List<Hub>, geocodeClient: GeocodeClient)`
  — the one real, complete production application, registering `/health`
  (Task 1) and all three tasks' routes together for the first time; every
  later task (17) and real deployment uses exactly this function, not a
  narrower per-task variant. Also: `Main.kt` reads the real graph file path
  from an environment variable `GRAPH_FILE_PATH` (never hardcoded in
  `main()` itself), and a missing/corrupt file fails startup loudly instead
  of serving a null graph.

Tasks 13-15 deliberately tested their own one route in isolation (a
self-contained `application { routing { theOneRoute(...) } }` block each,
not a shared, still-evolving `module()`) — precisely so this task could be
the single place all three come together, without three earlier tasks each
touching the same function and drifting out of sync with each other (a
real gap this plan's own pre-flight review caught and fixed before Task 1
was ever dispatched).

- [ ] **Step 1: Write the failing test**

```kotlin
package one.otpserverui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MainStartupTest {
    @Test
    fun `a missing graph file fails startup with a clear message`() {
        val exception = assertThrows<IllegalStateException> {
            loadGraphOrFail(java.nio.file.Path.of("does-not-exist.obj"))
        }
        assertThat(exception.message).contains("does-not-exist.obj")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "*.MainStartupTest"`
Expected: FAIL — `loadGraphOrFail` doesn't exist yet.

- [ ] **Step 3: Rewrite `Main.kt` as the real, complete assembly**

```kotlin
package one.otpserverui

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.nio.file.Files
import java.nio.file.Path
import one.otpserverui.api.GeocodeClient
import one.otpserverui.api.GooglePlacesGeocodeClient
import one.otpserverui.api.dropMeOffRoutes
import one.otpserverui.api.geocodeRoute
import one.otpserverui.api.searchRoute
import one.otpserverui.routing.Hub
import one.otpserverui.routing.HubCatalog
import one.otpserverui.routing.RoutingEngine

fun loadGraphOrFail(path: Path): LoadedGraph {
    check(Files.exists(path)) { "Graph file not found: $path" }
    return GraphLoader.load(path)
}

fun main() {
    val graphPath = Path.of(System.getenv("GRAPH_FILE_PATH") ?: error("GRAPH_FILE_PATH environment variable is required"))
    val loaded = loadGraphOrFail(graphPath)
    val engine = RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)
    val hubs = HubCatalog.load()
    val apiKey = System.getenv("GOOGLE_PLACES_API_KEY") ?: error("GOOGLE_PLACES_API_KEY environment variable is required")
    val geocodeClient = GooglePlacesGeocodeClient(io.ktor.client.HttpClient(), apiKey)

    embeddedServer(Netty, port = 8080, module = { module(engine, hubs, geocodeClient) }).start(wait = true)
}

fun Application.module(engine: RoutingEngine, hubs: List<Hub>, geocodeClient: GeocodeClient) {
    install(ContentNegotiation) { json() }
    routing {
        get("/health") { call.respondText("ok") }
        searchRoute(engine, hubs)
        dropMeOffRoutes(engine)
        geocodeRoute(geocodeClient)
    }
}
```

This replaces Task 1's original `fun Application.module()` (no args)
entirely — update Task 1's own `HealthCheckTest` to call
`module(testEngine(), emptyList(), FakeGeocodeClient(emptyList()))`
instead (reusing Task 13's `testEngine()` helper pattern and Task 15's
`FakeGeocodeClient`), so the whole test suite keeps calling one real
function shape, not two.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "*.MainStartupTest"`
Expected: PASS.

- [ ] **Step 5: Run the full backend test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, zero failures, across every module.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/one/otpserverui/Main.kt backend/src/test/kotlin/one/otpserverui/MainStartupTest.kt backend/src/test/kotlin/one/otpserverui/HealthCheckTest.kt
git commit -m "Assemble the real production application (all routes, configurable graph path)"
```

---

## Final Verification

### Task 17: Full backend smoke test across all three modes

**Files:**
- Test: `backend/src/test/kotlin/one/otpserverui/BackendSmokeTest.kt`

- [ ] **Step 1: Write one end-to-end test per mode**

This test uses the real, complete `module(engine, hubs, geocodeClient)`
from Task 16 — the same function `main()` calls — with a real engine/hubs
and a no-op fake geocode client (this test doesn't exercise `/geocode`, so
a real Google API key isn't needed here):

```kotlin
package one.otpserverui

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.io.path.toPath
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import one.otpserverui.api.GeocodeCandidate
import one.otpserverui.api.GeocodeClient
import one.otpserverui.api.NearbyRoutesResponse
import one.otpserverui.api.SearchResponse
import one.otpserverui.routing.Hub
import one.otpserverui.routing.HubCatalog
import one.otpserverui.routing.RoutingEngine
import org.junit.jupiter.api.Test

private class NoOpGeocodeClient : GeocodeClient {
    override suspend fun search(query: String): List<GeocodeCandidate> = emptyList()
}

class BackendSmokeTest {
    private fun realEngine(): RoutingEngine {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)
        return RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)
    }

    private fun realHubs(): List<Hub> = HubCatalog.load()

    @Test
    fun `bring_bike search returns a real result over HTTP`() = runTest {
        testApplication {
            application { module(realEngine(), realHubs(), NoOpGeocodeClient()) }
            val response = client.post("/search") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"mode":"bring_bike","timeMode":"depart_at","originLat":56.171798,"originLon":10.172087,"destinationLat":56.102216,"destinationLon":10.17293,"dateTimeIso":"2026-09-13T14:00:00Z","preferHubs":false}"""
                )
            }
            val body = Json.decodeFromString<SearchResponse>(response.bodyAsText())
            assertThat(body.itineraries).isNotEmpty()
        }
    }

    @Test
    fun `park_and_ride search returns a real result over HTTP`() = runTest {
        testApplication {
            application { module(realEngine(), realHubs(), NoOpGeocodeClient()) }
            val response = client.post("/search") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"mode":"park_and_ride","timeMode":"depart_at","originLat":56.171798,"originLon":10.172087,"destinationLat":56.102216,"destinationLon":10.17293,"dateTimeIso":"2026-09-13T14:00:00Z","preferHubs":false}"""
                )
            }
            val body = Json.decodeFromString<SearchResponse>(response.bodyAsText())
            assertThat(body.itineraries).isNotEmpty()
        }
    }

    @Test
    fun `drop_me_off nearby-routes returns real routes over HTTP`() = runTest {
        testApplication {
            application { module(realEngine(), realHubs(), NoOpGeocodeClient()) }
            val response = client.get("/nearby-routes?lat=56.171798&lon=10.172087&radiusMeters=500")
            val body = Json.decodeFromString<NearbyRoutesResponse>(response.bodyAsText())
            assertThat(body.routes).isNotEmpty()
        }
    }
}
```

- [ ] **Step 2: Run the test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/kotlin/one/otpserverui/BackendSmokeTest.kt
git commit -m "Add a full backend smoke test across search modes"
```

---

## Plan Amendment (2026-10-01): Tasks 18-19

This plan's final whole-branch review (dispatched after Task 17, on the most
capable available model) found that Task 11's Drop-me-off port was
incomplete in a way that matters: bikebus's real "connect to a route" flow
is a full, via-constrained origin→destination search (pick the cheapest
itinerary that actually rides the chosen route, optionally hub-stitch,
*then* find a flag point on the bus's own path) — not just a plain walk
from a client-supplied point, which is all `connectByFlaggingABus` (Task
11) and `POST /connect` (Task 14) currently do. The API as shipped through
Task 17 cannot support the real flow at all: `ConnectRequest` carries no
origin or route id, and `NearbyRouteDto` drops `stopIds`, so a client has
no way to constrain a search to a chosen route in the first place.

The user was presented this finding and chose to build the real feature
now, plus a small fix wave for three other review findings, rather than
rescoping the spec. Tasks 18-19 below implement that. Task 20 is the fix
wave. The rest of the whole-branch review's findings (unbounded resource
use, the vestigial transit-search lock, `/geocode`'s N+1 Details-fetching
and missing rate limiting) are deliberately NOT addressed here — they are
real, but scoped as pre-deployment hardening, out of scope for this plan's
own "complete, independently-testable JSON API backend" boundary. They are
recorded in this plan's ledger as accepted, carried-forward debt.

### Task 18: Port the via-constrained Bring Bike search and the hub/flag-stop decision logic

**Files:**
- Modify: `backend/src/main/kotlin/one/otpserverui/routing/BringBike.kt`
  (add `viaStopIds`/`numItineraries` support to the request this function
  already builds — see Step 1)
- Create: `backend/src/main/kotlin/one/otpserverui/domain/NearbyRoutesFinder.kt`
  additions (this file already exists from Task 11 with only
  `rankCandidates` ported; add the three members Task 11 deliberately
  omitted — see Step 2)
- Create: `backend/src/main/kotlin/one/otpserverui/model/FlagStopInfo.kt`
  (new, adapted from `bikebus/app/src/main/java/one/brj/bikebus/model/FlagStopInfo.kt`
  — plain data class, verbatim copy, package renamed)
- Create: `backend/src/main/kotlin/one/otpserverui/model/FlagStopConnectResult.kt`
  (new, adapted from `bikebus/app/src/main/java/one/brj/bikebus/model/FlagStopConnectResult.kt`
  — plain data class referencing the app-level `Leg` (Task 12), package
  renamed)
- Test: `backend/src/test/kotlin/one/otpserverui/routing/BringBikeViaTest.kt`
- Test: `backend/src/test/kotlin/one/otpserverui/domain/NearbyRoutesFinderTest.kt`

**Interfaces:**
- Consumes: `RoutingEngine`/`streetReach`/`transitSearch`/`toItineraries`/
  `directRoute`/`filter` (Task 6), `HubRouting.trimHubConnector`/`choose`
  (Task 10).
- Produces: `fun bringBike(engine, origin, destination, timeMode, dateTime, viaStopIds: List<String> = emptyList(), numItineraries: Int? = null): List<Itinerary>`
  (extends Task 9's existing signature, backward compatible — every
  existing call site keeps compiling unchanged since both new parameters
  default off), `object NearbyRoutesFinder { ... pickCheapestQualifying(...)
  findSplitForRoute(...) buildFlagStopInfo(...) }`, `data class
  RouteHubSplit(val hub: TransitHub, val routeOnOriginSide: Boolean)` — Task
  19 consumes all of these.

- [ ] **Step 1: Add via-location and numItineraries support to `bringBike`'s request**

Read `bikebus/app/src/main/java/one/brj/bikebus/domain/EmbeddedRequestBuilder.kt`
first — it's the real source `bringBike` (Task 9) was itself adapted from,
and its KDoc documents exactly why/how `viaStopIds`/`numItineraries` are
applied. The real recipe, confirmed against this project's own vendored
`otp-routing` source before writing this task (`VisitViaLocation`,
`FeedScopedId.parse`, `RouteRequestBuilder.withViaLocations`/
`withNumItineraries` are all real, already-vendored APIs — no new
dependency needed):

```kotlin
import org.opentripplanner.core.model.id.FeedScopedId
import org.opentripplanner.routing.api.request.via.VisitViaLocation

fun bringBike(
    engine: RoutingEngine,
    origin: WgsCoordinate,
    destination: WgsCoordinate,
    timeMode: TimeMode,
    dateTime: Instant,
    viaStopIds: List<String> = emptyList(),
    numItineraries: Int? = null,
): List<Itinerary> {
    val requestBuilder = engine.requestBuilder()
        .withFrom(GenericLocation.fromCoordinate(origin))
        .withTo(GenericLocation.fromCoordinate(destination))
        .withDateTime(dateTime)
        .apply { if (timeMode == TimeMode.ARRIVE_BY) withArriveBy(true) }
        .withJourney { it.withAllModes(StreetMode.BIKE) }
        .withSearchWindow(Duration.ofHours(12))
        .withPreferences { preferences -> preferences.withTransfer { transfer -> transfer.withCost(3600) } }
    if (viaStopIds.isNotEmpty()) {
        requestBuilder.withViaLocations(listOf(VisitViaLocation(null, null, FeedScopedId.parse(viaStopIds), null)))
    }
    if (numItineraries != null) {
        requestBuilder.withNumItineraries(numItineraries)
    }
    val request = requestBuilder.buildRequest()

    val accessEgressDuration = request.preferences().street().accessEgress().maxDuration().valueOf(StreetMode.BIKE)
    val access = engine.streetReach(origin, StreetMode.BIKE, accessEgressDuration, ReachDirection.ACCESS, request)
    val egress = engine.streetReach(destination, StreetMode.BIKE, accessEgressDuration, ReachDirection.EGRESS, request)

    val transitItineraries: List<Itinerary> = if (access.isEmpty() || egress.isEmpty()) {
        emptyList()
    } else {
        val paths = engine.transitSearch(access, egress, request)
        engine.toItineraries(paths, request)
    }

    // A via-constrained search must never fall back to a direct (non-transit) route -- bikebus's
    // own planEmbeddedItineraries does the same (directItineraries forced empty whenever
    // viaStopIds is non-empty), since "ride via this stop" has no meaning for a direct route.
    val directItineraries: List<Itinerary> = if (viaStopIds.isEmpty()) {
        engine.directRoute(origin, destination, StreetMode.BIKE, request)
    } else {
        emptyList()
    }

    return engine.filter(directItineraries + transitItineraries, request)
}
```

This is the existing `bringBike` body (already correct, per Task 9's own
fix round) with four additions: the two new parameters, the
`withViaLocations`/`withNumItineraries` conditional calls, and the
`viaStopIds.isEmpty()` guard around `directItineraries` (previously
unconditional). Every other line is unchanged. Confirm `FeedScopedId.parse`
really takes a `Collection<String>` and returns `List<FeedScopedId>` (not,
e.g., a single string) by reading the real vendored source yourself before
treating this as final — this plan's own history has had real signature
errors in nearly every task, verify rather than trust.

- [ ] **Step 2: Port NearbyRoutesFinder's remaining three members, adapted**

Read `bikebus/app/src/main/java/one/brj/bikebus/domain/NearbyRoutesFinder.kt`'s
real source (Task 11's own ledger entry already quotes it in full) for
`pickCheapestQualifying`/`findSplitForRoute`/`buildFlagStopInfo`/
`RouteHubSplit` — Task 11 deliberately did not port these three because
they depend on bikebus's own app-level `model.Leg` type
(`routeGtfsId`/`durationSeconds`/`toLat`/`toLon`/`distanceMeters` fields),
which this project doesn't have at the routing-engine layer (real OTP
`Leg` is used there instead) — but this project's Task 12 model, Itinerary/
Leg (app-level, `one.otpserverui.model.Itinerary`/`Leg`), already exists and
has the same field shape bikebus's model does. Adapt these three functions
to operate on **this project's own app-level `one.otpserverui.model.Leg`**
(not real OTP's `Leg`) — unlike `HubRouting.kt` (Task 10), which had to
adapt to real OTP types because it runs inside the routing-engine tier
before any app-level conversion happens, `pickCheapestQualifying`/
`findSplitForRoute`/`buildFlagStopInfo` in bikebus already operate on
post-`toAppItinerary()` data (see `connectViaRoute`'s real call site:
`planEmbedded(...).map { it.legs }` where `legs: List<Leg>` is the
app-level type) — so the adaptation here is purely a package rename plus
possibly nullable-field differences (check `one.otpserverui.model.Leg`'s
real fields against bikebus's `model.Leg`'s fields, particularly
`routeGtfsId`'s nullability, before treating field names as identical).

Add to the existing `backend/src/main/kotlin/one/otpserverui/domain/NearbyRoutesFinder.kt`
(from Task 11, currently only `rankCandidates`):

```kotlin
data class RouteHubSplit(val hub: TransitHub, val routeOnOriginSide: Boolean)

// (inside the existing NearbyRoutesFinder object)

private const val NEAR_STOP_RADIUS_METERS = 1_000.0

fun pickCheapestQualifying(itineraries: List<List<Leg>>, targetRouteGtfsId: String): List<Leg>? =
    itineraries
        .filter { legs -> legs.any { it.routeGtfsId == targetRouteGtfsId } }
        .minByOrNull { legs -> legs.sumOf { it.durationSeconds } }

fun findSplitForRoute(legs: List<Leg>, hubs: List<TransitHub>, targetRouteGtfsId: String): RouteHubSplit? {
    val routeLegIndex = legs.indexOfFirst { it.routeGtfsId == targetRouteGtfsId }
    if (routeLegIndex == -1) return null
    for (i in 0 until legs.size - 1) {
        val alight = legs[i]
        val board = legs[i + 1]
        if (!isTransit(alight.mode) || !isTransit(board.mode)) continue
        val nearest = hubs.minByOrNull { haversineMeters(it.lat, it.lon, alight.toLat, alight.toLon) } ?: continue
        if (haversineMeters(nearest.lat, nearest.lon, alight.toLat, alight.toLon) <= NEAR_STOP_RADIUS_METERS) {
            return RouteHubSplit(hub = nearest, routeOnOriginSide = routeLegIndex <= i)
        }
    }
    return null
}

fun buildFlagStopInfo(flagLat: Double, flagLon: Double, officialLeg: Leg, directLeg: Leg): FlagStopInfo =
    FlagStopInfo(
        flagLat = flagLat, flagLon = flagLon,
        officialFinalLegDistanceMeters = officialLeg.distanceMeters,
        officialFinalLegDurationSeconds = officialLeg.durationSeconds,
        flagStopDistanceMeters = directLeg.distanceMeters,
        flagStopDurationSeconds = directLeg.durationSeconds
    )

private fun isTransit(mode: String) = mode != "BICYCLE" && mode != "WALK"
```

`haversineMeters` already exists in this file's package (`domain/Geo.kt`,
ported in Task 11) — reuse it, don't redefine it. `TransitHub` (Task 10)
and `FlagStopInfo`/`Leg` (this task's Step 3 / Task 12) must resolve by
import.

- [ ] **Step 3: Port the two model files verbatim**

```bash
cp /c/Users/bru/spare-source/bikebus/app/src/main/java/one/brj/bikebus/model/FlagStopInfo.kt backend/src/main/kotlin/one/otpserverui/model/
cp /c/Users/bru/spare-source/bikebus/app/src/main/java/one/brj/bikebus/model/FlagStopConnectResult.kt backend/src/main/kotlin/one/otpserverui/model/
sed -i 's/one\.brj\.bikebus\.model/one.otpserverui.model/g' backend/src/main/kotlin/one/otpserverui/model/FlagStopInfo.kt backend/src/main/kotlin/one/otpserverui/model/FlagStopConnectResult.kt
```

- [ ] **Step 4: Write tests**

For `bringBike`'s new via-constraint: find a real route and its real
`stopIds` in this project's fixture graph that `bringBike`'s existing
baseline query (the Aarhus query used throughout this repo) already rides
through as a transit leg — confirm by reading the itinerary's legs
directly rather than guessing a route id — then assert that a via-
constrained `bringBike` call with that route's `stopIds` and
`numItineraries = 20` still returns at least one itinerary whose legs
include that route, and that an obviously-wrong/nonexistent via stop id
returns an empty list (not a crash). For `NearbyRoutesFinder`'s three new
functions: `pickCheapestQualifying`/`findSplitForRoute` are pure functions
over `List<Leg>` — construct small synthetic `one.otpserverui.model.Leg`
instances directly (it's a plain data class, no OTP graph/fixture needed)
the same way this plan's own `HubRoutingTest.kt` uses synthetic OTP
`StreetLeg`s for `HubRouting.trimHubConnector` — don't require a live
fixture search for logic this cheap to test synthetically, per this plan's
own established precedent (see this plan's ledger, Task 10's fix round).

- [ ] **Step 5: Run tests, then commit**

```bash
./gradlew :backend:test --tests "*.BringBikeViaTest" --tests "*.NearbyRoutesFinderTest"
git add backend/src/main/kotlin/one/otpserverui/routing/BringBike.kt backend/src/main/kotlin/one/otpserverui/domain/NearbyRoutesFinder.kt backend/src/main/kotlin/one/otpserverui/model/FlagStopInfo.kt backend/src/main/kotlin/one/otpserverui/model/FlagStopConnectResult.kt backend/src/test/kotlin/one/otpserverui/routing/BringBikeViaTest.kt backend/src/test/kotlin/one/otpserverui/domain/NearbyRoutesFinderTest.kt
git commit -m "Port via-constrained Bring Bike search and the flag-stop decision logic"
```

---

### Task 19: Real connect-to-route flow and API rewiring

**Files:**
- Modify: `backend/src/main/kotlin/one/otpserverui/routing/DropMeOff.kt`
  (replace `connectByFlaggingABus` with the real `connectToRoute` flow)
- Modify: `backend/src/main/kotlin/one/otpserverui/api/DropMeOffRoute.kt`
  (new `ConnectRequest` shape, `NearbyRouteDto` gains `stopIds`)
- Modify: `backend/src/test/kotlin/one/otpserverui/api/DropMeOffRouteTest.kt`
  (update the `/connect` test for the new request shape)
- Test: `backend/src/test/kotlin/one/otpserverui/routing/ConnectToRouteTest.kt`

**Interfaces:**
- Consumes: `bringBike` (Task 9/18, with via support), `NearbyRoutesFinder`
  (Task 11/18), `HubRouting.trimHubConnector`/`choose` (Task 10),
  `nearbyRoutes` (Task 11).
- Produces: `fun connectToRoute(engine: RoutingEngine, hubs: List<TransitHub>, origin: WgsCoordinate, destination: WgsCoordinate, routeGtfsId: String, routeStopIds: List<String>, timeMode: TimeMode, dateTime: Instant, preferHubs: Boolean): FlagStopConnectResult?`
  — replaces `connectByFlaggingABus` entirely (delete it; nothing else in
  this repo calls it after this task).

- [ ] **Step 1: Read bikebus's real orchestration one more time**

Read `bikebus/app/src/main/java/one/brj/bikebus/TripViewModel.kt` lines
570-698 (`connectToRoute`, `connectViaRoute`, `buildStitchedNearbyItinerary`,
`confirmFlagStop`) — quoted in full in this plan's amendment research (see
the SDD ledger's pre-Task-18 entry) — before writing this task's code. The
real flow, adapted to this project's stateless-HTTP shape (bikebus has
`state.itineraries` as an already-fetched baseline from the screen's own
prior `/search` call; this project's `/connect` endpoint has no session
state, so it must compute its own fresh baseline — **Ruling, made when this
amendment was written**: call `bringBike` once more with no via constraint,
same origin/destination/timeMode/dateTime, to get a comparable baseline
cost, mirroring what `state.itineraries`' cheapest entry already represents
in bikebus. Cost if wrong: one extra real search per `/connect` call,
which is already an expensive, low-frequency, user-triggered endpoint —
negligible.):

```kotlin
fun connectToRoute(
    engine: RoutingEngine,
    hubs: List<TransitHub>,
    origin: WgsCoordinate,
    destination: WgsCoordinate,
    routeGtfsId: String,
    routeStopIds: List<String>,
    timeMode: TimeMode,
    dateTime: Instant,
    preferHubs: Boolean,
): FlagStopConnectResult? {
    val plainLegs = connectViaRoute(engine, origin, destination, routeGtfsId, routeStopIds, timeMode, dateTime) ?: return null

    var finalLegs = plainLegs
    var hubName: String? = null
    if (preferHubs) {
        val split = NearbyRoutesFinder.findSplitForRoute(plainLegs, hubs, routeGtfsId)
        if (split != null) {
            val stitched = runCatching {
                buildStitchedNearbyItinerary(engine, origin, destination, split.hub, routeGtfsId, routeStopIds, split.routeOnOriginSide, timeMode, dateTime)
            }.getOrNull()
            if (stitched != null && stitched.any { it.routeGtfsId == routeGtfsId }) {
                val (chosen, chosenHubName) = HubRouting.choose(
                    listOf(toOtpLikeCost(plainLegs)), toOtpLikeCost(stitched)?.let { it }, split.hub.name,
                )
                // see Step 2 for why HubRouting.choose's real signature needs adapting here
            }
        }
    }

    val baseline = bringBike(engine, origin, destination, timeMode, dateTime)
    val extraRideSeconds = baseline.minOfOrNull { it.totalDuration().seconds.toDouble() }
        ?.let { baselineCost -> finalLegs.sumOf { it.durationSeconds } - baselineCost }

    val busLeg = finalLegs.firstOrNull { it.routeGtfsId == routeGtfsId }
    val flagStopInfo = busLeg?.let { confirmFlagStop(engine, it, finalLegs.last(), destination, timeMode, dateTime) }

    return FlagStopConnectResult(legs = finalLegs, flagStopInfo = flagStopInfo, extraRideSeconds = extraRideSeconds, hubName = hubName)
}
```

**This sketch has a real, known gap the implementer must resolve, not
paper over**: `HubRouting.choose(baseline: List<Itinerary>, stitched:
Itinerary?, hubName: String): Pair<List<Itinerary>, String?>` (Task 10)
operates on real OTP `Itinerary`, but `plainLegs`/`stitched` here are
app-level `List<Leg>` (post-`toAppItinerary()`, matching bikebus's own
`connectToRoute` which does the same comparison on app-level `Itinerary(legs
= plainLegs)` — bikebus has its OWN app-level `Itinerary`/`totalDurationSeconds`
to compare with, this project's `HubRouting.choose` does not operate on the
app-level type at all. Resolve this by either (a) writing a small,
app-level-`Leg`-based equivalent of `HubRouting.choose`'s cost-comparison
logic directly in this function (it's a short function — `MAX_ACCEPTABLE_DETOUR_SECONDS`
based comparison of two duration sums — not worth forcing through the
OTP-native `HubRouting.choose`), or (b) another approach you judge sound.
Do not force app-level `List<Leg>` through a function typed for real OTP
`Itinerary` via a hack — pick a real, type-correct design and document why.

- [ ] **Step 2: Write `connectViaRoute`, `buildStitchedNearbyItinerary`, `confirmFlagStop`**

Port these three following the same adaptation discipline as Step 1 — read
bikebus's real versions (lines 612-624, 630-671, 673-698 of
`TripViewModel.kt`) and adapt call-by-call to this project's real APIs:
`connectViaRoute` calls `bringBike(..., viaStopIds = routeStopIds,
numItineraries = 20)` (the real `CONNECT_BATCH_SIZE` value, confirmed from
bikebus's source — hardcode `20` directly, a named constant if you prefer,
this project has no equivalent shared constants file) then
`NearbyRoutesFinder.pickCheapestQualifying(...)`.
`buildStitchedNearbyItinerary` mirrors `Step 1`'s own
`bringBikeWithHubPreference`-style DEPART_AT/ARRIVE_BY branching (already
in `SearchRoute.kt`, Task 13) but with one side of the split staying
via-constrained to `routeStopIds` depending on `routeOnOriginSide` — reuse
`HubRouting.trimHubConnector`, but note it operates on real OTP `List<Leg>`
(Task 10), not this project's app-level `Leg` — **confirm which `Leg` type
each function in this chain actually needs before writing it**; this is
exactly the kind of type-boundary question Step 1's gap also raises, and
getting the app-level-vs-OTP-native boundary right throughout this whole
task is the main risk here, not the control flow itself (which is already
fully specified by bikebus's real, working source).

`confirmFlagStop`'s real version calls `decodePolyline`/
`closestPointOnPolyline` (ported in Task 11, `domain/Geo.kt`/`Polyline.kt`)
against the matched bus leg's `legGeometryPoints` (confirm this field
exists on this project's app-level `Leg` — check `Itinerary.kt`, Task 12),
then plans a short direct route from the flag point to the destination
using **the final leg's own real mode** (`finalLeg.mode`, a string —
confirm how to map this back to a real `StreetMode` for `engine.directRoute`'s
mode parameter; bikebus's real version only ever reaches this with "WALK"
or "BICYCLE" per its own guard `if (finalLeg.mode != "WALK" && finalLeg.mode
!= "BICYCLE") return null` — port that same guard), not the hardcoded
`StreetMode.WALK` the old `connectByFlaggingABus` used (this was one of
the whole-branch review's own findings).

- [ ] **Step 3: Update the API layer**

```kotlin
@Serializable
data class NearbyRouteDto(val routeGtfsId: String, val routeShortName: String?, val stopIds: List<String>, val distanceMeters: Double)

@Serializable
data class ConnectRequest(
    val originLat: Double,
    val originLon: Double,
    val destinationLat: Double,
    val destinationLon: Double,
    val routeGtfsId: String,
    val routeStopIds: List<String>,
    val timeMode: String,
    val dateTimeIso: String,
    val preferHubs: Boolean = false,
)

@Serializable
data class FlagStopInfoDto(
    val flagLat: Double, val flagLon: Double,
    val officialFinalLegDistanceMeters: Double, val officialFinalLegDurationSeconds: Double,
    val flagStopDistanceMeters: Double, val flagStopDurationSeconds: Double,
)

@Serializable
data class ConnectResponse(val itinerary: ItineraryDto, val flagStopInfo: FlagStopInfoDto?, val extraRideSeconds: Double?, val hubName: String?)
```

`GET /nearby-routes` now includes `stopIds` in each `NearbyRouteDto` (so a
client can carry them straight into a later `/connect` call's
`routeStopIds` — this is the real fix for the API-shape gap the whole-branch
review found). `POST /connect` takes the new `ConnectRequest`, calls
`connectToRoute(...)`, and on a null result responds `422
SearchErrorResponse("unreachable")` (unchanged from before); on success,
maps the real `FlagStopConnectResult` to `ConnectResponse` (the itinerary's
`legs`/`exceedsBikeLimit`/`hasLongWalkEgress` map through `ItineraryDto`
exactly as `/search`/the old `/connect` already did — construct a throwaway
app-level `Itinerary(legs = result.legs)` to reuse the existing mapping
code, or extract a small shared `List<Leg>.toItineraryDto()` helper if that
reads cleaner. Validate `timeMode` the same way `/search` does
(`"arrive_by"` vs. anything else defaults to `depart_at` — or, better,
reuse this task's Task 20 sibling fix for `/search`'s own
`timeMode`-validation gap if Task 20 lands first; check the ledger for
whether Task 20 has already run before writing this).

- [ ] **Step 4: Write tests, then commit**

Update `DropMeOffRouteTest.kt`'s existing `/connect` test for the new
request/response shape (a real end-to-end query: use `GET /nearby-routes`
first to find a real route+stopIds near the known Aarhus origin, then feed
that route's real `routeGtfsId`/`stopIds` into `/connect` — don't
hand-guess a route id). Add `ConnectToRouteTest.kt` exercising
`connectToRoute` directly (not just over HTTP) for: a real route that
exists and is reachable (non-null result, real flag-stop info), and a
`routeGtfsId` that doesn't appear on any reachable itinerary (returns
null, not a crash) — mirroring `ParkAndRideFinderTest`'s own
"honest empty result" precedent (Task 7/8).

```bash
./gradlew test
git add backend/src/main/kotlin/one/otpserverui/routing/DropMeOff.kt backend/src/main/kotlin/one/otpserverui/api/DropMeOffRoute.kt backend/src/test/kotlin/one/otpserverui/api/DropMeOffRouteTest.kt backend/src/test/kotlin/one/otpserverui/routing/ConnectToRouteTest.kt
git commit -m "Implement the real connect-to-route flow (via-constrained search, hub-stitching, flag-stop decision)"
```

---

### Task 20: Fix wave — hub-stitching detection bug, Park & Ride arrive_by, error-handling hardening

**Files:**
- Modify: `backend/src/main/kotlin/one/otpserverui/routing/HubRouting.kt`
  (fix `findHubSplit`'s short-leg blind spot — see Step 1)
- Modify: `backend/src/main/kotlin/one/otpserverui/api/SearchRoute.kt`
  (reject `park_and_ride` + `arrive_by`; validate `timeMode` the same way
  `mode` is already validated)
- Modify: `backend/src/main/kotlin/one/otpserverui/Main.kt` (install a
  `StatusPages` plugin)
- Modify: `backend/src/main/kotlin/one/otpserverui/api/GeocodeRoute.kt`
  (reject an empty/missing `q` before calling Google; let a real Google
  error surface as a typed JSON error instead of an uncaught exception)
- Test: extend `SearchRouteTest.kt`, `GeocodeRouteTest.kt`, add
  `HubRoutingTest.kt` cases.

This task responds to three of the final whole-branch review's findings
the user asked to fix now (a fourth, `/geocode`'s N+1 Details-fetching and
missing rate-limit, is deliberately left as ledgered pre-deployment debt,
per the user's own choice — do not attempt it here).

- [ ] **Step 1: Fix `findHubSplit`'s short-leg blind spot**

The whole-branch review's real finding: `HubRouting.findHubSplit` (Task 10)
only matches a transfer when BOTH adjacent legs are `isTransitLeg` — but a
real transfer between two different stops always has a short street
(bike/walk) leg between them, which `findHubSplit` sees and skips, so it
never detects the vast majority of real station transfers. bikebus's own
version only works because it runs on itinerary data that's already been
through `dropTinyLegs` (which removes any leg under 150m, collapsing the
short transfer hop so the two transit legs become directly adjacent) — a
post-processing step this project never ported (there is no
`ItineraryPostProcessing.kt`/`dropTinyLegs` here; `HubRouting.kt`'s own
KDoc already flags this exact gap as a comment, written when it was
believed not to matter — Task 12 then added real post-processing precedent
via `ItineraryPostProcessing.kt`'s `dropTinyLegs`, ported for
`OtpItineraryMapper`'s own use, but nobody revisited `HubRouting.kt`'s
comment or logic after that).

Fix `findHubSplit` to skip over short (sub-150m, matching `dropTinyLegs`'s
own real threshold — confirm the exact real value in
`domain/ItineraryPostProcessing.kt`, Task 12, before hardcoding a new
constant here) non-transit legs when deciding whether two transit legs are
"adjacent", rather than requiring literal list-adjacency:

```kotlin
fun findHubSplit(hubs: List<TransitHub>, itinerary: Itinerary): TransitHub? {
    val transitLegs = itinerary.legs().filter { it.isTransitLeg || it.distanceMeters() >= SHORT_LEG_THRESHOLD_METERS }
    // ... adjust the loop to walk `transitLegs` instead of `itinerary.legs()` directly, so a
    // short connector leg between two transit legs no longer breaks their adjacency.
}
```

(This is a sketch, not exact final code — the real fix needs the loop
logic rewritten carefully so it still correctly identifies which hub to
split at and doesn't accidentally change `trimHubConnector`'s own,
separate, still-correct 50m connector-trimming logic, which is unrelated
and must not be touched.) Write a synthetic-leg unit test (the same
`StreetLeg.of()`-based technique `HubRoutingTest.kt` already established in
Task 10's fix round) proving the fix: construct an itinerary with
`[transit-leg, short-connector-leg(<150m, non-transit), transit-leg]` near
a cataloged hub, and confirm `findHubSplit` now returns that hub (it would
have returned `null` before this fix — assert this is a real behavior
change, not a no-op).

- [ ] **Step 2: Reject Park & Ride + arrive_by**

In `SearchRoute.kt`'s `/search` handler, before dispatching to
`ParkAndRideFinder.search` (which only ever accepts a departure `Instant`
and silently treats it as such regardless of `request.timeMode`): if
`request.mode == "park_and_ride" && request.timeMode == "arrive_by"`,
respond `400 SearchErrorResponse("unsupported_time_mode")` and return,
mirroring bikebus's own real guard (`planParkAndRide`'s KDoc: "ARRIVE_BY
isn't supported... `search()` rejects that combination before
`fetchItineraries` is ever called"). Also validate `request.timeMode`
itself the same way `request.mode` is already validated — any value other
than `"depart_at"`/`"arrive_by"` should be a typed 400
(`"invalid_time_mode"` or similar), not silently treated as depart-at.
Add tests for both: a `park_and_ride`+`arrive_by` request returns 400, and
a request with `timeMode: "nonsense"` returns 400.

- [ ] **Step 3: Install a `StatusPages` plugin and harden `/geocode`'s error path**

In `Main.kt`'s `module()`, install `io.ktor.server.plugins.statuspages.StatusPages`
covering, at minimum: `NumberFormatException`/`IllegalStateException` (the
`checkNotNull`/`.toDouble()` failures on `/nearby-routes`'s query params) →
400 with a typed error body; `DateTimeParseException` on `/connect`'s
`dateTimeIso` → 400 `"invalid_request"` (matching `/search`'s own existing
handling); any exception thrown by `GooglePlacesGeocodeClient.search`
(a failed/timed-out/4xx-5xx Google call) → a typed JSON error (e.g. `502
SearchErrorResponse("geocode_unavailable")`), not a raw 500. In
`GeocodeRoute.kt`'s `geocodeRoute`, reject an empty/blank `q` before ever
calling `client.search(...)` (`call.respond(GeocodeResponse(emptyList()))`
immediately, matching the spec's existing "no results" contract, with zero
calls to Google for an empty query). Add tests: a malformed `lat` on
`/nearby-routes` returns a typed 400 (not a raw 500), an empty `q` on
`/geocode` returns an empty candidate list with zero calls into the
injected `GeocodeClient` (use a spy/counting fake to prove this, not just
an empty-list assertion), and a `GeocodeClient` that throws produces a
typed error response, not an unhandled exception propagating out of the
test.

- [ ] **Step 4: Run the full suite, then commit**

```bash
./gradlew test
git add backend/src/main/kotlin/one/otpserverui/routing/HubRouting.kt backend/src/main/kotlin/one/otpserverui/api/SearchRoute.kt backend/src/main/kotlin/one/otpserverui/Main.kt backend/src/main/kotlin/one/otpserverui/api/GeocodeRoute.kt backend/src/test/
git commit -m "Fix hub-detection blind spot, reject unsupported Park & Ride arrive_by, harden error handling"
```

---

This plan's scope ends here — a complete, independently-testable JSON API
backend covering the full feature set the spec promises (Park & Ride,
Bring Bike with hub preference, Drop-me-off's real two-step nearby-routes/
connect flow, geocoding). The frontend (Bun/HTML/CSS/TS) and pre-deployment
hardening (resource bounds, the vestigial transit-search lock, `/geocode`
rate-limiting/N+1 fetching) are separate follow-up work, per this plan's
own header note, the spec's own sequencing guidance, and the final
whole-branch review's own explicit scoping recommendation.
