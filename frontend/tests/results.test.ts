import { test, expect } from "bun:test";
import { renderResults } from "../src/render/results";
import type { ItineraryDto } from "../src/types";

function freshRoot(): HTMLElement {
  document.body.innerHTML = `<div id="results"></div>`;
  return document.body.querySelector("#results")!;
}

const walkOnlyItinerary: ItineraryDto = {
  legs: [
    { mode: "WALK", distanceMeters: 500, durationSeconds: 400, fromLat: 0, fromLon: 0, toLat: 0, toLon: 0, fromName: "Origin", toName: "Destination", routeShortName: null, departureEpochSecond: 1_000_000_000 },
  ],
  exceedsBikeLimit: false,
  hasLongWalkEgress: false,
};

const busItinerary: ItineraryDto = {
  legs: [
    { mode: "WALK", distanceMeters: 300, durationSeconds: 240, fromLat: 0, fromLon: 0, toLat: 1, toLon: 1, fromName: "Origin", toName: "Stop A", routeShortName: null, departureEpochSecond: 1_000_000_000 },
    { mode: "BUS", distanceMeters: 5000, durationSeconds: 900, fromLat: 1, fromLon: 1, toLat: 2, toLon: 2, fromName: "Stop A", toName: "Stop B", routeShortName: "42", departureEpochSecond: 1_000_000_240 },
  ],
  exceedsBikeLimit: false,
  hasLongWalkEgress: true,
};

test("renders nothing when itineraries is null (no search run yet)", () => {
  const root = freshRoot();
  renderResults(root, null, null);
  expect(root.innerHTML).toBe("");
});

test("renders a no-results message for an empty itinerary list", () => {
  const root = freshRoot();
  renderResults(root, [], null);
  expect(root.textContent).toContain("No itineraries found");
});

test("a walk-only leg (null routeShortName) never renders the literal string 'null'", () => {
  const root = freshRoot();
  renderResults(root, [walkOnlyItinerary], null);
  expect(root.innerHTML).not.toContain("null");
  expect(root.textContent).toContain("Origin → Destination");
});

test("renders the long-walk badge with bikebus's exact copy, and a Maps link per leg", () => {
  const root = freshRoot();
  renderResults(root, [busItinerary], null);
  expect(root.textContent).toContain("Long final walk (15m)");
  expect(root.textContent).toContain("Bus 42");
  const mapsLinks = root.querySelectorAll("a.leg-maps-link");
  expect(mapsLinks).toHaveLength(2);
  expect(mapsLinks[1].getAttribute("href")).toContain("travelmode=transit");
  expect(mapsLinks[1].getAttribute("href")).toContain("departure_time=1000000240");
});

test("wraps a bare hub-name notice into a 'via {name}' sentence", () => {
  const root = freshRoot();
  renderResults(root, [walkOnlyItinerary], "Århus Rutebilstation");
  expect(root.textContent).toContain("via Århus Rutebilstation");
});

test("an itinerary card's legs start collapsed and expand on clicking Details", () => {
  const root = freshRoot();
  renderResults(root, [busItinerary], null);
  const legsList = root.querySelector<HTMLElement>("ul.legs")!;
  const detailsButton = root.querySelector<HTMLButtonElement>(".details-toggle")!;
  expect(legsList.hidden).toBe(true);
  expect(detailsButton.textContent).toBe("⌄");

  detailsButton.dispatchEvent(new Event("click", { bubbles: true }));
  expect(legsList.hidden).toBe(false);
  expect(detailsButton.textContent).toBe("⌃");

  detailsButton.dispatchEvent(new Event("click", { bubbles: true }));
  expect(legsList.hidden).toBe(true);
  expect(detailsButton.textContent).toBe("⌄");
});

test("HTML special characters in place names and hub notices are escaped, not rendered as markup", () => {
  const root = freshRoot();
  const itineraryWithSpecialChars: ItineraryDto = {
    legs: [
      { mode: "WALK", distanceMeters: 100, durationSeconds: 60, fromLat: 0, fromLon: 0, toLat: 1, toLon: 1, fromName: "Origin <script>", toName: "Stop & Away", routeShortName: null, departureEpochSecond: 1_000_000_000 },
    ],
    exceedsBikeLimit: false,
    hasLongWalkEgress: false,
  };
  renderResults(root, [itineraryWithSpecialChars], "Hub <Alert>");
  expect(root.innerHTML).not.toContain("<script>");
  expect(root.innerHTML).toContain("&lt;script&gt;");
  expect(root.innerHTML).toContain("&amp;");
  expect(root.innerHTML).toContain("&lt;Alert&gt;");
  expect(root.textContent).toContain("Origin <script>");
  expect(root.textContent).toContain("Stop & Away");
});
