# `one.brj.bikebus` — Android native app

A native Jetpack Compose app for the OTP server UI. It talks to the
`otp-server-ui` backend's `/search`, `/nearby-routes`, and `/connect`
endpoints directly over HTTP (`OtpServerApi`, hand-written on top of
OkHttp + kotlinx.serialization — Retrofit is used only for `PlacesApi`)
and renders the results with its own Compose screens — there is no
WebView and no hosted frontend involved anymore.

Address autocomplete and geocoding go straight to Google Places
(`PlacesApi`, calling `https://places.googleapis.com/` directly with its
own `GOOGLE_PLACES_API_KEY`) rather than through the backend's
`/geocode` endpoint — that endpoint exists for the hosted web frontend,
but this app never uses it.

See `../docs/superpowers/specs/2026-10-07-android-native-rewrite-design.md`
and `../docs/superpowers/plans/2026-10-07-android-native-rewrite.md` for
the native rewrite's design and implementation plan.

## Building

This app needs three secrets at build time, each supplied only via an
environment variable, never committed to a source file:

- `OTP_AUTH_TOKEN` — must be the *same* value as terraform-oci's
  `otp_auth_token` variable; sent as the `X-Auth-Token` header on every
  `OtpServerApi` request.
- `GOOGLE_PLACES_API_KEY` — a Google Places API key, sent as the
  `X-Goog-Api-Key` header on every `PlacesApi` request.
- `OTP_SERVER_BASE_URL` — the backend's base URL (e.g.
  `https://otp.brj.one`), used to build every `OtpServerApi` request URL.

```bash
cd android
export OTP_AUTH_TOKEN="<the real otp_auth_token value from the OCI deployment>"
export GOOGLE_PLACES_API_KEY="<a real Google Places API key>"
export OTP_SERVER_BASE_URL="https://otp.brj.one"
./gradlew assembleDebug    # or assembleRelease
```

A build with any of these three unset fails immediately with a
`GradleException` (see `app/build.gradle.kts`'s `requiredEnv` helper)
rather than silently producing an APK that sends blank headers and gets
confusing failures at runtime. Each value is baked in at build time via
a generated `BuildConfig` field (`BuildConfig.OTP_AUTH_TOKEN`,
`BuildConfig.GOOGLE_PLACES_API_KEY`, `BuildConfig.OTP_SERVER_BASE_URL`),
read by `NetworkModule`.

For a local build-check only (no real secrets/server available yet),
placeholder values work for all three, e.g.
`OTP_AUTH_TOKEN=placeholder-for-build-check`,
`GOOGLE_PLACES_API_KEY=placeholder-for-build-check`,
`OTP_SERVER_BASE_URL=http://localhost:8081`.

## Signing key decision (Task 1)

**Decision: reuse bru's existing personal Android release-signing
keystore — do not generate a new one for this app.**

- **Keystore file:** `C:\Users\bru\android` (no file extension; PKCS12
  format). This is bru's one personal keystore, already reused across his
  other personal apps (BibApp, immich-mobile-with-history, face-notif,
  kayak-planby-rn, etc.) — not committed into any repo, lives only on his
  machine(s).
- **Why not generate a new one:** the plan's own default (Step 2's
  "if not found... generate a new keystore") was written assuming no
  existing keystore could be found at all. One *is* known to exist and is
  bru's standing convention for every personal app he signs — generating
  a second, app-specific keystore here would just be a one-off exception
  to that convention for no real benefit, and the plan itself frames this
  app as *replacing* the existing `bikebus` app, which argues for the same
  key, not a different one.
- **Search performed:** `find /c/Users/bru/spare-source/bikebus -iname
  "*.keystore" -o -iname "*.jks"` (per the plan's Step 1) found nothing —
  expected, since bru's existing `bikebus` app was evidently signed via
  Android Studio's own "Generate Signed APK" dialog (its
  `app/build.gradle.kts` has no `signingConfigs` block of its own), which
  stores the keystore path/alias in the IDE's per-project
  `workspace.xml`, not in the repo. `bikebus/app`'s own
  `.idea/workspace.xml` isn't present in that checkout (gitignored /
  never generated here), so it couldn't be read directly either.
- **Alias:** recorded elsewhere (BibApp's own `workspace.xml`) as `brj`.
  **Not yet independently confirmed for the actual keystore file** — that
  requires running `keytool -list -keystore "C:\Users\bru\android"` and
  typing the store password interactively, which only bru can do (this
  plan was executed by an agent with no interactive terminal available).
  `app/build.gradle.kts`'s `signingConfigs["release"]` defaults
  `keyAlias` to `"brj"` but lets `ANDROID_KEY_ALIAS` override it.
- **Passwords:** never hardcoded anywhere in this repo. Supplied either
  via `ANDROID_STORE_PASSWORD` / `ANDROID_KEY_PASSWORD` environment
  variables, or via a local `key.properties` file at `android/`
  (gitignored — see `.gitignore`), e.g.:

  ```properties
  storeFile=C:/Users/bru/android
  storePassword=...
  keyAlias=brj
  keyPassword=...
  ```

- **Consequence for installing over the existing `bikebus` app:** *if*
  `C:\Users\bru\android` / alias `brj` really is the key the currently
  installed `bikebus` app was signed with, this app installs as a
  seamless update over it. If it turns out not to be (e.g. the existing
  install used a different/debug key), Android will refuse to install
  over it, and the existing `bikebus` app needs a manual uninstall first
  — consistent with this whole project's "wholesale replace" direction
  already established on the server side. Either way, no code change is
  needed here; only the end state on-device differs.
- **None of the above blocks Tasks 2–3**, which only need `assembleDebug`
  (debug builds use the default debug signing, not the `"release"`
  signingConfig at all).

## Project layout

- `app/src/main/java/one/brj/bikebus/MainActivity.kt` — hosts the
  Compose content, requests the coarse-location permission, and renders
  `TripScreen`.
- `app/src/main/java/one/brj/bikebus/TripViewModel.kt` — the app's one
  `AndroidViewModel`; owns `TripUiState`, drives address autocomplete
  via `PlacesApi`, issues searches/connects via `OtpServerApi`, and
  persists favorites/recents via `SavedPlacesStore`.
- `app/src/main/java/one/brj/bikebus/ui/` — the Compose screen and its
  pieces: `TripScreen.kt` (the one screen), `ModeToggle.kt`,
  `AddressField.kt`, `DateTimePickers.kt`, `ResultsList.kt`,
  `NearbyRoutesResults.kt`, plus `theme/Theme.kt`.
- `app/src/main/java/one/brj/bikebus/network/` — `OtpServerApi.kt`
  (hand-written OkHttp client for `/search`, `/nearby-routes`,
  `/connect`, plus their DTOs in `OtpServerDto.kt` and mapping to UI
  models in `DtoMapping.kt`), `PlacesApi.kt` / `PlacesDto.kt` (Retrofit
  interface for Google Places autocomplete + place details), and
  `NetworkModule.kt` (builds the shared `OkHttpClient`/`Retrofit`
  instances and reads the three `BuildConfig` secrets).
- `app/src/main/java/one/brj/bikebus/data/SavedPlacesStore.kt` — local
  persistence for favorite/recent places.
- `app/src/main/java/one/brj/bikebus/domain/` — `LocationProvider.kt`
  (last-known coarse device location) and `MapsIntent.kt` (launching
  Google Maps for a resolved place).
- `app/src/main/java/one/brj/bikebus/model/` — plain Kotlin UI/domain
  models (`TripUiState`, `Itinerary`, `NearbyRoute`, `ResolvedPlace`,
  `SavedPlace`, etc.).

## Known deviations from the written plan

See the implementation plan's own text for the literal versions it
proposed (AGP 8.x / compileSdk 35); this project instead matches the
AGP/Gradle/Kotlin/compileSdk versions already proven to work in this
exact dev environment for the sibling `bikebus` native app
(`C:\Users\bru\spare-source\bikebus\app`), per the plan's own instruction
to "confirm against the Android Studio/AGP version actually installed"
rather than force an exact match:

- AGP `9.4.0`, Gradle `9.6.0`, Kotlin `2.3.21`, `compileSdk`/`targetSdk`
  `37` (installed and already working locally for `bikebus`). `minSdk`
  stays `26` as the plan specifies.
- The original WebView-era build added `androidx.appcompat:appcompat`
  and `androidx.activity:activity-ktx` (needed for that `MainActivity`
  to extend `AppCompatActivity` and use
  `onBackPressedDispatcher.addCallback`). The native Compose rewrite
  dropped both: `MainActivity` now extends plain `ComponentActivity`
  and calls `setContent { ... }`, per standard Compose convention, so
  appcompat is no longer a dependency.
- Added minimal adaptive-icon drawables (`ic_launcher_background` /
  `ic_launcher_foreground` / `mipmap-anydpi-v26/ic_launcher.xml`) since
  the manifest references `@mipmap/ic_launcher`, which must resolve to
  something for the build to succeed — placeholder only, per the plan's
  own Self-Review (app icon/name deliberately deferred).
