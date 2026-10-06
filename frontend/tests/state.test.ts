import { test, expect } from "bun:test";
import {
  createInitialState, setSearchMode, setFieldQuery, resolveField, swapFromTo, setMaxTransfers,
  setItineraries, setError, setNearbyRoutes, setNearbyRoutesError,
} from "../src/state";

test("setFieldQuery updates the query and clears the field's resolved place/suggestions", () => {
  const state = createInitialState();
  const withResolved = resolveField(state, "from", { placeId: "p1", label: "A", lat: 1, lon: 2, isStreet: false });
  const withNewQuery = setFieldQuery(withResolved, "from", "something else");
  expect(withNewQuery.from.query).toBe("something else");
  expect(withNewQuery.from.resolved).toBeNull();
  expect(withNewQuery.from.suggestions).toEqual([]);
});

test("resolveField sets the query to the candidate's label", () => {
  const state = createInitialState();
  const resolved = resolveField(state, "to", { placeId: "p2", label: "Somewhere", lat: 3, lon: 4, isStreet: false });
  expect(resolved.to.query).toBe("Somewhere");
  expect(resolved.to.resolved).toEqual({ placeId: "p2", label: "Somewhere", lat: 3, lon: 4 });
  expect(resolved.to.suggestions).toEqual([]);
});

test("swapFromTo exchanges both fields' query and resolved place, and clears results", () => {
  const state = createInitialState();
  const from = resolveField(state, "from", { placeId: "p1", label: "A", lat: 1, lon: 1, isStreet: false });
  const both = resolveField(from, "to", { placeId: "p2", label: "B", lat: 2, lon: 2, isStreet: false });
  const swapped = swapFromTo(both);
  expect(swapped.from.query).toBe("B");
  expect(swapped.to.query).toBe("A");
  expect(swapped.from.resolved?.placeId).toBe("p2");
  expect(swapped.to.resolved?.placeId).toBe("p1");
});

test("setSearchMode is a pure update, leaving the rest of state untouched", () => {
  const state = createInitialState();
  const updated = setSearchMode(state, "park_and_ride");
  expect(updated.searchMode).toBe("park_and_ride");
  expect(updated.timeMode).toBe(state.timeMode);
});

test("createInitialState defaults maxTransfers to null (unlimited)", () => {
  expect(createInitialState().maxTransfers).toBeNull();
});

test("setMaxTransfers is a pure update, leaving the rest of state untouched", () => {
  const state = createInitialState();
  const updated = setMaxTransfers(state, 0);
  expect(updated.maxTransfers).toBe(0);
  expect(updated.searchMode).toBe(state.searchMode);
});

test("setMaxTransfers can set the value back to null (unlimited)", () => {
  const state = setMaxTransfers(createInitialState(), 2);
  const updated = setMaxTransfers(state, null);
  expect(updated.maxTransfers).toBeNull();
});

test("setItineraries (a normal search) clears any leftover drop-me-off state", () => {
  const withDropMeOff = {
    ...createInitialState(),
    nearbyRoutes: [{ routeGtfsId: "1:x", routeShortName: "42", stopIds: [], distanceMeters: 10 }],
    nearbyRoutesError: "some earlier error",
    connectResults: { "1:x": {} as any },
    connectErrors: { "1:y": "oops" },
  };

  const updated = setItineraries(withDropMeOff, [], null);

  expect(updated.nearbyRoutes).toBeNull();
  expect(updated.nearbyRoutesError).toBeNull();
  expect(updated.connectResults).toEqual({});
  expect(updated.connectErrors).toEqual({});
});

test("setError (a failed normal search) clears any leftover drop-me-off state", () => {
  const withDropMeOff = {
    ...createInitialState(),
    nearbyRoutes: [{ routeGtfsId: "1:x", routeShortName: "42", stopIds: [], distanceMeters: 10 }],
    connectResults: { "1:x": {} as any },
  };

  const updated = setError(withDropMeOff, "search failed");

  expect(updated.nearbyRoutes).toBeNull();
  expect(updated.connectResults).toEqual({});
});

test("setNearbyRoutes (starting drop-me-off) clears any leftover normal-search state", () => {
  const withSearchResults = {
    ...createInitialState(),
    itineraries: [{ legs: [], exceedsBikeLimit: false, hasLongWalkEgress: false }],
    notice: "via some hub",
    error: "an old error",
    searched: true,
  };

  const updated = setNearbyRoutes(withSearchResults, []);

  expect(updated.itineraries).toBeNull();
  expect(updated.notice).toBeNull();
  expect(updated.error).toBeNull();
  expect(updated.searched).toBe(false);
});

test("setNearbyRoutesError (a failed drop-me-off lookup) clears any leftover normal-search state", () => {
  const withSearchResults = {
    ...createInitialState(),
    itineraries: [{ legs: [], exceedsBikeLimit: false, hasLongWalkEgress: false }],
    searched: true,
  };

  const updated = setNearbyRoutesError(withSearchResults, "lookup failed");

  expect(updated.itineraries).toBeNull();
  expect(updated.searched).toBe(false);
});
