import { test, expect } from "bun:test";
import { mountApp } from "../src/main";

test("mountApp renders into the given root element", () => {
  const root = document.createElement("main");
  mountApp(root);
  expect(root.textContent).toContain("Trip Planner");
});
