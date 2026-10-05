import { test, expect } from "bun:test";
import { messageForError } from "../src/errors";

test("every documented error code has its own message", () => {
  expect(messageForError({ error: "no_coverage" })).not.toBe(messageForError({ error: "internal_error" }));
  expect(messageForError({ error: "unreachable" })).toContain("route");
  expect(messageForError({ error: "unknown_mode" })).toBeTruthy();
  expect(messageForError({ error: "invalid_time_mode" })).toBeTruthy();
  expect(messageForError({ error: "unsupported_time_mode" })).toBe('Park & Ride doesn\'t support "arrive by" yet — try "depart at".');
  expect(messageForError({ error: "invalid_request" })).toBeTruthy();
  expect(messageForError({ error: "geocode_unavailable" })).toBeTruthy();
  expect(messageForError({ error: "internal_error" })).toBeTruthy();
});

test("an unrecognized code falls back to the generic internal_error message", () => {
  expect(messageForError({ error: "something_new" })).toBe(messageForError({ error: "internal_error" }));
});
