# VENDORED.md - :otp-street

## 1. Upstream
- URL: https://github.com/opentripplanner/OpenTripPlanner
- Commit: 61a3af6798
- Description: Vendored copy of the street module.

## 2. What was copied
- src/main/java (verbatim, minus deleted files)
- src/test/java (verbatim, minus deleted files)
- src/testFixtures/java (verbatim, renamed from src/test-fixtures/java)

## 3. Deleted files
Main:
- org/opentripplanner/street/configure/StreetRepositoryModule.java
- org/opentripplanner/street/service/StreetLimitationParametersServiceModule.java
- org/opentripplanner/service/vehicleparking/configure/VehicleParkingRepositoryModule.java
- org/opentripplanner/service/vehicleparking/configure/VehicleParkingServiceModule.java
- org/opentripplanner/service/vehiclerental/configure/VehicleRentalRepositoryModule.java
- org/opentripplanner/service/vehiclerental/configure/VehicleRentalServiceModule.java
- org/opentripplanner/street/graph/summary/GeoJsonIo.java

Tests:
- org/opentripplanner/street/geometry/DirectionUtilsTest.java (references excluded GeoTools library)

## 4. Edited files
JDK 17 Downgrades:
- org/opentripplanner/street/geometry/HashGridSpatialIndex.java: replaced Math.clamp (two call
  sites, clampLon/clampLat)
- org/opentripplanner/street/model/path/StreetPath.java: replaced getFirst/getLast (six call
  sites) and replaced `startTime().until(endTime())` with `Duration.between(startTime(), endTime())`
  (one call site, same JDK 17 category as the getFirst/getLast replacements in this file)
- org/opentripplanner/street/linking/AreaEdgeProperties.java: replaced getFirst
- org/opentripplanner/street/linking/PreparedAreaGroup.java: replaced getFirst
- org/opentripplanner/street/model/elevation/BicycleSlopeSpeedFunction.java: replaced Math.clamp
- org/opentripplanner/street/search/state/EdgeTraverser.java: replaced getFirst (one call site) and
  replaced two `catch (CouldNotTraverseException _)` with `catch (CouldNotTraverseException e)`
  (lines 35, 49)
- org/opentripplanner/street/graph/summary/StreetSummarizer.java: replaced its seven-arm
  pattern-matching `switch` over edge type with an `if`/`else if` chain (`instanceof` casts), and
  its `case TemporaryFreeEdge _ ->` arm's unused pattern variable is gone with it (this is the
  same file as the "Authorized Edits"/dependency-removal entry below; the pattern-switch rewrite
  is the JDK 17 half of what was done to it)
- src/test/java/org/opentripplanner/street/graph/GraphTest.java: replaced HashSet.newHashSet
- src/test/java/org/opentripplanner/street/integration/MillisecondResolutionTest.java: replaced getFirst
- src/test/java/org/opentripplanner/street/linking/PlatformLinkingTest.java: replaced getFirst
- src/test/java/org/opentripplanner/street/linking/VertexLinkerGeofencingTest.java: replaced getFirst
- src/test/java/org/opentripplanner/street/linking/ScopedLinkingTest.java: replaced two
  `(_, _) ->` lambda parameter lists with `(a, b) ->` (lines 35, 49)
- src/testFixtures/java/org/opentripplanner/street/search/state/TestStateBuilder.java: replaced '_' lambda param

Dependency removal (forbidden library removed from a file that must stay; recorded per call site,
upstream signature kept where the classpath allows it):
- org/opentripplanner/street/graph/summary/StreetSummarizer.java: replaced the
  `org.apache.commons.lang3.NotImplementedException` import and its one throw site (the `switch`
  default arm, now the trailing `else`) with `UnsupportedOperationException`; commons-lang3 is a
  forbidden dependency
- org/opentripplanner/street/geometry/GeometryUtils.java: `convertGeoJsonToJtsGeometry` and
  `convertPath` are the only two members that touch `org.geojson`, a forbidden dependency; their
  bodies are reduced to an unconditional `throw new UnsupportedOperationException("GeoJSON
  support removed")`, and their parameter types are widened from upstream's `GeoJsonObject`/
  `List<LngLatAlt>` (org.geojson types, which do not resolve without the dependency) to `Object`/
  `List<?>`. `getLineStrings` and every other member are byte-for-byte identical to upstream,
  including `List.copyOf(ret)`. (Previously misrecorded under an unsanctioned `## Other` heading
  as "fixed getLineStrings to work with JTS 1.20" — that description was false: `getLineStrings`
  needed no fix, and the file had also been reformatted/corrupted by whatever produced it. It was
  re-copied from upstream and this dependency removal re-applied; see git history for the fix.)
- org/opentripplanner/street/graph/summary/GraphSummarizer.java: `geoJsonUrl()`'s body
  (`GeoJsonIo.toUrl(this.listEdges(), graph.getVertices())`, calling the deleted `GeoJsonIo.java`)
  is replaced with the fixed string `"GeoJSON summary not supported"`. No commons-lang3 import and
  no pattern-switch exist in this file; only this one call site differs from upstream.
- src/testFixtures/java/org/opentripplanner/street/graph/summary/DisposableEdgeDataFetcher.java:
  stubbed GeoJsonIo call

### Authorized edit (Task 1: `Serializable` for `LoadedNetwork` round trip)
- org/opentripplanner/street/model/vertex/VertexLabel.java: the sealed interface now `extends
  Serializable` (one line), so its five permitted record implementations inherit it without their
  own edits. `Graph.vertices` is a non-transient `Map<VertexLabel, Vertex>`, and `LoadedNetwork`'s
  Java-serialization round trip needs the whole reachable object graph to be `Serializable`.

## 5. How to re-sync
1. Delete the current src directories.
2. Copy the trees from OpenTripPlanner/street/.
3. Re-apply the JDK 17 downgrades listed in section 4 above, file by file.
4. Re-apply the dependency removals listed in section 4 above, file by file:
   `StreetSummarizer.java` (commons-lang3 -> UnsupportedOperationException),
   `GeometryUtils.java` (org.geojson removed from `convertGeoJsonToJtsGeometry`/`convertPath`
   only, everything else byte-identical to upstream), `GraphSummarizer.java` (`geoJsonUrl()` body
   replaced), `DisposableEdgeDataFetcher.java` (GeoJsonIo call stubbed).
5. Re-delete the files listed in section 3.
6. Compile; follow any new unresolved symbol the same way; run `:otp-street:test`.
7. Verify: `diff -rq` (line-ending-normalised, `diff --strip-trailing-cr`) of each tree against
   upstream must show only the files listed in sections 3 and 4 above, plus stub files present
   only in the vendored tree. Additionally grep the tree for `^ $` (lines containing a single
   space) and for runs of more than two consecutive blank lines: both must be empty. Any
   differing file not named in section 3 or 4 is a regression, not a legitimate edit.
