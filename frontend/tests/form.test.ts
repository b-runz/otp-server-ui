import { test, expect, mock } from "bun:test";
import { renderForm } from "../src/render/form";
import { createInitialState, resolveField } from "../src/state";
import type { SavedPlace } from "../src/storage";

function freshRoot(): HTMLElement {
  document.body.innerHTML = `
    <form id="trip-form">
      <fieldset id="mode-toggle"></fieldset>
      <fieldset id="time-toggle"></fieldset>
      <input id="date-input" type="date" />
      <input id="time-input" type="time" />
      <div class="address-field">
        <input id="from-input" />
        <button type="button" id="from-favorite-star"></button>
        <button type="button" id="from-clear"></button>
      </div>
      <div class="address-field">
        <input id="to-input" />
        <button type="button" id="to-favorite-star"></button>
        <button type="button" id="to-clear"></button>
        <button type="button" id="swap-button"></button>
      </div>
      <input type="checkbox" id="prefer-hubs-checkbox" />
      <button type="button" id="max-transfers-decrement"></button>
      <span id="max-transfers-value"></span>
      <button type="button" id="max-transfers-increment"></button>
    </form>
  `;
  return document.body;
}

function noopHandlers() {
  return {
    onSearchModeChange: () => {}, onTimeModeChange: () => {}, onPreferHubsChange: () => {},
    onSwap: () => {}, onFromQueryChanged: () => {}, onToQueryChanged: () => {}, onDateTimeChanged: () => {},
    onToggleFavorite: () => {}, onFromFocusChanged: () => {}, onToFocusChanged: () => {},
    onMaxTransfersChange: () => {},
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

test("clicking the from-clear button clears the input and calls onFromQueryChanged with an empty string", () => {
  const root = freshRoot();
  const onFromQueryChanged = mock((_q: string) => {});
  const state = { ...createInitialState(), from: { query: "Langelandsgade", resolved: null, suggestions: [] } };
  renderForm(root, state, { ...noopHandlers(), onFromQueryChanged });
  const input = root.querySelector<HTMLInputElement>("#from-input")!;
  expect(input.value).toBe("Langelandsgade");
  root.querySelector<HTMLButtonElement>("#from-clear")!.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onFromQueryChanged).toHaveBeenCalledWith("");
  expect(input.value).toBe("");
});

test("clicking the to-clear button clears the input and calls onToQueryChanged with an empty string", () => {
  const root = freshRoot();
  const onToQueryChanged = mock((_q: string) => {});
  const state = { ...createInitialState(), to: { query: "Odder", resolved: null, suggestions: [] } };
  renderForm(root, state, { ...noopHandlers(), onToQueryChanged });
  root.querySelector<HTMLButtonElement>("#to-clear")!.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onToQueryChanged).toHaveBeenCalledWith("");
});

test("the date and time inputs are synced from state.dateTimeIso as separate fields", () => {
  const root = freshRoot();
  const state = { ...createInitialState(), dateTimeIso: new Date(2026, 8, 30, 12, 58).toISOString() };
  renderForm(root, state, noopHandlers());
  expect(root.querySelector<HTMLInputElement>("#date-input")!.value).toBe("2026-09-30");
  expect(root.querySelector<HTMLInputElement>("#time-input")!.value).toBe("12:58");
});

test("changing the date or time input combines both into one ISO string passed to onDateTimeChanged", () => {
  const root = freshRoot();
  const onDateTimeChanged = mock((_iso: string) => {});
  const state = { ...createInitialState(), dateTimeIso: new Date(2026, 8, 30, 12, 58).toISOString() };
  renderForm(root, state, { ...noopHandlers(), onDateTimeChanged });

  const dateInput = root.querySelector<HTMLInputElement>("#date-input")!;
  dateInput.value = "2026-10-01";
  dateInput.dispatchEvent(new Event("change", { bubbles: true }));

  expect(onDateTimeChanged).toHaveBeenCalledTimes(1);
  const isoArg = onDateTimeChanged.mock.calls[0]![0] as string;
  const combined = new Date(isoArg);
  expect(combined.getFullYear()).toBe(2026);
  expect(combined.getMonth()).toBe(9); // October
  expect(combined.getDate()).toBe(1);
  expect(combined.getHours()).toBe(12);
  expect(combined.getMinutes()).toBe(58);
});

test("the max-transfers value defaults to Unlimited, with decrement disabled at the floor", () => {
  const root = freshRoot();
  renderForm(root, createInitialState(), noopHandlers());
  expect(root.querySelector<HTMLSpanElement>("#max-transfers-value")!.textContent).toBe("Unlimited");
  expect(root.querySelector<HTMLButtonElement>("#max-transfers-decrement")!.disabled).toBe(true);
  expect(root.querySelector<HTMLButtonElement>("#max-transfers-increment")!.disabled).toBe(false);
});

test("the max-transfers value reflects a finite state.maxTransfers value, with both buttons enabled mid-range", () => {
  const root = freshRoot();
  const state = { ...createInitialState(), maxTransfers: 2 };
  renderForm(root, state, noopHandlers());
  expect(root.querySelector<HTMLSpanElement>("#max-transfers-value")!.textContent).toBe("2");
  expect(root.querySelector<HTMLButtonElement>("#max-transfers-decrement")!.disabled).toBe(false);
  expect(root.querySelector<HTMLButtonElement>("#max-transfers-increment")!.disabled).toBe(false);
});

test("increment is disabled at the ceiling (3)", () => {
  const root = freshRoot();
  const state = { ...createInitialState(), maxTransfers: 3 };
  renderForm(root, state, noopHandlers());
  expect(root.querySelector<HTMLButtonElement>("#max-transfers-increment")!.disabled).toBe(true);
});

test("clicking increment from Unlimited steps to 0", () => {
  const root = freshRoot();
  const onMaxTransfersChange = mock((_value: number | null) => {});
  renderForm(root, createInitialState(), { ...noopHandlers(), onMaxTransfersChange });
  root.querySelector<HTMLButtonElement>("#max-transfers-increment")!.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onMaxTransfersChange).toHaveBeenCalledWith(0);
});

test("clicking increment from a finite value steps up by one", () => {
  const root = freshRoot();
  const onMaxTransfersChange = mock((_value: number | null) => {});
  const state = { ...createInitialState(), maxTransfers: 1 };
  renderForm(root, state, { ...noopHandlers(), onMaxTransfersChange });
  root.querySelector<HTMLButtonElement>("#max-transfers-increment")!.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onMaxTransfersChange).toHaveBeenCalledWith(2);
});

test("clicking decrement from 0 steps back down to Unlimited (null)", () => {
  const root = freshRoot();
  const onMaxTransfersChange = mock((_value: number | null) => {});
  const state = { ...createInitialState(), maxTransfers: 0 };
  renderForm(root, state, { ...noopHandlers(), onMaxTransfersChange });
  root.querySelector<HTMLButtonElement>("#max-transfers-decrement")!.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onMaxTransfersChange).toHaveBeenCalledWith(null);
});

test("clicking decrement at Unlimited (the floor) never calls the handler", () => {
  const root = freshRoot();
  const onMaxTransfersChange = mock((_value: number | null) => {});
  renderForm(root, createInitialState(), { ...noopHandlers(), onMaxTransfersChange });
  root.querySelector<HTMLButtonElement>("#max-transfers-decrement")!.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onMaxTransfersChange).not.toHaveBeenCalled();
});

test("clicking increment at the ceiling (3) never calls the handler", () => {
  const root = freshRoot();
  const onMaxTransfersChange = mock((_value: number | null) => {});
  const state = { ...createInitialState(), maxTransfers: 3 };
  renderForm(root, state, { ...noopHandlers(), onMaxTransfersChange });
  root.querySelector<HTMLButtonElement>("#max-transfers-increment")!.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onMaxTransfersChange).not.toHaveBeenCalled();
});
