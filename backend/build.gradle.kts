plugins {
    kotlin("jvm") version "2.3.21"
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
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("org.slf4j:slf4j-simple:2.0.16")

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
