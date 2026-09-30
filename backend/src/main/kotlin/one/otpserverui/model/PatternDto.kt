package one.otpserverui.model

import kotlinx.serialization.Serializable

/**
 * Ported (verbatim, package-renamed) from bikebus's own
 * `one.brj.bikebus.network.OtpDto` -- only the plain DTO shapes
 * [PatternDto]/[PatternRouteDto]/[LegGeometryDto]/[StopRefDto] consumed by
 * `NearbyRoutesFinder.rankCandidates`/`NearbyPatternMapper`, kept as plain
 * `@Serializable` data classes with zero Retrofit/GraphQL annotations, exactly
 * as they were on bikebus's own side. Bikebus's GraphQL-only DTOs
 * (`GraphQlRequest`, `PlanVariables`, `LegDto`, etc., also declared in that
 * same source file) are deliberately NOT ported here: this project has no
 * GraphQL dependency (see this task's brief), and `DropMeOff.kt`'s
 * `connectByFlaggingABus` calls `RoutingEngine.directRoute` directly instead
 * of bikebus's own `otpApi`/`OtpQueryBuilder` GraphQL round-trip.
 */
@Serializable
data class PatternDto(
    val route: PatternRouteDto,
    val patternGeometry: LegGeometryDto,
    val stops: List<StopRefDto>
)

@Serializable
data class PatternRouteDto(val gtfsId: String, val shortName: String? = null, val mode: String, val type: Int? = null)

@Serializable
data class LegGeometryDto(val points: String, val length: Int)

@Serializable
data class StopRefDto(val gtfsId: String)
