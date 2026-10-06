import type { AppState, SearchMode, TimeMode } from "../state";
import type { SavedPlace } from "../storage";

export interface FormHandlers {
  onSearchModeChange: (mode: SearchMode) => void;
  onTimeModeChange: (mode: TimeMode) => void;
  onPreferHubsChange: (value: boolean) => void;
  onSwap: () => void;
  onFromQueryChanged: (query: string) => void;
  onToQueryChanged: (query: string) => void;
  onDateTimeChanged: (dateTimeIso: string) => void;
  onToggleFavorite: (field: "from" | "to") => void;
  onFromFocusChanged: (focused: boolean) => void;
  onToFocusChanged: (focused: boolean) => void;
  onMaxTransfersChange: (value: number | null) => void;
}

const SEARCH_MODE_OPTIONS: Array<[SearchMode, string]> = [
  ["park_and_ride", "Park & Ride"],
  ["bring_bike", "Bring Bike"],
];

const TIME_MODE_OPTIONS: Array<[TimeMode, string]> = [
  ["depart_at", "Depart at"],
  ["arrive_by", "Arrive by"],
];

function pad(n: number): string {
  return String(n).padStart(2, "0");
}

function toDateValue(iso: string): string {
  const date = new Date(iso);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function toTimeValue(iso: string): string {
  const date = new Date(iso);
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function renderFavoriteStar(
  button: HTMLButtonElement,
  resolved: { placeId: string } | null,
  favorites: SavedPlace[],
  onToggle: () => void,
): void {
  button.hidden = resolved == null;
  if (resolved != null) {
    button.textContent = favorites.some((p) => p.placeId === resolved.placeId) ? "★" : "☆";
  }
  button.onclick = onToggle;
}

function renderToggle<Value extends string>(
  fieldset: HTMLElement, options: Array<[Value, string]>, selected: Value, onSelect: (value: Value) => void,
): void {
  fieldset.querySelectorAll("button").forEach((button) => button.remove());
  for (const [value, label] of options) {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = label;
    button.setAttribute("aria-pressed", String(value === selected));
    button.addEventListener("click", () => onSelect(value));
    fieldset.appendChild(button);
  }
}

export function renderForm(root: HTMLElement, state: AppState, handlers: FormHandlers): void {
  const fromInput = root.querySelector<HTMLInputElement>("#from-input")!;
  const toInput = root.querySelector<HTMLInputElement>("#to-input")!;
  const modeToggle = root.querySelector<HTMLElement>("#mode-toggle")!;
  const timeToggle = root.querySelector<HTMLElement>("#time-toggle")!;
  const swapButton = root.querySelector<HTMLButtonElement>("#swap-button")!;
  const dateInput = root.querySelector<HTMLInputElement>("#date-input")!;
  const timeInput = root.querySelector<HTMLInputElement>("#time-input")!;
  const preferHubsCheckbox = root.querySelector<HTMLInputElement>("#prefer-hubs-checkbox")!;
  const maxTransfersSelect = root.querySelector<HTMLSelectElement>("#max-transfers-select")!;
  const fromFavoriteStar = root.querySelector<HTMLButtonElement>("#from-favorite-star")!;
  const toFavoriteStar = root.querySelector<HTMLButtonElement>("#to-favorite-star")!;
  const fromClear = root.querySelector<HTMLButtonElement>("#from-clear")!;
  const toClear = root.querySelector<HTMLButtonElement>("#to-clear")!;

  if (fromInput.value !== state.from.query) fromInput.value = state.from.query;
  if (toInput.value !== state.to.query) toInput.value = state.to.query;
  renderFavoriteStar(fromFavoriteStar, state.from.resolved, state.favorites, () => handlers.onToggleFavorite("from"));
  renderFavoriteStar(toFavoriteStar, state.to.resolved, state.favorites, () => handlers.onToggleFavorite("to"));
  preferHubsCheckbox.checked = state.preferHubs;
  const expectedMaxTransfersValue = state.maxTransfers == null ? "" : String(state.maxTransfers);
  if (maxTransfersSelect.value !== expectedMaxTransfersValue) maxTransfersSelect.value = expectedMaxTransfersValue;

  const expectedDateValue = toDateValue(state.dateTimeIso);
  if (dateInput.value !== expectedDateValue) dateInput.value = expectedDateValue;
  const expectedTimeValue = toTimeValue(state.dateTimeIso);
  if (timeInput.value !== expectedTimeValue) timeInput.value = expectedTimeValue;

  renderToggle(modeToggle, SEARCH_MODE_OPTIONS, state.searchMode, handlers.onSearchModeChange);
  renderToggle(timeToggle, TIME_MODE_OPTIONS, state.timeMode, handlers.onTimeModeChange);

  fromInput.oninput = () => handlers.onFromQueryChanged(fromInput.value);
  toInput.oninput = () => handlers.onToQueryChanged(toInput.value);
  fromInput.onfocus = () => handlers.onFromFocusChanged(true);
  fromInput.onblur = () => handlers.onFromFocusChanged(false);
  toInput.onfocus = () => handlers.onToFocusChanged(true);
  toInput.onblur = () => handlers.onToFocusChanged(false);
  swapButton.onclick = () => handlers.onSwap();
  fromClear.onclick = () => {
    fromInput.value = "";
    handlers.onFromQueryChanged("");
  };
  toClear.onclick = () => {
    toInput.value = "";
    handlers.onToQueryChanged("");
  };

  const onDateOrTimeChanged = () => {
    if (!dateInput.value || !timeInput.value) return;
    const date = new Date(`${dateInput.value}T${timeInput.value}`);
    if (Number.isNaN(date.getTime())) return;
    handlers.onDateTimeChanged(date.toISOString());
  };
  dateInput.onchange = onDateOrTimeChanged;
  timeInput.onchange = onDateOrTimeChanged;

  preferHubsCheckbox.onchange = () => handlers.onPreferHubsChange(preferHubsCheckbox.checked);
  maxTransfersSelect.onchange = () => {
    handlers.onMaxTransfersChange(maxTransfersSelect.value === "" ? null : Number(maxTransfersSelect.value));
  };
}
