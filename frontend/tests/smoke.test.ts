import { test, expect, mock } from "bun:test";
import { mountApp } from "../src/main";

function freshDocument(): HTMLElement {
  document.body.innerHTML = `
    <main id="app">
      <form id="trip-form">
        <fieldset id="mode-toggle"></fieldset>
        <fieldset id="time-toggle"></fieldset>
        <input id="date-input" type="date" />
        <input id="time-input" type="time" />
        <div class="address-field"><input id="from-input" /><button type="button" id="from-favorite-star" hidden></button><button type="button" id="from-clear"></button><ul id="from-suggestions" class="suggestions" hidden></ul></div>
        <div class="address-field"><input id="to-input" /><button type="button" id="to-favorite-star" hidden></button><button type="button" id="to-clear"></button><button type="button" id="swap-button"></button><ul id="to-suggestions" class="suggestions" hidden></ul></div>
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

test("recents dropdown only shows while focused, and a click on a recent still registers despite the blur delay", async () => {
  localStorage.setItem(
    "otp-server-ui:recents",
    JSON.stringify([{ placeId: "r1", label: "Recent Place", lat: 1, lon: 2, rank: 0 }]),
  );
  try {
    const root = freshDocument();
    mountApp(root);

    const fromInput = root.querySelector<HTMLInputElement>("#from-input")!;
    const fromList = root.querySelector<HTMLUListElement>("#from-suggestions")!;

    expect(fromList.hidden).toBe(true); // not focused yet, even though a recent exists

    fromInput.dispatchEvent(new Event("focus", { bubbles: true }));
    expect(fromList.hidden).toBe(false);
    const recentRow = fromList.querySelector<HTMLElement>(".saved-place-row")!;
    expect(recentRow.textContent).toContain("Recent Place");

    fromInput.dispatchEvent(new Event("blur", { bubbles: true }));
    // Blur is delayed -- a click arriving in this window must still register as a real selection.
    recentRow.dispatchEvent(new Event("click", { bubbles: true }));
    expect(fromInput.value).toBe("Recent Place");

    await new Promise((r) => setTimeout(r, 250));
    expect(fromList.hidden).toBe(true);
  } finally {
    localStorage.clear();
  }
});
