import type { ItineraryDto, LegDto } from "../types";
import {
  formatDuration, formatLegDistanceDuration, formatRouteLabel, iconForMode,
  buildMapsUrl, formatBikeLimitBadge, formatLongWalkBadge,
} from "../format";

const TIME_FORMATTER = new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit", hour12: false });

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function legRowHtml(leg: LegDto): string {
  const routeLabel = leg.routeShortName ? formatRouteLabel(leg.mode, leg.routeShortName) : null;
  const distanceDuration = formatLegDistanceDuration(leg.distanceMeters, leg.durationSeconds);
  const subtitle = [routeLabel, distanceDuration].filter((part): part is string => part != null).join(" · ");
  const mapsUrl = buildMapsUrl(leg.fromLat, leg.fromLon, leg.toLat, leg.toLon, leg.mode, leg.departureEpochSecond);
  return `
    <li class="leg">
      <span class="leg-icon" aria-hidden="true">${iconForMode(leg.mode)}</span>
      <span class="leg-headline">${escapeHtml(leg.fromName ?? "?")} → ${escapeHtml(leg.toName ?? "?")}</span>
      ${subtitle ? `<span class="leg-subtitle">${subtitle}</span>` : ""}
      <a class="leg-maps-link" href="${mapsUrl}" target="_blank" rel="noopener noreferrer">Maps</a>
    </li>
  `;
}

function itineraryCardHtml(itinerary: ItineraryDto): string {
  const first = itinerary.legs[0];
  const last = itinerary.legs[itinerary.legs.length - 1];
  const departureMillis = first.departureEpochSecond * 1000;
  const arrivalEpochSecond = last.departureEpochSecond + last.durationSeconds;
  const arrivalMillis = arrivalEpochSecond * 1000;
  const totalDurationSeconds = arrivalEpochSecond - first.departureEpochSecond;

  const badges: string[] = [];
  if (itinerary.exceedsBikeLimit) {
    badges.push(`<p class="badge badge-warning">${formatBikeLimitBadge(itinerary)}</p>`);
  }
  if (itinerary.hasLongWalkEgress) {
    badges.push(`<p class="badge badge-info">${formatLongWalkBadge(itinerary)}</p>`);
  }

  return `
    <li class="itinerary-card">
      <p class="itinerary-time">${TIME_FORMATTER.format(new Date(departureMillis))} → ${TIME_FORMATTER.format(new Date(arrivalMillis))}</p>
      <p class="itinerary-duration">${formatDuration(totalDurationSeconds)}</p>
      <button type="button" class="details-toggle">Details ▸</button>
      <ul class="legs" hidden>${itinerary.legs.map(legRowHtml).join("")}</ul>
      ${badges.join("")}
    </li>
  `;
}

// Itinerary expand/collapse is deliberately pure UI state (spec's own Frontend section: "itinerary
// expand/collapse (pure UI state, no data change)") -- it toggles the `hidden` attribute directly,
// with no AppState reducer and no re-render, so collapsing one card never disturbs another or
// re-fetches anything.
function wireDetailsToggles(root: HTMLElement): void {
  for (const button of root.querySelectorAll<HTMLButtonElement>(".details-toggle")) {
    button.addEventListener("click", () => {
      const legsList = button.nextElementSibling as HTMLElement;
      legsList.hidden = !legsList.hidden;
      button.textContent = legsList.hidden ? "Details ▸" : "Details ▾";
    });
  }
}

export function renderResults(root: HTMLElement, itineraries: ItineraryDto[] | null, notice: string | null): void {
  if (itineraries == null) {
    root.innerHTML = "";
    return;
  }
  if (itineraries.length === 0) {
    root.innerHTML = `<p class="no-results">No itineraries found.</p>`;
    return;
  }
  const noticeHtml = notice ? `<p class="hub-notice">via ${escapeHtml(notice)}</p>` : "";
  root.innerHTML = `${noticeHtml}<ul class="itineraries">${itineraries.map(itineraryCardHtml).join("")}</ul>`;
  wireDetailsToggles(root);
}
