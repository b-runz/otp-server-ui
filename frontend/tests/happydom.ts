import { Window } from "happy-dom";

const window = new Window();
globalThis.window = window as unknown as typeof globalThis.window;
globalThis.document = window.document as unknown as Document;
globalThis.HTMLElement = window.HTMLElement as unknown as typeof HTMLElement;
globalThis.Event = window.Event as unknown as typeof Event;
globalThis.localStorage = window.localStorage as unknown as Storage;
