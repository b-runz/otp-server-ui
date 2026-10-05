import type {
  SearchRequest, SearchResponse, ConnectRequest, ConnectResponse,
  NearbyRoutesResponse, GeocodeResponse, ApiError,
} from "./types";

async function safeFetchJson<T>(input: string, init?: RequestInit): Promise<T | ApiError> {
  try {
    const response = await fetch(input, init);
    return (await response.json()) as T | ApiError;
  } catch {
    return { error: "internal_error" };
  }
}

export async function search(request: SearchRequest): Promise<SearchResponse | ApiError> {
  return safeFetchJson<SearchResponse>("/search", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
}

export async function connect(request: ConnectRequest): Promise<ConnectResponse | ApiError> {
  return safeFetchJson<ConnectResponse>("/connect", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
}

export async function nearbyRoutes(lat: number, lon: number, radiusMeters?: number): Promise<NearbyRoutesResponse | ApiError> {
  const params = new URLSearchParams({ lat: String(lat), lon: String(lon) });
  if (radiusMeters != null) params.set("radiusMeters", String(radiusMeters));
  return safeFetchJson<NearbyRoutesResponse>(`/nearby-routes?${params.toString()}`);
}

export async function geocode(query: string, signal?: AbortSignal): Promise<GeocodeResponse | ApiError> {
  return safeFetchJson<GeocodeResponse>(`/geocode?q=${encodeURIComponent(query)}`, { signal });
}
