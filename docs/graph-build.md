# Building a graph.obj (throwaway builder, Task 4)

This documents the **throwaway** graph-building project used once, by Task 4 of the
`2026-09-30-backend-json-api` plan, to produce
`otp-routing/src/test/resources/tiny-fixture-graph.obj` -- a real, version-compatible serialized
OTP graph used as a test fixture. It is not part of the `otp-server-ui` Gradle reactor and is
**never committed** (`.tools/` is gitignored). This doc exists so the same approach can be
reproduced later without re-deriving it, and as the reference for the eventual production
Denmark-wide graph-building pipeline (explicitly out of scope here -- see "Not done here" below).

## Why a separate throwaway project, not real OTP's own `application`/`otp-shaded` build

Real OTP's own `application/pom.xml` unconditionally depends on `google-cloud-storage` and
`google-cloud-pubsub` (an optional GCS `DataSource` backend and an optional SIRI/Pubsub real-time
updater -- neither used by this project), which pull in `com.google.cloud:libraries-bom` -- a
version-alignment BOM covering essentially every GCP service (400+ sub-BOMs). Resolving it took
over 30 minutes even through the JetBrains Maven mirror. Neither `GraphBuilder.java` nor the real
`graph_builder.*`/`gtfs.*`/`osm.*` packages reference Google Cloud at all, so a separate project
vendoring just the graph-building slice avoids that resolution entirely. Elevation/DEM support
(`graph_builder.module.ned.*`, pulling in GeoTools/JAI) is likewise skipped -- confirmed optional,
not referenced by `GraphBuilder.java` itself, only by the config-driven wiring layer this driver
bypasses (see below).

## Where it lives

`.tools/graph-builder/` -- its own root: `settings.gradle.kts` + `build.gradle.kts` + `gradlew`
(a copy of this repo's own wrapper), depending on this repo's own already-built module jars as
flat `files(...)` dependencies (`otp-utils`, `otp-domain-core`, `otp-astar`, `otp-street`,
`otp-raptor`, `otp-routing` -- built via `./gradlew :otp-utils:jar :otp-domain-core:jar
:otp-astar:jar :otp-street:jar :otp-raptor:jar :otp-routing:jar` from this repo's own root first).
Dependency resolution is routed through the same JetBrains mirror as this repo's own root
`settings.gradle.kts`.

**Toolchain note:** the vendored source uses Java 21+ language features (unnamed lambda
parameters `_`, pattern matching in `switch`) that real upstream OTP compiles at `--release 25`.
This machine's default JDK is 17; the throwaway project's `build.gradle.kts` sets
`java.toolchain.languageVersion = 26` and Gradle auto-detected an installed JDK 26 at
`C:/Users/bru/.jdks/openjdk-26.0.1` (registered via `gradle.properties`'
`org.gradle.java.installations.paths`) -- the Gradle *daemon* itself still runs on JDK 17; only
the toolchain used for compilation is JDK 26.

## Step 1: the vendored source slice (~223 files)

All copied verbatim from the exact upstream commit `61a3af67983e5776d8c0fced23839086bb543a59`
(same commit used for everything else in this plan) into
`.tools/graph-builder/src/main/java/org/opentripplanner/`, **except** where noted below:

- `graph_builder/**` (upstream has 144 files) -- copied whole, then pruned:
  - `graph_builder/module/ned/**`, `graph_builder/services/ned/**` (13 files): elevation, skipped
    per the task brief (confirmed optional, GeoTools/JAI-heavy).
  - `graph_builder/issue/api/**`: 4 of upstream's 13 files (`DataImportIssue`,
    `DataImportIssueStore`, `DataImportIssueSummary`, `NoopDataImportIssueStore`) were **already**
    vendored into `otp-routing` by Task 3 -- reused from there (via the `otp-routing.jar`
    dependency), not re-copied. The other 9 (`Issue`, `OsmUrlGenerator`, and 7 more) were needed
    here and copied in.
  - `graph_builder/issue/report/**`: unused (only referenced by the Dagger config layer below;
    confirmed via grep that nothing outside it references this package).
  - `graph_builder/configure/GraphBuilderModule.java` (a Dagger `@Module`, confusingly
    same-named as the real `graph_builder/model/GraphBuilderModule.java` *interface*, which
    **is** kept) and `graph_builder/module/configure/**` (`GraphBuilderFactory`/
    `GraphBuilderModules`, the Dagger-generated factory wiring): **not vendored at all**. This
    driver constructs the needed `GraphBuilderModule` instances directly (Step 3) instead of going
    through OTP's real config-driven wiring layer, per the task brief.
  - `GraphBuilder.java`'s real `create(BuildConfig, GraphBuilderDataSources, ...)` factory method
    (which used that Dagger layer) was **deleted** from this copy; `addModule(...)` was changed
    from `private` to `public` so the driver can call it directly; a new public
    `setHasTransitData(boolean)` replaces what `create()` used to set internally. Everything else
    in `GraphBuilder.java` is untouched.
  - `GraphBuilderDataSources.java` (root file, config-driven data-source discovery) and
    `graph_builder/model/{ConfiguredDataSource,ConfiguredCompositeDataSource,DataSourceConfig}.java`
    are present in the copy but unused by the driver (kept only because `GtfsFeedParameters`
    happens to implement `DataSourceConfig`).
  - `GraphStats.java` (unreferenced standalone CLI reporting tool): not vendored.
  - `graph_builder/module/OsmBoardingLocationsModule.java` and
    `graph_builder/module/transfer/**` (`DirectTransferGenerator`/`DirectTransferAnalyzer`): not
    vendored / not used by the driver -- see "Known simplifications" below.
- `gtfs/**` (55 files) -- copied whole, then pruned:
  - `gtfs/mapping/{FareAttributeMapper,FareRuleMapper,FareLegRuleMapper,FareTransferRuleMapper,
    TimeframeMapper}.java` deleted (fare mapping lives in the excluded `ext.fares.*` sandbox
    package); `GTFSToTransitDataImportMapper.java` and `GtfsModule.java` were edited to drop the
    fare/flex-trip code paths that called them (see "Pruned upstream behaviour" below).
- `osm/**` (55 files) -- copied whole, then pruned:
  - `osm/tagmapping/{AtlantaMapper,ConstantSpeedMapper,FinlandMapper,GermanyMapper,HamburgMapper,
    HoustonMapper,NorwayMapper,PortlandMapper,TwinCitiesMapper,UKMapper}.java` (10 country-specific
    OSM tag mappers): deleted -- this driver only ever builds a Danish extract, so
    `OsmTagMapperSource` was trimmed down to a single `DEFAULT` entry (`new OsmTagMapper()`).
- A handful of files **outside** those three trees, needed transitively and not yet vendored
  anywhere in `otp-server-ui`: `framework/collection/TroveUtils.java`,
  `framework/functional/FunctionUtils.java`, `streetadapter/VertexFactory.java`,
  `model/{TransitDataImport,TripStopTimes}.java`, `model/impl/{TransitDataImportBuilder,
  DefaultTransitDataImport,MultipleCalendarsForServiceIdException}.java`,
  `model/calendar/{ServiceCalendar,ServiceCalendarDate,impl/CalendarServiceDataFactoryImpl}.java`.
  All copied verbatim except `TransitDataImportBuilder`/`DefaultTransitDataImport` (see below).
- `datastore/api/{DataSource,CompositeDataSource,FileType}.java` and
  `datastore/file/{AbstractFileDataSource,FileDataSource,ZipFileDataSource,ZipFileEntryDataSource,
  ZipFileEntryParent}.java`: the **real**, full upstream `DataSource`/`CompositeDataSource`
  abstraction (file and zip-backed), copied verbatim. This intentionally shadows (same fully-
  qualified name, richer shape) the much smaller 7-method `DataSource` *stub* Task 3 added to
  `otp-routing` -- safe here because it's confined entirely to this throwaway project's own
  classpath (Gradle puts a project's own compiled classes ahead of its dependency jars), and
  `DataSource`/`CompositeDataSource` instances are only ever used transiently during the build --
  never embedded in the saved `graph.obj` (see "otp-routing changes" below for why that
  distinction matters).

### Pruned upstream behaviour (flex + fares)

Two upstream sandbox features are threaded through `graph_builder`/`gtfs` code that otherwise
stayed in scope, and were surgically removed rather than vendoring their whole `ext.flex.*`/
`ext.fares.*` trees:

- **Fares**: `GtfsModule.forTest(...)` (test-only helper, deleted), the
  `fareServiceFactory.processGtfs(fareRulesData)` call in `GtfsModule.buildGraph()`, and all of
  `GTFSToTransitDataImportMapper`'s fare-rule/fare-product/timeframe/stop-area mapping were
  removed. `FareServiceFactory` stays the empty-marker stub Task 3 already added to `otp-routing`;
  the driver just never calls anything on it.
- **Flex**: `FlexTripsMapper`/`OTPFeature.FlexRouting` handling in `GtfsModule`, the
  `EntityById<FlexTrip<?,?>>`-typed `flexTripsById` field in `TransitDataImportBuilder`/
  `DefaultTransitDataImport` (changed to a plain `Map<FeedScopedId, FlexTrip>`/
  `Collection<FlexTrip>` -- the `otp-routing` `FlexTrip` stub isn't generic and doesn't extend
  `TransitEntity`, `EntityById`'s bound), and three call sites of static helper methods the stub
  doesn't declare (`FlexTrip.containsFlexStops(...)`, `.isFlexStop(...)` in
  `ValidateAndInterpolateStopTimesForEachTrip`/`GenerateTripPatternsOperation`/
  `GeometryProcessor`, all replaced with a literal `false` -- correct, since no flex data is ever
  built) were all pruned. `StreetLinkerModule.getStopLocationsUsedForFlexTrips(...)` was
  simplified to always return an empty set for the same reason.

One more small, unrelated fix: `LocationMapper.doMap(...)`'s `try { ... } catch
(UnsupportedGeometryException e)` was unwrapped -- the real upstream `GeometryUtils` declares that
checked exception on `convertGeoJsonToJtsGeometry(...)`, but the copy already vendored into
`otp-street` (Task 2) doesn't, making the catch block unreachable dead code.

## otp-routing changes (real, committed -- not throwaway)

Building a genuinely working graph surfaced a few gaps in Task 3's stub layer that needed real
fixes in `otp-routing` itself (not just this throwaway project), because **any concrete class
Kryo embeds in the saved `graph.obj` must already exist on `otp-routing`'s own classpath**, since
that's what deserializes it later (in `SerializedGraphObjectRoundTripTest` and every downstream
test). A class that only exists in this disposable `.tools/graph-builder` project would fail to
deserialize with a `ClassNotFoundException` (surfaced by `SerializedGraphObject.load(...)` as a
misleading "wrong OTP version" `OtpAppException`).

1. **Deleted a redundant, incorrect duplicate stub**:
   `otp-routing/.../service/vehicleparking/VehicleParkingRepository.java` (an empty marker
   interface Task 3 added) had the exact same fully-qualified name as the **real**
   `VehicleParkingRepository` interface `otp-street` already vendors in full (Task 2, with a real
   `DefaultVehicleParkingRepository` implementation) -- since `otp-routing` depends on
   `otp-street`, the otp-routing-local copy was silently shadowing the real one for
   `otp-routing`'s own compilation. Deleting the duplicate lets `otp-routing` fall through to
   `otp-street`'s real interface, which the throwaway driver now uses (via
   `DefaultVehicleParkingRepository`) with real behavior, not a stub.
2. **Upgraded two stubs from empty markers to real, working implementations** (small,
   self-contained, non-sandbox service packages -- verbatim vendoring, same commit):
   `service/worldenvelope/{WorldEnvelopeRepository,internal/DefaultWorldEnvelopeRepository,
   model/WorldEnvelope,model/WorldEnvelopeBuilder,model/MedianCalcForDoubles}.java` and
   `service/osminfo/{OsmInfoGraphBuildRepository,internal/DefaultOsmInfoGraphBuildRepository,
   model/Platform}.java`. Both are genuinely populated by graph building
   (`CalculateWorldEnvelopeModule`, `OsmModule`/`TurnRestrictionModule` respectively) and both
   flow into the saved `graph.obj`, so they needed real behavior, not just a marker type.
3. **Added two trivial concrete `Noop*` classes** next to existing empty-marker stub interfaces
   (`routing/fares/NoopFareServiceFactory.java`,
   `ext/stopconsolidation/NoopStopConsolidationRepository.java`) -- both stub interfaces declare no
   methods, so there's nothing to implement; they exist purely so a real, non-null,
   classpath-resolvable instance can be passed to `SerializedGraphObject`'s non-`@Nullable`
   `fareServiceFactory`/`stopConsolidationRepository` constructor parameters.
4. **Added the missing `otp-project-info.properties` resource**
   (`otp-routing/src/main/resources/otp-project-info.properties`, real key
   `otp.serialization.version.id=277`, matching the same commit's `pom.xml`). This resource did not
   exist anywhere in the ported repo before Task 4 -- without it,
   `OtpProjectInfoParser.loadFromProperties()` silently falls back to a placeholder
   `OtpProjectInfo()` whose graph-file-header id is the literal string `"UNKNOWN"`. Since that
   fallback would happen identically on *both* the write side (this throwaway builder, which also
   depends on `otp-routing.jar` and so shares this same classpath resource) and the read side
   (`otp-routing`'s own test), the round-trip test would have spuriously "passed" even with a
   completely missing/wrong version id (`"UNKNOWN".equals("UNKNOWN")`) -- not a genuine check of
   the real `277` this whole plan depends on. Confirmed fixed: the fixture's real file header now
   reads `OpenTripPlannerGraph;0000277;` and the loaded `OtpProjectInfo`'s parsed
   `ser.ver.id` is `277` (both checked directly, see Step 5+ below).

`EmissionRepository`, `EmpiricalDelayRepository`, and `OsmInfoGraphBuildRepository` (this last one
via the newly-real `DefaultOsmInfoGraphBuildRepository`) are threaded through with real/null values
per their `@Nullable` declarations -- see the driver code below.

## A cross-test static-state hazard this surfaced (fixed in the test itself)

`SubMode` (`otp-routing/.../transit/model/basic/SubMode.java`) keeps a process-global static cache
(`ALL`/`COUNTER`) that `SerializedGraphObject.load(...)` permanently mutates via
`SubMode.deserializeSubModeCache(...)`. The first time `SerializedGraphObjectRoundTripTest` ran for
real (previously `@Disabled`), it broke an unrelated, pre-existing test (`SubModeTest.getByIndex()`)
elsewhere in the same suite -- because JUnit runs all test classes in one JVM, and the real
submodes this fixture's real GTFS data produces got merged into the same static cache
`SubModeTest`'s own test-local submodes (`"localBus"`/`"nightBus"`) live in, with numeric index
collisions as a result. Fixed by having `SerializedGraphObjectRoundTripTest` snapshot
`SubMode`'s static state (via reflection, since the fields are private) before loading the graph
and restore it afterward, so this test's use of a real graph doesn't leak into any other test.
**This is a general hazard, not specific to this one test**: any future test (Task 6 onward) that
deserializes a real graph in the same JVM as other `SubMode`-touching tests will hit the same
issue unless it does the same snapshot/restore (or a shared JUnit extension is written for it --
not done here, out of this task's own scope).

## Step 2: `build.gradle.kts`

Real dependency coordinates (confirmed by grepping the real upstream `application/pom.xml` at the
same commit for the actual `<groupId>/<artifactId>/<version>`, not guessed):

| Dependency | Coordinates | Used for |
|---|---|---|
| jcommander | `com.beust:jcommander:1.82` | (transitively required; not directly called by this slice) |
| javacsv | `net.sourceforge.javacsv:javacsv:2.0` | **not** `com.csvreader:javacsv` as an early draft of this task assumed -- the real upstream groupId/artifactId differ |
| OpeningHoursParser | `ch.poole:OpeningHoursParser:0.29.0` | OSM `opening_hours` tag parsing |
| osmpbf | `org.openstreetmap.pbf:osmpbf:1.6.1` | `.osm.pbf` parsing (`crosby.binary.*`) -- not an "osmosis" artifact as might be guessed; this is the actual upstream coordinate |
| onebusaway-gtfs | `org.onebusaway:onebusaway-gtfs:14.2.2` | GTFS parsing |
| commons-collections4 | `org.apache.commons:commons-collections4:4.4` | transitive utility usage; version not pinned upstream (pulled in transitively there), picked a current stable release |
| commons-text | `org.apache.commons:commons-text:1.13.0` | same as above |
| guava, jts-core, trove4j, jakarta.inject-api, slf4j-api, jsr305, jackson-annotations, kryo, kryo-tools, kryo-serializers | same versions as `otp-routing`'s own `build.gradle.kts` | already-vendored shared deps |

Plus `files(...)` dependencies on this repo's own six already-built module jars. All resolved
through the JetBrains mirror; no Maven Central 429s were hit building this list.

## Step 3: the driver

`.tools/graph-builder/src/main/java/org/opentripplanner/graphbuilder/throwaway/
BuildFixtureGraph.java` -- a `public static void main(String[] args)` taking
`<osm.pbf path> <gtfs.zip path> <output graph.obj path>`. Shape:

1. Construct `Graph`, `Deduplicator`, `TransitRepository` (all real, no-arg constructors),
   `transitRepository.initTimeZone(ZoneId.of("Europe/Copenhagen"))`.
2. Construct real repository instances directly (no config/DI layer):
   `DefaultStreetDetailsRepository`, `DefaultStreetRepository`, `DefaultVehicleParkingRepository`,
   `DefaultOsmInfoGraphBuildRepository`, `DefaultTransferRepository(new TransferIndex())`,
   `DefaultWorldEnvelopeRepository`.
3. `new GraphBuilder(graph, deduplicator, transitRepository, DataImportIssueStore.NOOP, () -> {},
   GraphBuildCacheManager.NOOP)`, then `.setHasTransitData(true)`.
4. `OsmModule.of(List.of(new DefaultOsmProvider(osmFile, false)), graph,
   osmInfoGraphBuildRepository, streetDetailsRepository, streetRepository,
   vehicleParkingRepository).build()` -- all `.with*(...)` builder options left at their upstream
   defaults -- added via `graphBuilder.addModule(...)`.
5. `new GtfsModule(List.of(new GtfsBundle(new ZipFileDataSource(gtfsFile, FileType.GTFS), new
   GtfsFeedParameters(null, gtfsFile.toURI(), StopTransferPriority.defaultValue(), false, true,
   200))), transitRepository, streetDetailsRepository, graph, deduplicator,
   DataImportIssueStore.NOOP, LocalDateRange.ofUnbounded(), new NoopFareServiceFactory(), 150.0,
   120)`, added the same way.
6. Street/transit linking + cleanup modules, also added directly: `new StreetLinkerModule(graph,
   new VertexLinker(graph, GeofencingZoneService.EMPTY, VisibilityMode.TRAVERSE_AREA_EDGES,
   StreetConstants.DEFAULT_MAX_AREA_NODES, false), vehicleParkingRepository, transitRepository,
   issueStore)`, `new TurnRestrictionModule(graph, osmInfoGraphBuildRepository)`, `new
   StopConnectivityModule(graph, issueStore)`, `new CalculateWorldEnvelopeModule(graph,
   transitRepository, worldEnvelopeRepository)`.
7. `graphBuilder.run()`.
8. `new SerializedGraphObject(graph, osmInfoGraphBuildRepository, streetDetailsRepository,
   streetRepository, transitRepository, transferRepository, worldEnvelopeRepository,
   vehicleParkingRepository, new BuildConfig(), new RouterConfig(), graphBuilder.issueSummary(),
   null /* emissionRepository */, null /* empiricalDelayRepository */, new
   NoopStopConsolidationRepository(), new NoopFareServiceFactory())`, then
   `.save(new FileDataSource(outputFile, FileType.GRAPH))`.

### Known simplifications (not built into the fixture)

To keep this driver's own scope manageable, a few upstream `GraphBuilder.create(...)` steps are
**not** run: `OsmBoardingLocationsModule` (links OSM-tagged boarding-location platforms to
stops -- needs a real `OsmInfoGraphBuildService`/`Platform` wiring layer beyond what this driver
sets up), `DirectTransferGenerator`/`DirectTransferAnalyzer` (direct stop-to-stop transfers --
left the fixture's `TransferRepository` empty rather than populated), `IslandPruningModule`
(connectivity-island pruning), `GraphCoherencyCheckerModule`, `TripPatternNamer`,
`TimeZoneAdjusterModule`, `RouteToCentroidStationIdsValidator`, and the data-overlay/emission/
empirical-delay/stop-consolidation optional modules (all sandbox features, off by default
upstream too). If a later task's routing test needs one of these, re-run this same driver with
that module added (real upstream shape already vendored in `graph_builder/module/**` for
`OsmBoardingLocationsModule` -- it was excluded from the file copy itself, so it would need
re-copying too).

`transfers.txt` rows are kept only when **both** `from_stop_id` and `to_stop_id` survive the GTFS
clip (below), **and** any of its optional `from_route_id`/`to_route_id`/`from_trip_id`/
`to_trip_id` columns that are set reference a route/trip the clip also kept (found the hard way:
`onebusaway-gtfs`'s `Transfer` entity mapping throws `EntityReferenceNotFoundException` on an
unresolvable reference, so the first clip attempt -- which only filtered by `stop_id` -- failed
loading `transfers.txt` when running the driver).

## Step 4: sourcing + clipping OSM + GTFS

Raw, full-Denmark source files (already present locally, not re-downloaded):
`C:\Users\bru\spare-source\bikebus\pipeline\build\sources\denmark-latest.osm.pbf` (494 MB) and
`...\GTFS.zip` (53 MB). Clipped to the same Aarhus-area bbox `bikebus`'s own pipeline dev fixture
uses (so this plan's ported tests can reuse `bikebus`'s known-good expected values):

```
min_lon=10.05  min_lat=56.08  max_lon=10.30  max_lat=56.25
```

**OSM** (`osmium.ForwardReferenceWriter`, via `bikebus/pipeline/.venv`'s pyosmium install): a
single streaming pass over the full file with `FileProcessor(...).with_locations()` --

- a **node** is kept (written + its id remembered) if its coordinate falls in the bbox;
- a **way** is kept if *any* of its resolved node coordinates falls in the bbox (nodes are already
  location-annotated by `.with_locations()`'s incremental cache, since ways always follow nodes in
  `.osm.pbf` file order);
- a **relation** is kept if any member is a kept way or node id.

`back_references=False` (the writer's own automatic *forward*-reference completion -- pulling in
a kept way's own node dependencies even if outside the bbox -- always happens regardless; only the
optional *backward* pass, which would pull in every other way anywhere in Denmark sharing a
boundary node, was disabled to keep the extract's size predictable).

Result: from ~53.5M nodes / ~6.9M ways in the full Denmark file, kept ~1.27M nodes and ~171K ways
(see the script's own stderr progress log for exact final counts).

**GTFS**: no ready-made "clip a GTFS zip to a bbox, write a new valid zip" tool exists, so a small,
real, from-scratch Python script does it, streaming the two large files
(`stop_times.txt`, 220 MB; `shapes.txt`, 110 MB) row-by-row via `csv.DictReader` rather than
loading them whole:

1. Load `stops.txt` fully (3.5 MB, fits comfortably) → stops directly inside the bbox, plus their
   `parent_station` (fixed-point over the small in-memory table, in case of multi-level nesting).
2. Pass 1 over `stop_times.txt`: any trip touching a kept-so-far stop becomes a kept trip.
3. Pass 2 over `stop_times.txt`: every row of every kept trip is kept **in full** (even rows for
   stops outside the bbox, per the brief) -- while also recording every `stop_id` this actually
   references.
4. The kept-stops set is **expanded** to include those extra referenced stops (+ another
   fixed-point parent-station pass) -- necessary so the output `stops.txt` stays referentially
   complete for every `stop_time` row kept in step 3 (a trip kept "in full" can reference stops
   outside the original bbox, and those stops must still have a row for the GTFS to be valid).
5. `trips.txt`/`routes.txt`/`agency.txt`/`calendar.txt`/`calendar_dates.txt`/`frequencies.txt`/
   `shapes.txt`/`transfers.txt` are each filtered down to what the kept trips/stops actually
   reference; `attributions.txt` (feed-level metadata, no per-stop foreign keys) is copied whole.

Result (from the full Denmark feed): 2,292 stops, 10,674 trips, 55 routes, 4 agencies, 449
calendar rows, 3,119 calendar-date rows, 4,313 transfers, 326 shapes (204,369 shape points) -- a
3.7 MB zip.

Both scripts are one-off and were **not** committed anywhere (they lived in a scratch directory
during Task 4's own work); their logic is fully described above for reproducibility.

## Step 5+: running it, and the acceptance check

```bash
cd .tools/graph-builder
./gradlew run --args="<osm.pbf path> <gtfs.zip path> <output graph.obj path>"
```

The acceptance criterion for this whole task: the produced file's header must claim
`otp.serialization.version.id` `277` (`OpenTripPlanner/pom.xml`'s current value at the vendored
commit -- unchanged from this plan's earlier research) and
`otp-routing`'s own vendored `SerializedGraphObject.load(File)` must deserialize it into a
non-null `graph` without throwing. Verified by (re-)enabling
`SerializedGraphObjectRoundTripTest` against the copied
`otp-routing/src/test/resources/tiny-fixture-graph.obj`.

## Re-running this later

1. Rebuild this repo's own module jars: from the repo root,
   `./gradlew :otp-utils:jar :otp-domain-core:jar :otp-astar:jar :otp-street:jar :otp-raptor:jar
   :otp-routing:jar`.
2. From `.tools/graph-builder/` (recreate it per this doc if it's been deleted -- it's gitignored
   and never committed): `./gradlew run --args="<osm.pbf> <gtfs.zip> <output.obj>"`.
3. Copy the result over `otp-routing/src/test/resources/tiny-fixture-graph.obj` and re-run
   `./gradlew :otp-routing:test --tests "*.SerializedGraphObjectRoundTripTest"`.

## Not done here (deferred to a real production pipeline)

A full production Denmark-wide graph-building flow needs real `build-config.json`/
`router-config.json` handling (this driver hardcodes everything), proper error reporting
(`DataImportIssueReporter`/HTML output -- not vendored here), the modules this driver skips (see
"Known simplifications" above) reconsidered on their merits, elevation data if ever wanted, and
probably belongs back in the real `application` module's own Maven build once this project is far
enough along to justify paying the GCP-BOM dependency cost once, in CI/deployment rather than per
developer machine.
