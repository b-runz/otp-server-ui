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

const noopHandlers = { onSelectSuggestion: () => {}, onSelectSaved: () => {}, onAddHouseNumber: () => {}, onToggleFavoritePlace: () => {} };

test("renders an empty list cleanly when there are no suggestions and no favorites/recents", () => {
  const list = freshList();
  renderSuggestions(list, [], { favorites: [], recents: [] }, noopHandlers, true);
  expect(list.children.length).toBe(0);
});

test("renders one row per suggestion and calls onSelectSuggestion when clicked", () => {
  const list = freshList();
  const onSelectSuggestion = mock((_c: GeocodeCandidate) => {});
  renderSuggestions(list, [streetSuggestion], { favorites: [], recents: [] }, { ...noopHandlers, onSelectSuggestion }, true);
  const row = list.querySelector<HTMLElement>(".suggestion-row")!;
  row.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onSelectSuggestion).toHaveBeenCalledWith(streetSuggestion);
});

test("shows the 'Add house number' affordance only for a street-level match", () => {
  const list = freshList();
  renderSuggestions(list, [streetSuggestion, addressSuggestion], { favorites: [], recents: [] }, noopHandlers, true);
  const rows = [...list.querySelectorAll(".suggestion-row")];
  expect(rows[0].querySelector(".add-house-number")?.textContent).toBe("Add house number");
  expect(rows[1].querySelector(".add-house-number")).toBeNull();
});

test("clicking 'Add house number' calls onAddHouseNumber, not onSelectSuggestion, and doesn't bubble to the row", () => {
  const list = freshList();
  const onSelectSuggestion = mock((_c: GeocodeCandidate) => {});
  const onAddHouseNumber = mock((_c: GeocodeCandidate) => {});
  renderSuggestions(list, [streetSuggestion], { favorites: [], recents: [] }, { ...noopHandlers, onSelectSuggestion, onAddHouseNumber }, true);
  const addHouseNumber = list.querySelector<HTMLElement>(".add-house-number")!;
  addHouseNumber.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onAddHouseNumber).toHaveBeenCalledWith(streetSuggestion);
  expect(onSelectSuggestion).not.toHaveBeenCalled();
});

test("renders favorites and recents below suggestions, calling onSelectSaved when clicked", () => {
  const list = freshList();
  const onSelectSaved = mock((_p: SavedPlace) => {});
  const favorite: SavedPlace = { placeId: "fav1", label: "Home", lat: 0, lon: 0, rank: 1 };
  renderSuggestions(list, [], { favorites: [favorite], recents: [] }, { ...noopHandlers, onSelectSaved }, true);
  const savedRow = list.querySelector<HTMLElement>(".saved-place-row")!;
  expect(savedRow.textContent).toContain("Home");
  savedRow.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onSelectSaved).toHaveBeenCalledWith(favorite);
});

test("stays hidden while unfocused even if there are favorites/recents to show", () => {
  const list = freshList();
  const favorite: SavedPlace = { placeId: "fav1", label: "Home", lat: 0, lon: 0, rank: 1 };
  renderSuggestions(list, [], { favorites: [favorite], recents: [] }, noopHandlers, false);
  expect(list.hidden).toBe(true);
});

test("shows once focused, with the same content that was hidden before", () => {
  const list = freshList();
  const favorite: SavedPlace = { placeId: "fav1", label: "Home", lat: 0, lon: 0, rank: 1 };
  renderSuggestions(list, [], { favorites: [favorite], recents: [] }, noopHandlers, true);
  expect(list.hidden).toBe(false);
  expect(list.querySelector(".saved-place-row")?.textContent).toContain("Home");
});

test("each suggestion row has its own favorite star, filled only if it's already a favorite", () => {
  const list = freshList();
  const favorite: SavedPlace = { placeId: "p1", label: "Langelandsgade", lat: 1, lon: 2, rank: 0 };
  renderSuggestions(list, [streetSuggestion, addressSuggestion], { favorites: [favorite], recents: [] }, noopHandlers, true);
  const rows = [...list.querySelectorAll(".suggestion-row")];
  expect(rows[0].querySelector(".favorite-star")?.textContent).toBe("★"); // p1 is favorited
  expect(rows[1].querySelector(".favorite-star")?.textContent).toBe("☆"); // p2 is not
});

test("clicking a suggestion row's star calls onToggleFavoritePlace, not onSelectSuggestion, and doesn't bubble", () => {
  const list = freshList();
  const onSelectSuggestion = mock((_c: GeocodeCandidate) => {});
  const onToggleFavoritePlace = mock((_p: { placeId: string }) => {});
  renderSuggestions(list, [streetSuggestion], { favorites: [], recents: [] }, { ...noopHandlers, onSelectSuggestion, onToggleFavoritePlace }, true);
  const star = list.querySelector<HTMLElement>(".suggestion-row .favorite-star")!;
  star.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onToggleFavoritePlace).toHaveBeenCalledWith(streetSuggestion);
  expect(onSelectSuggestion).not.toHaveBeenCalled();
});

test("a favorite row's star is filled; a recent (not yet favorited) row's star is outline; both toggle via onToggleFavoritePlace without selecting", () => {
  const list = freshList();
  const onSelectSaved = mock((_p: SavedPlace) => {});
  const onToggleFavoritePlace = mock((_p: { placeId: string }) => {});
  const favorite: SavedPlace = { placeId: "fav1", label: "Home", lat: 0, lon: 0, rank: 1 };
  const recent: SavedPlace = { placeId: "rec1", label: "Work", lat: 0, lon: 0, rank: 0 };
  renderSuggestions(list, [], { favorites: [favorite], recents: [recent] }, { ...noopHandlers, onSelectSaved, onToggleFavoritePlace }, true);
  const rows = [...list.querySelectorAll(".saved-place-row")];
  expect(rows[0].querySelector(".favorite-star")?.textContent).toBe("★"); // Home: a favorite
  expect(rows[1].querySelector(".favorite-star")?.textContent).toBe("☆"); // Work: only a recent

  const star = rows[0].querySelector<HTMLElement>(".favorite-star")!;
  star.dispatchEvent(new Event("click", { bubbles: true }));
  expect(onToggleFavoritePlace).toHaveBeenCalledWith(favorite);
  expect(onSelectSaved).not.toHaveBeenCalled();
});
