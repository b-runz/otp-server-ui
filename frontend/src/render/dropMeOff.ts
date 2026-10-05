import type { NearbyRouteDto, ConnectResponse } from "../types";
import { formatDuration, formatRouteLabel, iconForMode, buildMapsUrl } from "../format";

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function connectResultHtml(result: ConnectResponse): string {
  const lastLeg = result.itinerary.legs[result.itinerary.legs.length - 1];
  const legsHtml = result.itinerary.legs
    .map((leg) => `
      <li class="leg">
        <span class="leg-icon" aria-hidden="true">${iconForMode(leg.mode)}</span>
        <span class="leg-headline">${escapeHtml(leg.fromName ?? "?")} → ${escapeHtml(leg.toName ?? "?")}</span>
        <a class="leg-maps-link" href="${buildMapsUrl(leg.fromLat, leg.fromLon, leg.toLat, leg.toLon, leg.mode, leg.departureEpochSecond)}" target="_blank" rel="noopener noreferrer">Maps</a>
      </li>
    `)
    .join("");

  const hubHtml = result.hubName ? `<p class="hub-notice">via ${escapeHtml(result.hubName)}</p>` : "";
  const extraRideHtml = result.extraRideSeconds != null
    ? `<p class="extra-ride">+${formatDuration(result.extraRideSeconds)} vs. a fresh search</p>`
    : "";
  const flagHtml = result.flagStopInfo
    ? `
      <div class="flag-stop-comparison">
        <p>Official stop: ${Math.round(result.flagStopInfo.officialFinalLegDistanceMeters)}m / ${formatDuration(result.flagStopInfo.officialFinalLegDurationSeconds)}</p>
        <p>Flag point: ${Math.round(result.flagStopInfo.flagStopDistanceMeters)}m / ${formatDuration(result.flagStopInfo.flagStopDurationSeconds)}</p>
        <a href="${buildMapsUrl(lastLeg.toLat, lastLeg.toLon, result.flagStopInfo.flagLat, result.flagStopInfo.flagLon, "WALK")}" target="_blank" rel="noopener noreferrer">Maps to flag point</a>
      </div>
    `
    : "";

  return `<ul class="legs">${legsHtml}</ul>${hubHtml}${extraRideHtml}${flagHtml}`;
}

export function renderNearbyRoutes(
  root: HTMLElement,
  routes: NearbyRouteDto[] | null,
  error: string | null,
  connectResults: Record<string, ConnectResponse>,
  connectErrors: Record<string, string>,
  onSelectRoute: (route: NearbyRouteDto) => void,
): void {
  if (error) {
    root.innerHTML = `<p class="error">${error}</p>`;
    return;
  }
  if (routes == null) {
    root.innerHTML = "";
    return;
  }
  if (routes.length === 0) {
    root.innerHTML = `<p class="no-results">No routes found near that destination.</p>`;
    return;
  }

  root.innerHTML = `
    <ul class="nearby-routes">
      ${routes.map((route) => `
        <li class="nearby-route">
          <button type="button" data-route-id="${route.routeGtfsId}">
            ${route.routeShortName ? formatRouteLabel("BUS", route.routeShortName) : route.routeGtfsId}
            (${Math.round(route.distanceMeters)}m away)
          </button>
          <div class="connect-result" data-route-id="${route.routeGtfsId}"></div>
        </li>
      `).join("")}
    </ul>
  `;

  for (const route of routes) {
    root.querySelector<HTMLButtonElement>(`button[data-route-id="${route.routeGtfsId}"]`)!
      .addEventListener("click", () => onSelectRoute(route));

    const slot = root.querySelector<HTMLElement>(`.connect-result[data-route-id="${route.routeGtfsId}"]`)!;
    const connectError = connectErrors[route.routeGtfsId];
    const connectResult = connectResults[route.routeGtfsId];
    if (connectError) {
      slot.innerHTML = `<p class="error">${connectError}</p>`;
    } else if (connectResult) {
      slot.innerHTML = connectResultHtml(connectResult);
    }
  }
}
