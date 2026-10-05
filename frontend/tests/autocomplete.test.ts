import { test, expect, mock } from "bun:test";
import { renderSuggestions } from "../src/render/autocomplete";
import type { GeocodeCandidate } from "../src/types";
import type { SavedPlace } from "../src/storage";

function freshList(): HTMLUListElement {
  document.body.innerHTML = `<ul id="suggestions"></ul>`;
  return document.body.querySelector("#suggestions")!;
}

const streetSuggestion: GeocodeCandidate = { placeId: "p1", label: "Langelandsgade", lat: 1, lon: 2, isStreet: true };
const addressSuggestion: GeocodeCandidate = { placeId: "p2", label: "Langelandsgade 1, Aarhus", lat: 1, lon: 2, isStreet: false };

test("renders an empty list cleanly when there are no suggestions and no favorites/recents", () => {
  const list = freshList();
  renderSuggestions(list, [], { favorites: [], recents: [] }, { onSelectSuggestion: () => {}, onSelectSaved: () => {} });
  expect(list.children.length).toBe(0);
});

test("renders one row per suggestion and calls onSelectSuggestion when clicked", () => {
  const list = freshList();
  const onSelectSuggestion = mock((_c: GeocodeCandidate) => {});
  renderSuggestions(list, [streetSuggestion], { favorites: [], recents: [] }, { onSelectSuggestion, onSelectSaved: () => {}, onAddHouseNumber: () => {} });
  const row = list.querySelector<HTMLElement>(".suggestion-row")!;
  row.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onSelectSuggestion).toHaveBeenCalledWith(streetSuggestion);
});

test("shows the 'Add house number' affordance only for a street-level match", () => {
  const list = freshList();
  renderSuggestions(list, [streetSuggestion, addressSuggestion], { favorites: [], recents: [] }, { onSelectSuggestion: () => {}, onSelectSaved: () => {}, onAddHouseNumber: () => {} });
  const rows = [...list.querySelectorAll(".suggestion-row")];
  expect(rows[0].querySelector(".add-house-number")?.textContent).toBe("Add house number");
  expect(rows[1].querySelector(".add-house-number")).toBeNull();
});

test("clicking 'Add house number' calls onAddHouseNumber, not onSelectSuggestion, and doesn't bubble to the row", () => {
  const list = freshList();
  const onSelectSuggestion = mock((_c: GeocodeCandidate) => {});
  const onAddHouseNumber = mock((_c: GeocodeCandidate) => {});
  renderSuggestions(list, [streetSuggestion], { favorites: [], recents: [] }, { onSelectSuggestion, onSelectSaved: () => {}, onAddHouseNumber });
  const addHouseNumber = list.querySelector<HTMLElement>(".add-house-number")!;
  addHouseNumber.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onAddHouseNumber).toHaveBeenCalledWith(streetSuggestion);
  expect(onSelectSuggestion).not.toHaveBeenCalled();
});

test("renders favorites and recents below suggestions, calling onSelectSaved when clicked", () => {
  const list = freshList();
  const onSelectSaved = mock((_p: SavedPlace) => {});
  const favorite: SavedPlace = { placeId: "fav1", label: "Home", lat: 0, lon: 0, rank: 1 };
  renderSuggestions(list, [], { favorites: [favorite], recents: [] }, { onSelectSuggestion: () => {}, onSelectSaved, onAddHouseNumber: () => {} });
  const savedRow = list.querySelector<HTMLElement>(".saved-place-row")!;
  expect(savedRow.textContent).toContain("Home");
  savedRow.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onSelectSaved).toHaveBeenCalledWith(favorite);
});
