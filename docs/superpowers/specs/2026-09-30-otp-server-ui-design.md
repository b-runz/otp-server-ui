# otp-server-ui: Server-Hosted Trip Planner Design

## Motivation

The `bikebus` Android app embeds OpenTripPlanner (OTP) for fully on-device
routing. That constraint (phone CPU/heap) is real: a genuine on-device query
(Aarhus -> Mørke, Bring Bike) took over a minute to resolve, and an earlier
finding in that project established that a national-scale transit core
section costs real memory/time to construct even for a two-station query,
regardless of how it's loaded.

None of that cost is inherent to routing itself — it's specifically the cost
of making routing work inside a phone's memory and CPU budget. A persistent
server process doesn't have that budget: it builds the OTP graph once, holds
it entirely in memory, and serves every subsequent request against the
already-built graph. This project builds that server, plus a plain web
frontend, as a new, independent repository — not a rewrite of the Android
app, and not sharing git history with `bikebus`.

**Goal:** a web-based trip planner, hosted on a persistent server (a
free-tier Oracle OCI VM), with feature parity against `bikebus`'s current
routing modes, that resolves queries fast because the graph is already
loaded and never has to be lazily reconstructed per request.

## Non-Goals

- Multi-user accounts or authentication. Single deployment, no login.
- Park & Ride's Arrive By mode. Today's `bikebus` app already rejects this
  combination; this project keeps that limitation for v1 and revisits later.
- An embedded map view. Per-leg "open in Maps" links keep deep-linking to an
  external maps app/site (see "Maps links," below) — no in-page map
  rendering.
- GTFS-realtime, service alerts, or any live-data feed beyond the static
  GTFS + OSM extract used to build the graph.
- Reusing `bikebus`'s *lazy/scoped* loading behavior (`LazyRaptorTransitDataProvider`'s
  per-search discovery, `ScopedPatternMaterializer`'s touched-subset
  scoping, `IncrementalGraphAssembler`'s tile-by-tile partial loading,
  `LazyOsmVertex`'s on-demand edge decoding). That behavior exists
  specifically to fit inside a phone's memory ceiling — a server has no such
  ceiling, so this project calls the same underlying construction code
  *unscoped* (see "Data pipeline and graph loading," below) instead of
  reimplementing it.
- Vendoring OTP's own graph-building/ingestion code (`graph_builder.*`,
  `gtfs.*`, `osm.*`) — `bikebus`'s own `VENDORED.md` confirms these were
  deliberately excluded from what it vendored, and pulling in a real
  released upstream OTP JAR just for this would risk a binary-incompatible
  `Graph` format against everything else that's vendored (the vendored
  commit, `61a3af6798`, is 2454 commits past the nearest tagged release, not
  itself a release). Building the graph from a raw GTFS zip + OSM `.pbf`
  extract happens externally, using a real, unmodified, standalone OTP build
  from that exact commit (available locally at
  `bikebus/OpenTripPlanner`) — this project only *loads* the resulting
  serialized graph file, it never *builds* one.

## Feature Inventory (parity target)

Migrated from `bikebus`, all computed server-side in one request per search:

- **Search modes:**
  - **Park & Ride** — bike to a stop, one zero-transfer transit ride, walk to
    destination. Two-tier egress: a strict 15-minute walk-egress search
    first, widened to a large fallback cap only if that finds nothing, so a
    real destination whose nearest stop is a longer walk away still returns
    a route (flagged as a long final walk) instead of "no route."
  - **Bring Bike** — direct bike route plus bike-access/bike-egress transit
    itineraries, filtered by an over-limit bike-distance warning (10 km).
  - **Drop-me-off** — nearby-routes-at-destination lookup, plus the "flag a
    passing bus" connect flow (walk to intercept a bus mid-route rather than
    at a scheduled stop). `bikebus`'s current implementation of this last
    piece calls a separate, pre-existing remote OTP server over GraphQL
    (`OtpQueryBuilder`/`otpApi`) — this project reimplements it directly
    against the same in-process routing code every other mode uses, so no
    GraphQL dependency survives into this project at all.
- **Time modes:** Depart at / Arrive by (both, for Bring Bike and
  Drop-me-off; Park & Ride keeps Depart-at-only, see Non-Goals).
- **Preferences:** Prefer transit hubs (hub-stitching: re-plan a two-leg trip
  through a cataloged hub when the baseline itinerary's transfer point sits
  near one, keep it only if it doesn't cost much more).
- **Search UX (all client-only, see Architecture):** address autocomplete
  (Google Places), the "Add house number" affordance after a street-level
  match, from/to swap, favorites/recents (per-browser, not server-side).
- **Result badges (server-computed flags, client-rendered):** over-limit
  bike distance, long walk egress.
- **Maps links:** each leg links to
  `https://www.google.com/maps/dir/?api=1&origin=...&destination=...&travelmode=...&departure_time=...`
  — Google's own cross-platform Maps URL format, already used by `bikebus`
  today (`MapsIntent.kt`) minus the Android-specific `setPackage(...)` call.
  Built entirely client-side from data already present in the search
  response; needs no backend involvement.

## Architecture

Two independently deployable pieces, served as a single process on the VM:

```
+-----------------------------------------------------------+
|  Server (Ktor, JVM)                                        |
|                                                              |
|  +-------------------+     +--------------------------+    |
|  | Static file server |---> | frontend/dist (built by  |    |
|  | (serves the built   |     | Bun ahead of time)       |    |
|  | frontend)           |     +--------------------------+    |
|  +-------------------+                                      |
|                                                              |
|  +----------------------------------------------------+    |
|  | JSON API                                             |    |
|  |  POST /search      -> routing/ (in-process)          |    |
|  |  GET  /geocode      -> Google Places proxy            |    |
|  +----------------------------------------------------+    |
|                                                              |
|  +----------------------------------------------------+    |
|  | routing/ (RoutingEngine, BuildingBlocks,              |    |
|  | ParkAndRideFinder, HubRouting, filters, mappers --    |    |
|  | ported from bikebus, eager loading only)              |    |
|  +----------------------------------------------------+    |
|                          |                                   |
|  +----------------------------------------------------+    |
|  | Graph (loaded once at startup from a pre-built        |    |
|  | serialized graph file -- see "Data pipeline and       |    |
|  | graph loading" -- held in memory for the process's    |    |
|  | lifetime)                                              |    |
|  +----------------------------------------------------+    |
+-----------------------------------------------------------+
```

**Why one process, not a separate frontend server:** the frontend is a
static build (HTML/CSS/JS) with no server-side rendering step — Bun is a
build-time tool, not something that needs to run on the VM. Ktor serving
both the static bundle and the JSON API means no CORS configuration, one
port, one deployable artifact, one thing to keep running.

**Why not GraphQL:** a single, purpose-built `POST /search` endpoint returns
exactly the shape the frontend needs to render a result — full itineraries,
badges, everything — computed in one server-side pass. No generic query
language, no client-side joining of separate responses, no N+1 client-driven
follow-up calls.

### Backend

- **Language/framework:** Kotlin, Ktor.
- **Vendored OTP modules:** `otp-raptor`, `otp-street`, `otp-routing`,
  `otp-domain-core`, `otp-astar`, `otp-utils`, copied from `bikebus` verbatim
  (these are OTP's own code, untouched by the lazy-loading work and not
  specific to the phone constraint).
- **Routing logic:** `RoutingEngine`, `BuildingBlocks`, `ParkAndRideFinder`,
  `HubRouting`, the itinerary filter chain, and the `Itinerary`/`Leg`
  mapper, copied from `bikebus` and adapted to drop every lazy/scoped-bridge
  parameter and code path — `RoutingEngine` goes back to eager
  `TransitService`/whole-network `RaptorTransitData` construction, since the
  server holds one graph in memory for its whole lifetime and there is no
  per-request scoping to do.
- **Graph build vs. graph load — two separate concerns, see "Data pipeline
  and graph loading" below:** *building* a graph from raw GTFS + OSM stays
  entirely external to this repo (real, unmodified, standalone OTP, run from
  the exact vendored commit's own source). This repo only *loads* the
  resulting serialized graph file at startup, via a newly-vendored slice of
  OTP's own graph serialization code (`routing/graph/SerializedGraphObject.java`
  plus its `kryosupport/` package, ~8 files, using Kryo — much smaller than
  the ingestion pipeline these two rely on).
- **`POST /search` request:** origin/destination coordinates, mode
  (park-and-ride / bring-bike / drop-me-off), time mode + datetime, prefer-
  hubs flag, and (drop-me-off only) any via-stop context the connect flow
  needs.
- **`POST /search` response:** a complete, ready-to-render itinerary list —
  legs (mode, distance, duration, endpoints, route name, geometry), and the
  bike-limit/long-walk badge flags — already computed. Nothing left for the
  client to calculate.
- **`GET /geocode?q=`:** thin proxy to Google Places Autocomplete, holding
  the API key server-side so it never reaches the browser.

### Frontend

- **Toolchain:** Bun, plain HTML/CSS/TypeScript. No UI framework.
- **Fully client-side, zero backend calls except the two above:** address
  autocomplete UI (calling `/geocode`), the house-number affordance,
  from/to swap, mode toggle, depart/arrive + date/time pickers, favorites
  and recents (`localStorage`, mirroring today's per-device behavior — no
  server-side persistence), itinerary expand/collapse (pure UI state, no
  data change).
- **Rendering:** itinerary cards built directly from the `/search` JSON
  response — no follow-up calls, no client-side computation of badges or
  totals beyond simple display formatting (e.g. duration-to-"1h 12m").

## Data Pipeline and Graph Loading

Building a graph (parsing GTFS + OSM into a routable `Graph`/
`TransitRepository`) and loading a graph (deserializing an already-built one
into memory) are two separate concerns here, deliberately kept apart:

- **Building stays external to this repo.** It uses real, unmodified,
  standalone OpenTripPlanner, run directly from the full source checkout at
  `bikebus/OpenTripPlanner` (commit `61a3af6798` — the exact commit
  `bikebus`'s own vendored modules were themselves cut from, so the
  resulting serialized graph format is guaranteed compatible). This project
  never vendors `graph_builder.*`/`gtfs.*`/`osm.*` — `bikebus`'s own
  `VENDORED.md` explicitly excluded that slice as out of scope, and it pulls
  in a large, unrelated dependency footprint (`onebusaway-gtfs`, GeoTools,
  JTS) this project has no other use for.
- **Loading is a small, newly-vendored addition.** OTP's own
  `routing/graph/SerializedGraphObject.java` plus its `kryosupport/`
  subpackage (Kryo-based binary serialization, ~8 files total) reads a
  serialized graph file back into a real `Graph`/`TransitRepository`/
  `TransferRepository`. It references a handful of repository types
  (`WorldEnvelopeRepository`, `VehicleParkingRepository`,
  `OsmInfoGraphBuildRepository`, `StopConsolidationRepository`,
  `EmissionRepository`, `EmpiricalDelayRepository`, `FareServiceFactory`)
  this project doesn't otherwise need — these get stubbed the same way
  `bikebus`'s own `VENDORED.md`/`STUBS.md` already stub comparable
  boundary/`ext.*` classes it excluded, not implemented for real.
- **Server startup:** load the pre-built serialized graph file via this
  newly-vendored code, extract the real `Graph`/`TransitRepository`/
  `TransferRepository` it deserializes to, and construct `RoutingEngine`
  directly from them — no lazy/scoped bridge, no per-request rebuilding.

## Data Flow: One Search

1. User fills the form entirely client-side: autocomplete resolves from/to
   to coordinates + display names (via `/geocode`, debounced per keystroke,
   returning a small JSON list of candidates), mode/time/preferences are
   local UI state.
2. Click Search: one `POST /search` with the full request body.
3. Backend builds a `RouteRequest`, calls the matching composition
   (`ParkAndRideFinder.search`, the Bring Bike composition, or the
   Drop-me-off nearby+connect flow), runs the filter chain, maps to the
   response DTO, returns it.
4. Frontend renders the itinerary list directly from that response. Per-leg
   "Maps" links are built client-side from leg coordinates/mode/departure
   time already in the payload.

No intermediate network calls, no client-side re-fetching of "more detail"
for an itinerary already returned.

## Error Handling

- A `RoutingValidationException`-equivalent (origin/destination outside the
  loaded graph, or linked but disconnected) maps to a specific error shape
  in the `/search` response (e.g. `{"error": "no_coverage"}` /
  `{"error": "unreachable"}`), not a generic 500 — the frontend renders a
  clear, mode-appropriate message, mirroring `bikebus`'s existing
  `RoutingError.NoCoverageForArea` handling.
- `/geocode` proxy failures (Google API errors, rate limits) return a small
  JSON error the frontend can show inline near the address field, without
  failing the whole page.
- Startup graph-load failure (missing file, corrupt/incompatible serialized
  format) is fatal — the process does not start serving traffic with a
  partially-loaded or absent graph. (Decide exact health-check/readiness
  behavior during implementation planning.)

## Testing

- **Backend:** JVM/JUnit, following the same discipline established in
  `bikebus`'s own routing tests — a small, real, pre-built serialized graph
  test fixture (built once, offline, the same way the production graph is —
  see "Data Pipeline and Graph Loading" — covering the same real Aarhus area
  `bikebus`'s own test fixtures use, checked in as a binary test resource),
  golden-value assertions, no long-running heap/latency benchmarks as
  recurring unit tests. Add API-level tests against Ktor's test client for
  each endpoint's request/response contract, including the error shapes
  above.
- **Frontend:** Bun's built-in test runner for client-side logic that has
  real behavior worth pinning (address-swap logic, badge rendering,
  Maps-link construction) — no framework-heavy component testing needed
  given there's no framework. A basic smoke test (form fill -> submit ->
  result rendered) is in scope; broader end-to-end testing is not required
  for v1.

## Deployment

- Single Kotlin/Ktor JVM process on a free-tier Oracle OCI VM, serving both
  the JSON API and the pre-built static frontend.
- Exact process supervision (systemd unit vs. container) and the
  GTFS/OSM.pbf refresh cadence are implementation-planning decisions, not
  fixed here.

## Open Items Carried Into Implementation Planning

- The exact stubbing needed for `SerializedGraphObject`'s handful of
  repository dependencies (`WorldEnvelopeRepository`,
  `VehicleParkingRepository`, `OsmInfoGraphBuildRepository`,
  `StopConsolidationRepository`, `EmissionRepository`,
  `EmpiricalDelayRepository`, `FareServiceFactory`) — confirm each is safe
  to stub as an empty/no-op implementation (this project doesn't use any of
  the features they back), the way `bikebus`'s own `STUBS.md` already
  stubbed comparable boundary classes.
- The real, standalone-OTP command/config needed to build a serialized
  graph file from a GTFS zip + Denmark OSM `.pbf` extract, and how often
  that data gets refreshed operationally.
- The exact via-stop/flag-stop request shape for Drop-me-off's connect flow,
  once that logic is ported off `OtpQueryBuilder`/GraphQL onto the
  in-process routing code.
