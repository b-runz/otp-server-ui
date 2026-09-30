# Stubs

Every file below starts with the line `// STUB: not upstream OTP code` and is not a byte-for-byte
copy of an upstream OpenTripPlanner file. Each entry lists the type's members and the files in
this module that reference it.

## Boundary: `org.opentripplanner.updater.*` / `org.opentripplanner.standalone.*` (excluded packages)

- **`org/opentripplanner/updater/GraphUpdaterStatus.java`** — interface, matches the upstream
  shape: `numberOfUpdaters()`, `listUnprimedUpdaters()`, `getUpdaterDescriptions()`,
  `getUpdaterClass(int)`. Referenced by `transit/service/DefaultTransitService.java`,
  `transit/service/TransitService.java`.
- **`org/opentripplanner/updater/GraphUpdaterManager.java`** — class implementing
  `GraphUpdaterStatus`; public no-arg constructor (Task 6 can instantiate it directly); all
  methods report "no updaters" (`0`, empty list/map, `getUpdaterClass` throws
  `UnsupportedOperationException("stub")` since there is never a valid id to look up).
  Referenced by `transit/service/TransitRepository.java`.
- **`org/opentripplanner/updater/configure/UpdaterConfigurator.java`** — empty class, public
  no-arg constructor. Referenced only from a Javadoc `@see` tag in
  `transit/service/TransitRepository.java`.
- **`org/opentripplanner/standalone/config/routerconfig/TransitRoutingConfig.java`** — empty
  class, public no-arg constructor. Referenced only from a Javadoc `@link` tag in
  `routing/api/request/RouteRequest.java` and a commented-out field in
  `routing/service/DefaultRoutingService.java`. (Named explicitly in the task brief.)
- **`org/opentripplanner/standalone/config/BuildConfig.java`**,
  **`.../OtpConfig.java`**, **`.../RouterConfig.java`** — empty classes, public no-arg
  constructors. Not named in the brief; surfaced by the compiler because
  `model/projectinfo/OtpProjectInfo.java` imports them purely for Javadoc `@link` tags
  (`{@link OtpConfig#configVersion}` etc.) — the import itself must still resolve even though the
  class member is never referenced outside the comment.

## `org.opentripplanner.ext.*` (excluded packages)

- **`org/opentripplanner/ext/accessibilityscore/DecorateWithAccessibilityScore.java`** —
  implements `ItineraryDecorator`; constructor `(double wheelchairMaxSlope)`; `decorate(Itinerary)`
  returns the itinerary unchanged (no accessibility score attached — matches
  `Leg#accessibilityScore()`'s existing "null means not computed" default). Referenced by
  `routing/algorithm/filterchain/ItineraryListFilterChainBuilder.java`.
- **`org/opentripplanner/ext/carpooling/CarpoolingService.java`** — interface:
  `routeDirect(RouteRequest): List<Itinerary>`,
  `routeAccessEgress(RouteRequest, StreetRequest, AccessEgressType, TransitServiceResolver, ZonedDateTime): List<CarpoolAccessEgress>`.
  Referenced (as a nullable field/parameter) by `routing/algorithm/RoutingWorker.java`,
  `routing/service/DefaultRoutingService.java`,
  `routing/algorithm/raptoradapter/router/TransitRouter.java`.
- **`org/opentripplanner/ext/carpooling/internal/CarpoolItineraryMapper.java`** — public no-arg
  constructor; `toItinerary(CarpoolAccessEgress): Itinerary` throws
  `UnsupportedOperationException("stub")` (it fabricates a real itinerary; unreachable in
  practice since `CarpoolingService` always returns an empty list). Referenced by
  `routing/algorithm/mapping/RaptorPathToItineraryMapper.java`.
- **`org/opentripplanner/ext/carpooling/routing/CarpoolAccessEgress.java`** — implements
  `RoutingAccessEgress`; `hasOpeningHours()`/`isWalkOnly()` return `false`, `penalty()` returns
  `TimeAndCost.ZERO`, every other method throws `UnsupportedOperationException("stub")`. Nothing
  in this slice constructs one (only `instanceof`/cast checks). Referenced by
  `routing/algorithm/mapping/RaptorPathToItineraryMapper.java`.
- **`org/opentripplanner/ext/dataoverlay/api/DataOverlayParameters.java`** — empty class, public
  no-arg constructor; opaque field type only (stored/compared, never inspected). Referenced by
  `routing/api/request/preference/SystemPreferences.java`.
- **`org/opentripplanner/ext/dataoverlay/configuration/DataOverlayParameterBindings.java`** —
  empty class, public no-arg constructor; carried through as a nullable parameter, never read.
  Referenced by `routing/algorithm/RoutingWorker.java`, `routing/service/DefaultRoutingService.java`,
  `routing/algorithm/raptoradapter/router/TransitRouter.java`,
  `routing/algorithm/raptoradapter/router/street/DirectStreetRouter.java`,
  `routing/algorithm/raptoradapter/router/street/DirectFlexRouter.java` (stub),
  `routing/algorithm/raptoradapter/router/AccessEgressFetcher.java`.
- **`org/opentripplanner/ext/dataoverlay/routing/DataOverlayContext.java`** — static
  `listExtensionRequestContexts(DataOverlayParameters, DataOverlayParameterBindings): List<ExtensionRequestContext>`
  always returns `List.of()` ("no data-overlay penalty/threshold applied", matching the feature
  being off). Referenced by `DirectStreetRouter.java`, `DirectFlexRouter.java` (stub),
  `AccessEgressFetcher.java`.
- **`org/opentripplanner/ext/flex/edgetype/FlexTripEdge.java`** — extends `Edge`; constructor
  `(Vertex, Vertex, FeedScopedId fromStopId, FeedScopedId toStopId)`; `fromStopId()`/`toStopId()`
  accessors; `getName()`/`traverse(State)` throw `UnsupportedOperationException("stub")` (never
  invoked: flex graph-building is excluded, so no instance is ever added to the street graph).
  Referenced by `routing/algorithm/mapping/StreetPathToLegsMapper.java`.
- **`org/opentripplanner/ext/flex/FlexAccessEgress.java`** — `stop()`/`lastState()`/
  `earliestDepartureTime(int)`/`latestArrivalTime(int)` throw `UnsupportedOperationException("stub")`;
  `stopReachedOnBoard()` returns `false`. Referenced by
  `routing/algorithm/raptoradapter/transit/FlexAccessEgressAdapter.java` (stub).
- **`org/opentripplanner/ext/flex/FlexibleTransitLeg.java`** — implements `TransitLeg` (the
  supertype `StreetPathToLegsMapper`/`Itinerary` check it against, per the upstream declaration);
  static `of(): Builder` with `withFlexTripEdge/withFromStop/withToStop/withStartTime/withEndTime/withGeneralizedCost/build()`.
  `startTime()`, `endTime()`, `generalizedCost()`, `from()`, `to()` return the builder-stored
  values (plain accessors, not fabricated behaviour); `mode()`, `start()`, `end()`,
  `distanceMeters()`, `decorateWithAlerts(...)`, `withEmissionPerPerson(...)` throw
  `UnsupportedOperationException("stub")`; `legGeometry()`/`emissionPerPerson()` return `null`
  (both `@Nullable` upstream); `listTransitAlerts()`/`fareOffers()` return empty. Referenced by
  `model/plan/Itinerary.java` (`instanceof` check) and
  `routing/algorithm/mapping/StreetPathToLegsMapper.java` (builder chain in the otherwise-dead
  `generateFlexLeg` path).
- **`org/opentripplanner/ext/flex/FlexIndex.java`** — constructor `(TransitRepository)`;
  `contains(Route)` returns `false`; `findRoutes(StopLocation)`, `getAllFlexRoutes()`,
  `getAllFlexTrips()` return empty collections. Referenced by
  `transit/service/TransitRepository.java`, `transit/service/StopModelIndex.java`
  (the `TransitRepositoryIndex` class), `transit/service/DefaultTransitService.java`.
- **`org/opentripplanner/ext/flex/FlexParameters.java`** — public no-arg constructor;
  `maxAccessWalkDuration()`/`maxEgressWalkDuration()` return `Duration.ZERO`. Referenced by
  `RoutingWorker.java`, `DefaultRoutingService.java`, `TransitRouter.java`,
  `DirectFlexRouter.java` (stub), `FlexAccessEgressRouter.java` (stub).
- **`org/opentripplanner/ext/flex/trip/FlexTrip.java`** — constructor
  `(FeedScopedId id, Trip trip)`; `getId()`/`getTrip()` accessors. Referenced by
  `transit/service/TransitRepository.java`, `transit/service/StopModelIndex.java`.
- **`org/opentripplanner/ext/realtimeresolver/RealtimeResolver.java`** — static
  `populateLegsWithRealtime(List<Itinerary>, TransitService, TransitAlertService): List<Itinerary>`
  returns the itineraries unchanged ("no additional realtime info applied"). Referenced by
  `routing/algorithm/mapping/RoutingResponseMapper.java`.
- **`org/opentripplanner/ext/ridehailing/DecorateWithRideHailing.java`** — implements
  `ItineraryListFilter`; constructor `(List<RideHailingService>, boolean wheelchair)`;
  `filter(List<Itinerary>)` returns the list unchanged. Referenced by
  `routing/algorithm/mapping/RouteRequestToFilterChainMapper.java`.
- **`org/opentripplanner/ext/ridehailing/RideHailingAccessShifter.java`** — static
  `shiftAccesses(boolean, List<RoutingAccessEgress>, List<RideHailingService>, RouteRequest, Instant): List<RoutingAccessEgress>`
  returns the results unshifted. Referenced by
  `routing/algorithm/raptoradapter/router/AccessEgressFetcher.java`.
- **`org/opentripplanner/ext/ridehailing/RideHailingService.java`** — empty marker interface (no
  method on it is ever called directly in this slice; only carried in a `List<RideHailingService>`).
  Referenced by `RoutingWorker.java`, `DefaultRoutingService.java`, `TransitRouter.java`,
  `RouteRequestToFilterChainMapper.java`, `RideHailingAccessShifter.java` (stub),
  `DecorateWithRideHailing.java` (stub).
- **`org/opentripplanner/ext/sorlandsbanen/SorlandsbanenNorwayService.java`** —
  `createExtraMcRouterSearch(RouteRequest, AccessEgresses, RaptorTransitData): ExtraMcRouterSearch<TripSchedule>`
  (`@Nullable`) always returns `null` — a legitimate upstream outcome ("no extra search needed"),
  not a fabricated result. Referenced by `RoutingWorker.java`, `DefaultRoutingService.java`,
  `TransitRouter.java`.
- **`org/opentripplanner/ext/stopconsolidation/DecorateConsolidatedStopNames.java`** —
  implements `ItineraryDecorator`; constructor `(StopConsolidationService)`;
  `decorate(Itinerary)` returns the itinerary unchanged. Referenced by
  `model/plan/Itinerary.java` (comment only) and
  `routing/algorithm/mapping/RouteRequestToFilterChainMapper.java`.
- **`org/opentripplanner/ext/stopconsolidation/StopConsolidationService.java`** — interface
  with only `isActive(): boolean` (the only member actually called in this slice). Referenced by
  `RoutingWorker.java`, `DefaultRoutingService.java`, `RouteRequestToFilterChainMapper.java`.

## Replaced by stubs instead of copied (main, non-excluded packages)

These three files are NOT in an excluded package — they are ordinary
`routing.algorithm.raptoradapter.*` orchestration classes — but their upstream bodies depend on
the excluded `org.opentripplanner.ext.flex.FlexRouter`/`FilterMapper`, so per the brief they are
replaced with stubs rather than copied.

- **`org/opentripplanner/routing/algorithm/raptoradapter/router/street/DirectFlexRouter.java`** —
  keeps the public `route(...)` signature `RoutingWorker` calls; always returns `List.of()`.
- **`org/opentripplanner/routing/algorithm/raptoradapter/router/street/FlexAccessEgressRouter.java`** —
  keeps the public `routeAccessEgress(...)` signature `AccessEgressFetcher` calls; always returns
  `List.of()`.
- **`org/opentripplanner/routing/algorithm/raptoradapter/transit/FlexAccessEgressAdapter.java`** —
  keeps the upstream constructors/overrides of `DefaultAccessEgress` (`hasOpeningHours()`,
  `isWalkOnly()`, `withPenalty(...)`), rewired against the `FlexAccessEgress` stub; the body isn't
  hollowed out further because `FlexAccessEgressRouter` always returning an empty collection
  already means this constructor is never actually invoked.

## Extra excluded types the compiler surfaced beyond the brief's enumerated list

Not named in the brief's Step 5 list, but required once the seeds' transitive dependencies were
followed; grouped above by package, called out here for visibility: `standalone.config.BuildConfig`,
`standalone.config.OtpConfig`, `standalone.config.RouterConfig`.

(The brief's list also named `ext.flex.FlexRouter` and `ext.flex.filter.FilterMapper` as
compiler-surfaced unresolved symbols. Once `DirectFlexRouter`/`FlexAccessEgressRouter` were
rewritten as stubs that never call `FlexRouter`/`FilterMapper`, those two types were no longer
referenced anywhere in the module, so no stub was needed for them.)

## otp-server-ui addition: graph loading (see `VENDORED.md`'s matching section)

Added on top of the above when `routing/graph/SerializedGraphObject.java` was vendored in. All
seven are empty marker interfaces: `SerializedGraphObject` only holds each as a
constructor-injected field and never calls a method on it, so nothing beyond the type itself
needed to exist.

- **`org/opentripplanner/service/worldenvelope/WorldEnvelopeRepository.java`** — `extends
  Serializable`, matching upstream. Referenced by `routing/graph/SerializedGraphObject.java`.
- **`org/opentripplanner/service/vehicleparking/VehicleParkingRepository.java`** — plain
  interface (upstream does not extend `Serializable`). Referenced by
  `routing/graph/SerializedGraphObject.java`.
- **`org/opentripplanner/service/osminfo/OsmInfoGraphBuildRepository.java`** — `extends
  Serializable`, matching upstream. Referenced (as a `@Nullable` field) by
  `routing/graph/SerializedGraphObject.java`.
- **`org/opentripplanner/ext/stopconsolidation/StopConsolidationRepository.java`** — `extends
  Serializable`, matching upstream. Referenced by `routing/graph/SerializedGraphObject.java`.
- **`org/opentripplanner/ext/emission/EmissionRepository.java`** — `extends Serializable`,
  matching upstream. Referenced (as a `@Nullable` field) by
  `routing/graph/SerializedGraphObject.java`.
- **`org/opentripplanner/ext/empiricaldelay/EmpiricalDelayRepository.java`** — `extends
  Serializable`, matching upstream. Referenced (as a `@Nullable` field) by
  `routing/graph/SerializedGraphObject.java`.
- **`org/opentripplanner/routing/fares/FareServiceFactory.java`** — plain interface (upstream does
  not extend `Serializable`). Referenced by `routing/graph/SerializedGraphObject.java`.

Two more stubs, not enumerated in the task brief, were needed to compile `SerializedGraphObject`'s
own dependency closure:

- **`org/opentripplanner/datastore/api/DataSource.java`** — interface with only the methods
  `SerializedGraphObject` itself calls: `name()`, `path()`, `exists()`, `isWritable()`, `size()`,
  `asInputStream()`, `asOutputStream()`, all with no bodies (an interface, so none are needed).
  Real upstream is a much larger file/zip/cloud-storage abstraction backing the excluded
  graph-building/data-import pipeline; nothing in this module ever constructs a `DataSource`
  (graph loading here always goes through `SerializedGraphObject.load(File)`, not
  `load(DataSource)`). Referenced by `routing/graph/SerializedGraphObject.java`.
- **`org/opentripplanner/kryo/BuildConfigSerializer.java`** /
  **`org/opentripplanner/kryo/RouterConfigSerializer.java`** — Kryo `Serializer<BuildConfig>` /
  `Serializer<RouterConfig>` implementations; `write()` is a no-op and `read()` returns
  `new BuildConfig()` / `new RouterConfig()`. Real upstream round-trips the JSON config tree
  through `standalone.config.framework.file.ConfigFileLoader` (out of scope); since this module's
  `BuildConfig`/`RouterConfig` stubs (see above) are empty no-arg-constructor classes with no
  state, there is nothing to actually serialize. Referenced by
  `routing/graph/kryosupport/KryoBuilder.java`.
