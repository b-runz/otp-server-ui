import type {
  SearchRequest, SearchResponse, ConnectRequest, ConnectResponse,
  NearbyRoutesResponse, GeocodeResponse, ApiError,
} from "./types";

async function parseJsonOrError<T>(response: Response): Promise<T | ApiError> {
  const body = await response.json();
  return body as T | ApiError;
}

export async function search(request: SearchRequest): Promise<SearchResponse | ApiError> {
  const response = await fetch("/search", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  return parseJsonOrError<SearchResponse>(response);
}

export async function connect(request: ConnectRequest): Promise<ConnectResponse | ApiError> {
  const response = await fetch("/connect", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  return parseJsonOrError<ConnectResponse>(response);
}

export async function nearbyRoutes(lat: number, lon: number, radiusMeters?: number): Promise<NearbyRoutesResponse | ApiError> {
  const params = new URLSearchParams({ lat: String(lat), lon: String(lon) });
  if (radiusMeters != null) params.set("radiusMeters", String(radiusMeters));
  const response = await fetch(`/nearby-routes?${params.toString()}`);
  return parseJsonOrError<NearbyRoutesResponse>(response);
}

export async function geocode(query: string, signal?: AbortSignal): Promise<GeocodeResponse | ApiError> {
  const response = await fetch(`/geocode?q=${encodeURIComponent(query)}`, { signal });
  return parseJsonOrError<GeocodeResponse>(response);
}
