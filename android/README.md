# `one.brj.bikebus` — Android WebView app

A single-screen Android app that loads the real deployed OTP server UI
(`https://otp.brj.one`) in a WebView, attaching `X-Auth-Token` to every
request the page makes (not just the initial navigation) via a custom
`WebViewClient.shouldInterceptRequest`.

See `../docs/superpowers/specs/2026-10-07-android-app-design.md` for the
full design and `../docs/superpowers/plans/2026-10-07-android-app.md` for
the implementation plan this project was scaffolded from.

## Building

This app **must be built after the OCI deployment plan's Task 11 is
complete** — it needs the real, deployed `otp_auth_token` value to build
against (`OTP_AUTH_TOKEN` must be the *same* value as terraform-oci's
`otp_auth_token` variable), and the real, live `https://otp.brj.one`
endpoint to actually load anything useful.

`OTP_AUTH_TOKEN` is supplied only via an environment variable at build
time, never committed to a source file:

```bash
cd android
export OTP_AUTH_TOKEN="<the real otp_auth_token value from the OCI deployment>"
./gradlew assembleDebug    # or assembleRelease
```

A build with `OTP_AUTH_TOKEN` unset fails immediately with a
`GradleException` (see `app/build.gradle.kts`) rather than silently
producing an APK that sends a blank header and gets a confusing 403 at
runtime.

For a local build-check only (no real token/server available yet), any
placeholder value works, e.g. `OTP_AUTH_TOKEN=placeholder-for-build-check`.

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

- `app/src/main/java/one/brj/bikebus/MainActivity.kt` — the app's only
  screen.
- `app/src/main/java/one/brj/bikebus/TokenInjectingWebViewClient.kt` —
  the `WebViewClient` that re-fetches every resource via OkHttp with
  `X-Auth-Token` attached.

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
- Added `androidx.appcompat:appcompat` and
  `androidx.activity:activity-ktx` dependencies, not listed in the plan's
  own `build.gradle.kts` snippet — required for `MainActivity` to extend
  `AppCompatActivity` (which also requires `Theme.OtpServerUi` to extend
  a `Theme.AppCompat` descendant, not a bare platform theme) and for the
  `onBackPressedDispatcher.addCallback(this) { ... }` extension the
  plan's own `MainActivity.kt` snippet uses.
- Added minimal adaptive-icon drawables (`ic_launcher_background` /
  `ic_launcher_foreground` / `mipmap-anydpi-v26/ic_launcher.xml`) since
  the manifest references `@mipmap/ic_launcher`, which must resolve to
  something for the build to succeed — placeholder only, per the plan's
  own Self-Review (app icon/name deliberately deferred).
