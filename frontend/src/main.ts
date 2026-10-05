import { search, nearbyRoutes, connect, geocode } from "./api";
import { isApiError, type GeocodeCandidate, type NearbyRouteDto } from "./types";
import {
  createInitialState, setSearchMode, setTimeMode, setPreferHubs, setDateTimeIso,
  swapFromTo, setFieldQuery, setFieldSuggestions, resolveField,
  setItineraries, setError, setNearbyRoutes, setNearbyRoutesError,
  setConnectResult, setConnectError, type AppState,
} from "./state";
import { createAutocompleteController } from "./addressAutocomplete";
import {
  loadFavorites, saveFavorites, loadRecents, saveRecents,
  rememberRecent, incrementRank, favoritesSortedForPicker,
} from "./storage";
import { renderForm } from "./render/form";
import { renderSuggestions } from "./render/autocomplete";
import { renderResults } from "./render/results";
import { renderNearbyRoutes } from "./render/dropMeOff";
import { messageForError } from "./errors";

export function mountApp(root: HTMLElement): void {
  let state: AppState = {
    ...createInitialState(),
    favorites: loadFavorites(),
    recents: loadRecents(),
  };

  function render(): void {
    renderForm(root, state, {
      onSearchModeChange: (mode) => update(setSearchMode(state, mode)),
      onTimeModeChange: (mode) => update(setTimeMode(state, mode)),
      onPreferHubsChange: (value) => update(setPreferHubs(state, value)),
      onSwap: () => update(swapFromTo(state)),
      onFromQueryChanged: (query) => onFieldQueryChanged("from", query),
      onToQueryChanged: (query) => onFieldQueryChanged("to", query),
      onDateTimeChanged: (iso) => update(setDateTimeIso(state, iso)),
    });

    const fromList = root.querySelector<HTMLUListElement>("#from-suggestions")!;
    renderSuggestions(fromList, state.from.suggestions, { favorites: favoritesSortedForPicker(state.favorites), recents: state.recents }, {
      onSelectSuggestion: (candidate) => onSelectSuggestion("from", candidate),
      onSelectSaved: (place) => onSelectSaved("from", place),
      onAddHouseNumber: (candidate) => onAddHouseNumber("from", candidate),
    });
    const toList = root.querySelector<HTMLUListElement>("#to-suggestions")!;
    renderSuggestions(toList, state.to.suggestions, { favorites: favoritesSortedForPicker(state.favorites), recents: state.recents }, {
      onSelectSuggestion: (candidate) => onSelectSuggestion("to", candidate),
      onSelectSaved: (place) => onSelectSaved("to", place),
      onAddHouseNumber: (candidate) => onAddHouseNumber("to", candidate),
    });

    const errorBanner = root.querySelector<HTMLElement>("#error-banner")!;
    errorBanner.hidden = state.error == null;
    errorBanner.textContent = state.error ?? "";

    renderResults(root.querySelector<HTMLElement>("#results")!, state.itineraries, state.notice);
    renderNearbyRoutes(
      root.querySelector<HTMLElement>("#nearby-routes")!,
      state.nearbyRoutes, state.nearbyRoutesError, state.connectResults, state.connectErrors,
      (route) => onSelectRoute(route),
    );
  }

  function update(next: AppState): void {
    state = next;
    render();
  }

  const autocomplete = createAutocompleteController({
    geocode: async (query, signal) => {
      const result = await geocode(query, signal);
      return isApiError(result) ? [] : result.candidates;
    },
    onSuggestions: (field, suggestions) => update(setFieldSuggestions(state, field, suggestions)),
  });

  function onFieldQueryChanged(field: "from" | "to", query: string): void {
    update(setFieldQuery(state, field, query));
    autocomplete.onQueryChanged(field, query);
  }

  function onSelectSuggestion(field: "from" | "to", candidate: GeocodeCandidate): void {
    update(resolveField(state, field, candidate));
    const recents = rememberRecent(state.favorites, state.recents, candidate.placeId, candidate.label, candidate.lat, candidate.lon);
    saveRecents(recents);
    update({ ...state, recents });
  }

  // "Add house number" is explicitly NOT a selection: it keeps the field in edit mode, pre-filled
  // with a trailing space so the user can keep typing the house number -- see this task's own
  // "Adaptation from bikebus" note on why this is "{label} " rather than bikebus's own
  // "{mainText} , {secondaryText}" split, which this backend's flatter GeocodeCandidate can't
  // reproduce. Reuses the normal onFieldQueryChanged path so the edited text re-triggers
  // autocomplete exactly like any other keystroke would.
  function onAddHouseNumber(field: "from" | "to", candidate: GeocodeCandidate): void {
    const input = root.querySelector<HTMLInputElement>(`#${field}-input`)!;
    const filled = `${candidate.label} `;
    input.value = filled;
    input.focus();
    input.setSelectionRange(filled.length, filled.length);
    onFieldQueryChanged(field, filled);
  }

  function onSelectSaved(field: "from" | "to", place: { placeId: string; label: string; lat: number; lon: number }): void {
    update(resolveField(state, field, { ...place, isStreet: false }));
    const { favorites, recents } = incrementRank(state.favorites, state.recents, place.placeId);
    saveFavorites(favorites);
    saveRecents(recents);
    update({ ...state, favorites, recents });
  }

  async function onSelectRoute(route: NearbyRouteDto): Promise<void> {
    if (state.from.resolved == null || state.to.resolved == null) return;
    const result = await connect({
      originLat: state.from.resolved.lat, originLon: state.from.resolved.lon,
      destinationLat: state.to.resolved.lat, destinationLon: state.to.resolved.lon,
      routeGtfsId: route.routeGtfsId, routeStopIds: route.stopIds,
      timeMode: state.timeMode, dateTimeIso: state.dateTimeIso, preferHubs: state.preferHubs,
    });
    if (isApiError(result)) {
      update(setConnectError(state, route.routeGtfsId, messageForError(result)));
    } else {
      update(setConnectResult(state, route.routeGtfsId, result));
    }
  }

  root.querySelector<HTMLFormElement>("#trip-form")!.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (state.from.resolved == null || state.to.resolved == null) return;
    const result = await search({
      mode: state.searchMode, timeMode: state.timeMode,
      originLat: state.from.resolved.lat, originLon: state.from.resolved.lon,
      destinationLat: state.to.resolved.lat, destinationLon: state.to.resolved.lon,
      dateTimeIso: state.dateTimeIso, preferHubs: state.preferHubs,
    });
    if (isApiError(result)) {
      update(setError(state, messageForError(result)));
    } else {
      update(setItineraries(state, result.itineraries, result.notice));
    }
  });

  root.querySelector<HTMLButtonElement>("#drop-me-off-button")!.addEventListener("click", async () => {
    if (state.to.resolved == null) return;
    const result = await nearbyRoutes(state.to.resolved.lat, state.to.resolved.lon);
    if (isApiError(result)) {
      update(setNearbyRoutesError(state, messageForError(result)));
    } else {
      update(setNearbyRoutes(state, result.routes));
    }
  });

  render();
}

const appRoot = typeof document !== "undefined" ? document.getElementById("app") : null;
if (appRoot) {
  mountApp(appRoot);
}
