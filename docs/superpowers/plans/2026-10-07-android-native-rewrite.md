# Android Native Rewrite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `otp-server-ui/android/`'s WebView shell with a native Compose app ported from `bikebus-main/app`, calling the `otp-server-ui` backend's `/search`, `/nearby-routes`, `/connect` endpoints directly instead of rendering a web frontend.

**Architecture:** Port bikebus-main's UI, local-only domain code (favorites/recents, maps intents, location), and direct-Google-Places geocoding unchanged. Delete all client-side OTP-routing logic (hub-stitching, Park & Ride candidate probing, flag-stop re-planning, nearby-routes ranking) since the backend now owns it. Add a small OkHttp-based client for the three backend endpoints, and shrink `TripViewModel` to thin calls into it.

**Tech Stack:** Kotlin 2.3.21, AGP 9.4.0, Jetpack Compose (BOM 2024.09.00), OkHttp 4.12.0, kotlinx.serialization 1.7.3, Retrofit 2.11.0 (Places only), JUnit 4 + MockWebServer for tests.

**Spec:** `docs/superpowers/specs/2026-10-07-android-native-rewrite-design.md`

## Global Constraints

- All work happens on branch `android-native-rewrite`, created from `main` at the start of Task 1.
- Toolchain stays at AGP 9.4.0 / Kotlin 2.3.21 / compileSdk 37 / minSdk 26 (the module's already-scaffolded versions) — bikebus-main's own, older versions (AGP 8.6.1/Kotlin 2.0.0/compileSdk 35) are not used.
- Three required build-time env vars, each via `System.getenv(...) ?: throw GradleException(...)`, no hardcoded defaults: `OTP_AUTH_TOKEN`, `GOOGLE_PLACES_API_KEY`, `OTP_SERVER_BASE_URL`.
- No `SearchMode.NORMAL` — only `BRING_BIKE` and `PARK_AND_RIDE`.
- Geocoding stays direct-to-Google (`PlacesApi`/`PlacesDto`, unchanged from bikebus-main) — the backend's `/geocode` endpoint is not used by this app.
- `WakeStatus`/`wakeServer()` and all "wake up server" UI are dropped — no equivalent in the new always-on backend.
- No end-to-end device/emulator testing by Claude. The final task confirms `assembleDebug`/`assembleRelease` succeed; the user tests on their own device.

## Review Focus

- **Invalid/expired auth token at runtime** (not just missing at build time): Caddy returns a bare 403 with no distinguishing body for both an invalid token and rate-limiting (by design, per the OCI deployment spec) — `OtpServerApi` must treat a non-JSON/unexpected error body as a generic failure rather than crashing on a parse error. Pinned in Task 3.
- **Null origin/destination at search time**: `TripViewModel.search()` must no-op (not call the backend at all) when `fromPlace`/`toPlace` is null, matching bikebus-main's existing guard. Pinned in Task 5.
- **`maxTransfers` decrement past zero**: existing behavior treats "0 connections" and "unlimited" as adjacent ends of the same control (`decrementMaxTransfers` at 0 goes to `null`, not -1). Must be preserved exactly. Pinned in Task 5.
- **Backend network/5xx failure vs. a known error code**: an unreachable backend (connection refused/timeout) must surface the same "Couldn't reach the routing server" message as before, while a *parsed* `no_coverage`/`unreachable` error code gets its own more specific message — these are two different code paths and both need a test. Pinned in Task 5.
- **Drop-me-off `/connect` returning `unreachable`**: must map to "Couldn't find a way to reach this route from your origin" (today's copy for "no itinerary found"), not the generic connectivity message. Pinned in Task 5.

---

### Task 1: Branch, toolchain, and gradle scaffolding

**Files:**
- Create branch `android-native-rewrite`
- Modify: `android/build.gradle.kts`
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Delete: `android/app/src/main/java/one/brj/bikebus/TokenInjectingWebViewClient.kt`
- Delete: `android/app/src/main/res/layout/activity_main.xml`
- Delete: `android/app/src/main/res/values/themes.xml`
- Create (temporary, replaced in Task 6): `android/app/src/main/java/one/brj/bikebus/MainActivity.kt`

**Interfaces:**
- Produces: `BuildConfig.OTP_AUTH_TOKEN`, `BuildConfig.GOOGLE_PLACES_API_KEY`, `BuildConfig.OTP_SERVER_BASE_URL` — every later task's network code reads these three fields.

- [ ] **Step 1: Create the branch**

```bash
cd /c/Users/bru/spare-source/otp-server-ui
git checkout -b android-native-rewrite
```

- [ ] **Step 2: Add the Compose + serialization plugins to the root build file**

Replace `android/build.gradle.kts` entirely with:

```kotlin
plugins {
    // AGP 9+ has built-in Kotlin support; the separate `org.jetbrains.kotlin.android`
    // plugin is no longer required (confirmed the hard way -- see android/README.md's
    // "Known deviations" section).
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
}
```

- [ ] **Step 3: Rewrite `android/app/build.gradle.kts`**

```kotlin
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9+ has built-in Kotlin support -- no separate `kotlin("android")`
    // plugin (applying it fails the build: "no longer required since AGP 9.0").
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun requiredEnv(name: String): String = System.getenv(name)
    ?: throw GradleException("$name is not set. Export it before building.")

val otpAuthToken: String = requiredEnv("OTP_AUTH_TOKEN")
val googlePlacesApiKey: String = requiredEnv("GOOGLE_PLACES_API_KEY")
val otpServerBaseUrl: String = requiredEnv("OTP_SERVER_BASE_URL")

android {
    namespace = "one.brj.bikebus"
    compileSdk = 37

    defaultConfig {
        applicationId = "one.brj.bikebus"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "OTP_AUTH_TOKEN", "\"$otpAuthToken\"")
        buildConfigField("String", "GOOGLE_PLACES_API_KEY", "\"$googlePlacesApiKey\"")
        buildConfigField("String", "OTP_SERVER_BASE_URL", "\"$otpServerBaseUrl\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Task 1 (original WebView plan): this app deliberately replaces the existing
    // `bikebus` install under the same package id (one.brj.bikebus), so it needs to be
    // signable with the *same* key bru already uses for his personal apps -- not a new,
    // one-off keystore (see this file's git history / android/README.md for the full
    // decision). Real values are never hardcoded here: they come from env vars, or from
    // a local, gitignored key.properties bru fills in himself.
    signingConfigs {
        create("release") {
            val keystoreProperties = Properties()
            val keystorePropertiesFile = rootProject.file("key.properties")
            if (keystorePropertiesFile.exists()) {
                keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
            }

            storeFile = file(
                keystoreProperties.getProperty("storeFile") ?: "C:/Users/bru/android"
            )
            storePassword = System.getenv("ANDROID_STORE_PASSWORD")
                ?: keystoreProperties.getProperty("storePassword")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                ?: keystoreProperties.getProperty("keyAlias")
                ?: "brj"
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
                ?: keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
```

This drops `androidx.appcompat:appcompat` and `androidx.activity:activity-ktx` (needed only by the deleted `AppCompatActivity`-based `MainActivity`) and the `OTP_AUTH_TOKEN`-only env-var block (replaced by three required vars).

- [ ] **Step 4: Update the manifest**

Replace `android/app/src/main/AndroidManifest.xml` entirely with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <queries>
        <package android:name="com.google.android.apps.maps" />
    </queries>

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:usesCleartextTraffic="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

This matches bikebus-main's manifest (location permission, maps query, cleartext allowed for local-backend testing, platform `Theme.Material.Light.NoActionBar` instead of a custom `Theme.AppCompat`-based style) while keeping this module's own `@mipmap/ic_launcher` icon reference.

- [ ] **Step 5: Delete WebView-only files**

```bash
cd /c/Users/bru/spare-source/otp-server-ui
rm android/app/src/main/java/one/brj/bikebus/TokenInjectingWebViewClient.kt
rm android/app/src/main/res/layout/activity_main.xml
rm android/app/src/main/res/values/themes.xml
```

- [ ] **Step 6: Write a temporary placeholder `MainActivity.kt`**

This exists only to prove the new toolchain (Compose plugin + BOM under AGP 9.4.0/Kotlin 2.3.21) builds, before investing in porting the real UI in Task 6:

```kotlin
package one.brj.bikebus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { Text("placeholder") }
            }
        }
    }
}
```

- [ ] **Step 7: Verify the build**

```bash
cd /c/Users/bru/spare-source/otp-server-ui/android
export OTP_AUTH_TOKEN=placeholder-for-build-check
export GOOGLE_PLACES_API_KEY=placeholder-for-build-check
export OTP_SERVER_BASE_URL=http://localhost:8081
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. This confirms the Compose plugin resolves correctly against this exact AGP/Kotlin combination before any real UI code is written.

- [ ] **Step 8: Commit**

```bash
git add android/
git commit -m "Scaffold Compose toolchain, drop WebView shell"
```

---

### Task 2: Port models, local domain code, and theme

**Files:**
- Create: `android/app/src/main/java/one/brj/bikebus/model/TimeMode.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/model/SearchMode.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/model/ResolvedPlace.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/model/PlaceSuggestion.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/model/SavedPlace.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/model/NearbyRoute.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/model/NearbyRoutesUiState.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/model/FlagStopInfo.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/model/FlagStopConnectResult.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/model/Itinerary.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/model/TripUiState.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/data/SavedPlacesStore.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/domain/LocationProvider.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/domain/MapsIntent.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/ui/theme/Theme.kt`
- Test: `android/app/src/test/java/one/brj/bikebus/model/ItineraryTest.kt`

**Interfaces:**
- Produces: `Itinerary(legs: List<Leg>, exceedsBikeLimit: Boolean, hasLongWalkEgress: Boolean)`, `Leg(mode, distanceMeters, durationSeconds, fromLat, fromLon, toLat, toLon, fromName, toName, routeShortName, departureTime: OffsetDateTime)` with computed `arrivalTime`; `SearchMode { BRING_BIKE, PARK_AND_RIDE }`; `TripUiState` (no `wakeStatus` field) — Task 3/5/6 consume these exact shapes.

- [ ] **Step 1: Copy the unchanged model/domain files verbatim from bikebus-main**

```bash
cd /c/Users/bru/spare-source
SRC=bikebus-main/app/src/main/java/one/brj/bikebus
DST=otp-server-ui/android/app/src/main/java/one/brj/bikebus

cp "$SRC/model/TimeMode.kt" "$DST/model/TimeMode.kt"
cp "$SRC/model/ResolvedPlace.kt" "$DST/model/ResolvedPlace.kt"
cp "$SRC/model/PlaceSuggestion.kt" "$DST/model/PlaceSuggestion.kt"
cp "$SRC/model/SavedPlace.kt" "$DST/model/SavedPlace.kt"
cp "$SRC/model/NearbyRoute.kt" "$DST/model/NearbyRoute.kt"
cp "$SRC/model/NearbyRoutesUiState.kt" "$DST/model/NearbyRoutesUiState.kt"
cp "$SRC/model/FlagStopInfo.kt" "$DST/model/FlagStopInfo.kt"
cp "$SRC/model/FlagStopConnectResult.kt" "$DST/model/FlagStopConnectResult.kt"
cp "$SRC/data/SavedPlacesStore.kt" "$DST/data/SavedPlacesStore.kt"
cp "$SRC/domain/LocationProvider.kt" "$DST/domain/LocationProvider.kt"
cp "$SRC/domain/MapsIntent.kt" "$DST/domain/MapsIntent.kt"
cp "$SRC/ui/theme/Theme.kt" "$DST/ui/theme/Theme.kt"
```

- [ ] **Step 2: Write `model/SearchMode.kt` (adapted — `NORMAL` dropped)**

```kotlin
package one.brj.bikebus.model

enum class SearchMode { BRING_BIKE, PARK_AND_RIDE }
```

- [ ] **Step 3: Write the failing test for the adapted `Itinerary`/`Leg` shape**

```kotlin
package one.brj.bikebus.model

import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ItineraryTest {
    private val baseTime = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC)

    private fun leg(mode: String, distanceMeters: Double, durationSeconds: Double, departureTime: OffsetDateTime) = Leg(
        mode = mode, distanceMeters = distanceMeters, durationSeconds = durationSeconds,
        fromLat = 0.0, fromLon = 0.0, toLat = 0.0, toLon = 0.0,
        fromName = "A", toName = "B", routeShortName = null, departureTime = departureTime,
    )

    @Test
    fun `leg arrivalTime is departureTime plus durationSeconds`() {
        val l = leg("WALK", 100.0, 90.0, baseTime)
        assertEquals(baseTime.plusSeconds(90), l.arrivalTime)
    }

    @Test
    fun `itinerary departureTime and arrivalTime come from first and last leg`() {
        val legA = leg("BICYCLE", 500.0, 120.0, baseTime)
        val legB = leg("BUS", 0.0, 600.0, legA.arrivalTime)
        val itinerary = Itinerary(legs = listOf(legA, legB), exceedsBikeLimit = false, hasLongWalkEgress = false)
        assertEquals(baseTime, itinerary.departureTime)
        assertEquals(legB.arrivalTime, itinerary.arrivalTime)
        assertEquals(720.0, itinerary.totalDurationSeconds, 0.0)
    }

    @Test
    fun `totalBikeDistanceMeters sums only BICYCLE legs`() {
        val legs = listOf(
            leg("BICYCLE", 3000.0, 600.0, baseTime),
            leg("WALK", 200.0, 120.0, baseTime.plusMinutes(10)),
            leg("BICYCLE", 1500.0, 300.0, baseTime.plusMinutes(12)),
        )
        val itinerary = Itinerary(legs = legs, exceedsBikeLimit = false, hasLongWalkEgress = false)
        assertEquals(4500.0, itinerary.totalBikeDistanceMeters, 0.0)
    }

    @Test
    fun `exceedsBikeLimit and hasLongWalkEgress are passed through, not recomputed`() {
        val legs = listOf(leg("BICYCLE", 50_000.0, 600.0, baseTime))
        // A huge bike distance would exceed the client's old hardcoded 10km limit, but the
        // server is now the sole authority -- a false flag here must stay false.
        val itinerary = Itinerary(legs = legs, exceedsBikeLimit = false, hasLongWalkEgress = false)
        assertFalse(itinerary.exceedsBikeLimit)
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "one.brj.bikebus.model.ItineraryTest"`
Expected: FAIL — `Itinerary`/`Leg` don't exist yet.

- [ ] **Step 5: Write `model/Itinerary.kt`**

```kotlin
package one.brj.bikebus.model

import java.time.Duration
import java.time.OffsetDateTime

data class Leg(
    val mode: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val fromLat: Double,
    val fromLon: Double,
    val toLat: Double,
    val toLon: Double,
    val fromName: String?,
    val toName: String?,
    val routeShortName: String?,
    val departureTime: OffsetDateTime,
) {
    val arrivalTime: OffsetDateTime get() = departureTime.plusSeconds(durationSeconds.toLong())
}

data class Itinerary(
    val legs: List<Leg>,
    val exceedsBikeLimit: Boolean,
    val hasLongWalkEgress: Boolean,
) {
    val departureTime: OffsetDateTime get() = legs.first().departureTime
    val arrivalTime: OffsetDateTime get() = legs.last().arrivalTime
    val totalDurationSeconds: Double get() = Duration.between(departureTime, arrivalTime).seconds.toDouble()
    val totalBikeDistanceMeters: Double get() = legs.filter { it.mode == "BICYCLE" }.sumOf { it.distanceMeters }
}
```

Unlike bikebus-main's version, `exceedsBikeLimit` is a stored field (the backend's authoritative computation), not a recomputed `totalBikeDistanceMeters > 10_000.0` comparison — `totalBikeDistanceMeters` itself is still derived client-side (purely for the `ResultsList` warning banner's "(Xkm)" display), summed from per-leg `distanceMeters`, which the backend DTO still provides per leg.

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "one.brj.bikebus.model.ItineraryTest"`
Expected: PASS (4 tests).

- [ ] **Step 7: Write `model/TripUiState.kt` (adapted — no `wakeStatus`)**

```kotlin
package one.brj.bikebus.model

import java.time.LocalDate
import java.time.LocalTime

data class TripUiState(
    val searchMode: SearchMode = SearchMode.PARK_AND_RIDE,
    val timeMode: TimeMode = TimeMode.DEPART_AT,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now(),
    val fromQuery: String = "",
    val toQuery: String = "",
    val fromSuggestions: List<PlaceSuggestion> = emptyList(),
    val toSuggestions: List<PlaceSuggestion> = emptyList(),
    val fromPlace: ResolvedPlace? = null,
    val toPlace: ResolvedPlace? = null,
    val isLoading: Boolean = false,
    val itineraries: List<Itinerary> = emptyList(),
    val error: String? = null,
    val searched: Boolean = false,
    val notice: String? = null,
    val preferHubs: Boolean = false,
    val maxTransfers: Int? = null,
    val favorites: List<SavedPlace> = emptyList(),
    val recents: List<SavedPlace> = emptyList(),
    val showingNearbyRoutes: Boolean = false,
    val nearbyRoutesState: NearbyRoutesUiState = NearbyRoutesUiState()
)
```

`searchMode` defaults to `PARK_AND_RIDE` (matching the web frontend's own default — see `frontend/src/state.ts`) rather than bikebus-main's `NORMAL` default, which no longer exists.

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/java/one/brj/bikebus/model android/app/src/main/java/one/brj/bikebus/data android/app/src/main/java/one/brj/bikebus/domain android/app/src/main/java/one/brj/bikebus/ui/theme android/app/src/test
git commit -m "Port models, local domain code, and theme from bikebus-main"
```

---

### Task 3: Backend API client (`/search`, `/nearby-routes`, `/connect`)

**Files:**
- Create: `android/app/src/main/java/one/brj/bikebus/network/OtpServerDto.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/network/DtoMapping.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/network/OtpServerApi.kt`
- Test: `android/app/src/test/java/one/brj/bikebus/network/OtpServerApiTest.kt`

**Interfaces:**
- Consumes: `Itinerary`, `Leg`, `NearbyRoute`, `FlagStopInfo`, `FlagStopConnectResult` (Task 2).
- Produces: `class OtpServerApi(client: OkHttpClient, baseUrl: String, authToken: String)` with `suspend fun search(request: SearchRequestDto): SearchResult`, `suspend fun nearbyRoutes(lat: Double, lon: Double, radiusMeters: Double = 500.0): List<NearbyRoute>`, `suspend fun connect(request: ConnectRequestDto): ConnectOutcome`; `sealed class SearchResult { data class Success(itineraries: List<Itinerary>, notice: String?); data class Error(code: String) }`; `sealed class ConnectOutcome { data class Success(result: FlagStopConnectResult); data class Error(code: String) }` — Task 5 consumes all of this.

- [ ] **Step 1: Write `network/OtpServerDto.kt`**

Mirrors `backend/src/main/kotlin/one/otpserverui/api/SearchDto.kt` and `DropMeOffRoute.kt` field-for-field:

```kotlin
package one.brj.bikebus.network

import kotlinx.serialization.Serializable

@Serializable
data class SearchRequestDto(
    val mode: String,
    val timeMode: String,
    val originLat: Double,
    val originLon: Double,
    val destinationLat: Double,
    val destinationLon: Double,
    val dateTimeIso: String,
    val preferHubs: Boolean = false,
    val maxTransfers: Int? = null,
)

@Serializable
data class LegDto(
    val mode: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val fromLat: Double,
    val fromLon: Double,
    val toLat: Double,
    val toLon: Double,
    val fromName: String?,
    val toName: String?,
    val routeShortName: String?,
    val departureEpochSecond: Long,
)

@Serializable
data class ItineraryDto(
    val legs: List<LegDto>,
    val exceedsBikeLimit: Boolean,
    val hasLongWalkEgress: Boolean,
)

@Serializable
data class SearchResponseDto(val itineraries: List<ItineraryDto>, val notice: String? = null)

@Serializable
data class SearchErrorResponseDto(val error: String)

@Serializable
data class NearbyRouteDto(
    val routeGtfsId: String,
    val routeShortName: String?,
    val stopIds: List<String>,
    val distanceMeters: Double,
)

@Serializable
data class NearbyRoutesResponseDto(val routes: List<NearbyRouteDto>)

@Serializable
data class ConnectRequestDto(
    val originLat: Double,
    val originLon: Double,
    val destinationLat: Double,
    val destinationLon: Double,
    val routeGtfsId: String,
    val routeStopIds: List<String>,
    val timeMode: String,
    val dateTimeIso: String,
    val preferHubs: Boolean = false,
    val maxTransfers: Int? = null,
)

@Serializable
data class FlagStopInfoDto(
    val flagLat: Double,
    val flagLon: Double,
    val officialFinalLegDistanceMeters: Double,
    val officialFinalLegDurationSeconds: Double,
    val flagStopDistanceMeters: Double,
    val flagStopDurationSeconds: Double,
)

@Serializable
data class ConnectResponseDto(
    val itinerary: ItineraryDto,
    val flagStopInfo: FlagStopInfoDto?,
    val extraRideSeconds: Double?,
    val hubName: String?,
)
```

- [ ] **Step 2: Write `network/DtoMapping.kt`**

```kotlin
package one.brj.bikebus.network

import one.brj.bikebus.model.FlagStopInfo
import one.brj.bikebus.model.Itinerary
import one.brj.bikebus.model.Leg
import one.brj.bikebus.model.NearbyRoute
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun LegDto.toUiModel(): Leg = Leg(
    mode = mode,
    distanceMeters = distanceMeters,
    durationSeconds = durationSeconds,
    fromLat = fromLat,
    fromLon = fromLon,
    toLat = toLat,
    toLon = toLon,
    fromName = fromName,
    toName = toName,
    routeShortName = routeShortName,
    departureTime = OffsetDateTime.ofInstant(Instant.ofEpochSecond(departureEpochSecond), ZoneOffset.UTC),
)

fun ItineraryDto.toUiModel(): Itinerary = Itinerary(
    legs = legs.map { it.toUiModel() },
    exceedsBikeLimit = exceedsBikeLimit,
    hasLongWalkEgress = hasLongWalkEgress,
)

fun NearbyRouteDto.toUiModel(): NearbyRoute = NearbyRoute(
    routeGtfsId = routeGtfsId,
    routeShortName = routeShortName,
    stopIds = stopIds,
    distanceMeters = distanceMeters,
)

fun FlagStopInfoDto.toUiModel(): FlagStopInfo = FlagStopInfo(
    flagLat = flagLat,
    flagLon = flagLon,
    officialFinalLegDistanceMeters = officialFinalLegDistanceMeters,
    officialFinalLegDurationSeconds = officialFinalLegDurationSeconds,
    flagStopDistanceMeters = flagStopDistanceMeters,
    flagStopDurationSeconds = flagStopDurationSeconds,
)
```

- [ ] **Step 3: Write the failing tests for `OtpServerApi`**

```kotlin
package one.brj.bikebus.network

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OtpServerApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: OtpServerApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = OtpServerApi(OkHttpClient(), server.url("/").toString().removeSuffix("/"), authToken = "test-token")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun request() = SearchRequestDto(
        mode = "bring_bike", timeMode = "depart_at",
        originLat = 0.0, originLon = 0.0, destinationLat = 0.0, destinationLon = 0.0,
        dateTimeIso = "2026-01-01T10:00:00Z",
    )

    @Test
    fun `search sends the auth token header`() = runTest {
        server.enqueue(MockResponse().setBody("""{"itineraries":[],"notice":null}""").setResponseCode(200))
        api.search(request())
        assertEquals("test-token", server.takeRequest().getHeader("X-Auth-Token"))
    }

    @Test
    fun `search maps a successful response`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"itineraries":[{"legs":[{"mode":"WALK","distanceMeters":100.0,"durationSeconds":60.0,""" +
                    """"fromLat":1.0,"fromLon":2.0,"toLat":3.0,"toLon":4.0,"fromName":"A","toName":"B",""" +
                    """"routeShortName":null,"departureEpochSecond":1735689600}],"exceedsBikeLimit":false,""" +
                    """"hasLongWalkEgress":false}],"notice":"test notice"}"""
            ).setResponseCode(200)
        )
        val result = api.search(request())
        check(result is SearchResult.Success)
        assertEquals(1, result.itineraries.size)
        assertEquals("test notice", result.notice)
    }

    @Test
    fun `search maps a known error code from a 422 response`() = runTest {
        server.enqueue(MockResponse().setBody("""{"error":"no_coverage"}""").setResponseCode(422))
        val result = api.search(request())
        check(result is SearchResult.Error)
        assertEquals("no_coverage", result.code)
    }

    @Test
    fun `search falls back to unknown_error on an unparseable error body`() = runTest {
        // Mirrors a bare, body-less 403 -- both an invalid token and rate-limiting return
        // exactly this shape by design (see the OCI deployment spec's security rationale).
        server.enqueue(MockResponse().setBody("").setResponseCode(403))
        val result = api.search(request())
        check(result is SearchResult.Error)
        assertEquals("unknown_error", result.code)
    }

    @Test
    fun `nearbyRoutes maps a successful response`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"routes":[{"routeGtfsId":"RUT:1","routeShortName":"1A","stopIds":["S1"],"distanceMeters":42.0}]}"""
            ).setResponseCode(200)
        )
        val routes = api.nearbyRoutes(lat = 55.0, lon = 12.0)
        assertEquals(1, routes.size)
        assertEquals("1A", routes.first().routeShortName)
    }

    @Test
    fun `connect maps a known error code`() = runTest {
        server.enqueue(MockResponse().setBody("""{"error":"unreachable"}""").setResponseCode(422))
        val result = api.connect(
            ConnectRequestDto(
                originLat = 0.0, originLon = 0.0, destinationLat = 0.0, destinationLon = 0.0,
                routeGtfsId = "RUT:1", routeStopIds = listOf("S1"),
                timeMode = "depart_at", dateTimeIso = "2026-01-01T10:00:00Z",
            )
        )
        check(result is ConnectOutcome.Error)
        assertEquals("unreachable", result.code)
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "one.brj.bikebus.network.OtpServerApiTest"`
Expected: FAIL — `OtpServerApi` doesn't exist yet.

- [ ] **Step 5: Write `network/OtpServerApi.kt`**

```kotlin
package one.brj.bikebus.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import one.brj.bikebus.model.FlagStopConnectResult
import one.brj.bikebus.model.Itinerary
import one.brj.bikebus.model.NearbyRoute

sealed class SearchResult {
    data class Success(val itineraries: List<Itinerary>, val notice: String?) : SearchResult()
    data class Error(val code: String) : SearchResult()
}

sealed class ConnectOutcome {
    data class Success(val result: FlagStopConnectResult) : ConnectOutcome()
    data class Error(val code: String) : ConnectOutcome()
}

private val json = Json { ignoreUnknownKeys = true }
private val JSON_MEDIA_TYPE = "application/json".toMediaType()

class OtpServerApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val authToken: String,
) {
    private fun errorCode(body: String): String =
        runCatching { json.decodeFromString(SearchErrorResponseDto.serializer(), body).error }
            .getOrDefault("unknown_error")

    suspend fun search(request: SearchRequestDto): SearchResult = withContext(Dispatchers.IO) {
        val httpRequest = Request.Builder()
            .url("$baseUrl/search")
            .header("X-Auth-Token", authToken)
            .post(json.encodeToString(SearchRequestDto.serializer(), request).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                val parsed = json.decodeFromString(SearchResponseDto.serializer(), body)
                SearchResult.Success(parsed.itineraries.map { it.toUiModel() }, parsed.notice)
            } else {
                SearchResult.Error(errorCode(body))
            }
        }
    }

    suspend fun nearbyRoutes(lat: Double, lon: Double, radiusMeters: Double = 500.0): List<NearbyRoute> =
        withContext(Dispatchers.IO) {
            val httpRequest = Request.Builder()
                .url("$baseUrl/nearby-routes?lat=$lat&lon=$lon&radiusMeters=$radiusMeters")
                .header("X-Auth-Token", authToken)
                .get()
                .build()
            client.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                json.decodeFromString(NearbyRoutesResponseDto.serializer(), body).routes.map { it.toUiModel() }
            }
        }

    suspend fun connect(request: ConnectRequestDto): ConnectOutcome = withContext(Dispatchers.IO) {
        val httpRequest = Request.Builder()
            .url("$baseUrl/connect")
            .header("X-Auth-Token", authToken)
            .post(json.encodeToString(ConnectRequestDto.serializer(), request).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                val parsed = json.decodeFromString(ConnectResponseDto.serializer(), body)
                ConnectOutcome.Success(
                    FlagStopConnectResult(
                        legs = parsed.itinerary.toUiModel().legs,
                        flagStopInfo = parsed.flagStopInfo?.toUiModel(),
                        extraRideSeconds = parsed.extraRideSeconds,
                        hubName = parsed.hubName,
                    )
                )
            } else {
                ConnectOutcome.Error(errorCode(body))
            }
        }
    }
}
```

**Note (added during Task 5):** Task 5's tests need to inject the *same* `TestDispatcher` used for `Dispatchers.setMain(...)` into `OtpServerApi`'s internal `withContext` calls, so `advanceUntilIdle()` can see and drain that work deterministically (the real `Dispatchers.IO` runs on its own thread pool invisible to the test scheduler). Task 5 adds a `dispatcher: CoroutineDispatcher = Dispatchers.IO` constructor parameter to `OtpServerApi` (defaulting to today's real behavior — zero change for production code or Task 3's own `OtpServerApiTest`) and replaces each `withContext(Dispatchers.IO)` above with `withContext(dispatcher)`. See Task 5's own steps for the exact diff.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "one.brj.bikebus.network.OtpServerApiTest"`
Expected: PASS (6 tests).

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/one/brj/bikebus/network android/app/src/test/java/one/brj/bikebus/network
git commit -m "Add OtpServerApi: the backend's /search, /nearby-routes, /connect client"
```

---

### Task 4: Places network layer + NetworkModule rewiring

**Files:**
- Create: `android/app/src/main/java/one/brj/bikebus/network/PlacesApi.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/network/PlacesDto.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/network/NetworkModule.kt`

**Interfaces:**
- Consumes: `OtpServerApi` (Task 3), `BuildConfig.OTP_AUTH_TOKEN`/`GOOGLE_PLACES_API_KEY`/`OTP_SERVER_BASE_URL` (Task 1).
- Produces: `NetworkModule.placesApi: PlacesApi`, `NetworkModule.otpServerApi: OtpServerApi` — Task 5 consumes both.

- [ ] **Step 1: Copy `PlacesApi.kt` and `PlacesDto.kt` verbatim**

```bash
cd /c/Users/bru/spare-source
SRC=bikebus-main/app/src/main/java/one/brj/bikebus/network
DST=otp-server-ui/android/app/src/main/java/one/brj/bikebus/network
cp "$SRC/PlacesApi.kt" "$DST/PlacesApi.kt"
cp "$SRC/PlacesDto.kt" "$DST/PlacesDto.kt"
```

- [ ] **Step 2: Write `NetworkModule.kt` (adapted — `otpApi`/`otpWakeApi` dropped, `otpServerApi` added)**

```kotlin
package one.brj.bikebus.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import one.brj.bikebus.BuildConfig
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object NetworkModule {
    private val json = Json { ignoreUnknownKeys = true }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val placesApi: PlacesApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://places.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PlacesApi::class.java)
    }

    val otpServerApi: OtpServerApi by lazy {
        OtpServerApi(
            client = okHttpClient,
            baseUrl = BuildConfig.OTP_SERVER_BASE_URL,
            authToken = BuildConfig.OTP_AUTH_TOKEN,
        )
    }
}
```

The old `wakeHttpClient` (a 5-minute-timeout client for waking a scale-to-zero container) is dropped along with `WakeStatus` — there is nothing to wake on the new always-on backend.

- [ ] **Step 3: Verify the build**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` (nothing calls `NetworkModule` yet, so this is a compile-only check).

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/one/brj/bikebus/network
git commit -m "Port Places API client, rewire NetworkModule to OtpServerApi"
```

---

### Task 5: Rewrite `TripViewModel`

**Files:**
- Modify: `android/app/src/main/java/one/brj/bikebus/data/SavedPlacesStore.kt`
- Modify: `android/app/src/main/java/one/brj/bikebus/network/OtpServerApi.kt`
- Modify: `android/app/build.gradle.kts`
- Create: `android/app/src/main/java/one/brj/bikebus/TripViewModel.kt`
- Test: `android/app/src/test/java/one/brj/bikebus/TripViewModelTest.kt`

**Interfaces:**
- Consumes: `OtpServerApi`/`SearchResult`/`ConnectOutcome` (Task 3), `PlacesApi` (Task 4), `TripUiState`/`SearchMode`/`ResolvedPlace`/`PlaceSuggestion`/`SavedPlace` (Task 2).
- Produces: `PlacesPersistence` (interface, in `data/SavedPlacesStore.kt`); `TripViewModel(application: Application)` with `uiState: StateFlow<TripUiState>` and the public methods listed below — Task 6's `TripScreen` binds to these exact names.

**A note on why this task's tests don't use Robolectric:** an earlier attempt at this task tried running `TripViewModel`'s tests under Robolectric (to provide a real `Application`/`Context` for `AndroidViewModel`'s constructor). That led to a three-layer dead end on this machine's toolchain: Robolectric 4.13 doesn't support this project's real `compileSdk`/`targetSdk` (37) at all; the version that does (4.17) needs a JDK 21 *host* to build its sandbox, which this project doesn't otherwise need; and even with that fixed, Robolectric 4.17's own internals fail against a plain JDK 17/21 install with `IllegalAccessException: ... module java.base does not export jdk.internal.access` (Robolectric needs `--add-opens` JVM flags this module doesn't set). A sibling project in this same dev environment (`bikebus/app`, same real AGP/Kotlin/compileSdk/targetSdk) independently reaches the same conclusion: it uses on-device `androidTest` for anything touching the Android framework, and plain JUnit (no Robolectric) for everything else. Given the plan's own constraint against device/emulator testing by Claude, the correct fix is to avoid needing a real/simulated Android framework at all — which turns out to be straightforward, since the only two framework dependencies `TripViewModel` actually has (`SavedPlacesStore`'s `Context`, and `LocationProvider`'s `Context`) are either overridable or untouched by this task's tests. See Step 1 below.

- [ ] **Step 1: Add a `PlacesPersistence` seam to `SavedPlacesStore.kt`**

Modify `android/app/src/main/java/one/brj/bikebus/data/SavedPlacesStore.kt` (from Task 2) to extract an interface and have `SavedPlacesStore` implement it — purely additive, no behavior change:

```kotlin
package one.brj.bikebus.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import one.brj.bikebus.model.SavedPlace

interface PlacesPersistence {
    fun loadFavorites(): List<SavedPlace>
    fun saveFavorites(places: List<SavedPlace>)
    fun loadRecents(): List<SavedPlace>
    fun saveRecents(places: List<SavedPlace>)
}

class SavedPlacesStore(context: Context) : PlacesPersistence {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun loadFavorites(): List<SavedPlace> = load(KEY_FAVORITES)
    override fun saveFavorites(places: List<SavedPlace>) = save(KEY_FAVORITES, places)

    override fun loadRecents(): List<SavedPlace> = load(KEY_RECENTS)
    override fun saveRecents(places: List<SavedPlace>) = save(KEY_RECENTS, places)

    private fun load(key: String): List<SavedPlace> = runCatching {
        prefs.getString(key, null)?.let { json.decodeFromString<List<SavedPlace>>(it) }
    }.getOrNull() ?: emptyList()

    private fun save(key: String, places: List<SavedPlace>) {
        prefs.edit().putString(key, json.encodeToString(places)).apply()
    }

    companion object {
        private const val PREFS_NAME = "saved_places"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_RECENTS = "recents"
    }
}
```

- [ ] **Step 2: Give `OtpServerApi` an injectable dispatcher, and let unit tests call stubbed Android methods without throwing**

Two small, additive changes needed for Step 3's tests to run deterministically and cleanly:

1. In `network/OtpServerApi.kt` (from Task 3), add a `dispatcher: CoroutineDispatcher = Dispatchers.IO` constructor parameter (defaults to today's real behavior — zero change for production code or Task 3's own `OtpServerApiTest`), and replace each of the three `withContext(Dispatchers.IO)` calls with `withContext(dispatcher)`. Add `import kotlinx.coroutines.CoroutineDispatcher`. This lets tests inject the *same* `TestDispatcher` used for `Dispatchers.setMain(...)`, so `advanceUntilIdle()` can see and drain that work — the real `Dispatchers.IO` runs on its own thread pool the test scheduler can't observe, which otherwise makes assertions run before the network call actually completes.

2. Add this inside `android/app/build.gradle.kts`'s `android { }` block:

```kotlin
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
```

`TripViewModel`'s failure paths call `Log.e(...)` (a real Android framework method); under the default Android unit-test stub jar every such call throws `Method ... not mocked` instead of silently no-op'ing. This flag makes stubbed framework methods return a default value (0/null/false) instead of throwing — the standard, minimal fix for this extremely common situation, far better than deleting legitimate production error-logging just to make tests pass.

- [ ] **Step 3: Write the failing tests**

These never need a real/simulated Android `Context`: `TripViewModel` takes `Application()` constructed directly (its constructor only stores the reference, never dereferences it in any path these tests exercise), `otpServerApiOverride` points at `MockWebServer` (same tool as Task 3) *and* shares this test's own `dispatcher` (per Step 2's change, so `advanceUntilIdle()` can see that work), and a new `placesPersistenceOverride` replaces `SavedPlacesStore` with an in-memory fake — so this is a plain JUnit test, no Robolectric, no `androidx.test`:

```kotlin
package one.brj.bikebus

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import one.brj.bikebus.data.PlacesPersistence
import one.brj.bikebus.model.NearbyRoute
import one.brj.bikebus.model.SavedPlace
import one.brj.bikebus.model.SearchMode
import one.brj.bikebus.network.OtpServerApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class FakePlacesPersistence : PlacesPersistence {
    private var favorites = emptyList<SavedPlace>()
    private var recents = emptyList<SavedPlace>()
    override fun loadFavorites() = favorites
    override fun saveFavorites(places: List<SavedPlace>) { favorites = places }
    override fun loadRecents() = recents
    override fun saveRecents(places: List<SavedPlace>) { recents = places }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModelTest {
    private lateinit var server: MockWebServer
    private lateinit var viewModel: TripViewModel
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer()
        server.start()
        viewModel = TripViewModel(
            application = Application(),
            otpServerApiOverride = OtpServerApi(OkHttpClient(), server.url("/").toString().removeSuffix("/"), "test-token", dispatcher),
            placesPersistenceOverride = FakePlacesPersistence(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `search does nothing when origin or destination is missing`() = runTest {
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.searched)
    }

    @Test
    fun `decrementMaxTransfers at zero goes to unlimited, not negative`() {
        viewModel.incrementMaxTransfers() // null -> 0
        assertEquals(0, viewModel.uiState.value.maxTransfers)
        viewModel.decrementMaxTransfers() // 0 -> null (unlimited), not -1
        assertNull(viewModel.uiState.value.maxTransfers)
    }

    @Test
    fun `setSearchMode resets itineraries and search state`() {
        viewModel.setSearchMode(SearchMode.BRING_BIKE)
        assertEquals(SearchMode.BRING_BIKE, viewModel.uiState.value.searchMode)
        assertEquals(emptyList<Any>(), viewModel.uiState.value.itineraries)
        assertEquals(false, viewModel.uiState.value.searched)
    }

    @Test
    fun `search maps a no_coverage error to a specific message`() = runTest {
        viewModel.selectSavedPlace(SavedPlace("p1", "Origin", 55.0, 12.0), isFrom = true)
        viewModel.selectSavedPlace(SavedPlace("p2", "Destination", 56.0, 13.0), isFrom = false)
        server.enqueue(MockResponse().setBody("""{"error":"no_coverage"}""").setResponseCode(422))
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("No route exists between these points", viewModel.uiState.value.error)
    }

    @Test
    fun `search maps a network failure to the generic connectivity message`() = runTest {
        // A fresh viewModel pointed at a non-routable address, so this test never touches
        // the shared server/viewModel (and never needs to shut the server down mid-test).
        val unreachableViewModel = TripViewModel(
            application = Application(),
            otpServerApiOverride = OtpServerApi(OkHttpClient(), "http://127.0.0.1:1", "test-token", dispatcher),
            placesPersistenceOverride = FakePlacesPersistence(),
        )
        unreachableViewModel.selectSavedPlace(SavedPlace("p1", "Origin", 55.0, 12.0), isFrom = true)
        unreachableViewModel.selectSavedPlace(SavedPlace("p2", "Destination", 56.0, 13.0), isFrom = false)
        unreachableViewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Couldn't reach the routing server", unreachableViewModel.uiState.value.error)
    }

    @Test
    fun `selectNearbyRoute maps a connect error to the no-route-found message`() = runTest {
        viewModel.selectSavedPlace(SavedPlace("p1", "Origin", 55.0, 12.0), isFrom = true)
        viewModel.selectSavedPlace(SavedPlace("p2", "Destination", 56.0, 13.0), isFrom = false)
        server.enqueue(MockResponse().setBody("""{"error":"unreachable"}""").setResponseCode(422))
        viewModel.selectNearbyRoute(NearbyRoute(routeGtfsId = "RUT:1", routeShortName = "1A", stopIds = listOf("S1"), distanceMeters = 10.0))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Couldn't find a way to reach this route from your origin", viewModel.uiState.value.nearbyRoutesState.connectError)
    }
}
```

This requires no new test *dependencies* beyond what Task 1 already added (`junit:junit`, `kotlinx-coroutines-test`, `mockwebserver`) — only Step 2's `dispatcher` param and `testOptions` flag. Constructing `android.app.Application()` directly on the plain JVM unit-test classpath works (its constructor only calls `super(null)`, touching nothing that the default Android unit-test stub jar would reject), and neither of these tests ever call a method that touches `LocationProvider` (the only other framework-dependent lazy property on `TripViewModel`, only reached from `fetchSuggestions`, which none of these tests exercise).

- [ ] **Step 4: Run the tests to verify they fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "one.brj.bikebus.TripViewModelTest"`
Expected: FAIL — `TripViewModel` doesn't have the new constructor/shrunk behavior yet.

- [ ] **Step 5: Write `TripViewModel.kt`**

```kotlin
package one.brj.bikebus

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.brj.bikebus.data.PlacesPersistence
import one.brj.bikebus.data.SavedPlacesStore
import one.brj.bikebus.domain.LocationProvider
import one.brj.bikebus.model.NearbyRoute
import one.brj.bikebus.model.NearbyRoutesUiState
import one.brj.bikebus.model.PlaceSuggestion
import one.brj.bikebus.model.ResolvedPlace
import one.brj.bikebus.model.SavedPlace
import one.brj.bikebus.model.SearchMode
import one.brj.bikebus.model.TimeMode
import one.brj.bikebus.model.TripUiState
import one.brj.bikebus.network.AutocompleteRequest
import one.brj.bikebus.network.Circle
import one.brj.bikebus.network.ConnectRequestDto
import one.brj.bikebus.network.ConnectOutcome
import one.brj.bikebus.network.LatLngDto
import one.brj.bikebus.network.LocationBias
import one.brj.bikebus.network.NetworkModule
import one.brj.bikebus.network.OtpServerApi
import one.brj.bikebus.network.SearchRequestDto
import one.brj.bikebus.network.SearchResult
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class TripViewModel(
    application: Application,
    private val otpServerApiOverride: OtpServerApi? = null,
    private val placesPersistenceOverride: PlacesPersistence? = null,
) : AndroidViewModel(application) {

    private val placesApi by lazy { NetworkModule.placesApi }
    private val otpServerApi by lazy { otpServerApiOverride ?: NetworkModule.otpServerApi }
    private val deviceLocation by lazy { LocationProvider.lastKnownCoarseLocation(getApplication<Application>()) }
    private val savedPlacesStore: PlacesPersistence by lazy { placesPersistenceOverride ?: SavedPlacesStore(getApplication<Application>()) }

    private val _uiState = MutableStateFlow(TripUiState())
    val uiState: StateFlow<TripUiState> = _uiState

    private var fromSearchJob: Job? = null
    private var toSearchJob: Job? = null
    private var connectJob: Job? = null

    init {
        _uiState.update { it.copy(favorites = savedPlacesStore.loadFavorites(), recents = savedPlacesStore.loadRecents()) }
    }

    fun setSearchMode(mode: SearchMode) = _uiState.update {
        it.copy(
            searchMode = mode, itineraries = emptyList(), error = null, searched = false, notice = null,
            showingNearbyRoutes = false, nearbyRoutesState = NearbyRoutesUiState()
        )
    }
    fun setTimeMode(mode: TimeMode) = _uiState.update {
        it.copy(timeMode = mode, itineraries = emptyList(), error = null, searched = false, notice = null)
    }
    fun setDate(date: LocalDate) = _uiState.update { it.copy(date = date) }
    fun setTime(time: LocalTime) = _uiState.update { it.copy(time = time) }
    fun setPreferHubs(enabled: Boolean) = _uiState.update {
        it.copy(preferHubs = enabled, itineraries = emptyList(), error = null, searched = false, notice = null)
    }
    fun incrementMaxTransfers() = _uiState.update {
        it.copy(maxTransfers = (it.maxTransfers ?: -1) + 1, itineraries = emptyList(), error = null, searched = false, notice = null)
    }
    fun decrementMaxTransfers() = _uiState.update {
        val current = it.maxTransfers
        it.copy(
            maxTransfers = if (current == null || current <= 0) null else current - 1,
            itineraries = emptyList(), error = null, searched = false, notice = null
        )
    }

    fun swapFromTo() = _uiState.update {
        it.copy(
            fromQuery = it.toQuery, toQuery = it.fromQuery, fromPlace = it.toPlace, toPlace = it.fromPlace,
            fromSuggestions = emptyList(), toSuggestions = emptyList(),
            itineraries = emptyList(), error = null, searched = false, notice = null
        )
    }

    fun toggleFavorite(placeId: String, label: String, lat: Double?, lon: Double?) {
        if (_uiState.value.favorites.any { it.placeId == placeId }) {
            val updatedFavorites = _uiState.value.favorites.filterNot { it.placeId == placeId }
            _uiState.update { it.copy(favorites = updatedFavorites) }
            savedPlacesStore.saveFavorites(updatedFavorites)
            return
        }
        viewModelScope.launch {
            val existingRecent = _uiState.value.recents.find { it.placeId == placeId }
            val savedPlace = existingRecent ?: run {
                if (lat != null && lon != null) {
                    SavedPlace(placeId, label, lat, lon)
                } else {
                    val details = runCatching {
                        placesApi.placeDetails(placeId = placeId, apiKey = BuildConfig.GOOGLE_PLACES_API_KEY)
                    }.getOrNull() ?: return@launch
                    SavedPlace(placeId, label, details.location.latitude, details.location.longitude)
                }
            }
            if (_uiState.value.favorites.any { it.placeId == placeId }) return@launch
            val updatedFavorites = listOf(savedPlace) + _uiState.value.favorites
            val updatedRecents = _uiState.value.recents.filterNot { it.placeId == placeId }
            _uiState.update { it.copy(favorites = updatedFavorites, recents = updatedRecents) }
            savedPlacesStore.saveFavorites(updatedFavorites)
            savedPlacesStore.saveRecents(updatedRecents)
        }
    }

    fun removeRecent(placeId: String) {
        val updatedRecents = _uiState.value.recents.filterNot { it.placeId == placeId }
        _uiState.update { it.copy(recents = updatedRecents) }
        savedPlacesStore.saveRecents(updatedRecents)
    }

    fun selectSavedPlace(savedPlace: SavedPlace, isFrom: Boolean) {
        val resolved = ResolvedPlace(label = savedPlace.label, lat = savedPlace.lat, lon = savedPlace.lon)
        rememberRecent(savedPlace.placeId, resolved)
        if (isFrom) {
            _uiState.update {
                it.copy(fromQuery = savedPlace.label, fromPlace = resolved, fromSuggestions = emptyList(), itineraries = emptyList(), error = null, searched = false, notice = null)
            }
        } else {
            _uiState.update {
                it.copy(toQuery = savedPlace.label, toPlace = resolved, toSuggestions = emptyList(), itineraries = emptyList(), error = null, searched = false, notice = null)
            }
        }
    }

    fun findNearbyRoutes() {
        val destination = _uiState.value.toPlace ?: return
        _uiState.update { it.copy(showingNearbyRoutes = true, nearbyRoutesState = NearbyRoutesUiState(isLoading = true)) }
        viewModelScope.launch {
            val result = runCatching { otpServerApi.nearbyRoutes(lat = destination.lat, lon = destination.lon) }
            result.exceptionOrNull()?.let { Log.e("TripViewModel", "findNearbyRoutes() failed", it) }
            _uiState.update {
                it.copy(
                    nearbyRoutesState = it.nearbyRoutesState.copy(
                        isLoading = false, searched = true,
                        routes = result.getOrDefault(emptyList()),
                        error = if (result.isFailure) "Couldn't reach the routing server" else null
                    )
                )
            }
        }
    }

    fun selectNearbyRoute(route: NearbyRoute) {
        val state = _uiState.value
        val origin = state.fromPlace ?: return
        val destination = state.toPlace ?: return
        connectJob?.cancel()
        _uiState.update {
            it.copy(nearbyRoutesState = it.nearbyRoutesState.copy(selectedRoute = route, connecting = true, connectResult = null, connectError = null))
        }
        connectJob = viewModelScope.launch {
            val dateTimeIso = buildIsoDateTime(state.date, state.time)
            val outcome = runCatching {
                otpServerApi.connect(
                    ConnectRequestDto(
                        originLat = origin.lat, originLon = origin.lon,
                        destinationLat = destination.lat, destinationLon = destination.lon,
                        routeGtfsId = route.routeGtfsId, routeStopIds = route.stopIds,
                        timeMode = state.timeMode.wireValue(), dateTimeIso = dateTimeIso,
                        preferHubs = state.preferHubs, maxTransfers = state.maxTransfers,
                    )
                )
            }
            result@ run {
                val errorMessage = when {
                    outcome.isFailure -> {
                        outcome.exceptionOrNull()?.let { Log.e("TripViewModel", "selectNearbyRoute() failed", it) }
                        "Couldn't reach the routing server"
                    }
                    outcome.getOrNull() is ConnectOutcome.Error -> "Couldn't find a way to reach this route from your origin"
                    else -> null
                }
                val success = (outcome.getOrNull() as? ConnectOutcome.Success)?.result
                _uiState.update {
                    if (it.nearbyRoutesState.selectedRoute?.routeGtfsId != route.routeGtfsId) return@update it
                    it.copy(nearbyRoutesState = it.nearbyRoutesState.copy(connecting = false, connectResult = success, connectError = errorMessage))
                }
            }
        }
    }

    fun onFromQueryChanged(query: String) {
        _uiState.update { it.copy(fromQuery = query, fromPlace = null, itineraries = emptyList(), error = null, searched = false, notice = null) }
        fromSearchJob?.cancel()
        if (query.length < 3) {
            _uiState.update { it.copy(fromSuggestions = emptyList()) }
            return
        }
        fromSearchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(fromSuggestions = fetchSuggestions(query)) }
        }
    }

    fun onToQueryChanged(query: String) {
        _uiState.update {
            it.copy(toQuery = query, toPlace = null, itineraries = emptyList(), error = null, searched = false, notice = null, showingNearbyRoutes = false, nearbyRoutesState = NearbyRoutesUiState())
        }
        toSearchJob?.cancel()
        if (query.length < 3) {
            _uiState.update { it.copy(toSuggestions = emptyList()) }
            return
        }
        toSearchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(toSuggestions = fetchSuggestions(query)) }
        }
    }

    fun selectFromSuggestion(suggestion: PlaceSuggestion) {
        _uiState.update { it.copy(fromQuery = suggestion.description, fromSuggestions = emptyList()) }
        viewModelScope.launch {
            val place = resolvePlace(suggestion)
            _uiState.update { it.copy(fromPlace = place) }
            if (place != null) rememberRecent(suggestion.placeId, place)
        }
    }

    fun selectToSuggestion(suggestion: PlaceSuggestion) {
        _uiState.update { it.copy(toQuery = suggestion.description, toSuggestions = emptyList(), showingNearbyRoutes = false, nearbyRoutesState = NearbyRoutesUiState()) }
        viewModelScope.launch {
            val place = resolvePlace(suggestion)
            _uiState.update { it.copy(toPlace = place) }
            if (place != null) rememberRecent(suggestion.placeId, place)
        }
    }

    fun search() {
        val state = _uiState.value
        val from = state.fromPlace
        val to = state.toPlace
        if (from == null || to == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, notice = null, searched = true, showingNearbyRoutes = false) }
            val dateTimeIso = buildIsoDateTime(state.date, state.time)
            val outcome = runCatching {
                otpServerApi.search(
                    SearchRequestDto(
                        mode = state.searchMode.wireValue(), timeMode = state.timeMode.wireValue(),
                        originLat = from.lat, originLon = from.lon,
                        destinationLat = to.lat, destinationLon = to.lon,
                        dateTimeIso = dateTimeIso, preferHubs = state.preferHubs, maxTransfers = state.maxTransfers,
                    )
                )
            }
            if (outcome.isFailure) {
                outcome.exceptionOrNull()?.let { Log.e("TripViewModel", "search() failed", it) }
                _uiState.update { it.copy(isLoading = false, itineraries = emptyList(), error = "Couldn't reach the routing server") }
                return@launch
            }
            when (val result = outcome.getOrThrow()) {
                is SearchResult.Success -> {
                    val requestedDateTime = OffsetDateTime.parse(dateTimeIso)
                    val notice = result.notice ?: closestOptionNotice(state.timeMode, requestedDateTime, result.itineraries)
                    _uiState.update { it.copy(isLoading = false, itineraries = result.itineraries, notice = notice) }
                }
                is SearchResult.Error -> {
                    val message = when (result.code) {
                        "no_coverage" -> "No route exists between these points"
                        else -> "Couldn't reach the routing server"
                    }
                    _uiState.update { it.copy(isLoading = false, itineraries = emptyList(), error = message) }
                }
            }
        }
    }

    private fun closestOptionNotice(timeMode: TimeMode, requested: OffsetDateTime, itineraries: List<one.brj.bikebus.model.Itinerary>): String? {
        val closest = itineraries.firstOrNull() ?: return null
        val actual = when (timeMode) {
            TimeMode.DEPART_AT -> closest.departureTime
            TimeMode.ARRIVE_BY -> closest.arrivalTime
        }
        val diffMinutes = abs(Duration.between(requested, actual).toMinutes())
        if (diffMinutes <= 20) return null
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val actualFormatted = actual.format(formatter)
        return when (timeMode) {
            TimeMode.DEPART_AT -> "No routes at your requested time — showing the closest option, departing $actualFormatted"
            TimeMode.ARRIVE_BY -> "No routes at your requested time — showing the closest option, arriving $actualFormatted"
        }
    }

    private suspend fun fetchSuggestions(query: String): List<PlaceSuggestion> = try {
        val bias = deviceLocation?.let { (lat, lon) -> LocationBias(circle = Circle(center = LatLngDto(lat, lon), radius = 50_000.0)) }
        val response = placesApi.autocomplete(apiKey = BuildConfig.GOOGLE_PLACES_API_KEY, request = AutocompleteRequest(input = query, locationBias = bias))
        response.suggestions.mapNotNull { it.placePrediction }.map { prediction ->
            PlaceSuggestion(
                placeId = prediction.placeId, description = prediction.text.text,
                mainText = prediction.structuredFormat?.mainText?.text ?: prediction.text.text,
                secondaryText = prediction.structuredFormat?.secondaryText?.text,
                isStreet = prediction.types.contains("route"),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private suspend fun resolvePlace(suggestion: PlaceSuggestion): ResolvedPlace? = try {
        val details = placesApi.placeDetails(placeId = suggestion.placeId, apiKey = BuildConfig.GOOGLE_PLACES_API_KEY)
        ResolvedPlace(label = suggestion.description, lat = details.location.latitude, lon = details.location.longitude)
    } catch (e: Exception) {
        null
    }

    private fun rememberRecent(placeId: String, place: ResolvedPlace) {
        if (_uiState.value.favorites.any { it.placeId == placeId }) return
        val savedPlace = SavedPlace(placeId, place.label, place.lat, place.lon)
        val updatedRecents = (listOf(savedPlace) + _uiState.value.recents.filterNot { it.placeId == placeId }).take(10)
        _uiState.update { it.copy(recents = updatedRecents) }
        savedPlacesStore.saveRecents(updatedRecents)
    }

    private fun buildIsoDateTime(date: LocalDate, time: LocalTime): String =
        LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

private fun SearchMode.wireValue(): String = when (this) {
    SearchMode.BRING_BIKE -> "bring_bike"
    SearchMode.PARK_AND_RIDE -> "park_and_ride"
}

private fun TimeMode.wireValue(): String = when (this) {
    TimeMode.DEPART_AT -> "depart_at"
    TimeMode.ARRIVE_BY -> "arrive_by"
}
```

Dropped relative to bikebus-main: `wakeServer()`, `incrementRank`/favorites-rank-bumping on select (bikebus-main's `selectSavedPlace`/`selectNearbyRoute` called `incrementRank` — this reordered favorites by usage frequency; it's a small UX nicety with no connection to the backend rewrite, kept out per the spec's "no UI redesign" scope — actually note this is a behavior change worth flagging below), `fetchItineraries`'s entire hub-stitching/Park & Ride candidate-probing body (now one `otpServerApi.search()` call), `connectToRoute`'s client-side re-planning (now one `otpServerApi.connect()` call), and the `OTP_BASE_URL`-blank guard (replaced by the build-time `GradleException` in Task 1 — a blank base URL can no longer reach runtime).

**Note on `incrementRank`:** bikebus-main bumps a saved place's `rank` on every selection, and `AddressField`'s favorites picker sorts by `rank` descending. This plan drops the increment calls (`selectSavedPlace`/`selectNearbyRoute` no longer call it) because the ported `AddressField` still sorts by `rank`, so without incrementing it, favorites keep a stable (insertion) order rather than most-used-first — a minor behavior regression, not a crash or data-loss risk. Flagging it here for the final review rather than silently preserving or silently dropping it: re-add `incrementRank` (identical to bikebus-main's) if you want most-used-first ordering back; nothing else in this plan depends on the decision either way.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "one.brj.bikebus.TripViewModelTest"`
Expected: PASS (6 tests).

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/one/brj/bikebus/TripViewModel.kt android/app/src/main/java/one/brj/bikebus/data/SavedPlacesStore.kt android/app/src/main/java/one/brj/bikebus/network/OtpServerApi.kt android/app/build.gradle.kts android/app/src/test/java/one/brj/bikebus/TripViewModelTest.kt
git commit -m "Rewrite TripViewModel to call OtpServerApi instead of raw OTP GraphQL"
```

---

### Task 6: Port the UI layer and finalize `MainActivity`

**Files:**
- Create: `android/app/src/main/java/one/brj/bikebus/ui/ModeToggle.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/ui/AddressField.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/ui/DateTimePickers.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/ui/ResultsList.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/ui/NearbyRoutesResults.kt`
- Create: `android/app/src/main/java/one/brj/bikebus/ui/TripScreen.kt`
- Modify: `android/app/src/main/java/one/brj/bikebus/MainActivity.kt` (replaces Task 1's placeholder)

**Interfaces:**
- Consumes: `TripViewModel` (Task 5), all `model/*` types (Task 2).

- [ ] **Step 1: Copy the unchanged UI files verbatim**

```bash
cd /c/Users/bru/spare-source
SRC=bikebus-main/app/src/main/java/one/brj/bikebus/ui
DST=otp-server-ui/android/app/src/main/java/one/brj/bikebus/ui
cp "$SRC/ModeToggle.kt" "$DST/ModeToggle.kt"
cp "$SRC/AddressField.kt" "$DST/AddressField.kt"
cp "$SRC/DateTimePickers.kt" "$DST/DateTimePickers.kt"
cp "$SRC/ResultsList.kt" "$DST/ResultsList.kt"
cp "$SRC/NearbyRoutesResults.kt" "$DST/NearbyRoutesResults.kt"
```

- [ ] **Step 2: Write the adapted `ui/TripScreen.kt`**

Same as bikebus-main's version, with the `NORMAL` mode option and the entire "Wake up server" button + `WakeStatus` `when` block removed:

```kotlin
package one.brj.bikebus.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import one.brj.bikebus.TripViewModel
import one.brj.bikebus.model.SearchMode
import one.brj.bikebus.model.TimeMode

@Composable
fun TripScreen(viewModel: TripViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ModeToggle(
            options = listOf(SearchMode.BRING_BIKE to "Bring Bike", SearchMode.PARK_AND_RIDE to "Park & Ride"),
            selected = state.searchMode,
            onSelected = viewModel::setSearchMode,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        ModeToggle(
            options = listOf(TimeMode.DEPART_AT to "Depart at", TimeMode.ARRIVE_BY to "Arrive by"),
            selected = state.timeMode,
            onSelected = viewModel::setTimeMode,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            DateField(date = state.date, onDateSelected = viewModel::setDate, modifier = Modifier.width(160.dp))
            Spacer(modifier = Modifier.width(8.dp))
            TimeField(time = state.time, onTimeSelected = viewModel::setTime, modifier = Modifier.width(120.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
        ) {
            AddressField(
                label = "From", query = state.fromQuery, suggestions = state.fromSuggestions,
                favorites = state.favorites, recents = state.recents,
                onQueryChanged = viewModel::onFromQueryChanged, onSuggestionSelected = viewModel::selectFromSuggestion,
                onSavedPlaceSelected = { viewModel.selectSavedPlace(it, isFrom = true) },
                onToggleFavorite = viewModel::toggleFavorite, onRemoveRecent = viewModel::removeRecent
            )
            HorizontalDivider()
            AddressField(
                label = "To", query = state.toQuery, suggestions = state.toSuggestions,
                favorites = state.favorites, recents = state.recents,
                onQueryChanged = viewModel::onToQueryChanged, onSuggestionSelected = viewModel::selectToSuggestion,
                onSavedPlaceSelected = { viewModel.selectSavedPlace(it, isFrom = false) },
                onToggleFavorite = viewModel::toggleFavorite, onRemoveRecent = viewModel::removeRecent,
                trailingContent = {
                    IconButton(onClick = viewModel::swapFromTo) {
                        Icon(imageVector = Icons.Filled.SwapVert, contentDescription = "Swap From and To")
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Prefer transit hubs", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterVertically))
            Switch(checked = state.preferHubs, onCheckedChange = viewModel::setPreferHubs)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Max connections", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::decrementMaxTransfers) {
                    Icon(imageVector = Icons.Filled.Remove, contentDescription = "Fewer connections")
                }
                Text(
                    text = state.maxTransfers?.toString() ?: "Unlimited",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(72.dp), textAlign = TextAlign.Center
                )
                IconButton(onClick = viewModel::incrementMaxTransfers) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "More connections")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = viewModel::search,
            enabled = state.fromPlace != null && state.toPlace != null && !state.isLoading && !state.nearbyRoutesState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Search") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = viewModel::findNearbyRoutes,
            enabled = state.fromPlace != null && state.toPlace != null && !state.isLoading && !state.nearbyRoutesState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Drop me off") }
        Spacer(modifier = Modifier.height(16.dp))
        when {
            state.showingNearbyRoutes -> NearbyRoutesResults(state = state.nearbyRoutesState, onRouteSelected = viewModel::selectNearbyRoute)
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            state.error != null -> Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            state.searched && state.itineraries.isEmpty() -> Text("No routes found")
            else -> Column {
                state.notice?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(bottom = 8.dp))
                }
                ResultsList(itineraries = state.itineraries)
            }
        }
    }
}
```

- [ ] **Step 3: Replace `MainActivity.kt`**

```kotlin
package one.brj.bikebus

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import one.brj.bikebus.ui.TripScreen
import one.brj.bikebus.ui.theme.BikeBusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(Unit) {
                permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            BikeBusTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TripScreen()
                }
            }
        }
    }
}
```

- [ ] **Step 4: Verify the build**

```bash
cd /c/Users/bru/spare-source/otp-server-ui/android
export OTP_AUTH_TOKEN=placeholder-for-build-check
export GOOGLE_PLACES_API_KEY=placeholder-for-build-check
export OTP_SERVER_BASE_URL=http://localhost:8081
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/one/brj/bikebus/ui android/app/src/main/java/one/brj/bikebus/MainActivity.kt
git commit -m "Port the Compose UI layer and switch MainActivity to it"
```

---

### Task 7: Docs and final handoff

**Files:**
- Modify: `android/README.md`

- [ ] **Step 1: Rewrite `android/README.md`**

Replace its "Building" section's env var instructions and "Project layout" section to describe the new architecture (three env vars instead of one, native Compose screens instead of a WebView, `TripViewModel` calling `OtpServerApi` instead of a WebView token-injecting client), keeping the existing "Signing key decision" and "Known deviations" sections (still accurate — same app, same keystore, same toolchain-version history). Add a line noting this app no longer uses the backend's `/geocode` endpoint and instead calls Google Places directly, with its own `GOOGLE_PLACES_API_KEY`.

- [ ] **Step 2: Final build verification**

```bash
cd /c/Users/bru/spare-source/otp-server-ui/android
export OTP_AUTH_TOKEN="<real otp_auth_token value>"
export GOOGLE_PLACES_API_KEY="<real Google Places API key>"
export OTP_SERVER_BASE_URL="https://otp.brj.one"
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Expected: both `BUILD SUCCESSFUL`. This is the full test suite from Tasks 2/3/5 running together, plus a real debug APK built against the real backend and Places key. No emulator/device run by Claude — hand the APK off here for the user to install and test on their own device, per their explicit instruction.

- [ ] **Step 3: Commit**

```bash
git add android/README.md
git commit -m "Update android/README.md for the native rewrite"
```

---

### Task 8: Strip frontend-serving from the backend image

**Files:**
- Modify: `backend/src/main/kotlin/one/otpserverui/Main.kt`
- Delete: `backend/src/test/kotlin/one/otpserverui/StaticFilesTest.kt`
- Modify: `Dockerfile`
- Modify: `.github/workflows/publish-images.yml`
- Modify: `terraform-oci/README.md`

**Interfaces:** None — this task is independent of Tasks 1-7 (different directories: `backend/`, root `Dockerfile`, `.github/`, `terraform-oci/`, never `android/`) and can be implemented in any order relative to them.

- [ ] **Step 1: Remove the static-file-serving block from `Main.kt`**

Remove the `staticFiles` import (`io.ktor.server.http.content.staticFiles`), the `staticDir` parameter from `Application.module(...)`, and the `if (Files.isDirectory(staticDir)) { staticFiles("/", staticDir.toFile()) }` block from the `routing { }` block. The resulting `module` function signature and routing block:

```kotlin
fun Application.module(
    engine: RoutingEngine,
    hubs: List<TransitHub>,
    geocodeClient: GeocodeClient,
) {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<NumberFormatException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, SearchErrorResponse("invalid_request"))
        }
        exception<IllegalStateException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, SearchErrorResponse("invalid_request"))
        }
        exception<DateTimeParseException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, SearchErrorResponse("invalid_request"))
        }
        exception<Throwable> { call, _ ->
            call.respond(HttpStatusCode.InternalServerError, SearchErrorResponse("internal_error"))
        }
    }
    routing {
        get("/health") { call.respondText("ok") }
        searchRoute(engine, hubs)
        dropMeOffRoutes(engine, hubs)
        geocodeRoute(geocodeClient)
    }
}
```

Also remove the now-unused `java.nio.file.Files` import if nothing else in the file uses `Files` (check: `loadGraphOrFail` at the top of the file calls `Files.exists(path)`, so the import stays — only the `staticFiles` import and the `staticDir` parameter/block are removed).

- [ ] **Step 2: Delete the test for the removed feature**

```bash
cd /c/Users/bru/spare-source/otp-server-ui
rm backend/src/test/kotlin/one/otpserverui/StaticFilesTest.kt
```

- [ ] **Step 3: Run the backend test suite**

Run: `./gradlew :backend:test`
Expected: `BUILD SUCCESSFUL` — confirms removing the parameter didn't break any other caller of `module(...)` (grep for other call sites first: `grep -rn "fun Application.module\|application { module(" backend/src` should show only `Main.kt`'s own `main()` and `StaticFilesTest.kt`, the latter just deleted).

- [ ] **Step 4: Trim the Dockerfile**

Remove these two lines from the root `Dockerfile`'s runtime stage:

```dockerfile
COPY --from=build /src/frontend/dist /app/frontend-dist
ENV FRONTEND_DIST_PATH=/app/frontend-dist
```

The resulting file:

```dockerfile
# --- Build stage ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :backend:installDist --no-daemon

# --- Runtime stage ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/backend/build/install/backend /app
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/bin/backend"]
```

- [ ] **Step 5: Trim the CI workflow**

Remove the `oven-sh/setup-bun@v2` and `bun install && bun run build` steps from the `backend` job in `.github/workflows/publish-images.yml`:

```yaml
name: Publish images

on:
  push:
    branches: [main]

permissions:
  contents: read
  packages: write

jobs:
  backend:
    runs-on: ubuntu-24.04-arm
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v6
        with:
          context: .
          file: Dockerfile
          push: true
          tags: ghcr.io/${{ github.repository_owner }}/otp-server-ui:latest

  graph-builder:
    runs-on: ubuntu-24.04-arm
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v6
        with:
          context: .
          file: graph-builder/Dockerfile
          push: true
          tags: ghcr.io/${{ github.repository_owner }}/otp-graph-builder:latest
```

(The `graph-builder` job is shown unchanged, for context — don't touch it.)

- [ ] **Step 6: Update `terraform-oci/README.md`'s opening line**

Change:

```markdown
Deploys this project's own backend (API + frontend) behind a
custom-built, rate-limited, token-gated Caddy, on an Always Free
`VM.Standard.A1.Flex` instance.
```

to:

```markdown
Deploys this project's own backend (a pure API service — see
`docs/superpowers/specs/2026-10-07-android-native-rewrite-design.md` for
why the frontend is no longer served) behind a custom-built, rate-limited,
token-gated Caddy, on an Always Free `VM.Standard.A1.Flex` instance.
```

- [ ] **Step 7: Verify the real CI build still succeeds**

Push this task's commit (see Step 8) and confirm via `gh run watch` (or the Actions tab) that the `backend` job still builds and pushes `ghcr.io/<owner>/otp-server-ui:latest` successfully without the Bun steps. This is the same real-CI-as-verification approach already used for every other Dockerfile change in this project — no local emulated Docker build needed given CI already proves it on real hardware.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin/one/otpserverui/Main.kt Dockerfile .github/workflows/publish-images.yml terraform-oci/README.md
git rm backend/src/test/kotlin/one/otpserverui/StaticFilesTest.kt
git commit -m "Strip frontend-serving from the backend: API-only now that the Android app is native"
```
