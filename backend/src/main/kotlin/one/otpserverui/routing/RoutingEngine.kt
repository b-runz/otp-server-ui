package one.otpserverui.routing

import java.time.Duration
import org.opentripplanner.ext.flex.FlexParameters
import org.opentripplanner.ext.ridehailing.RideHailingService
import org.opentripplanner.framework.time.ZoneIdFallback
import org.opentripplanner.raptor.api.request.RaptorEnvironment
import org.opentripplanner.raptor.api.request.RaptorTuningParameters
import org.opentripplanner.raptor.configure.RaptorConfig
import org.opentripplanner.routing.algorithm.RequestPreProcessor
import org.opentripplanner.routing.algorithm.RoutingWorker
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TransitTuningParameters
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule
import org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.RaptorTransitDataMapper
import org.opentripplanner.routing.api.request.RouteRequest
import org.opentripplanner.routing.api.request.RouteRequestBuilder
import org.opentripplanner.routing.api.response.RoutingResponse
import org.opentripplanner.routing.impl.TransitAlertServiceImpl
import org.opentripplanner.routing.linking.LinkingContextFactory
import org.opentripplanner.routing.linking.internal.VertexCreationService
import org.opentripplanner.routing.services.TransitAlertService
import org.opentripplanner.routing.via.service.DefaultViaCoordinateTransferFactory
import org.opentripplanner.service.streetdetails.internal.DefaultStreetDetailsRepository
import org.opentripplanner.service.streetdetails.internal.DefaultStreetDetailsService
import org.opentripplanner.service.vehiclerental.GeofencingZoneService
import org.opentripplanner.service.vehiclerental.internal.DefaultVehicleRentalRepository
import org.opentripplanner.service.vehiclerental.internal.DefaultVehicleRentalService
import org.opentripplanner.street.graph.Graph
import org.opentripplanner.street.internal.DefaultStreetRepository
import org.opentripplanner.street.linking.VertexLinker
import org.opentripplanner.street.linking.VisibilityMode
import org.opentripplanner.street.model.StreetConstants
import org.opentripplanner.street.model.StreetMode
import org.opentripplanner.street.service.DefaultStreetLimitationParametersService
import org.opentripplanner.transfer.regular.TransferRepository
import org.opentripplanner.transfer.regular.internal.DefaultTransferRepository
import org.opentripplanner.transfer.regular.internal.DefaultTransferService
import org.opentripplanner.transfer.regular.internal.TransferIndex
import org.opentripplanner.transit.service.DefaultTransitService
import org.opentripplanner.transit.service.TransitRepository

/**
 * Wires OTP's own routing services once over a fixed `graph`/`transitRepository` pair, mirroring
 * `DefaultRoutingService`'s construction (see the spec's "The facade: `routing` module" section).
 * Safe to call from multiple threads once constructed (see "Thread safety" below) - **construction
 * itself is not**: see the constructor-side-effect note below. Reference tier only: [requestBuilder]
 * pre-loaded with the app's [RoutingDefaults], and [route] through OTP's own `RoutingWorker`,
 * filter chain included.
 *
 * **Construction side effect (I2):** if [transitRepository] does not already have
 * `RaptorTransitData` installed, this constructor computes and installs it (via
 * [RaptorTransitDataMapper], from [transferRepository] and [EmbeddedTransitTuningParameters]) on
 * the shared [transitRepository] instance the caller passed in - a mutation visible to any other
 * `RoutingEngine` (or any other reader) built over the same repository. Two engines constructed
 * over one [transitRepository] with *different* [transferRepository] arguments: whichever is
 * built first wins, and the second silently inherits the first's raptor data (transfers
 * included). This check-then-act (`raptorTransitData == null` then `initRaptorTransitData(...)`)
 * is not atomic, so constructing two engines over the same repository from different threads
 * races. **Only construct one `RoutingEngine` per `TransitRepository`.**
 *
 * This project (`otp-server-ui`) always loads a whole graph into memory (no phone-memory
 * constraint, unlike bikebus's Android app), so unlike bikebus's own `RoutingEngine` this class has
 * no `transitIndexPaths` constructor parameter and no lazy, per-request two-phase transit-data
 * bridge: `BuildingBlocks.kt`'s `transitSearch`/`toItineraries` always run their raptor searches
 * against the one whole-network `RaptorTransitData` this constructor installs below.
 *
 * **One engine per `Graph`, too:** construction also creates a [VertexLinker], and `VertexLinker`'s
 * own Javadoc states only one should be active on a graph at any given time
 * (`street/linking/VertexLinker.java`). **Construct at most one `RoutingEngine` per `Graph`.**
 *
 * **Thread safety (final-review fix wave, Finding 3).** Once construction has completed without
 * racing another engine over the same repository/graph, [route] and every building-block function
 * may be called on one instance from multiple threads concurrently. `BuildingBlocks.transitSearch`/
 * `toItineraries` still serialize their raptor searches under one engine-wide lock
 * ([Services.scopedTransitLock]), matching [route]'s own use of the same lock for its whole transit
 * search, even though there is no scoped-bridge mutation left to protect here - see that lock's own
 * KDoc. The parts that only read the graph, the `SiteRepository` or this engine's
 * construction-time `TransitService` snapshot - `streetReach`, `directRoute`, `filter`, `stopsNear`,
 * and `transitSearch`'s own phase-1 discovery search, which uses a private reader/provider - run
 * fully in parallel. The lock is per engine instance, so it says nothing about two engines built
 * over one repository (see above: don't).
 */
class RoutingEngine(
    private val graph: Graph,
    private val transitRepository: TransitRepository,
    private val transferRepository: TransferRepository = DefaultTransferRepository(TransferIndex()),
) {

    private val transitService = DefaultTransitService(transitRepository)
    private val transitAlertService: TransitAlertService = TransitAlertServiceImpl()
    private val raptorTuningParameters = object : RaptorTuningParameters {}
    private val raptorConfig = RaptorConfig<TripSchedule>(raptorTuningParameters, object : RaptorEnvironment {})

    // De-duplicated per Ruling 19: this used to be a second anonymous TransitTuningParameters
    // object here (and a near-identical one in ToyNetwork's test fixture) with two values wrong
    // (maxSearchWindow() = null, pagingSearchWindowAdjustments() = emptyList()), which crashed
    // route() with an NPE whenever the filter chain cropped itineraries - see C1 and
    // EmbeddedTransitTuningParameters's own KDoc for the OTP-cited defaults.
    private val transitTuningParameters: TransitTuningParameters = EmbeddedTransitTuningParameters

    private val streetLimitationParametersService =
        DefaultStreetLimitationParametersService(DefaultStreetRepository())
    private val vehicleRentalService = DefaultVehicleRentalService(DefaultVehicleRentalRepository())
    private val streetDetailsService = DefaultStreetDetailsService(DefaultStreetDetailsRepository())

    // This slice has no graph_builder (see STUBS.md / VENDORED.md: transfer computation is a
    // graph-build-time concern there), so RoutingEngine cannot compute a TransferRepository
    // itself; it accepts one as a constructor parameter instead, defaulting to an empty one for
    // callers that have none.
    private val transferService = DefaultTransferService(transferRepository)
    private val flexParameters = FlexParameters()
    private val rideHailingServices: List<RideHailingService> = emptyList()
    private val viaTransferResolver =
        DefaultViaCoordinateTransferFactory(graph, transitService, Duration.ofMinutes(15))

    private val linkingContextFactory: LinkingContextFactory
    private val requestPreProcessor: RequestPreProcessor

    init {
        // See the class KDoc's "Construction side effect" note: a caller that has already
        // installed RaptorTransitData on transitRepository (over its own transferRepository,
        // possibly a different one than this engine was given) is left alone rather than
        // overwritten; otherwise this computes and installs it here, from the transferRepository
        // this engine was given. ToyNetwork deliberately does NOT pre-install raptor data (see its
        // own comment) precisely so this branch - the engine's own path - is the one every test
        // exercises, rather than a branch no test ever takes.
        if (transitRepository.raptorTransitData == null) {
            transitRepository.initRaptorTransitData(
                RaptorTransitDataMapper.map(transitTuningParameters, transitRepository, transferRepository)
            )
        }

        val vertexLinker = VertexLinker(
            graph,
            GeofencingZoneService.EMPTY,
            VisibilityMode.COMPUTE_AREA_VISIBILITY_LINES,
            StreetConstants.DEFAULT_MAX_AREA_NODES,
            true,
        )
        linkingContextFactory = LinkingContextFactory(graph, VertexCreationService(vertexLinker))

        val zoneId = ZoneIdFallback.zoneId(transitService.timeZone)
        requestPreProcessor = RequestPreProcessor(transitService, raptorTuningParameters, zoneId)
    }

    /**
     * The engine's already-wired OTP services, exposed only so the building-block tier
     * ([one.otpserverui.routing]'s `BuildingBlocks.kt`) can call OTP's own routing classes
     * directly with them, the way [route] itself does through `RoutingWorker`, without
     * re-wiring anything. Not for use outside this module: phase 3 goes through the building
     * blocks, never through this accessor directly.
     */
    internal val services: Services = Services(
        graph = graph,
        transitRepository = transitRepository,
        transitService = transitService,
        transitAlertService = transitAlertService,
        raptorConfig = raptorConfig,
        raptorTuningParameters = raptorTuningParameters,
        transitTuningParameters = transitTuningParameters,
        streetLimitationParametersService = streetLimitationParametersService,
        vehicleRentalService = vehicleRentalService,
        streetDetailsService = streetDetailsService,
        transferService = transferService,
        flexParameters = flexParameters,
        rideHailingServices = rideHailingServices,
        viaTransferResolver = viaTransferResolver,
        linkingContextFactory = linkingContextFactory,
        requestPreProcessor = requestPreProcessor,
    )

    /**
     * A [RouteRequestBuilder] pre-loaded with the app's routing defaults: `numItineraries` 5,
     * `transferPenalty` 900, bike and bike-to-park access duration 2000 s. Bike preferences keep
     * OTP's default `SAFE_STREETS`; callers select `TRIANGLE` themselves when slope should count.
     */
    fun requestBuilder(): RouteRequestBuilder {
        val builder = RouteRequest.of()
        builder.withNumItineraries(RoutingDefaults.NUM_ITINERARIES)
        builder.withPreferences { preferences ->
            preferences.withTransfer { it.withCost(RoutingDefaults.TRANSFER_PENALTY_SECONDS) }
            preferences.withStreet { street ->
                street.withAccessEgress { accessEgress ->
                    accessEgress.withMaxDuration { maxDuration ->
                        maxDuration
                            .with(StreetMode.BIKE, RoutingDefaults.BIKE_ACCESS_MAX)
                            .with(StreetMode.BIKE_TO_PARK, RoutingDefaults.BIKE_TO_PARK_ACCESS_MAX)
                    }
                }
            }
            // This engine never wires an updater (see STUBS.md), so DefaultTransitService is
            // always constructed with a null realtime snapshot; TransitRouter otherwise asks for
            // the (always-null) realtime RaptorTransitData whenever a request does not opt out of
            // realtime updates, which throws away all scheduled data. Ignoring realtime updates by
            // default routes every request against the always-present scheduled RaptorTransitData.
            preferences.withTransit { it.withIgnoreRealtimeUpdates(true) }
        }
        return builder
    }

    /**
     * Routes [request] through OTP's own `RoutingWorker`, filter chain included. `RouteRequest`,
     * `RoutingResponse`, `Itinerary` and `Leg` are OTP's types, returned unchanged;
     * `RoutingValidationException`/`RoutingErrorCode` pass through untranslated.
     */
    fun route(request: RouteRequest): RoutingResponse {
        // Mirrors DefaultRoutingService.route(): validate before mapping/pre-processing, so an
        // unresolvable origin/destination fails here with OTP's own validation error instead of
        // deeper and less legibly inside RoutingWorker.
        request.validateOriginAndDestination()
        return synchronized(services.scopedTransitLock) { routeWithWorker(request) }
    }

    private fun routeWithWorker(request: RouteRequest): RoutingResponse {
        val workerRequest = requestPreProcessor.computeRequest(request)
        val worker = RoutingWorker(
            transitService,
            transitAlertService,
            graph,
            raptorConfig,
            streetLimitationParametersService,
            vehicleRentalService,
            streetDetailsService,
            transferService,
            flexParameters,
            rideHailingServices,
            null,
            null,
            viaTransferResolver,
            null,
            null,
            null,
            linkingContextFactory,
            transitTuningParameters,
            raptorTuningParameters,
            workerRequest,
        )
        return worker.route()
    }
}

/**
 * The subset of [RoutingEngine]'s wired OTP services the building-block tier needs, gathered
 * behind one `internal` accessor ([RoutingEngine.services]) instead of a constructor with one
 * parameter per service. Every field here is one of [RoutingEngine]'s own private vals; nothing
 * is computed specially for this holder.
 */
internal class Services(
    val graph: Graph,
    val transitRepository: TransitRepository,
    val transitService: DefaultTransitService,
    val transitAlertService: TransitAlertService,
    val raptorConfig: RaptorConfig<TripSchedule>,
    val raptorTuningParameters: RaptorTuningParameters,
    val transitTuningParameters: TransitTuningParameters,
    val streetLimitationParametersService: DefaultStreetLimitationParametersService,
    val vehicleRentalService: DefaultVehicleRentalService,
    val streetDetailsService: DefaultStreetDetailsService,
    val transferService: DefaultTransferService,
    val flexParameters: FlexParameters,
    val rideHailingServices: List<RideHailingService>,
    val viaTransferResolver: DefaultViaCoordinateTransferFactory,
    val linkingContextFactory: LinkingContextFactory,
    val requestPreProcessor: RequestPreProcessor,
) {
    /**
     * Serializes every mutation of [transitRepository] together with every read of its result -
     * see [RoutingEngine]'s "Thread safety" KDoc. One per engine; a plain monitor, since this
     * tier's functions are blocking, not `suspend`.
     */
    val scopedTransitLock: Any = Any()
}
