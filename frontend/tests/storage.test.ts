import { test, expect, beforeEach } from "bun:test";
import {
  loadFavorites, saveFavorites, loadRecents, saveRecents,
  rememberRecent, removeRecent, toggleFavorite, incrementRank,
  favoritesSortedForPicker, type SavedPlace,
} from "../src/storage";

beforeEach(() => {
  localStorage.clear();
});

test("favorites/recents round-trip through localStorage", () => {
  const place: SavedPlace = { placeId: "p1", label: "Somewhere", lat: 1, lon: 2, rank: 0 };
  saveFavorites([place]);
  expect(loadFavorites()).toEqual([place]);
  saveRecents([place]);
  expect(loadRecents()).toEqual([place]);
});

test("rememberRecent caps at 10, most-recent-first, deduplicated by placeId", () => {
  let recents: SavedPlace[] = [];
  for (let i = 0; i < 12; i++) {
    recents = rememberRecent([], recents, `p${i}`, `Place ${i}`, 0, 0);
  }
  expect(recents).toHaveLength(10);
  expect(recents[0].placeId).toBe("p11");
  expect(recents[9].placeId).toBe("p2");

  const reselected = rememberRecent([], recents, "p5", "Place 5", 0, 0);
  expect(reselected).toHaveLength(10);
  expect(reselected[0].placeId).toBe("p5");
  expect(reselected.filter((p) => p.placeId === "p5")).toHaveLength(1);
});

test("rememberRecent never adds a place already in favorites", () => {
  const favorites: SavedPlace[] = [{ placeId: "fav1", label: "Favorite place", lat: 0, lon: 0, rank: 0 }];
  const recents = rememberRecent(favorites, [], "fav1", "Favorite place", 0, 0);
  expect(recents).toHaveLength(0);
});

test("toggleFavorite adds (reusing an existing recent's data) and removes", () => {
  const recents: SavedPlace[] = [{ placeId: "p1", label: "Somewhere", lat: 1, lon: 2, rank: 3 }];
  const added = toggleFavorite([], recents, "p1", "Somewhere", 1, 2);
  expect(added.favorites).toEqual([{ placeId: "p1", label: "Somewhere", lat: 1, lon: 2, rank: 3 }]);
  expect(added.recents).toHaveLength(0);

  const removed = toggleFavorite(added.favorites, added.recents, "p1", "Somewhere", 1, 2);
  expect(removed.favorites).toHaveLength(0);
});

test("favorites are uncapped", () => {
  let favorites: SavedPlace[] = [];
  for (let i = 0; i < 15; i++) {
    favorites = toggleFavorite(favorites, [], `p${i}`, `Place ${i}`, 0, 0).favorites;
  }
  expect(favorites).toHaveLength(15);
});

test("incrementRank only bumps the matching entry in whichever list it's in", () => {
  const favorites: SavedPlace[] = [{ placeId: "fav1", label: "F", lat: 0, lon: 0, rank: 0 }];
  const recents: SavedPlace[] = [{ placeId: "rec1", label: "R", lat: 0, lon: 0, rank: 0 }];

  const bumpedFav = incrementRank(favorites, recents, "fav1");
  expect(bumpedFav.favorites[0].rank).toBe(1);
  expect(bumpedFav.recents[0].rank).toBe(0);

  const bumpedRec = incrementRank(favorites, recents, "rec1");
  expect(bumpedRec.favorites[0].rank).toBe(0);
  expect(bumpedRec.recents[0].rank).toBe(1);
});

test("favoritesSortedForPicker sorts by rank descending", () => {
  const favorites: SavedPlace[] = [
    { placeId: "low", label: "Low", lat: 0, lon: 0, rank: 1 },
    { placeId: "high", label: "High", lat: 0, lon: 0, rank: 5 },
  ];
  expect(favoritesSortedForPicker(favorites).map((p) => p.placeId)).toEqual(["high", "low"]);
});
