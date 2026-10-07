# Android native rewrite: from WebView to a direct API client

## Goal

Replace `otp-server-ui/android/`'s current WebView-wrapping-hosted-frontend app
with a native Jetpack Compose app, ported from `bikebus-main/app`'s existing
UI design, that talks directly to the `otp-server-ui` backend's search API
instead of rendering a web frontend inside a WebView.

## Background

`otp-server-ui/android/` currently exists as a minimal WebView shell
(`MainActivity` + `TokenInjectingWebViewClient`) built to wrap the hosted web
frontend behind a token-gated Caddy (see
`2026-10-07-android-app-design.md`/`2026-10-07-oci-deployment-design.md`).
That design is sound for a thin wrapper, but a fuller native app already
exists: `bikebus-main/app` is a working Compose app with the same
`one.brj.bikebus` package identity, the same trip-search UX (Park & Ride,
Bring Bike, Drop-me-off, favorites/recents, hub-aware routing), but built
against the *old* architecture, where the Android app itself performed all
routing logic client-side (direct OTP GraphQL calls, client-side hub
splitting/stitching, client-side Park & Ride candidate-stop probing,
client-side flag-stop confirmation) plus direct Google Places calls for
address autocomplete.

The new `otp-server-ui` backend has already absorbed essentially all of that
routing domain logic server-side, behind four simple, token-gated endpoints:

- `POST /search` — Park & Ride / Bring Bike search; the backend does hub
  preference/stitching internally and returns the final chosen itinerary.
- `GET /nearby-routes` — Drop-me-off's browse step.
- `POST /connect` — Drop-me-off's connect step; the backend does flag-stop
  confirmation and hub preference internally.
- `GET /geocode` — a Places-autocomplete proxy (not used by this rewrite —
  see "Geocoding" below).

This rewrite ports bikebus-main's UI and local-only behavior into
`otp-server-ui/android/`'s existing module (keeping its already-scaffolded,
newer toolchain), and replaces bikebus-main's client-side routing/network
layer with thin calls to the three backend endpoints above.

## Non-goals

- No backend changes. The backend's API surface is used exactly as it
  exists today.
- No `NORMAL` (plain transit, no bike) search mode. The backend and the
  current web frontend only support Park & Ride and Bring Bike; bikebus-main's
  third `SearchMode.NORMAL` is dropped, not ported.
- No change to geocoding. The app keeps calling Google Places directly with
  its own API key, exactly as bikebus-main does today — see "Geocoding"
  below for why.
- No UI redesign. Visual design, screen flow, and interaction patterns are
  preserved from bikebus-main as closely as the underlying data change
  allows.

## Geocoding stays direct-to-Google

The backend's `/geocode` endpoint only returns a flat `label` string per
candidate, not the `mainText`/`secondaryText` split bikebus-main's
`AddressField` UI uses for its two-line suggestion rows. Rather than change
backend behavior to accommodate the Android app, the app keeps using
`PlacesApi`/`PlacesDto` exactly as bikebus-main has them — direct calls to
Google Places' autocomplete and place-details endpoints, with the Places API
key embedded in the app the same way bikebus-main already does it (this is
not new exposure; it's the same model the original bikebus app already
shipped with).

## Architecture

### Kept close to unchanged (ported from bikebus-main)

UI layer (`ui/AddressField.kt`, `ui/DateTimePickers.kt`, `ui/ModeToggle.kt`,
`ui/NearbyRoutesResults.kt`, `ui/ResultsList.kt`, `ui/theme/Theme.kt`,
`ui/TripScreen.kt`), local-only concerns (`data/SavedPlacesStore.kt`,
`domain/LocationProvider.kt`, `domain/MapsIntent.kt`, `domain/Polyline.kt`,
`domain/Geo.kt`), and UI-facing model classes (`model/Itinerary.kt`,
`model/NearbyRoute.kt`, `model/NearbyRoutesUiState.kt`,
`model/PlaceSuggestion.kt`, `model/ResolvedPlace.kt`, `model/SavedPlace.kt`,
`model/TimeMode.kt`, `model/FlagStopInfo.kt`,
`model/FlagStopConnectResult.kt`) move into `otp-server-ui/android/`
essentially unchanged — none of them know or care where itinerary data comes
from. `network/PlacesApi.kt`/`network/PlacesDto.kt` move unchanged per
"Geocoding stays direct-to-Google" above.

`model/SearchMode.kt` is ported with `NORMAL` removed: `enum class
SearchMode { BRING_BIKE, PARK_AND_RIDE }`.

### Deleted outright (routing logic the backend now owns)

`domain/HubCatalog.kt`, `domain/HubRouting.kt`, `domain/ItineraryMapper.kt`,
`domain/ItineraryPostProcessing.kt`, `domain/NearbyRoutesFinder.kt`,
`domain/OtpQueryBuilder.kt`, `domain/ParkAndRideFinder.kt`,
`network/OtpApi.kt`, `network/OtpDto.kt`, `model/TransitHub.kt` (the client
no longer needs its own hub catalog or hub-routing logic — the backend's
`/search` and `/connect` already return the final, hub-stitched result, and
any user-facing note about it arrives as the response's own plain-text
`notice` field).

`model/WakeStatus.kt` and `TripViewModel`'s `wakeServer()` are also deleted:
they existed to wake a serverless/cold-start OTP backend (the old Scaleway
deployment). The new backend is an always-on container on an Oracle VM —
there is nothing to wake, so this feature has no equivalent and is not
carried forward.

From the current (WebView) `otp-server-ui/android/` app:
`TokenInjectingWebViewClient.kt` and `res/layout/activity_main.xml` are
deleted — there is no WebView left to intercept or host.

### New

- `network/OtpServerApi.kt` — a small OkHttp-based client for the three
  backend endpoints used (`/search`, `/nearby-routes`, `/connect`), setting
  `X-Auth-Token` on every request. Plain OkHttp + `kotlinx.serialization`
  rather than Retrofit: three endpoints with DTOs that already mirror the
  backend's own `@Serializable` wire types don't need Retrofit's extra
  dependency surface.
- `network/OtpServerDto.kt` — client-side copies of the backend's wire types
  (`SearchRequest`, `ItineraryDto`, `LegDto`, `NearbyRouteDto`,
  `ConnectRequest`, `ConnectResponse`, `FlagStopInfoDto`), matching
  `backend/src/main/kotlin/one/otpserverui/api/SearchDto.kt` and
  `DropMeOffRoute.kt` field-for-field.
- `network/DtoMapping.kt` — pure functions mapping the above DTOs onto the
  existing UI model classes (`ItineraryDto.toUiModel(): Itinerary`, etc.).
  No logic beyond field transcription — the shapes are already close to
  identical.
- `MainActivity.kt` — replaced with bikebus-main's Compose entry point
  (`setContent { BikeBusTheme { TripScreen() } }` plus the
  `ACCESS_COARSE_LOCATION` permission launch), instead of hosting a WebView.

### `TripViewModel` — what changes

The ported `TripViewModel` keeps its state shape (`TripUiState`), its UI-facing
methods (`setSearchMode`, `setTimeMode`, `swapFromTo`, favorites/recents
handling, address-suggestion debouncing via `PlacesApi`), and deletes
everything that was reimplementing server-side routing:

- `fetchItineraries` becomes one `OtpServerApi.search(...)` call, mapped via
  `DtoMapping`, instead of building raw OTP GraphQL queries and
  (for Park & Ride) probing multiple candidate stops client-side.
- `findNearbyRoutes` becomes one `OtpServerApi.nearbyRoutes(...)` call.
- `selectNearbyRoute`/`connectToRoute` becomes one `OtpServerApi.connect(...)`
  call — no more client-side flag-stop re-planning or hub-split stitching;
  `/connect`'s response already includes `flagStopInfo`, `extraRideSeconds`,
  and `hubName`.

### Error handling

The backend returns typed error codes via specific HTTP statuses
(`SearchErrorResponse("no_coverage")` on 422, `"unreachable"` on 422,
`"unsupported_time_mode"`/`"invalid_request"`/`"unknown_mode"` on 400,
`"geocode_unavailable"` on 502 — not used here per "Geocoding stays
direct-to-Google"). `OtpServerApi` surfaces these as a small sealed result
type:

```kotlin
sealed class SearchResult {
    data class Success(val itineraries: List<ItineraryDto>, val notice: String?) : SearchResult()
    data class Error(val code: String) : SearchResult()
}
```

`TripViewModel` maps known codes to user-facing strings, keeping today's
generic "Couldn't reach the routing server" for network/5xx failures, and
adding slightly more specific messages for `no_coverage` (no route exists
at all) and `unreachable` (Drop-me-off's connect target can't be reached)
since the backend is now explicit about *why* a search failed, rather than
collapsing every failure into one generic catch-all as the old
client-side-exception-based error handling did.

## Config & secrets

Three build-time values, all via environment variable (matching the pattern
already established in `otp-server-ui/android/app/build.gradle.kts` for
`OTP_AUTH_TOKEN` — not bikebus-main's `local.properties` style), all
required with no hardcoded defaults (nothing about this specific deployment
is baked into the repo):

```kotlin
val otpAuthToken: String = System.getenv("OTP_AUTH_TOKEN")
    ?: throw GradleException("OTP_AUTH_TOKEN is not set...")
val googlePlacesApiKey: String = System.getenv("GOOGLE_PLACES_API_KEY")
    ?: throw GradleException("GOOGLE_PLACES_API_KEY is not set...")
val otpServerBaseUrl: String = System.getenv("OTP_SERVER_BASE_URL")
    ?: throw GradleException("OTP_SERVER_BASE_URL is not set...")
```

- `OTP_AUTH_TOKEN` — same value as `terraform-oci`'s `otp_auth_token`, used
  only by `OtpServerApi`'s auth interceptor.
- `GOOGLE_PLACES_API_KEY` — used only by `PlacesApi`. Same key already used
  server-side; a separate, client-embedded copy, same exposure model
  bikebus-main already shipped with (not new).
- `OTP_SERVER_BASE_URL` — not a secret, but still not hardcoded, so the same
  build can point at a local/staging backend during development.

`android/README.md` gets these three documented alongside its existing
`OTP_AUTH_TOKEN` instructions.

## Toolchain

The ported code moves into `otp-server-ui/android/`'s already-scaffolded
module and builds on *its* toolchain (AGP 9.4.0, Kotlin 2.3.21, compileSdk
37) — not bikebus-main's older one (AGP 8.6.1, Kotlin 2.0.0, compileSdk 35).
This means adding the Compose plugin and Compose BOM dependencies (currently
absent, since the existing module is WebView-only) to
`android/app/build.gradle.kts`, and confirming bikebus-main's Compose code
compiles unchanged against the newer Compose BOM/Kotlin version during
implementation (expected to be source-compatible; AGP 9's bundled Kotlin
support was already confirmed working for this exact module in the earlier
WebView-app work).

## Testing

- `DtoMapping.kt`'s pure functions: JVM unit tests, input/output assertions
  per DTO shape.
- `OtpServerApi`: tests against a local OkHttp `MockWebServer`, covering
  success-response mapping, each known error code mapping to the right
  `SearchResult.Error`, and confirming `X-Auth-Token` is actually sent.
- `TripViewModel`: unit tests for the state-transition methods and the
  shrunk `fetchItineraries`/error-mapping logic; bikebus-main's existing
  `TripViewModel` test coverage (if any) is reviewed during planning for
  what still applies.
- End-to-end verification is explicitly **not** done by Claude for this
  project: the implementation plan's final task is to confirm
  `assembleDebug`/`assembleRelease` succeeds and hand off the APK; the user
  installs and tests it on their own physical device.

## Backend: strip frontend-serving from the server image

Once the Android app is a native client calling the backend's API directly,
nothing loads the hosted web frontend anymore. It was never reachable by a
plain browser in the first place — Caddy gates every request behind
`X-Auth-Token`, which only the (now-replaced) WebView's token injection
could attach — so this isn't a behavior change for any real user, just
removing now-genuinely-dead weight:

- `backend/src/main/kotlin/one/otpserverui/Main.kt`: remove the `staticFiles`
  conditional block, its `staticDir` parameter, and the `staticFiles` import.
  Nothing else in the backend depends on this.
- `backend/src/test/kotlin/one/otpserverui/StaticFilesTest.kt`: delete (tests
  the feature being removed).
- Root `Dockerfile`: drop `COPY --from=build /src/frontend/dist
  /app/frontend-dist` and `ENV FRONTEND_DIST_PATH=...`.
- `.github/workflows/publish-images.yml`: drop the `oven-sh/setup-bun@v2` and
  `bun install && bun run build` steps from the `backend` job — nothing
  copies `frontend/dist` into the image anymore, so building it in CI is
  pointless.
- `terraform-oci/README.md`'s opening line updated to describe an API-only
  backend.

`frontend/` source stays in the repo (useful for local dev/testing against
the API); the graph-builder/weekly-cron deployment pieces are unrelated and
untouched; Caddy's config needs no changes (it already gates on headers,
not paths).

## Branching

All work happens on a new branch in `otp-server-ui` (not in `bikebus-main`,
which stays untouched, read-only reference material). Branch name:
`android-native-rewrite`.
