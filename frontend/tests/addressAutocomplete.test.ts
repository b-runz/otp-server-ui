import { test, expect } from "bun:test";
import { createAutocompleteController } from "../src/addressAutocomplete";
import type { GeocodeCandidate } from "../src/types";

function candidate(label: string): GeocodeCandidate {
  return { placeId: label, label, lat: 0, lon: 0, isStreet: false };
}

test("fires geocode only at 3+ characters, after the debounce delay", async () => {
  const calls: string[] = [];
  const results: Record<string, GeocodeCandidate[]> = {};
  const controller = createAutocompleteController({
    debounceMs: 5,
    minQueryLength: 3,
    geocode: async (query) => { calls.push(query); return []; },
    onSuggestions: (field, suggestions) => { results[field] = suggestions; },
  });

  controller.onQueryChanged("from", "ab");
  await new Promise((r) => setTimeout(r, 20));
  expect(calls).toEqual([]);

  controller.onQueryChanged("from", "abc");
  await new Promise((r) => setTimeout(r, 20));
  expect(calls).toEqual(["abc"]);
});

test("a later keystroke cancels the previous debounce timer, not just supersedes its result", async () => {
  const calls: string[] = [];
  const controller = createAutocompleteController({
    debounceMs: 10,
    minQueryLength: 1,
    geocode: async (query) => { calls.push(query); return [candidate(query)]; },
    onSuggestions: () => {},
  });

  controller.onQueryChanged("from", "a");
  await new Promise((r) => setTimeout(r, 2));
  controller.onQueryChanged("from", "ab");
  await new Promise((r) => setTimeout(r, 30));

  expect(calls).toEqual(["ab"]);
});

test("a later keystroke's result wins even if an earlier, slower in-flight fetch resolves after it", async () => {
  const latestByField: Record<string, GeocodeCandidate[]> = {};
  const controller = createAutocompleteController({
    debounceMs: 1,
    minQueryLength: 1,
    geocode: async (query) => {
      const delay = query === "slow" ? 30 : 1;
      await new Promise((r) => setTimeout(r, delay));
      return [candidate(query)];
    },
    onSuggestions: (field, suggestions) => { latestByField[field] = suggestions; },
  });

  controller.onQueryChanged("from", "slow");
  await new Promise((r) => setTimeout(r, 15));
  controller.onQueryChanged("from", "fast");
  await new Promise((r) => setTimeout(r, 60));

  expect(latestByField.from.map((c) => c.label)).toEqual(["fast"]);
});
