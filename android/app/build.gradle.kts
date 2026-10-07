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

    testOptions {
        unitTests.isReturnDefaultValues = true
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
