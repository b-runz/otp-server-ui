export function mountApp(root: HTMLElement): void {
  root.innerHTML = "<h1>Trip Planner</h1>";
}

const appRoot = typeof document !== "undefined" ? document.getElementById("app") : null;
if (appRoot) {
  mountApp(appRoot);
}
