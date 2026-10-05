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
  - **Drop-me-off** — a real, two-step flow: `GET /nearby-routes` finds real
    routes (with their GTFS route id and stop ids) near the destination;
    `POST /connect` then runs a full, via-constrained origin-to-destination
    search restricted to the chosen route (origin coordinates, the route's
    id + stop ids, time mode, prefer-hubs), picks the cheapest itinerary that
    actually rides it, optionally hub-stitches, and finds a flag point on the
    bus's own path to compare against the official stop. `bikebus`'s current
    implementation of this last piece calls a separate, pre-existing remote
    OTP server over GraphQL (`OtpQueryBuilder`/`otpApi`) — this project
    reimplements the whole thing directly against the same in-process routing
    code every other mode uses, so no GraphQL dependency survives into this
    project at all.
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
|  |  POST /search         -> routing/ (in-process)        |    |
|  |  GET  /nearby-routes  -> routing/ (in-process)        |    |
|  |  POST /connect        -> routing/ (in-process)        |    |
|  |  GET  /geocode        -> Google Places proxy          |    |
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
  (park_and_ride / bring_bike), time mode + datetime, prefer-hubs flag.
- **`POST /search` response:** a complete, ready-to-render itinerary list —
  legs (mode, distance, duration, endpoints, route name, departure time),
  and the bike-limit/long-walk badge flags — already computed. Nothing left
  for the client to calculate.
- **`GET /nearby-routes?lat=&lon=&radiusMeters=`:** real routes near a
  destination, each with its GTFS route id, short name, distance, and its
  real stop ids (needed by `/connect` below) — Drop-me-off's first step.
- **`POST /connect` request:** origin coordinates, destination coordinates,
  the chosen route's GTFS id + stop ids (from `/nearby-routes`), time mode +
  datetime, prefer-hubs flag. Drop-me-off's second step: a full,
  via-constrained search restricted to that route.
- **`POST /connect` response:** the resulting itinerary (same shape as a
  search result), an optional flag-stop comparison (official stop vs. the
  flag point found on the bus's own path, each with distance/duration), the
  extra ride time versus a fresh baseline search, and the hub name if
  hub-stitching applied — or a typed `unreachable` error.
- **`GET /geocode?q=`:** thin proxy to Google Places Autocomplete, holding
  the API key server-side so it never reaches the browser.

### Frontend

- **Toolchain:** Bun, plain HTML/CSS/TypeScript. No UI framework —
  considered and declined adopting one (e.g. Svelte) during frontend
  planning: this app is one screen with modest interactivity, and a
  hand-written render layer stays small enough (roughly 150-200 lines
  across 4-5 sections) that a compiled framework's benefit doesn't offset
  its added toolchain complexity.
- **State/rendering approach:** one plain state object, mutated by named
  functions (`setSearchMode`, `selectFromSuggestion`, etc.), each of which
  calls the specific hand-written `render*()` function(s) for the sections
  that depend on what changed — no virtual DOM, no diffing, no reactive
  framework.
- **Fully client-side, zero backend calls except the four above:** address
  autocomplete UI (calling `/geocode`, 300ms debounce, 3-character minimum
  query length, matching `bikebus`'s real `onFromQueryChanged`/
  `onToQueryChanged` timing), the house-number affordance, from/to swap,
  mode toggle, depart/arrive + date/time pickers, favorites and recents
  (`localStorage`, mirroring today's per-device behavior — no server-side
  persistence; recents cap at 10, most-recent-first, deduplicated by place,
  matching `bikebus`'s real `rememberRecent`), itinerary expand/collapse
  (pure UI state, no data change).
- **Rendering:** itinerary cards built directly from the `/search`/
  `/connect` JSON response — no follow-up calls, no client-side computation
  of badges or totals beyond simple display formatting (e.g.
  duration-to-"1h 12m").
- **Not ported from `bikebus`:** the "Wake up server" button
  (`WakeStatus`/`otpWakeApi`) — it existed only because `bikebus`'s
  Drop-me-off used to depend on a separate, possibly-sleeping remote OTP
  server over GraphQL. This project's backend is one persistent process
  with nothing to wake.

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
   (`ParkAndRideFinder.search` or the Bring Bike composition, including
   hub-stitching when requested), runs the filter chain, maps to the
   response DTO, returns it.
4. Frontend renders the itinerary list directly from that response. Per-leg
   "Maps" links are built client-side from leg coordinates/mode/departure
   time already in the payload.

No intermediate network calls, no client-side re-fetching of "more detail"
for an itinerary already returned.

## Data Flow: Drop-me-off (two steps)

1. Click "Drop me off": one `GET /nearby-routes` against the resolved
   destination. Frontend renders the returned routes as a list.
2. User taps a route: one `POST /connect`, carrying that route's real GTFS
   id + stop ids (from step 1's response) alongside origin/destination/time/
   preferences.
3. Backend runs the real connect-to-route flow (`connectToRoute`): a
   via-constrained search restricted to the chosen route, picks the
   cheapest qualifying itinerary, optionally hub-stitches, finds a flag
   point on the bus's own path, and computes the official-stop-vs-flag-point
   comparison plus the extra ride time versus a fresh baseline search.
4. Frontend renders the connect result inline under the selected route: the
   itinerary's legs (each with its own Maps link), the comparison panel, and
   a dedicated Maps link to the flag point — or the typed `unreachable`
   error if no route exists at all.

## Error Handling

Every endpoint returns a typed `{"error": "<code>"}` body (never a bare
500) for every error path reachable from valid-looking client input. The
real, final set of codes, each with its own frontend-rendered message:

- `"no_coverage"` (422) — origin/destination outside the loaded graph, or
  linked but disconnected (`/search`'s `RoutingValidationException` catch).
- `"unreachable"` (422) — `/connect` found no route to the flag point at
  all.
- `"unknown_mode"` (400) — `/search`'s `mode` field isn't `park_and_ride`/
  `bring_bike`.
- `"invalid_time_mode"` (400) — `timeMode` isn't `depart_at`/`arrive_by`.
- `"unsupported_time_mode"` (400) — `park_and_ride` combined with
  `arrive_by` (Park & Ride is depart-at only, per Non-Goals).
- `"invalid_request"` (400) — an unparseable `dateTimeIso`.
- `"geocode_unavailable"` (502) — a failed/timed-out/erroring call to
  Google Places from `/geocode`.
- `"internal_error"` (500) — a generic backend-wide catch-all for anything
  else uncaught; the frontend shows a generic "something went wrong, try
  again" message for this one, since it carries no specific meaning.

Startup graph-load failure (missing file, corrupt/incompatible serialized
format) is fatal — the process does not start serving traffic with a
partially-loaded or absent graph.

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

## Backend Status

The backend described above is fully implemented and tested (20-task plan,
complete as of 2026-10-05) — every item this section originally listed as
open (repository stubbing, the via-stop/flag-stop request shape, the real
connect-to-route flow) is resolved; see that plan's own ledger for detail.
Known, deliberately accepted backend debt, out of scope for the frontend:
`/geocode` has no rate limiting and fetches full place-details for every
autocomplete suggestion rather than just the chosen one; `/search`/
`/nearby-routes` have no resource bounds (unbounded `radiusMeters`, no
distance cap or timeout); a leftover lock still serializes transit searches
unnecessarily. None of these block building the frontend against the real,
current API.

## Open Items Carried Into Frontend Implementation Planning

- The exact recents-list cap (10, most-recent-first, deduplicated by place)
  and favorites/recents' exact stored shape, confirmed from `bikebus`'s real
  `SavedPlacesStore`/`TripViewModel.rememberRecent` — carried into the
  implementation plan as fixed values, not re-derived there.
- Exact DOM-testing capability of Bun's built-in test runner for the
  smoke-test scenario (form fill → submit → result rendered) — confirm
  during implementation planning rather than assuming jsdom-equivalent
  support exists out of the box.
