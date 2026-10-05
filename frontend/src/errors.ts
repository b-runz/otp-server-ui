import type { ApiError } from "./types";

const ERROR_MESSAGES: Record<string, string> = {
  no_coverage: "That trip is outside the area this planner covers.",
  unreachable: "No route was found to that stop.",
  unknown_mode: "Unknown search mode.",
  invalid_time_mode: "Unknown time mode.",
  // Exact copy ported from bikebus's real TripViewModel.kt:426-431.
  unsupported_time_mode: 'Park & Ride doesn\'t support "arrive by" yet — try "depart at".',
  invalid_request: "That request couldn't be understood — check the date/time and try again.",
  geocode_unavailable: "Address search is temporarily unavailable — try again shortly.",
  internal_error: "Something went wrong. Please try again.",
};

export function messageForError(apiError: ApiError): string {
  return ERROR_MESSAGES[apiError.error] ?? ERROR_MESSAGES.internal_error;
}
