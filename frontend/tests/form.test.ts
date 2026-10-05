import { test, expect, mock } from "bun:test";
import { renderForm } from "../src/render/form";
import { createInitialState } from "../src/state";

function freshRoot(): HTMLElement {
  document.body.innerHTML = `
    <form id="trip-form">
      <div class="address-field"><input id="from-input" /></div>
      <button type="button" id="swap-button"></button>
      <div class="address-field"><input id="to-input" /></div>
      <fieldset id="mode-toggle"></fieldset>
      <fieldset id="time-toggle"></fieldset>
      <input id="datetime-input" type="datetime-local" />
      <input type="checkbox" id="prefer-hubs-checkbox" />
    </form>
  `;
  return document.body;
}

test("renderForm renders the mode and time toggle options with bikebus's exact labels", () => {
  const root = freshRoot();
  renderForm(root, createInitialState(), {
    onSearchModeChange: () => {}, onTimeModeChange: () => {}, onPreferHubsChange: () => {},
    onSwap: () => {}, onFromQueryChanged: () => {}, onToQueryChanged: () => {}, onDateTimeChanged: () => {},
  });
  const modeLabels = [...root.querySelectorAll("#mode-toggle button")].map((b) => b.textContent);
  expect(modeLabels).toEqual(["Park & Ride", "Bring Bike"]);
  const timeLabels = [...root.querySelectorAll("#time-toggle button")].map((b) => b.textContent);
  expect(timeLabels).toEqual(["Depart at", "Arrive by"]);
});

test("clicking a mode button calls onSearchModeChange with that mode", () => {
  const root = freshRoot();
  const onSearchModeChange = mock((_mode: string) => {});
  renderForm(root, createInitialState(), {
    onSearchModeChange, onTimeModeChange: () => {}, onPreferHubsChange: () => {},
    onSwap: () => {}, onFromQueryChanged: () => {}, onToQueryChanged: () => {}, onDateTimeChanged: () => {},
  });
  const bringBikeButton = [...root.querySelectorAll("#mode-toggle button")].find((b) => b.textContent === "Bring Bike")!;
  bringBikeButton.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onSearchModeChange).toHaveBeenCalledWith("bring_bike");
});

test("the active mode/time button gets an aria-pressed=true attribute", () => {
  const root = freshRoot();
  const state = { ...createInitialState(), searchMode: "bring_bike" as const };
  renderForm(root, state, {
    onSearchModeChange: () => {}, onTimeModeChange: () => {}, onPreferHubsChange: () => {},
    onSwap: () => {}, onFromQueryChanged: () => {}, onToQueryChanged: () => {}, onDateTimeChanged: () => {},
  });
  const bringBikeButton = [...root.querySelectorAll("#mode-toggle button")].find((b) => b.textContent === "Bring Bike")!;
  expect(bringBikeButton.getAttribute("aria-pressed")).toBe("true");
  const parkAndRideButton = [...root.querySelectorAll("#mode-toggle button")].find((b) => b.textContent === "Park & Ride")!;
  expect(parkAndRideButton.getAttribute("aria-pressed")).toBe("false");
});

test("typing in the from-input calls onFromQueryChanged with the new value", () => {
  const root = freshRoot();
  const onFromQueryChanged = mock((_q: string) => {});
  renderForm(root, createInitialState(), {
    onSearchModeChange: () => {}, onTimeModeChange: () => {}, onPreferHubsChange: () => {},
    onSwap: () => {}, onFromQueryChanged, onToQueryChanged: () => {}, onDateTimeChanged: () => {},
  });
  const input = root.querySelector<HTMLInputElement>("#from-input")!;
  input.value = "Langelandsg";
  input.dispatchEvent(new Event("input", { bubbles: true }));
  expect(onFromQueryChanged).toHaveBeenCalledWith("Langelandsg");
});
