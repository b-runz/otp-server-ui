export interface SearchRequest {
  mode: "park_and_ride" | "bring_bike";
  timeMode: "depart_at" | "arrive_by";
  originLat: number;
  originLon: number;
  destinationLat: number;
  destinationLon: number;
  dateTimeIso: string;
  preferHubs: boolean;
}

export interface LegDto {
  mode: string;
  distanceMeters: number;
  durationSeconds: number;
  fromLat: number;
  fromLon: number;
  toLat: number;
  toLon: number;
  fromName: string | null;
  toName: string | null;
  routeShortName: string | null;
  departureEpochSecond: number;
}

export interface ItineraryDto {
  legs: LegDto[];
  exceedsBikeLimit: boolean;
  hasLongWalkEgress: boolean;
}

export interface SearchResponse {
  itineraries: ItineraryDto[];
  notice: string | null;
}

export interface ApiError {
  error: string;
}

export interface NearbyRouteDto {
  routeGtfsId: string;
  routeShortName: string | null;
  stopIds: string[];
  distanceMeters: number;
}

export interface NearbyRoutesResponse {
  routes: NearbyRouteDto[];
}

export interface ConnectRequest {
  originLat: number;
  originLon: number;
  destinationLat: number;
  destinationLon: number;
  routeGtfsId: string;
  routeStopIds: string[];
  timeMode: "depart_at" | "arrive_by";
  dateTimeIso: string;
  preferHubs: boolean;
}

export interface FlagStopInfoDto {
  flagLat: number;
  flagLon: number;
  officialFinalLegDistanceMeters: number;
  officialFinalLegDurationSeconds: number;
  flagStopDistanceMeters: number;
  flagStopDurationSeconds: number;
}

export interface ConnectResponse {
  itinerary: ItineraryDto;
  flagStopInfo: FlagStopInfoDto | null;
  extraRideSeconds: number | null;
  hubName: string | null;
}

export interface GeocodeCandidate {
  placeId: string;
  label: string;
  lat: number;
  lon: number;
  isStreet: boolean;
}

export interface GeocodeResponse {
  candidates: GeocodeCandidate[];
}

export function isApiError(value: unknown): value is ApiError {
  return typeof value === "object" && value !== null && typeof (value as Record<string, unknown>).error === "string";
}
