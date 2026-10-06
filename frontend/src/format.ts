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

// Clean monochrome line icons (stroke="currentColor", no fill) instead of colorful platform
// emoji -- each is a plain inline SVG so it inherits whatever text color applies, matching the
// rest of the UI's dark/line-art theme rather than rendering as a full-color pictograph.
function svgIcon(body: string): string {
  return `<svg viewBox="0 0 24 24" width="1.1em" height="1.1em" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`;
}

const MODE_ICONS: Record<string, string> = {
  BICYCLE: svgIcon(
    '<circle cx="6" cy="17" r="3.3"/><circle cx="18" cy="17" r="3.3"/>' +
    '<path d="M6 17 L10 9 H15 L18 17 M10 9 L12.5 5.5 H14.5 M10 9 L13 17"/>',
  ),
  WALK: svgIcon(
    '<circle cx="12" cy="4.5" r="1.8"/>' +
    '<path d="M12 7 L11 14 M11 10 L7 13 M11 10 L15.5 12 M11 14 L8 20 M11 14 L14.5 20"/>',
  ),
  BUS: svgIcon(
    '<rect x="4" y="5" width="16" height="11" rx="2.5"/>' +
    '<path d="M4 11 H20 M8 5 V16 M16 5 V16"/>' +
    '<circle cx="8" cy="18.3" r="1.5"/><circle cx="16" cy="18.3" r="1.5"/>',
  ),
  RAIL: svgIcon(
    '<rect x="5" y="4" width="14" height="12" rx="2.5"/>' +
    '<path d="M5 9.5 H19 M9 4 V13 M15 4 V13 M8 20 L6 22 M16 20 L18 22"/>',
  ),
  TRAM: svgIcon(
    '<rect x="5" y="6" width="14" height="10" rx="2.5"/>' +
    '<path d="M12 6 V2 M9 2 H15 M5 11 H19"/>' +
    '<circle cx="8.5" cy="18" r="1.4"/><circle cx="15.5" cy="18" r="1.4"/>',
  ),
  SUBWAY: svgIcon(
    '<rect x="5" y="4" width="14" height="11" rx="2.5"/>' +
    '<path d="M5 9.5 H19 M9 15 V17 M15 15 V17 M9 20 L12 23 L15 20"/>',
  ),
  FERRY: svgIcon(
    '<path d="M4 16 L20 16 L17 20 H7 Z"/>' +
    '<path d="M7 16 V10 H17 V16"/>' +
    '<path d="M12 10 V5 M10 5 H14"/>',
  ),
  CAR: svgIcon(
    '<rect x="3" y="11" width="18" height="6" rx="2"/>' +
    '<path d="M6 11 L8.5 6.5 H15.5 L18 11"/>' +
    '<circle cx="7.5" cy="18" r="1.6"/><circle cx="16.5" cy="18" r="1.6"/>',
  ),
  AIRPLANE: svgIcon('<path d="M21 3 L3 11 L10 13 L12 20 L14 14 L21 3 Z"/>'),
};

MODE_ICONS.COACH = MODE_ICONS.BUS;
MODE_ICONS.TROLLEYBUS = MODE_ICONS.BUS;

// Generic map-pin outline for any mode without its own icon above.
const FALLBACK_ICON = svgIcon('<path d="M12 21 C8 16 6 12.5 6 9 A6 6 0 0 1 18 9 C18 12.5 16 16 12 21 Z"/><circle cx="12" cy="9" r="2.2"/>');

export function iconForMode(mode: string): string {
  return MODE_ICONS[mode] ?? FALLBACK_ICON;
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
