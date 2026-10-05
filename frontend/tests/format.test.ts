import { test, expect } from "bun:test";
import {
  formatDuration, formatLegDistanceDuration, formatRouteLabel, iconForMode,
  travelModeFor, buildMapsUrl, formatBikeLimitBadge, formatLongWalkBadge,
} from "../src/format";
import type { ItineraryDto } from "../src/types";

test("formatDuration matches bikebus's exact format", () => {
  expect(formatDuration(72 * 60)).toBe("1h 12m");
  expect(formatDuration(45 * 60)).toBe("45m");
  expect(formatDuration(60 * 60)).toBe("1h 0m");
});

test("formatLegDistanceDuration matches bikebus's exact thresholds", () => {
  expect(formatLegDistanceDuration(50, 30)).toBeNull();
  expect(formatLegDistanceDuration(250, 180)).toBe("250m · 3m");
  expect(formatLegDistanceDuration(2500, 600)).toBe("3km · 10m");
});

test("formatRouteLabel matches bikebus's exact mode-to-label mapping", () => {
  expect(formatRouteLabel("BUS", "42")).toBe("Bus 42");
  expect(formatRouteLabel("COACH", "X1")).toBe("Bus X1");
  expect(formatRouteLabel("RAIL", "R")).toBe("Train R");
  expect(formatRouteLabel("TRAM", "1")).toBe("Tram 1");
  expect(formatRouteLabel("SUBWAY", "M1")).toBe("Metro M1");
  expect(formatRouteLabel("FERRY", "F1")).toBe("Ferry F1");
  expect(formatRouteLabel("BICYCLE", "n/a")).toBe("n/a");
});

test("iconForMode has a generic fallback for unmapped modes", () => {
  expect(iconForMode("BICYCLE")).toBe("🚲");
  expect(iconForMode("SCOOTER")).toBe("🚏");
});

test("travelModeFor matches bikebus's exact mapping including the transit set", () => {
  expect(travelModeFor("BICYCLE")).toBe("bicycling");
  expect(travelModeFor("WALK")).toBe("walking");
  expect(travelModeFor("CAR")).toBe("driving");
  expect(travelModeFor("BUS")).toBe("transit");
  expect(travelModeFor("SCOOTER")).toBe("walking");
});

test("buildMapsUrl matches bikebus's exact URL template, departure_time only for transit", () => {
  const walkUrl = buildMapsUrl(56.1, 10.1, 56.2, 10.2, "WALK", 1234567890);
  expect(walkUrl).toBe("https://www.google.com/maps/dir/?api=1&origin=56.1,10.1&destination=56.2,10.2&travelmode=walking");

  const transitUrl = buildMapsUrl(56.1, 10.1, 56.2, 10.2, "BUS", 1234567890);
  expect(transitUrl).toBe("https://www.google.com/maps/dir/?api=1&origin=56.1,10.1&destination=56.2,10.2&travelmode=transit&departure_time=1234567890");
});

const fixtureItinerary: ItineraryDto = {
  legs: [
    { mode: "BICYCLE", distanceMeters: 12000, durationSeconds: 1800, fromLat: 0, fromLon: 0, toLat: 0, toLon: 0, fromName: "A", toName: "B", routeShortName: null, departureEpochSecond: 0 },
    { mode: "WALK", distanceMeters: 1200, durationSeconds: 1080, fromLat: 0, fromLon: 0, toLat: 0, toLon: 0, fromName: "B", toName: "C", routeShortName: null, departureEpochSecond: 1800 },
  ],
  exceedsBikeLimit: true,
  hasLongWalkEgress: true,
};

test("formatBikeLimitBadge sums BICYCLE-mode legs only, matching the backend's own formula", () => {
  expect(formatBikeLimitBadge(fixtureItinerary)).toBe("Over 10km bike limit (12km)");
});

test("formatLongWalkBadge reads the final leg's duration", () => {
  expect(formatLongWalkBadge(fixtureItinerary)).toBe("Long final walk (18m)");
});
