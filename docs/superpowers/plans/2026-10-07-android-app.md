# Android WebView App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A single-screen Android app, package `one.brj.bikebus`, that
loads the real deployed site in a WebView with `X-Auth-Token` attached to
every request it makes — not just the first one.

**Architecture:** One `Activity`, one `WebView`, a custom `WebViewClient`
whose `shouldInterceptRequest` re-fetches every resource itself (via
OkHttp) with the token header attached, then hands the real response back
to the WebView.

**Tech Stack:** Kotlin, Android Gradle Plugin, OkHttp.

**Spec:** `docs/superpowers/specs/2026-10-07-android-app-design.md`

**Depends on:** `docs/superpowers/plans/2026-10-07-oci-deployment.md`
(Task 11) being complete — this app needs the real, deployed
`otp_auth_token` value to build against, and the real, live
`https://otp.brj.one` endpoint to test against. Do not start Task 3 of
this plan before that.

## Global Constraints

- `OTP_AUTH_TOKEN` is supplied only via an environment variable at build
  time (`System.getenv` in `app/build.gradle.kts`), written into
  `BuildConfig`, never into a committed source file. A build with it
  unset fails loudly, not silently.
- Permissions: `INTERNET` only.
- Package id is `one.brj.bikebus` — this is a deliberate replacement of
  the existing `bikebus` app, not a new, separate app; Task 1 resolves
  what that means for signing concretely, rather than leaving it vague.
- `shouldInterceptRequest` is the only mechanism used to attach the
  header — never `WebView.loadUrl`'s own `extraHeaders` parameter, which
  only covers the top-level navigation (see the spec's own "Why not
  `WebView.loadUrl`'s own extraHeaders" section).

## Review Focus

- **Every request the SPA itself makes (`/search`, `/geocode`,
  `/nearby-routes`, `/connect`), not just the initial page load, must
  carry the header** — Task 4's own test is specifically built to catch
  the mistake of only covering the top-level navigation.
- **`domStorageEnabled` must actually be on** — easy to silently miss on
  a bare WebView, and the frontend's own favorites/recents feature
  (`localStorage`) would silently fail (not crash, just never persist)
  without it, which is the kind of bug that's invisible until someone
  notices favorites never survive a restart.
- **A build with no `OTP_AUTH_TOKEN` set must fail the build itself**,
  not produce an APK that silently sends a blank header and gets a
  confusing 403 at runtime.

---

### Task 1: Resolve signing key strategy and project identity

**Files:** none yet (decision-only task)

- [ ] **Step 1: Check whether the original `bikebus` app's signing
  keystore is available/known**

  ```bash
  find /c/Users/bru/spare-source/bikebus -iname "*.keystore" -o -iname "*.jks"
  ```

- [ ] **Step 2: Decide based on Step 1's result**
  - **If a keystore is found and its credentials are known:** reuse it
    for this app too, so it installs as a seamless update over the
    existing `bikebus` app.
  - **If not found, or credentials aren't available (the expected,
    default case):** generate a **new** signing keystore for this app
    specifically, and plan for a manual uninstall of the existing
    `bikebus` app before first installing this one (Android refuses to
    install a same-package-id app signed with a different key over an
    existing install) — consistent with this whole project's "wholesale
    replace" direction already established on the server side.

    ```bash
    keytool -genkeypair -v -keystore android/otp-server-ui-release.keystore \
      -alias otp-server-ui -keyalg RSA -keysize 2048 -validity 10000
    ```

    (Store the resulting keystore and its passwords somewhere durable —
    a password manager, not this repo. Add `android/*.keystore` to
    `.gitignore`.)

- [ ] **Step 3: Record the decision** directly in `android/README.md`
  (created in Task 2) so it isn't re-litigated later.

---

### Task 2: Scaffold the Android Studio project

**Files:**
- Create: `android/` (new Gradle project, its own `settings.gradle.kts`)
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/README.md`
- Modify: root `.gitignore` (add `android/.gradle/`, `android/.idea/`,
  `android/*/build/`, `android/*.keystore`, `android/local.properties`)

**Interfaces:**
- Produces: a buildable (if still near-empty) Android app module,
  package `one.brj.bikebus`.

- [ ] **Step 1: Generate the project** (via Android Studio's own "New
  Project" → Empty Views Activity template, or `gradle init`-equivalent
  scaffolding) with:
  - Package: `one.brj.bikebus`
  - Language: Kotlin
  - minSdk: 26
  - compileSdk / targetSdk: current stable (35, at time of writing —
    confirm against the Android Studio/AGP version actually installed)
  - AGP: current stable 8.x
  - Kotlin: 2.x (matching this repo's own backend's Kotlin 2.3.21 where
    practical, though the Android Gradle Plugin's own supported Kotlin
    range may constrain the exact version — confirm compatibility rather
    than force an exact match)

- [ ] **Step 2: `android/app/src/main/AndroidManifest.xml`**

  ```xml
  <manifest xmlns:android="http://schemas.android.com/apk/res/android">
      <uses-permission android:name="android.permission.INTERNET" />

      <application
          android:allowBackup="true"
          android:label="@string/app_name"
          android:icon="@mipmap/ic_launcher"
          android:theme="@style/Theme.OtpServerUi">
          <activity
              android:name=".MainActivity"
              android:exported="true"
              android:configChanges="orientation|screenSize">
              <intent-filter>
                  <action android:name="android.intent.action.MAIN" />
                  <category android:name="android.intent.category.LAUNCHER" />
              </intent-filter>
          </activity>
      </application>
  </manifest>
  ```

- [ ] **Step 3: `android/app/build.gradle.kts`** — read `OTP_AUTH_TOKEN`
  from the environment, fail the build if it's missing, add OkHttp:

  ```kotlin
  plugins {
      id("com.android.application")
      kotlin("android")
  }

  val otpAuthToken: String = System.getenv("OTP_AUTH_TOKEN")
      ?: throw GradleException(
          "OTP_AUTH_TOKEN is not set. Export it before building: " +
          "export OTP_AUTH_TOKEN=<the same value as terraform-oci's otp_auth_token>"
      )

  android {
      namespace = "one.brj.bikebus"
      compileSdk = 35

      defaultConfig {
          applicationId = "one.brj.bikebus"
          minSdk = 26
          targetSdk = 35
          versionCode = 1
          versionName = "1.0"
          buildConfigField("String", "OTP_AUTH_TOKEN", "\"$otpAuthToken\"")
      }

      buildFeatures {
          buildConfig = true
      }

      signingConfigs {
          create("release") {
              // Populated from Task 1's chosen keystore -- local.properties
              // or env vars, never hardcoded values in this file.
          }
      }

      buildTypes {
          release {
              signingConfig = signingConfigs.getByName("release")
          }
      }
  }

  dependencies {
      implementation("com.squareup.okhttp3:okhttp:4.12.0")
  }
  ```

- [ ] **Step 4: `android/README.md`** — document: the `OTP_AUTH_TOKEN`
  env var requirement, Task 1's signing decision, and that this app must
  be built *after* the OCI deployment plan's Task 11 is complete (the
  real token value doesn't exist before then).

- [ ] **Step 5: Update root `.gitignore`**

  ```
  android/.gradle/
  android/.idea/
  android/*/build/
  android/*.keystore
  android/local.properties
  ```

- [ ] **Step 6: Verify it builds** (with a placeholder token, since the
  real one may not exist yet depending on how far along the OCI plan is):

  ```bash
  cd android
  OTP_AUTH_TOKEN=placeholder-for-build-check ./gradlew assembleDebug
  ```

  Expect `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

  ```bash
  git add android .gitignore
  git commit -m "Scaffold the Android WebView app project"
  ```

---

### Task 3: `MainActivity` and the token-injecting `WebViewClient`

**Files:**
- Create: `android/app/src/main/java/one/brj/bikebus/MainActivity.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/TokenInjectingWebViewClient.kt`
- Create: `android/app/src/main/res/layout/activity_main.xml`

**Interfaces:**
- Produces: `MainActivity`, the app's only screen.

- [ ] **Step 1: `activity_main.xml`** — a single `WebView` filling the
  screen.

  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <WebView xmlns:android="http://schemas.android.com/apk/res/android"
      android:id="@+id/webView"
      android:layout_width="match_parent"
      android:layout_height="match_parent" />
  ```

- [ ] **Step 2: `TokenInjectingWebViewClient.kt`**

  ```kotlin
  package one.brj.bikebus

  import android.webkit.WebResourceRequest
  import android.webkit.WebResourceResponse
  import android.webkit.WebView
  import android.webkit.WebViewClient
  import okhttp3.OkHttpClient
  import okhttp3.Request
  import java.io.ByteArrayInputStream

  class TokenInjectingWebViewClient(
      private val authToken: String,
      private val onLoadFailed: () -> Unit,
  ) : WebViewClient() {

      private val client = OkHttpClient()

      override fun shouldInterceptRequest(
          view: WebView,
          request: WebResourceRequest,
      ): WebResourceResponse? {
          val upstreamRequest = Request.Builder()
              .url(request.url.toString())
              .method(request.method, null)
              .apply {
                  request.requestHeaders.forEach { (name, value) -> header(name, value) }
              }
              .header("X-Auth-Token", authToken)
              .build()

          return try {
              val response = client.newCall(upstreamRequest).execute()
              val body = response.body ?: return null
              val contentType = body.contentType()
              WebResourceResponse(
                  contentType?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream",
                  contentType?.charset()?.name() ?: "utf-8",
                  body.byteStream(),
              )
          } catch (e: Exception) {
              null // falls through to the WebView's own default handling / onReceivedError
          }
      }

      override fun onReceivedError(
          view: WebView,
          request: WebResourceRequest,
          error: android.webkit.WebResourceError,
      ) {
          if (request.isForMainFrame) onLoadFailed()
      }
  }
  ```

  **Note:** `shouldInterceptRequest` runs on a background thread, not the
  UI thread — confirmed via the Android WebView docs; the blocking
  `client.newCall(...).execute()` call above is correct for that reason
  (no `enqueue`/callback needed).

- [ ] **Step 3: `MainActivity.kt`**

  ```kotlin
  package one.brj.bikebus

  import android.os.Bundle
  import android.webkit.WebView
  import androidx.appcompat.app.AppCompatActivity

  class MainActivity : AppCompatActivity() {
      private lateinit var webView: WebView

      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          setContentView(R.layout.activity_main)

          webView = findViewById(R.id.webView)
          webView.settings.javaScriptEnabled = true
          webView.settings.domStorageEnabled = true
          webView.webViewClient = TokenInjectingWebViewClient(
              authToken = BuildConfig.OTP_AUTH_TOKEN,
              onLoadFailed = { runOnUiThread { showRetryView() } },
          )
          webView.loadUrl("https://otp.brj.one")

          onBackPressedDispatcher.addCallback(this) {
              if (webView.canGoBack()) webView.goBack() else isEnabled = false.also { finish() }
          }
      }

      private fun showRetryView() {
          // A minimal native view with a message + Retry button that
          // re-calls webView.loadUrl("https://otp.brj.one") -- real layout
          // resource to be added here; not detailed further in this plan
          // since it's a small, low-risk UI addition with no real design
          // decisions left open.
      }
  }
  ```

- [ ] **Step 4: Build and run against a real placeholder token**, in an
  emulator or a real device, confirming the activity launches and
  attempts to load the URL (it will fail/403 at this point if the real
  OCI deployment isn't live yet — that's expected and fine for this
  step; Task 4 does the real end-to-end check once it is).

- [ ] **Step 5: Commit**

  ```bash
  git add android/app/src/main/java android/app/src/main/res/layout/activity_main.xml
  git commit -m "Add MainActivity and the token-injecting WebViewClient"
  ```

---

### Task 4: Real end-to-end verification against the live deployed site

**Files:** none (verification only)

**Prerequisite:** the OCI deployment plan's Task 11 is complete — this
needs the real `https://otp.brj.one` and the real `otp_auth_token` value.

- [ ] **Step 1: Build a real debug APK with the real token**

  ```bash
  cd android
  export OTP_AUTH_TOKEN="<the real otp_auth_token value from the OCI deployment>"
  ./gradlew assembleDebug
  ```

- [ ] **Step 2: Install and launch on a real device/emulator**

  ```bash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

  Confirm: the app launches straight into the real site with no
  intermediate screen, and the site actually loads (not a 403 page).

- [ ] **Step 3: Confirm every XHR call carries the header, not just the
  page load** — perform a real search in the app (origin → destination →
  Search), confirming it returns a real itinerary. This is the test that
  actually proves `shouldInterceptRequest` (not `loadUrl`'s own
  `extraHeaders`) is doing its job, since `/search` is an XHR call the
  page's own JS makes, not the initial navigation.

- [ ] **Step 4: Negative check** — rebuild with a deliberately wrong
  token and confirm the site fails to load (shows the server's real
  `403`), proving the header is genuinely being checked server-side, not
  just assumed to work:

  ```bash
  OTP_AUTH_TOKEN="deliberately-wrong-value" ./gradlew assembleDebug
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

- [ ] **Step 5: Rotation check** — start a search, rotate the device,
  confirm the in-progress state survives (no reload) — proves the
  manifest's `configChanges` entry is doing its job.

- [ ] **Step 6: Rebuild one final time with the real token** (undoing
  Step 4's deliberate-wrong-value build) and confirm normal operation
  before considering this plan complete.

## Self-Review

- **Spec coverage:** Components 1 and 2 from the spec map to Tasks 2–3;
  the spec's own Error Handling section maps to `onReceivedError`
  (Task 3) and the negative-token check (Task 4); all three Testing
  bullets from the spec map directly to Task 4's own steps.
- **Placeholder scan:** the one open question left genuinely unresolved
  (app icon/name) is deliberately deferred to a low-stakes follow-up, not
  silently guessed — the manifest references `@string/app_name` /
  `@mipmap/ic_launcher`, Android Studio's own project template default,
  which the person running this plan can swap for something real at any
  point without touching any other file.
- **Review Focus:** all three items map to concrete checks (Task 4 Step 3
  for the XHR-coverage concern, Task 3 Step 3 for `domStorageEnabled`,
  Task 2 Step 3's `GradleException` for the missing-token-fails-the-build
  concern).

## Execution Handoff

Plan complete and saved to
`docs/superpowers/plans/2026-10-07-android-app.md`. Please review it.

This plan has a real **sequencing dependency** on the OCI deployment
plan (Task 4 specifically needs the real live endpoint and token) — if
both plans are executed "in one go" as requested, Tasks 1–3 here can run
in parallel with the OCI plan's own earlier tasks, but Task 4 must wait
for OCI Task 11 to actually complete.

I recommend **subagent-driven-development** for this plan specifically
(unlike the OCI plan) — it has no irreversible/destructive steps of its
own, and the tasks are independent enough (scaffold → activity → verify)
to benefit from fresh-context review at each step.
