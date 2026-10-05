import { test, expect, mock } from "bun:test";
import { renderNearbyRoutes } from "../src/render/dropMeOff";
import type { NearbyRouteDto, ConnectResponse } from "../src/types";

function freshRoot(): HTMLElement {
  document.body.innerHTML = `<div id="nearby-routes"></div>`;
  return document.body.querySelector("#nearby-routes")!;
}

const route: NearbyRouteDto = { routeGtfsId: "route-42", routeShortName: "42", stopIds: ["stop-1"], distanceMeters: 120 };

test("renders an unreachable error message for a route that had one, without crashing", () => {
  const root = freshRoot();
  renderNearbyRoutes(root, [route], null, {}, { "route-42": "No route was found to that stop." }, () => {});
  expect(root.textContent).toContain("No route was found to that stop.");
});

test("clicking a route button calls onSelectRoute with that route", () => {
  const root = freshRoot();
  const onSelectRoute = mock((_r: NearbyRouteDto) => {});
  renderNearbyRoutes(root, [route], null, {}, {}, onSelectRoute);
  const button = root.querySelector<HTMLButtonElement>('button[data-route-id="route-42"]')!;
  button.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onSelectRoute).toHaveBeenCalledWith(route);
});

test("renders the connect result's flag-stop comparison and hub notice when present", () => {
  const root = freshRoot();
  const connectResult: ConnectResponse = {
    itinerary: {
      legs: [{ mode: "BUS", distanceMeters: 2000, durationSeconds: 600, fromLat: 0, fromLon: 0, toLat: 1, toLon: 1, fromName: "A", toName: "B", routeShortName: "42", departureEpochSecond: 1000 }],
      exceedsBikeLimit: false,
      hasLongWalkEgress: false,
    },
    flagStopInfo: {
      flagLat: 1.1, flagLon: 1.1,
      officialFinalLegDistanceMeters: 400, officialFinalLegDurationSeconds: 300,
      flagStopDistanceMeters: 100, flagStopDurationSeconds: 60,
    },
    extraRideSeconds: 120,
    hubName: "Århus Rutebilstation",
  };
  renderNearbyRoutes(root, [route], null, { "route-42": connectResult }, {}, () => {});
  expect(root.textContent).toContain("via Århus Rutebilstation");
  expect(root.textContent).toContain("2m");
  expect(root.querySelector(".flag-stop-comparison")).not.toBeNull();
});
