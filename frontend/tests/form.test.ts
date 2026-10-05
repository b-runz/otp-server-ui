import { test, expect, mock } from "bun:test";
import { renderForm } from "../src/render/form";
import { createInitialState, resolveField } from "../src/state";
import type { SavedPlace } from "../src/storage";

function freshRoot(): HTMLElement {
  document.body.innerHTML = `
    <form id="trip-form">
      <div class="address-field"><input id="from-input" /><button type="button" id="from-favorite-star"></button></div>
      <button type="button" id="swap-button"></button>
      <div class="address-field"><input id="to-input" /><button type="button" id="to-favorite-star"></button></div>
      <fieldset id="mode-toggle"></fieldset>
      <fieldset id="time-toggle"></fieldset>
      <input id="datetime-input" type="datetime-local" />
      <input type="checkbox" id="prefer-hubs-checkbox" />
    </form>
  `;
  return document.body;
}

function noopHandlers() {
  return {
    onSearchModeChange: () => {}, onTimeModeChange: () => {}, onPreferHubsChange: () => {},
    onSwap: () => {}, onFromQueryChanged: () => {}, onToQueryChanged: () => {}, onDateTimeChanged: () => {},
    onToggleFavorite: () => {}, onFromFocusChanged: () => {}, onToFocusChanged: () => {},
  };
}

test("renderForm renders the mode and time toggle options with bikebus's exact labels", () => {
  const root = freshRoot();
  renderForm(root, createInitialState(), noopHandlers());
  const modeLabels = [...root.querySelectorAll("#mode-toggle button")].map((b) => b.textContent);
  expect(modeLabels).toEqual(["Park & Ride", "Bring Bike"]);
  const timeLabels = [...root.querySelectorAll("#time-toggle button")].map((b) => b.textContent);
  expect(timeLabels).toEqual(["Depart at", "Arrive by"]);
});

test("clicking a mode button calls onSearchModeChange with that mode", () => {
  const root = freshRoot();
  const onSearchModeChange = mock((_mode: string) => {});
  renderForm(root, createInitialState(), { ...noopHandlers(), onSearchModeChange });
  const bringBikeButton = [...root.querySelectorAll("#mode-toggle button")].find((b) => b.textContent === "Bring Bike")!;
  bringBikeButton.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onSearchModeChange).toHaveBeenCalledWith("bring_bike");
});

test("the active mode/time button gets an aria-pressed=true attribute", () => {
  const root = freshRoot();
  const state = { ...createInitialState(), searchMode: "bring_bike" as const };
  renderForm(root, state, noopHandlers());
  const bringBikeButton = [...root.querySelectorAll("#mode-toggle button")].find((b) => b.textContent === "Bring Bike")!;
  expect(bringBikeButton.getAttribute("aria-pressed")).toBe("true");
  const parkAndRideButton = [...root.querySelectorAll("#mode-toggle button")].find((b) => b.textContent === "Park & Ride")!;
  expect(parkAndRideButton.getAttribute("aria-pressed")).toBe("false");
});

test("typing in the from-input calls onFromQueryChanged with the new value", () => {
  const root = freshRoot();
  const onFromQueryChanged = mock((_q: string) => {});
  renderForm(root, createInitialState(), { ...noopHandlers(), onFromQueryChanged });
  const input = root.querySelector<HTMLInputElement>("#from-input")!;
  input.value = "Langelandsg";
  input.dispatchEvent(new Event("input", { bubbles: true }));
  expect(onFromQueryChanged).toHaveBeenCalledWith("Langelandsg");
});

test("the favorite star is hidden when the field has no resolved place", () => {
  const root = freshRoot();
  renderForm(root, createInitialState(), noopHandlers());
  const star = root.querySelector<HTMLButtonElement>("#from-favorite-star")!;
  expect(star.hidden).toBe(true);
});

test("the favorite star shows outline when resolved but not favorited, filled when favorited", () => {
  const root = freshRoot();
  const resolved = resolveField(createInitialState(), "from", { placeId: "p1", label: "Somewhere", lat: 1, lon: 2, isStreet: false });

  renderForm(root, resolved, noopHandlers());
  const star = root.querySelector<HTMLButtonElement>("#from-favorite-star")!;
  expect(star.hidden).toBe(false);
  expect(star.textContent).toBe("☆");

  const favorite: SavedPlace = { placeId: "p1", label: "Somewhere", lat: 1, lon: 2, rank: 0 };
  renderForm(root, { ...resolved, favorites: [favorite] }, noopHandlers());
  expect(star.textContent).toBe("★");
});

test("clicking the favorite star calls onToggleFavorite with the field name", () => {
  const root = freshRoot();
  const resolved = resolveField(createInitialState(), "to", { placeId: "p1", label: "Somewhere", lat: 1, lon: 2, isStreet: false });
  const onToggleFavorite = mock((_field: "from" | "to") => {});
  renderForm(root, resolved, { ...noopHandlers(), onToggleFavorite });
  const star = root.querySelector<HTMLButtonElement>("#to-favorite-star")!;
  star.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onToggleFavorite).toHaveBeenCalledWith("to");
});

test("focusing and blurring the from-input calls onFromFocusChanged with true then false", () => {
  const root = freshRoot();
  const onFromFocusChanged = mock((_focused: boolean) => {});
  renderForm(root, createInitialState(), { ...noopHandlers(), onFromFocusChanged });
  const input = root.querySelector<HTMLInputElement>("#from-input")!;
  input.dispatchEvent(new Event("focus", { bubbles: true }));
  expect(onFromFocusChanged).toHaveBeenCalledWith(true);
  input.dispatchEvent(new Event("blur", { bubbles: true }));
  expect(onFromFocusChanged).toHaveBeenCalledWith(false);
});

test("focusing and blurring the to-input calls onToFocusChanged, independent of the from-input", () => {
  const root = freshRoot();
  const onFromFocusChanged = mock((_focused: boolean) => {});
  const onToFocusChanged = mock((_focused: boolean) => {});
  renderForm(root, createInitialState(), { ...noopHandlers(), onFromFocusChanged, onToFocusChanged });
  const input = root.querySelector<HTMLInputElement>("#to-input")!;
  input.dispatchEvent(new Event("focus", { bubbles: true }));
  expect(onToFocusChanged).toHaveBeenCalledWith(true);
  expect(onFromFocusChanged).not.toHaveBeenCalled();
});
