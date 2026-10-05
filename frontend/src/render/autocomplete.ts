import type { GeocodeCandidate } from "../types";
import type { SavedPlace } from "../storage";

export interface AutocompleteHandlers {
  onSelectSuggestion: (candidate: GeocodeCandidate) => void;
  onSelectSaved: (place: SavedPlace) => void;
  onAddHouseNumber: (candidate: GeocodeCandidate) => void;
}

export function renderSuggestions(
  listEl: HTMLUListElement,
  suggestions: GeocodeCandidate[],
  favoritesAndRecents: { favorites: SavedPlace[]; recents: SavedPlace[] },
  handlers: AutocompleteHandlers,
): void {
  listEl.innerHTML = "";
  listEl.hidden = suggestions.length === 0 && favoritesAndRecents.favorites.length === 0 && favoritesAndRecents.recents.length === 0;

  for (const suggestion of suggestions) {
    const row = document.createElement("li");
    row.className = "suggestion-row";
    row.textContent = suggestion.label;
    row.addEventListener("click", () => handlers.onSelectSuggestion(suggestion));
    if (suggestion.isStreet) {
      const addHouseNumber = document.createElement("span");
      addHouseNumber.className = "add-house-number";
      addHouseNumber.textContent = "Add house number";
      // A direct listener here (not relying on the row's own click handler above) fires
      // first and must stop the event from also bubbling into the row's onSelectSuggestion --
      // tapping this is explicitly "keep editing", not "pick this street as the place".
      addHouseNumber.addEventListener("click", (event) => {
        event.stopPropagation();
        handlers.onAddHouseNumber(suggestion);
      });
      row.appendChild(addHouseNumber);
    }
    listEl.appendChild(row);
  }

  for (const place of [...favoritesAndRecents.favorites, ...favoritesAndRecents.recents]) {
    const row = document.createElement("li");
    row.className = "saved-place-row";
    row.textContent = place.label;
    row.addEventListener("click", () => handlers.onSelectSaved(place));
    listEl.appendChild(row);
  }
}
