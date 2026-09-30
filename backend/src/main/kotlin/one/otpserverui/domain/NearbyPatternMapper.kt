package one.otpserverui.domain

import one.otpserverui.model.LegGeometryDto
import one.otpserverui.model.PatternDto
import one.otpserverui.model.PatternRouteDto
import one.otpserverui.model.StopRefDto
import one.otpserverui.routing.NearbyPattern

/**
 * Maps a [NearbyPattern] (the embedded engine's [one.otpserverui.routing.stopsNear] result shape)
 * 1:1 onto [PatternDto], the shape [NearbyRoutesFinder.rankCandidates] already consumes -- so the
 * embedded engine's `findNearbyRoutes` path can feed `rankCandidates` exactly like bikebus's now-
 * retired GraphQL path did. `patternGeometry.length` isn't read anywhere in [NearbyRoutesFinder]
 * (only `.points` is), so it's carried straight from [NearbyPattern.patternGeometryPointCount] --
 * itself already populated from `PolylineEncoder.encodeGeometry`'s own reported point count --
 * rather than re-decoding [patternGeometryPoints] just to count its points again.
 */
fun NearbyPattern.toPatternDto(): PatternDto = PatternDto(
    route = PatternRouteDto(
        gtfsId = routeGtfsId,
        shortName = routeShortName,
        mode = routeMode,
        type = routeGtfsType,
    ),
    patternGeometry = LegGeometryDto(
        points = patternGeometryPoints,
        length = patternGeometryPointCount,
    ),
    stops = stopGtfsIds.map { StopRefDto(gtfsId = it) },
)
