# otp-server-ui: Android WebView App Design

## Motivation

The OCI deployment design (`2026-10-07-oci-deployment-design.md`) gates
every request on a static `X-Auth-Token` header, checked as cheaply as
possible by Caddy before anything else runs. That header has to come from
somewhere. This is that somewhere: a minimal native Android app whose
entire job is to open the real site in a WebView with the token attached
to every request it makes — not just the first one.

**Package id: `one.brj.bikebus`** — the same package id the existing
`bikebus` app already uses. This is a deliberate choice, not an
oversight, and it has a real consequence worth stating plainly: Android
treats an app's package id as its identity. Installing this app under
the same id as an existing `bikebus` install either **updates it in
place** (only possible if both are signed with the same key) or requires
**uninstalling the old one first** (if signed differently, or if the
signing key isn't shared) — there is no way to have both installed at
once. Given the project's own stated direction ("wholesale replace...
we don't want to preserve anything regarding old [infrastructure]"),
replacing `bikebus` entirely — not running alongside it — is exactly the
intended outcome here, carried from the server side into the client side
too.

## Non-Goals

- **Play Store distribution.** Sideloaded APK only (manual install/
  update) — a Play listing, review process, and privacy-policy hosting
  are real, separate work this personal utility app doesn't need.
- **Any onboarding, settings, or login screen.** "One step" means launch
  → WebView, immediately, with no intermediate screen of any kind. The
  token is a build-time secret (see "Secrets," below), never something a
  user enters.
- **Certificate pinning.** The server's TLS cert renews automatically via
  Let's Encrypt (see the OCI spec); pinning to a specific cert/key would
  risk breaking the app on every renewal for a security benefit that
  doesn't matter much for a personal app already gated by a secret header.
- **Geolocation / "use my current location."** The web frontend itself
  doesn't use the browser Geolocation API today (confirmed: no
  `navigator.geolocation` reference anywhere in `frontend/src/`) — nothing
  to wire up on the native side for a feature that doesn't exist yet. If
  it's added to the frontend later, this app would need
  `WebChromeClient.onGeolocationPermissionsShowPrompt` plus a runtime
  `ACCESS_FINE_LOCATION` permission request — explicitly out of scope
  until that day comes.
- **Automated (CI) APK builds/signing.** Built locally via Gradle/Android
  Studio for now, matching the manual-deploy posture the OCI side also
  still has for Terraform itself (GitHub Actions there only covers the
  two server images, not this app).
- **Supporting devices without Google-Play-Services-updated WebView** —
  assumed to be a non-issue on the user's own real device(s).

## Architecture

```
MainActivity (the only screen)
  onCreate():
    - WebView fills the screen
    - settings: javaScriptEnabled = true, domStorageEnabled = true
      (the frontend's own favorites/recents use localStorage -- off by
      default on a bare WebView, easy to silently miss)
    - webViewClient = TokenInjectingWebViewClient(BuildConfig.OTP_AUTH_TOKEN)
    - loadUrl("https://otp.brj.one")

TokenInjectingWebViewClient : WebViewClient
  shouldInterceptRequest(view, request):
    - fetches `request.url` itself (OkHttp or HttpURLConnection),
      adding "X-Auth-Token: <token>" to the outgoing request
    - wraps the real response in a WebResourceResponse and returns it
    - this runs for *every* resource the page loads -- the initial HTML,
      every JS/CSS file, and every XHR/fetch call the SPA's own code
      makes to /search, /geocode, /nearby-routes, /connect -- not just
      the first navigation (see "Why not WebView.loadUrl's own
      extraHeaders," below)
  onReceivedError / onReceivedHttpError:
    - shows a small native "Can't reach the server" view with a Retry
      button that re-calls loadUrl -- never a raw browser error page
```

### Why not `WebView.loadUrl(url, extraHeaders)`

That overload only attaches its headers to the **top-level navigation
request** — the one initial page load. Every subsequent request the
page's own JavaScript makes (every `fetch()` call `frontend/src/api.ts`
issues for `/search`, `/geocode`, `/nearby-routes`, `/connect`) goes out
through the WebView's ordinary networking path with no extra headers at
all, since the browser engine has no reason to think those unrelated
requests should carry the same ones. `shouldInterceptRequest` is the
mechanism that actually sees and can modify *every* request the WebView
makes, which is what "attach this header to everything" genuinely
requires.

## Components

### 1. New Android Studio project, `android/` (in this repo)

- **Package:** `one.brj.bikebus`.
- **minSdk:** a reasonably modern floor (26 — "Oreo" — proposed; this
  only ever needs to run on the user's own real device(s), not support
  arbitrary old hardware).
- **Permissions:** `INTERNET` only. No camera, location, storage, or
  anything else — a plain HTTPS WebView needs nothing more.
- **`AndroidManifest.xml`:** `android:configChanges="orientation|
  screenSize"` on the single activity, so a device rotation doesn't
  recreate the Activity (and with it, reload the WebView and lose
  whatever the user was doing mid-search) — standard practice for a
  WebView-based app.
- Default Android **Network Security Config** already blocks cleartext
  traffic on a modern `targetSdk` — correct and sufficient here, since
  the server is HTTPS-only; nothing extra to configure.
- **Back button:** overrides the default back behavior to navigate the
  WebView's own history (`webView.canGoBack()` / `webView.goBack()`)
  before falling through to actually exiting the app — otherwise the
  back button would just close the app from any page.

### 2. Secrets — the token, at build time

Mirrors the same "never commit it, supply it via environment variable at
the point it's actually needed" pattern the OCI spec uses for
`GOOGLE_PLACES_API_KEY`:

- `app/build.gradle.kts` reads `System.getenv("OTP_AUTH_TOKEN")` and
  writes it into a generated `BuildConfig.OTP_AUTH_TOKEN` field — nothing
  about the real value is ever written into a committed source file.
- For a local build: the operator exports `OTP_AUTH_TOKEN` in their own
  shell before running `./gradlew assembleRelease` (same token value as
  the OCI deployment's own `otp_auth_token` Terraform variable — these
  two have to actually match for the app to work at all, which is worth
  a direct note in whatever runbook ties the two specs' implementation
  together).
- A build with no `OTP_AUTH_TOKEN` set should fail loudly at build time
  (an explicit check in `build.gradle.kts`), not silently produce an APK
  that sends an empty/wrong header and gets a confusing 403 at runtime.

## Error Handling

- **Wrong or missing token** (shouldn't happen in practice, since it's
  baked in and matched against the real deployed value, but a config
  drift between the app build and the server deploy is possible): the
  server's own `403` comes back through `shouldInterceptRequest`'s
  response and renders as a real (if cryptic) page inside the WebView —
  acceptable for a personal app's own misconfiguration case, not worth
  building special detection for.
- **No network / server unreachable:** `onReceivedError` (for the
  top-level page) or a failed `shouldInterceptRequest` fetch (for a
  subresource) shows the native retry view described above.
- **Backend returning a `502`/`429`** (server down, or rate-limited):
  passes through as the real HTTP response/status — the WebView shows
  whatever the page itself does with that status; no special native
  handling beyond the network-level retry view above.

## Testing

- **Manual, against the real deployed site** (no server-side test
  double planned — this app has nothing to meaningfully unit-test beyond
  "does the header actually get attached," which is best verified for
  real): install a debug build, confirm the app loads the real site
  immediately with no intermediate screen, confirm a full search
  round-trip works (proving every XHR call — not just the page load —
  carries the header correctly).
- **Negative check:** temporarily build with a deliberately wrong
  `OTP_AUTH_TOKEN` and confirm every request fails with the real
  server's `403` — proving the header is genuinely being sent per-request
  and checked server-side, not just assumed to work.
- **Rotation:** rotate the device mid-search and confirm the in-progress
  state survives (no reload) — the concrete, observable proof the
  `configChanges` manifest entry is doing its job.

## Open Questions for the Implementation Plan

1. **Signing key for `one.brj.bikebus`.** If the existing `bikebus` app
   is still installed on the target device under this same package id,
   confirm whether this new app will be signed with the *same* key (for
   a seamless update) or whether the plan is to uninstall the old app
   first and install this one fresh — a real decision, not guessed here.
2. **App icon and display name.** Not specified in this round of
   feedback — reuse `bikebus`'s existing icon/name for continuity, or
   pick something new? Low-stakes, but needs an actual answer before
   the manifest/resources are finalized.
3. **OkHttp vs. plain `HttpURLConnection`** for `shouldInterceptRequest`'s
   own re-fetch — OkHttp is the more ergonomic, widely-used choice and
   worth the one extra dependency; confirm during planning rather than
   assume.
4. **Exact Gradle/AGP/Kotlin versions** for the new Android project —
   pick current stable versions during planning, not guessed here.
