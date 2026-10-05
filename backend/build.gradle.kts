plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    application
}

repositories {
    maven("https://cache-redirector.jetbrains.com/maven-central")
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-netty:3.0.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-server-status-pages:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    // GooglePlacesGeocodeClient's outbound HTTP calls to the real Places API (GeocodeRoute.kt).
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-cio:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    // NearbyStops.kt builds its own bounding-box Envelope (same as OTP's own
    // StraightLineNearbyStopFinder) -- otp-street only declares jts-core as `implementation`,
    // so it isn't visible transitively here; same version pinned there.
    implementation("org.locationtech.jts:jts-core:1.20.0")

    implementation(project(":otp-routing"))
    implementation(project(":otp-street"))
    implementation(project(":otp-domain-core"))
    implementation(project(":otp-raptor"))
    implementation(project(":otp-astar"))
    implementation(project(":otp-utils"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("io.ktor:ktor-server-test-host:3.0.3")
}

application {
    mainClass.set("one.otpserverui.MainKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
