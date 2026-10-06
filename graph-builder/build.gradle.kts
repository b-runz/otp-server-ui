plugins {
    application
}

repositories {
    maven("https://cache-redirector.jetbrains.com/maven-central")
    mavenCentral()
}

val otpServerUi = ".."

dependencies {
    implementation(files("$otpServerUi/otp-utils/build/libs/otp-utils.jar"))
    implementation(files("$otpServerUi/otp-domain-core/build/libs/otp-domain-core.jar"))
    implementation(files("$otpServerUi/otp-astar/build/libs/otp-astar.jar"))
    implementation(files("$otpServerUi/otp-street/build/libs/otp-street.jar"))
    implementation(files("$otpServerUi/otp-raptor/build/libs/otp-raptor.jar"))
    implementation(files("$otpServerUi/otp-routing/build/libs/otp-routing.jar"))

    implementation("org.locationtech.jts:jts-core:1.20.0")
    implementation("net.sf.trove4j:trove4j:3.0.3")
    implementation("com.google.guava:guava:33.7.1-jre")
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")
    implementation("org.slf4j:slf4j-api:2.0.19")
    implementation("org.slf4j:slf4j-simple:2.0.19")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.22")
    implementation("com.esotericsoftware:kryo:5.6.2")
    implementation("com.conveyal:kryo-tools:1.5.0")
    implementation("de.javakaffee:kryo-serializers:0.45")

    // graph-building-slice-specific dependencies (real upstream coordinates, confirmed via
    // application/pom.xml in the same commit -- see docs/graph-build.md)
    implementation("com.beust:jcommander:1.82")
    implementation("net.sourceforge.javacsv:javacsv:2.0")
    implementation("ch.poole:OpeningHoursParser:0.29.0")
    implementation("org.openstreetmap.pbf:osmpbf:1.6.1")
    implementation("org.onebusaway:onebusaway-gtfs:14.2.2")
    implementation("org.apache.commons:commons-collections4:4.4")
    implementation("org.apache.commons:commons-text:1.13.0")
}

application {
    mainClass.set("org.opentripplanner.graphbuilder.throwaway.BuildFixtureGraph")
}

tasks.named<JavaExec>("run") {
    // Full-Denmark build (not the small clipped fixture) needs real heap headroom.
    jvmArgs = listOf("-Xmx8g")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
