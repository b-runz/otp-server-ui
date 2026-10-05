export interface SavedPlace {
  placeId: string;
  label: string;
  lat: number;
  lon: number;
  rank: number;
}

const FAVORITES_KEY = "otp-server-ui:favorites";
const RECENTS_KEY = "otp-server-ui:recents";
const RECENTS_CAP = 10;

function load(key: string): SavedPlace[] {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as SavedPlace[]) : [];
  } catch {
    return [];
  }
}

function save(key: string, places: SavedPlace[]): void {
  try {
    localStorage.setItem(key, JSON.stringify(places));
  } catch {
    // localStorage can throw (private browsing, storage full); favorites/recents are a
    // convenience, not required state, so a failed write is silently dropped.
  }
}

export function loadFavorites(): SavedPlace[] {
  return load(FAVORITES_KEY);
}

export function saveFavorites(places: SavedPlace[]): void {
  save(FAVORITES_KEY, places);
}

export function loadRecents(): SavedPlace[] {
  return load(RECENTS_KEY);
}

export function saveRecents(places: SavedPlace[]): void {
  save(RECENTS_KEY, places);
}

export function rememberRecent(
  favorites: SavedPlace[], recents: SavedPlace[],
  placeId: string, label: string, lat: number, lon: number,
): SavedPlace[] {
  if (favorites.some((p) => p.placeId === placeId)) return recents;
  const entry: SavedPlace = { placeId, label, lat, lon, rank: 0 };
  return [entry, ...recents.filter((p) => p.placeId !== placeId)].slice(0, RECENTS_CAP);
}

export function removeRecent(recents: SavedPlace[], placeId: string): SavedPlace[] {
  return recents.filter((p) => p.placeId !== placeId);
}

export function toggleFavorite(
  favorites: SavedPlace[], recents: SavedPlace[],
  placeId: string, label: string, lat: number, lon: number,
): { favorites: SavedPlace[]; recents: SavedPlace[] } {
  if (favorites.some((p) => p.placeId === placeId)) {
    return { favorites: favorites.filter((p) => p.placeId !== placeId), recents };
  }
  const existingRecent = recents.find((p) => p.placeId === placeId);
  const entry: SavedPlace = existingRecent ?? { placeId, label, lat, lon, rank: 0 };
  return {
    favorites: [entry, ...favorites],
    recents: recents.filter((p) => p.placeId !== placeId),
  };
}

export function incrementRank(
  favorites: SavedPlace[], recents: SavedPlace[], placeId: string,
): { favorites: SavedPlace[]; recents: SavedPlace[] } {
  const bump = (list: SavedPlace[]) => list.map((p) => (p.placeId === placeId ? { ...p, rank: p.rank + 1 } : p));
  if (favorites.some((p) => p.placeId === placeId)) return { favorites: bump(favorites), recents };
  if (recents.some((p) => p.placeId === placeId)) return { favorites, recents: bump(recents) };
  return { favorites, recents };
}

export function favoritesSortedForPicker(favorites: SavedPlace[]): SavedPlace[] {
  return [...favorites].sort((a, b) => b.rank - a.rank);
}
