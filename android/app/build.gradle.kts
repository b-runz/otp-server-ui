import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9+ has built-in Kotlin support -- no separate `kotlin("android")` plugin
    // (applying it fails the build: "no longer required since AGP 9.0").
    id("com.android.application")
}

val otpAuthToken: String = System.getenv("OTP_AUTH_TOKEN")
    ?: throw GradleException(
        "OTP_AUTH_TOKEN is not set. Export it before building: " +
            "export OTP_AUTH_TOKEN=<the same value as terraform-oci's otp_auth_token>"
    )

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
    }

    buildFeatures {
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

    // Task 1: this app deliberately replaces the existing `bikebus` install under the
    // same package id (one.brj.bikebus), so it needs to be signable with the *same* key
    // bru already uses for his personal apps -- not a new, one-off keystore (see
    // android/README.md for the full decision). Real values are never hardcoded here:
    // they come from env vars, or from a local, gitignored key.properties bru fills in
    // himself. Left blank/null is fine for every build this plan actually runs
    // (assembleDebug never touches the "release" signingConfig); only a real
    // assembleRelease needs these populated.
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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // AppCompatActivity (MainActivity's superclass) requires a Theme.AppCompat
    // descendant (see res/values/themes.xml) and this dependency.
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Supplies the OnBackPressedDispatcher.addCallback(owner) { ... } extension
    // MainActivity uses for in-WebView back navigation.
    implementation("androidx.activity:activity-ktx:1.9.3")
}
