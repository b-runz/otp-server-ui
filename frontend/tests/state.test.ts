import { test, expect } from "bun:test";
import { createInitialState, setSearchMode, setFieldQuery, resolveField, swapFromTo } from "../src/state";

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
