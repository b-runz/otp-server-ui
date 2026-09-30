package one.otpserverui.routing

import java.time.Duration
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TransitTuningParameters
import org.opentripplanner.routing.api.request.RouteRequest
import org.opentripplanner.transit.model.site.StopTransferPriority

/**
 * The single, de-duplicated implementation of OTP's `TransitTuningParameters` for this engine,
 * used by [RoutingEngine] (see Ruling 19). `TransitTuningParameters` itself has no defaults -
 * every member is abstract - so its real defaults live in OTP's
 * `standalone.config.routerconfig.TransitRoutingConfig`, which this slice stubs out (see
 * `otp-routing/VENDORED.md`/`STUBS.md`). The values below are that config class's own defaults,
 * copied by hand and cited against
 * `OpenTripPlanner/application/src/main/java/org/opentripplanner/standalone/config/routerconfig/TransitRoutingConfig.java`
 * (upstream, read-only, commit 61a3af6798) at the time this was written:
 *
 * - [enableStopTransferPriority] `false` - `TransitRoutingConfig.java:268-270` returns
 *   `stopBoardAlightDuringTransferCost != null`; that map is only ever populated by config that
 *   this slice never parses, so it is always null and the method always returns `false` there.
 * - [stopBoardAlightDuringTransferCost] `0` for every key - moot given
 *   [enableStopTransferPriority] is `false` (`RaptorTransitDataMapper`/callers only consult this
 *   when priorities are enabled), but `0` is what `TransitRoutingConfig.java:154` documents as
 *   the "preferred"/no-op cost, i.e. the least surprising stand-in value.
 * - [transferCacheMaxSize] `25` - `TransitRoutingConfig.java:170`, `.asInt(25)`.
 * - [maxSearchWindow] `Duration.ofHours(24)` - `TransitRoutingConfig.java:233`,
 *   `.asDuration(Duration.ofHours(24))`.
 * - [pagingSearchWindowAdjustments] `TransitTuningParameters.PAGING_SEARCH_WINDOW_ADJUSTMENTS`
 *   (`"4h 2h 1h 30m 20m 10m"`) - `TransitRoutingConfig.java:216`,
 *   `.asDurations(PAGING_SEARCH_WINDOW_ADJUSTMENTS)`, the interface's own public constant
 *   (`TransitTuningParameters.java:10`).
 * - [transferCacheRequests] empty list - upstream's own default
 *   (`TransitRoutingConfig.java:196-199`) is `List.of(routingRequestDefaults)`, one `RouteRequest`
 *   built from `router-config.json`'s `routingDefaults` section; this slice has no
 *   `standalone.config` and therefore no such default request to seed the cache with, so an empty
 *   list is used instead (Ruling 19) - the transfer cache (bounded by
 *   [transferCacheMaxSize] above) is simply never pre-filled, and is populated lazily from actual
 *   requests instead.
 *
 * C1: this object replaces an anonymous `TransitTuningParameters` object in [RoutingEngine] that
 * got [maxSearchWindow] and [pagingSearchWindowAdjustments] wrong (`null` and `emptyList()`
 * respectively), which crashed `RoutingEngine.route()` with an NPE on any request whose filter
 * chain cropped itineraries (see `PagingSearchWindowAdjuster.normalizeSearchWindow`, which
 * unconditionally dereferences `maxSearchWindow.getSeconds()` once `NumItinerariesFilter` has
 * produced a page cut).
 */
object EmbeddedTransitTuningParameters : TransitTuningParameters {
    override fun enableStopTransferPriority() = false

    override fun stopBoardAlightDuringTransferCost(key: StopTransferPriority) = 0

    override fun transferCacheMaxSize() = 25

    override fun maxSearchWindow(): Duration = Duration.ofHours(24)

    override fun pagingSearchWindowAdjustments(): List<Duration> =
        TransitTuningParameters.PAGING_SEARCH_WINDOW_ADJUSTMENTS

    override fun transferCacheRequests(): List<RouteRequest> = emptyList()
}
