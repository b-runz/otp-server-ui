import { test, expect, mock } from "bun:test";
import { mountApp } from "../src/main";

function freshDocument(): HTMLElement {
  document.body.innerHTML = `
    <main id="app">
      <form id="trip-form">
        <div class="address-field"><input id="from-input" /><ul id="from-suggestions" class="suggestions" hidden></ul></div>
        <button type="button" id="swap-button"></button>
        <div class="address-field"><input id="to-input" /><ul id="to-suggestions" class="suggestions" hidden></ul></div>
        <fieldset id="mode-toggle"></fieldset>
        <fieldset id="time-toggle"></fieldset>
        <input id="datetime-input" type="datetime-local" />
        <input type="checkbox" id="prefer-hubs-checkbox" />
        <button type="submit" id="search-button">Search</button>
        <button type="button" id="drop-me-off-button">Drop me off</button>
      </form>
      <div id="error-banner" hidden></div>
      <div id="results"></div>
      <div id="nearby-routes"></div>
    </main>
  `;
  return document.body.querySelector("#app")!;
}

test("form fill -> submit -> result rendered", async () => {
  const fakeFetch = mock(async (input: string) => {
    if (String(input).startsWith("/geocode")) {
      return new Response(JSON.stringify({
        candidates: [{ placeId: "p1", label: "Langelandsgade, Aarhus", lat: 56.17, lon: 10.19, isStreet: false }],
      }), { status: 200 });
    }
    if (String(input) === "/search") {
      return new Response(JSON.stringify({
        itineraries: [{
          legs: [{ mode: "WALK", distanceMeters: 500, durationSeconds: 400, fromLat: 0, fromLon: 0, toLat: 0, toLon: 0, fromName: "Origin", toName: "Destination", routeShortName: null, departureEpochSecond: 1_000_000_000 }],
          exceedsBikeLimit: false,
          hasLongWalkEgress: false,
        }],
        notice: null,
      }), { status: 200 });
    }
    throw new Error(`unexpected fetch: ${input}`);
  });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = fakeFetch as unknown as typeof fetch;

  try {
    const root = freshDocument();
    mountApp(root);

    const fromInput = root.querySelector<HTMLInputElement>("#from-input")!;
    fromInput.value = "Langelandsg";
    fromInput.dispatchEvent(new Event("input", { bubbles: true }));
    await new Promise((r) => setTimeout(r, 350));

    const suggestionRow = root.querySelector<HTMLElement>("#from-suggestions .suggestion-row")!;
    suggestionRow.dispatchEvent(new Event("click", { bubbles: true }));

    const toInput = root.querySelector<HTMLInputElement>("#to-input")!;
    toInput.value = "Somewhere else";
    toInput.dispatchEvent(new Event("input", { bubbles: true }));
    await new Promise((r) => setTimeout(r, 350));

    const toSuggestionRow = root.querySelector<HTMLElement>("#to-suggestions .suggestion-row")!;
    toSuggestionRow.dispatchEvent(new Event("click", { bubbles: true }));

    const form = root.querySelector<HTMLFormElement>("#trip-form")!;
    form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await new Promise((r) => setTimeout(r, 10));

    expect(root.querySelector("#results")!.textContent).toContain("Origin → Destination");
  } finally {
    globalThis.fetch = originalFetch;
  }
});
