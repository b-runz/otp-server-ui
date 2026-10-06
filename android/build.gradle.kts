plugins {
    // AGP 9+ has built-in Kotlin support; the separate `org.jetbrains.kotlin.android`
    // plugin is no longer required (confirmed the hard way -- see android/README.md's
    // "Known deviations" section).
    id("com.android.application") version "9.4.0" apply false
}
