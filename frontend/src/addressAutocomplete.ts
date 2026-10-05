import type { GeocodeCandidate } from "./types";
import type { AddressFieldName } from "./state";

interface PendingJob {
  timer: ReturnType<typeof setTimeout> | null;
  controller: AbortController | null;
}

export interface AutocompleteControllerOptions {
  debounceMs?: number;
  minQueryLength?: number;
  geocode: (query: string, signal: AbortSignal) => Promise<GeocodeCandidate[]>;
  onSuggestions: (field: AddressFieldName, suggestions: GeocodeCandidate[]) => void;
}

export function createAutocompleteController(options: AutocompleteControllerOptions) {
  const debounceMs = options.debounceMs ?? 300;
  const minQueryLength = options.minQueryLength ?? 3;
  const pending: Record<AddressFieldName, PendingJob> = {
    from: { timer: null, controller: null },
    to: { timer: null, controller: null },
  };

  function onQueryChanged(field: AddressFieldName, query: string): void {
    const job = pending[field];
    if (job.timer != null) clearTimeout(job.timer);
    job.controller?.abort();
    job.controller = null;

    if (query.length < minQueryLength) {
      options.onSuggestions(field, []);
      return;
    }

    job.timer = setTimeout(() => {
      const controller = new AbortController();
      job.controller = controller;
      options.geocode(query, controller.signal)
        .then((suggestions) => {
          if (!controller.signal.aborted) options.onSuggestions(field, suggestions);
        })
        .catch(() => {
          if (!controller.signal.aborted) options.onSuggestions(field, []);
        });
    }, debounceMs);
  }

  return { onQueryChanged };
}
