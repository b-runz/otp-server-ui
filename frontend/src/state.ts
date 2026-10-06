import type { GeocodeCandidate, ItineraryDto, NearbyRouteDto, ConnectResponse } from "./types";
import type { SavedPlace } from "./storage";

export type SearchMode = "park_and_ride" | "bring_bike";
export type TimeMode = "depart_at" | "arrive_by";
export type AddressFieldName = "from" | "to";

export interface ResolvedPlace {
  placeId: string;
  label: string;
  lat: number;
  lon: number;
}

export interface AddressFieldState {
  query: string;
  resolved: ResolvedPlace | null;
  suggestions: GeocodeCandidate[];
}

export interface AppState {
  searchMode: SearchMode;
  timeMode: TimeMode;
  preferHubs: boolean;
  maxTransfers: number | null; // null = unlimited; shared across every search mode
  dateTimeIso: string;
  from: AddressFieldState;
  to: AddressFieldState;
  favorites: SavedPlace[];
  recents: SavedPlace[];
  itineraries: ItineraryDto[] | null;
  notice: string | null;
  error: string | null;
  searched: boolean;
  nearbyRoutes: NearbyRouteDto[] | null;
  nearbyRoutesError: string | null;
  connectResults: Record<string, ConnectResponse>;
  connectErrors: Record<string, string>;
}

function emptyField(): AddressFieldState {
  return { query: "", resolved: null, suggestions: [] };
}

export function createInitialState(): AppState {
  return {
    searchMode: "park_and_ride",
    timeMode: "depart_at",
    preferHubs: false,
    maxTransfers: null,
    dateTimeIso: new Date().toISOString(),
    from: emptyField(),
    to: emptyField(),
    favorites: [],
    recents: [],
    itineraries: null,
    notice: null,
    error: null,
    searched: false,
    nearbyRoutes: null,
    nearbyRoutesError: null,
    connectResults: {},
    connectErrors: {},
  };
}

export function setSearchMode(state: AppState, mode: SearchMode): AppState {
  return { ...state, searchMode: mode };
}

export function setTimeMode(state: AppState, mode: TimeMode): AppState {
  return { ...state, timeMode: mode };
}

export function setPreferHubs(state: AppState, value: boolean): AppState {
  return { ...state, preferHubs: value };
}

export function setMaxTransfers(state: AppState, value: number | null): AppState {
  return { ...state, maxTransfers: value };
}

export function setDateTimeIso(state: AppState, iso: string): AppState {
  return { ...state, dateTimeIso: iso };
}

function clearResults(state: AppState): AppState {
  return {
    ...state,
    itineraries: null, notice: null, error: null, searched: false,
    nearbyRoutes: null, nearbyRoutesError: null, connectResults: {}, connectErrors: {},
  };
}

export function swapFromTo(state: AppState): AppState {
  return clearResults({ ...state, from: state.to, to: state.from });
}

export function setFieldQuery(state: AppState, field: AddressFieldName, query: string): AppState {
  return clearResults({ ...state, [field]: { query, resolved: null, suggestions: [] } });
}

export function setFieldSuggestions(state: AppState, field: AddressFieldName, suggestions: GeocodeCandidate[]): AppState {
  return { ...state, [field]: { ...state[field], suggestions } };
}

export function resolveField(state: AppState, field: AddressFieldName, candidate: GeocodeCandidate): AppState {
  return clearResults({
    ...state,
    [field]: {
      query: candidate.label,
      resolved: { placeId: candidate.placeId, label: candidate.label, lat: candidate.lat, lon: candidate.lon },
      suggestions: [],
    },
  });
}

// Searching and drop-me-off are mutually exclusive result modes rendered in separate, always-on
// areas of the page (#results and #nearby-routes, both driven unconditionally by state) -- without
// this, switching from one to the other left the previous mode's stale results on screen alongside
// the new ones instead of replacing them.
function clearDropMeOffResults(state: AppState): AppState {
  return { ...state, nearbyRoutes: null, nearbyRoutesError: null, connectResults: {}, connectErrors: {} };
}

function clearSearchResults(state: AppState): AppState {
  return { ...state, itineraries: null, notice: null, error: null, searched: false };
}

export function setItineraries(state: AppState, itineraries: ItineraryDto[], notice: string | null): AppState {
  return clearDropMeOffResults({ ...state, itineraries, notice, error: null, searched: true });
}

export function setError(state: AppState, message: string): AppState {
  return clearDropMeOffResults({ ...state, itineraries: null, notice: null, error: message, searched: true });
}

export function setNearbyRoutes(state: AppState, routes: NearbyRouteDto[]): AppState {
  return clearSearchResults({ ...state, nearbyRoutes: routes, nearbyRoutesError: null, connectResults: {}, connectErrors: {} });
}

export function setNearbyRoutesError(state: AppState, message: string): AppState {
  return clearSearchResults({ ...state, nearbyRoutes: null, nearbyRoutesError: message });
}

export function setConnectResult(state: AppState, routeGtfsId: string, result: ConnectResponse): AppState {
  const connectErrors = { ...state.connectErrors };
  delete connectErrors[routeGtfsId];
  return { ...state, connectResults: { ...state.connectResults, [routeGtfsId]: result }, connectErrors };
}

export function setConnectError(state: AppState, routeGtfsId: string, message: string): AppState {
  const connectResults = { ...state.connectResults };
  delete connectResults[routeGtfsId];
  return { ...state, connectErrors: { ...state.connectErrors, [routeGtfsId]: message }, connectResults };
}
