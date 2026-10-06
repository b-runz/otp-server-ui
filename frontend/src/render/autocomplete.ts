import type { GeocodeCandidate } from "../types";
import type { SavedPlace } from "../storage";

export interface AutocompleteHandlers {
  onSelectSuggestion: (candidate: GeocodeCandidate) => void;
  onSelectSaved: (place: SavedPlace) => void;
  onAddHouseNumber: (candidate: GeocodeCandidate) => void;
  onToggleFavoritePlace: (place: { placeId: string; label: string; lat: number; lon: number }) => void;
}

// Shared by both suggestion rows and saved-place rows: a star that reflects whether THIS place
// is currently a favorite (independent of whether it's also shown because it's a recent), and
// which toggles that status directly -- it never selects/resolves the row, matching bikebus's
// real behavior (tapping the star in the list just toggles favorite status of that entry).
function appendFavoriteStar(
  row: HTMLElement,
  place: { placeId: string; label: string; lat: number; lon: number },
  favorites: SavedPlace[],
  onToggleFavoritePlace: AutocompleteHandlers["onToggleFavoritePlace"],
): void {
  const star = document.createElement("button");
  star.type = "button";
  star.className = "favorite-star";
  star.setAttribute("aria-label", "Toggle favorite");
  star.textContent = favorites.some((p) => p.placeId === place.placeId) ? "★" : "☆";
  star.addEventListener("click", (event) => {
    event.stopPropagation();
    onToggleFavoritePlace(place);
  });
  row.appendChild(star);
}

export function renderSuggestions(
  listEl: HTMLUListElement,
  suggestions: GeocodeCandidate[],
  favoritesAndRecents: { favorites: SavedPlace[]; recents: SavedPlace[] },
  handlers: AutocompleteHandlers,
  focused: boolean,
): void {
  listEl.innerHTML = "";
  const hasContent = suggestions.length > 0 || favoritesAndRecents.favorites.length > 0 || favoritesAndRecents.recents.length > 0;
  listEl.hidden = !focused || !hasContent;

  for (const suggestion of suggestions) {
    const row = document.createElement("li");
    row.className = "suggestion-row";
    row.addEventListener("click", () => handlers.onSelectSuggestion(suggestion));

    const text = document.createElement("span");
    text.className = "suggestion-row-text";
    const label = document.createElement("span");
    label.textContent = suggestion.label;
    text.appendChild(label);
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
      text.appendChild(addHouseNumber);
    }
    row.appendChild(text);
    appendFavoriteStar(row, suggestion, favoritesAndRecents.favorites, handlers.onToggleFavoritePlace);
    listEl.appendChild(row);
  }

  for (const place of [...favoritesAndRecents.favorites, ...favoritesAndRecents.recents]) {
    const row = document.createElement("li");
    row.className = "saved-place-row";
    const label = document.createElement("span");
    label.textContent = place.label;
    row.appendChild(label);
    row.addEventListener("click", () => handlers.onSelectSaved(place));
    appendFavoriteStar(row, place, favoritesAndRecents.favorites, handlers.onToggleFavoritePlace);
    listEl.appendChild(row);
  }
}
