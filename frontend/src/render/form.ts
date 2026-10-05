import type { AppState, SearchMode, TimeMode } from "../state";

export interface FormHandlers {
  onSearchModeChange: (mode: SearchMode) => void;
  onTimeModeChange: (mode: TimeMode) => void;
  onPreferHubsChange: (value: boolean) => void;
  onSwap: () => void;
  onFromQueryChanged: (query: string) => void;
  onToQueryChanged: (query: string) => void;
  onDateTimeChanged: (dateTimeIso: string) => void;
}

const SEARCH_MODE_OPTIONS: Array<[SearchMode, string]> = [
  ["park_and_ride", "Park & Ride"],
  ["bring_bike", "Bring Bike"],
];

const TIME_MODE_OPTIONS: Array<[TimeMode, string]> = [
  ["depart_at", "Depart at"],
  ["arrive_by", "Arrive by"],
];

function toDateTimeLocalValue(iso: string): string {
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
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
  const dateTimeInput = root.querySelector<HTMLInputElement>("#datetime-input")!;
  const preferHubsCheckbox = root.querySelector<HTMLInputElement>("#prefer-hubs-checkbox")!;

  if (fromInput.value !== state.from.query) fromInput.value = state.from.query;
  if (toInput.value !== state.to.query) toInput.value = state.to.query;
  preferHubsCheckbox.checked = state.preferHubs;

  const expectedDateTimeValue = toDateTimeLocalValue(state.dateTimeIso);
  if (dateTimeInput.value !== expectedDateTimeValue) dateTimeInput.value = expectedDateTimeValue;

  renderToggle(modeToggle, SEARCH_MODE_OPTIONS, state.searchMode, handlers.onSearchModeChange);
  renderToggle(timeToggle, TIME_MODE_OPTIONS, state.timeMode, handlers.onTimeModeChange);

  fromInput.oninput = () => handlers.onFromQueryChanged(fromInput.value);
  toInput.oninput = () => handlers.onToQueryChanged(toInput.value);
  swapButton.onclick = () => handlers.onSwap();
  dateTimeInput.onchange = () => {
    if (!dateTimeInput.value) return;
    const date = new Date(dateTimeInput.value);
    if (Number.isNaN(date.getTime())) return;
    handlers.onDateTimeChanged(date.toISOString());
  };
  preferHubsCheckbox.onchange = () => handlers.onPreferHubsChange(preferHubsCheckbox.checked);
}
