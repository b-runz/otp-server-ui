import { test, expect, mock } from "bun:test";
import { search, geocode } from "../src/api";
import { isApiError } from "../src/types";

test("search posts the request body to /search and parses a success response", async () => {
  const fakeFetch = mock(async (input: string, init?: RequestInit) => {
    expect(input).toBe("/search");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(String(init?.body)).mode).toBe("bring_bike");
    return new Response(JSON.stringify({ itineraries: [], notice: null }), { status: 200 });
  });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = fakeFetch as unknown as typeof fetch;
  try {
    const result = await search({
      mode: "bring_bike", timeMode: "depart_at",
      originLat: 56.17, originLon: 10.17, destinationLat: 56.10, destinationLon: 10.17,
      dateTimeIso: "2026-09-13T14:00:00Z", preferHubs: false, maxTransfers: null,
    });
    expect(isApiError(result)).toBe(false);
    if (!isApiError(result)) expect(result.itineraries).toEqual([]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("search surfaces a typed error response instead of throwing", async () => {
  const fakeFetch = mock(async () => new Response(JSON.stringify({ error: "no_coverage" }), { status: 422 }));
  const originalFetch = globalThis.fetch;
  globalThis.fetch = fakeFetch as unknown as typeof fetch;
  try {
    const result = await search({
      mode: "bring_bike", timeMode: "depart_at",
      originLat: 0, originLon: 0, destinationLat: 0, destinationLon: 0,
      dateTimeIso: "2026-09-13T14:00:00Z", preferHubs: false, maxTransfers: null,
    });
    expect(isApiError(result)).toBe(true);
    if (isApiError(result)) expect(result.error).toBe("no_coverage");
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("geocode URL-encodes the query and forwards the abort signal", async () => {
  const fakeFetch = mock(async (input: string, init?: RequestInit) => {
    expect(input).toBe("/geocode?q=Langelandsg%20Aarhus");
    expect(init?.signal).toBeInstanceOf(AbortSignal);
    return new Response(JSON.stringify({ candidates: [] }), { status: 200 });
  });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = fakeFetch as unknown as typeof fetch;
  try {
    const controller = new AbortController();
    await geocode("Langelandsg Aarhus", controller.signal);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
