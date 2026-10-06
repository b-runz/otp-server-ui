package one.otpserverui.routing

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import org.opentripplanner.model.GenericLocation
import org.opentripplanner.model.plan.Itinerary
import org.opentripplanner.raptor.RaptorService
import org.opentripplanner.raptor.api.path.RaptorPath
import org.opentripplanner.raptor.spi.RaptorTripSchedule
import org.opentripplanner.routing.algorithm.mapping.RaptorPathToItineraryMapper
import org.opentripplanner.routing.algorithm.mapping.RouteRequestToFilterChainMapper
import org.opentripplanner.routing.algorithm.raptoradapter.router.street.AccessEgressRouter
import org.opentripplanner.routing.algorithm.raptoradapter.router.street.AccessEgressType
import org.opentripplanner.routing.algorithm.raptoradapter.router.street.DirectStreetRouter
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RaptorTransitData
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RoutingAccessEgress
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule
import org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.AccessEgressMapper
import org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.LookupStopIndexCallback
import org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.RaptorRequestMapper
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.DefaultTransitDataProviderFilter
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.RaptorRoutingRequestTransitData
import org.opentripplanner.routing.api.request.RouteRequest
import org.opentripplanner.routing.api.request.request.StreetRequest
import org.opentripplanner.routing.linking.LinkingContextRequest
import org.opentripplanner.routing.linking.mapping.LinkingContextRequestMapper
import org.opentripplanner.street.geometry.WgsCoordinate
import org.opentripplanner.street.linking.TemporaryVerticesContainer
import org.opentripplanner.street.model.StreetMode
import org.opentripplanner.transit.model.network.grouppriority.TransitGroupPriorityService
import org.opentripplanner.transit.service.TransitServiceResolver

/**
 * Which side of a transit search [streetReach] is computing: access (origin -> stop) or egress
 * (stop -> destination). Mirrors `AccessEgressType` (see the vendored
 * `AccessEgressRouter`/`AccessEgressType`), kept as our own enum since the wrapper's whole point
 * is to expose OTP's building blocks without leaking every OTP type into this signature.
 */
enum class ReachDirection { ACCESS, EGRESS }

/**
 * Building-block tier: thin wrappers over OTP's own routing classes, taking [RoutingEngine]'s
 * already-wired [RoutingEngine.services] so phase 3 never meets the twenty-argument constructors
 * these classes have upstream. See the spec's "The facade: `routing` module" section,
 * "Building-block tier" paragraph. Each wrapper below mirrors exactly how
 * `TransitRouter`/`RoutingWorker` (in `:otp-routing`) call the same OTP classes; no wrapper adds
 * routing logic of its own.
 *
 * **Temporary-vertex lifetime (I6):** [streetReach], [transitSearch] and [directRoute] each open
 * and close their own `TemporaryVerticesContainer` (`use { ... }`), unlike `RoutingWorker`, which
 * holds one open across its entire `route()` (access/egress, raptor, itinerary mapping, filter
 * chain, response mapping - see `RoutingWorker.java`'s own `try (var temporaryVerticesContainer =
 * ...)`). The [RoutingAccessEgress] objects a wrapper returns remain usable after its container
 * closes only because `TemporaryVerticesContainer.close` unlinks temporary edges from the
 * *permanent* graph vertices, while the `State` chain inside each `RoutingAccessEgress` keeps its
 * own plain object references regardless. That is an invariant of the current OTP version, not a
 * contract this tier enforces - do not assume it holds across an OTP upgrade without re-checking
 * `TemporaryVerticesContainer.close`. A caller composing several of these wrappers together (e.g.
 * feeding [streetReach]'s access/egress into [transitSearch]) must treat the composition as
 * "values passed after each call has returned", never as "temporary vertices still linked into a
 * graph another wrapper is about to search" - each wrapper's temporary vertices are gone by the
 * time control returns to the caller. [transitSearch] additionally builds its raptor request
 * inside a container it closes before running the actual raptor search, which would matter as
 * soon as via-locations are used (a later draft's concern, not this one's).
 */

/**
 * One street mode's reach from a single point, over OTP's `AccessEgressRouter`
 * (`routing/algorithm/raptoradapter/router/street/AccessEgressRouter.java`, wrapped the way
 * `AccessEgressFetcher.fetchAccessEgresses` calls it, minus that fetcher's flex/ride-hailing/
 * car-pooling extras, which this slice does not use). [from] is applied to [request]'s `from`
 * (for [ReachDirection.ACCESS]) or `to` (for [ReachDirection.EGRESS]) - overriding whatever
 * [request] itself carries there - and linked to the street graph with [mode], regardless of
 * [request]'s own access/egress preferences: each call is one street mode, one direction, one
 * point, which is exactly how Park & Ride's bike-access-plus-walk-egress (or this task's
 * mixed-mode composition test) works - two independent calls, never one `RouteRequest` trying to
 * express both modes/points through its own access/egress fields.
 *
 * **Origin/destination resolution deliberately still goes through OTP's real, unmodified
 * `AccessEgressRouter`/`LinkingContext` machinery, not a coordinate-snapping shortcut.** That's
 * this flood-fill access/egress search's own real requirement, not an oversight: `LinkingContext`
 * resolves [from] by splitting the nearest street *edge* at the exact query point into a real
 * `SplitterVertex` (`VertexLinker.linkVertexForRequest`, via `Graph.requireIndex()`/
 * `streetIndex.findEdges` -- see this function's own inline comment below), which is what
 * preserves real distance/duration fidelity for a flood-fill search's whole reachable-stop set.
 */
fun RoutingEngine.streetReach(
    from: WgsCoordinate,
    mode: StreetMode,
    maxDuration: Duration,
    direction: ReachDirection,
    request: RouteRequest,
): Collection<RoutingAccessEgress> {
    val location = GenericLocation.fromCoordinate(from)
    val delegateRequest = when (direction) {
        ReachDirection.ACCESS -> request.copyOf().withFrom(location).buildRequest()
        ReachDirection.EGRESS -> request.copyOf().withTo(location).buildRequest()
    }

    // AccessEgressRouter.findAccessEgresses resolves vertices via
    // linkingContext.findVertices(delegateRequest.from()/.to()) (see AccessEgressRouter.java:56-58),
    // so the LinkingContext must be built from delegateRequest's own (now-overridden) from/to -
    // both of which are real, distinct locations by construction, so no "same origin and
    // destination" collision arises the way it would from reusing one coordinate for both sides.
    val linkingRequest = LinkingContextRequest.of()
        .withFrom(delegateRequest.from())
        .withTo(delegateRequest.to())
        .withDirectMode(mode)
        .build()

    val accessEgressType = when (direction) {
        ReachDirection.ACCESS -> AccessEgressType.ACCESS
        ReachDirection.EGRESS -> AccessEgressType.EGRESS
    }
    val maxStopCount = delegateRequest.preferences().street().accessEgress().maxStopCountLimit().limitForMode(mode)

    return TemporaryVerticesContainer().use { container ->
        val linkingContext = services.linkingContextFactory.create(container, linkingRequest)
        val nearbyStops = AccessEgressRouter.findAccessEgresses(
            delegateRequest,
            mode,
            emptyList(),
            accessEgressType,
            maxDuration,
            maxStopCount,
            linkingContext,
        )
        AccessEgressMapper(TransitServiceResolver(services.transitService)).mapNearbyStops(nearbyStops)
    }
}

/**
 * A raptor transit search over [access]/[egress], wrapping `RaptorRoutingRequestTransitData`,
 * `RaptorRequestMapper` and `RaptorService` exactly the way `TransitRouter.route` (see
 * `routing/algorithm/raptoradapter/router/TransitRouter.java`, the `createRequestTransitDataProvider`
 * / "Prepare transit search" / "Transit routing using Raptor" sections) prepares and runs the same
 * search - minus the monitoring-removed `MeterRegistry` argument `RaptorRequestMapper.of` no
 * longer takes (see `:otp-routing`'s `VENDORED.md` section 4).
 *
 * **No lazy-bridge branch here (unlike bikebus's own `RoutingEngine`).** This project always loads
 * a whole graph into memory - there is no phone-memory-constrained lazy `.bxi` bridge to fall back
 * from - so the `RaptorTransitData` this search runs against is always
 * [scopedOrInstalledRaptorTransitDataForSearch]'s unconditional whole-network
 * `services.transitService.raptorTransitData`, the same one [RoutingEngine]'s constructor installs
 * (see its own "Construction side effect" KDoc).
 */
fun RoutingEngine.transitSearch(
    access: Collection<RoutingAccessEgress>,
    egress: Collection<RoutingAccessEgress>,
    request: RouteRequest,
): Collection<RaptorPath<TripSchedule>> {
    val workerRequest = services.requestPreProcessor.computeRequest(request)
    val transitSearchTimeZero = workerRequest.transitSearchTimeZero()
    val additionalSearchDays = workerRequest.additionalSearchDays()

    val transitGroupPriorityService = TransitGroupPriorityService.of(
        request.preferences().transit().relaxTransitGroupPriority(),
        request.journey().transit().priorityGroupsByAgency(),
        request.journey().transit().priorityGroupsGlobal(),
    )
    val raptorRequest = buildRaptorRequest<TripSchedule>(access, egress, request, transitSearchTimeZero)
    val raptorService = RaptorService(services.raptorConfig)

    return synchronized(services.scopedTransitLock) {
        val raptorTransitData = scopedOrInstalledRaptorTransitDataForSearch(access, egress, request, transitSearchTimeZero)
        val transitDataProvider = RaptorRoutingRequestTransitData(
            raptorTransitData,
            transitGroupPriorityService,
            transitSearchTimeZero,
            additionalSearchDays.additionalSearchDaysInPast(),
            additionalSearchDays.additionalSearchDaysInFuture(),
            DefaultTransitDataProviderFilter.ofRequest(request),
            request,
        )
        raptorService.route(raptorRequest, transitDataProvider).paths()
    }
}

/**
 * Maps raptor [paths] to OTP `Itinerary`s, wrapping `RaptorPathToItineraryMapper` the way
 * `TransitRouter.route`'s "Create itineraries" section does.
 *
 * **No lazy-bridge branch here either (see [transitSearch]'s own KDoc).** [paths] were produced by
 * a real search over the whole-network `RaptorTransitData`, and this function's own
 * [scopedOrInstalledRaptorTransitDataForPaths] returns that same instance unconditionally -
 * [RaptorPathToItineraryMapper] needs a concrete `RaptorTransitData` (real, unmodified OTP requires
 * one, not the generic SPI - see the design spec's "Itinerary construction" section), which this
 * function passes it directly rather than re-deriving any scoped subset.
 */
fun RoutingEngine.toItineraries(paths: Collection<RaptorPath<TripSchedule>>, request: RouteRequest): List<Itinerary> {
    val workerRequest = services.requestPreProcessor.computeRequest(request)
    val transitSearchTimeZero = workerRequest.transitSearchTimeZero()

    return synchronized(services.scopedTransitLock) { mapItineraries(paths, request, transitSearchTimeZero) }
}

/** [toItineraries]'s body under [Services.scopedTransitLock]: fetch the transit data, then map. */
private fun RoutingEngine.mapItineraries(
    paths: Collection<RaptorPath<TripSchedule>>,
    request: RouteRequest,
    transitSearchTimeZero: ZonedDateTime,
): List<Itinerary> {
    val raptorTransitData = scopedOrInstalledRaptorTransitDataForPaths(paths, transitSearchTimeZero)

    val mapper = RaptorPathToItineraryMapper<TripSchedule>(
        services.graph,
        services.transitService,
        services.streetDetailsService,
        raptorTransitData,
        transitSearchTimeZero,
        request,
    )
    return paths.map(mapper::createItinerary)
}

/**
 * The whole-network `RaptorTransitData` [transitSearch] runs its raptor search against. Unlike
 * bikebus's own `BuildingBlocks.kt` (which branches here on a real `transitIndexPaths` list to
 * build a small, scoped `RaptorTransitData` via a lazy `.bxi` bridge), this project's
 * [RoutingEngine] never has such a list to populate - it always loads a whole graph into memory -
 * so this is unconditionally the same `RaptorTransitData` [RoutingEngine]'s constructor installed
 * on [Services.transitRepository] (see that class's "Construction side effect" KDoc). [access],
 * [egress], [request] and [transitSearchTimeZero] are accepted only to keep this function's own
 * call site symmetrical with [scopedOrInstalledRaptorTransitDataForPaths]'s; none of them affect
 * the result.
 */
private fun RoutingEngine.scopedOrInstalledRaptorTransitDataForSearch(
    access: Collection<RoutingAccessEgress>,
    egress: Collection<RoutingAccessEgress>,
    request: RouteRequest,
    transitSearchTimeZero: ZonedDateTime,
): RaptorTransitData = services.transitService.raptorTransitData

/**
 * [toItineraries]'s own equivalent of [scopedOrInstalledRaptorTransitDataForSearch] - see that
 * function's KDoc for why this is always the whole-network `RaptorTransitData` here, never a
 * scoped rebuild. [paths] and [transitSearchTimeZero] are accepted only to keep this function's
 * call site symmetrical with [scopedOrInstalledRaptorTransitDataForSearch]'s; neither affects the
 * result.
 */
private fun RoutingEngine.scopedOrInstalledRaptorTransitDataForPaths(
    paths: Collection<RaptorPath<TripSchedule>>,
    transitSearchTimeZero: ZonedDateTime,
): RaptorTransitData = services.transitService.raptorTransitData

/**
 * Builds one raptor request over [access]/[egress]/[request], typed for whichever
 * [RaptorTripSchedule] implementation [T] is - always `TripSchedule` here ([transitSearch]'s own
 * real search; bikebus's own version of this function is also called with `DefaultTripSchedule`
 * for its lazy-bridge discovery phase, which this project has no equivalent of). Opens and closes
 * its own `TemporaryVerticesContainer`, same as [transitSearch] always has (see this file's own
 * top-level KDoc, "Temporary-vertex lifetime" paragraph).
 */
private inline fun <reified T : RaptorTripSchedule> RoutingEngine.buildRaptorRequest(
    access: Collection<RoutingAccessEgress>,
    egress: Collection<RoutingAccessEgress>,
    request: RouteRequest,
    transitSearchTimeZero: ZonedDateTime,
) = TemporaryVerticesContainer().use { container ->
    val linkingContext = services.linkingContextFactory.create(container, LinkingContextRequestMapper.map(request))
    val lookupStopIndexes = LookupStopIndexCallback { stopLocationId ->
        services.transitService.findStopOrChildStops(stopLocationId).stream().mapToInt { it.index }
    }
    RaptorRequestMapper.of<T>(
        request,
        transitSearchTimeZero,
        services.raptorConfig.isMultiThreaded,
        access,
        egress,
        services.viaTransferResolver,
        lookupStopIndexes,
        linkingContext,
    ).mapRaptorRequest()
}

/**
 * The direct (non-transit) street route from [from] to [to] by [mode], wrapping
 * `DirectStreetRouter` the way `RoutingWorker.routeDirectStreet` calls it. [from]/[to] are
 * applied to [request]'s `from`/`to` - overriding whatever [request] itself carries there -
 * since `DirectStreetRouter` (`straightLineDistanceIsWithinLimit`) and `GraphPathFinder` both
 * resolve their vertices via `linkingContext.findVertices(request.from()/.to())` on the exact
 * `RouteRequest` passed in, not from separate coordinate arguments.
 *
 * **Same deliberate choice as [streetReach]'s own KDoc explains: this still resolves [from]/[to]
 * through OTP's real, unmodified `LinkingContext`/`VertexLinker` edge-splitting.** A point-to-point
 * search's start/end fidelity depends on the same real `SplitterVertex` anchoring `streetReach`
 * needs.
 */
fun RoutingEngine.directRoute(from: WgsCoordinate, to: WgsCoordinate, mode: StreetMode, request: RouteRequest): List<Itinerary> {
    val directRequest = request.copyOf()
        .withFrom(GenericLocation.fromCoordinate(from))
        .withTo(GenericLocation.fromCoordinate(to))
        .withJourney { it.withDirect(StreetRequest(mode)) }
        .buildRequest()

    val linkingRequest = LinkingContextRequest.of()
        .withFrom(directRequest.from())
        .withTo(directRequest.to())
        .withDirectMode(mode)
        .build()

    return TemporaryVerticesContainer().use { container ->
        val linkingContext = services.linkingContextFactory.create(container, linkingRequest)
        DirectStreetRouter.route(
            services.graph,
            services.transitService,
            services.streetLimitationParametersService,
            services.vehicleRentalService,
            services.streetDetailsService,
            null,
            directRequest,
            linkingContext,
        )
    }
}

/**
 * Runs [itineraries] through OTP's own filter chain, wrapping `RouteRequestToFilterChainMapper`
 * the way `RoutingWorker.route`'s "Filter itineraries" section does, so a composed result (built
 * from the other building blocks) can be cleaned up the same way the reference tier's own
 * `route()` cleans its result - **with three caveats (I5)**, one per `null`/`false` argument
 * below where `RoutingWorker.java` passes a real value:
 *
 * - [earliestDepartureTimeUsed] `null` (`RoutingWorker` passes its own
 *   `earliestDepartureTimeUsed()`): filters that behave differently near the edge of the search
 *   window (e.g. `OutsideSearchWindowFilter`) see no earliest-departure boundary here.
 * - [searchWindowUsed] `null` (`RoutingWorker` passes its own `searchWindowUsed()`):
 *   `PagingFilter`/`OutsideSearchWindowFilter` do not crop a composed result to a search window,
 *   because this tier has no single search window to report - each wrapper call
 *   ([streetReach]/[transitSearch]) can use its own.
 * - [removeWalkAllTheWayResults] `false` (`RoutingWorker` passes
 *   `result.removeWalkAllTheWayResults() || removeWalkAllTheWayResultsFromDirectFlex`):
 *   `RemoveTransitIfStreetOnlyIsBetter`-style walk-only-itinerary removal does not run, so a
 *   composed result keeps walk-all-the-way itineraries the reference tier's `route()` would drop.
 *
 * Callers that need reference-tier-equivalent cropping should pass their own values for these
 * three (mirroring the request they built the composed search from) rather than assume `filter`
 * matches `route()` exactly. The trailing `{}` is `RouteRequestToFilterChainMapper`'s
 * `pageCursorInputSubscriber` - the reference tier's `route()` (through `RoutingWorker`) captures
 * this callback to build the *next* page's cursor from the filter chain's own cropping decisions;
 * this wrapper has no page cursor to report back into (there is no page cursor tier here, only a
 * single filter call), so the callback is a no-op rather than wired to anything.
 */
/**
 * The itinerary in this list that arrives soonest, or `null` if empty.
 *
 * Several composition call sites need "the single best route from A to B," not "whichever
 * itinerary a mode's own composition happens to place first." [bringBike]'s own result list is
 * `directItineraries + transitItineraries`, with no re-sort afterward -- its direct, non-transit
 * itinerary can and does sort ahead of a faster transit alternative (confirmed directly against the
 * real production graph: a direct route at 9807s sorting first, ahead of a real transit alternative
 * at 5907s). `.firstOrNull()` silently picked that slower, non-representative itinerary wherever a
 * caller actually wanted "the fastest" -- that part of this correction is sound.
 *
 * **Soonest arrival, not shortest own duration.** The first version of this function picked
 * `minByOrNull { it.totalDuration() }` instead, which looks equivalent but is wrong for exactly the
 * case this function exists for: a fresh sub-search run from a mid-trip instant (e.g. "continue on
 * from this hub") can return several non-dominated itineraries that depart at genuinely different
 * real times -- e.g. one leaving soon with a 90-minute ride versus one leaving an hour later with a
 * 60-minute ride, neither strictly dominating the other in OTP's own pareto filtering. Picking by
 * shortest own-[Itinerary.totalDuration] prefers the second (shorter span) even though it arrives
 * later overall -- confirmed directly: this exact shape caused a real hub-preferred Bring Bike
 * search to skip a real, well-timed bus 121 connection at "Rønde Busterminal" in favor of a bus
 * over an hour later with a marginally shorter ride, making the hub-preferred alternative look far
 * worse than it really was. Sorting by [Itinerary.endTimeAsInstant] instead picks whichever
 * itinerary actually gets there first, which is what "the single best route from A to B" means.
 */
fun List<Itinerary>.fastest(): Itinerary? = minByOrNull { it.endTimeAsInstant() }

fun RoutingEngine.filter(
    itineraries: List<Itinerary>,
    request: RouteRequest,
    earliestDepartureTimeUsed: Instant? = null,
    searchWindowUsed: Duration? = null,
    removeWalkAllTheWayResults: Boolean = false,
): List<Itinerary> {
    val filterChain = RouteRequestToFilterChainMapper.createFilterChain(
        request,
        services.transitService,
        services.transitAlertService,
        services.rideHailingServices,
        null, // emissionItineraryDecorator: stubbed sandbox extension, see STUBS.md
        null, // stopConsolidationService: stubbed sandbox extension, see STUBS.md
        earliestDepartureTimeUsed,
        searchWindowUsed,
        removeWalkAllTheWayResults,
    ) {}
    return filterChain.filter(itineraries)
}
