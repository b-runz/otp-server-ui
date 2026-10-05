import type { ItineraryDto } from "./types";

export function formatDuration(seconds: number): string {
  const totalMinutes = Math.round(seconds / 60);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;
}

export function formatLegDistanceDuration(distanceMeters: number, durationSeconds: number): string | null {
  if (distanceMeters < 100) return null;
  if (distanceMeters < 1000) return `${Math.round(distanceMeters)}m · ${formatDuration(durationSeconds)}`;
  return `${Math.round(distanceMeters / 1000)}km · ${formatDuration(durationSeconds)}`;
}

export function formatRouteLabel(mode: string, shortName: string): string {
  switch (mode) {
    case "BUS":
    case "COACH":
    case "TROLLEYBUS":
      return `Bus ${shortName}`;
    case "RAIL":
      return `Train ${shortName}`;
    case "TRAM":
      return `Tram ${shortName}`;
    case "SUBWAY":
      return `Metro ${shortName}`;
    case "FERRY":
      return `Ferry ${shortName}`;
    default:
      return shortName;
  }
}

const MODE_ICONS: Record<string, string> = {
  BICYCLE: "🚲",
  WALK: "🚶",
  BUS: "🚌",
  COACH: "🚌",
  TROLLEYBUS: "🚌",
  RAIL: "🚆",
  TRAM: "🚊",
  SUBWAY: "🚇",
  FERRY: "⛴",
  CAR: "🚗",
  AIRPLANE: "✈",
};

export function iconForMode(mode: string): string {
  return MODE_ICONS[mode] ?? "🚏";
}

const TRANSIT_MODES = new Set([
  "BUS", "RAIL", "TRAM", "SUBWAY", "FERRY", "COACH", "TRANSIT",
  "TROLLEYBUS", "MONORAIL", "GONDOLA", "CABLE_CAR", "FUNICULAR", "AIRPLANE",
]);

export function travelModeFor(mode: string): "bicycling" | "walking" | "driving" | "transit" {
  if (mode === "BICYCLE") return "bicycling";
  if (mode === "WALK") return "walking";
  if (mode === "CAR") return "driving";
  return TRANSIT_MODES.has(mode) ? "transit" : "walking";
}

export function buildMapsUrl(
  fromLat: number, fromLon: number, toLat: number, toLon: number,
  mode: string, departureEpochSecond?: number | null,
): string {
  const travelMode = travelModeFor(mode);
  let url = `https://www.google.com/maps/dir/?api=1&origin=${fromLat},${fromLon}&destination=${toLat},${toLon}&travelmode=${travelMode}`;
  if (travelMode === "transit" && departureEpochSecond != null) {
    url += `&departure_time=${departureEpochSecond}`;
  }
  return url;
}

export function formatBikeLimitBadge(itinerary: ItineraryDto): string {
  const totalBikeMeters = itinerary.legs
    .filter((leg) => leg.mode === "BICYCLE")
    .reduce((sum, leg) => sum + leg.distanceMeters, 0);
  return `Over 10km bike limit (${Math.round(totalBikeMeters / 1000)}km)`;
}

export function formatLongWalkBadge(itinerary: ItineraryDto): string {
  const lastLeg = itinerary.legs[itinerary.legs.length - 1];
  return `Long final walk (${formatDuration(lastLeg.durationSeconds)})`;
}
