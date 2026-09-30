plugins {
    `java-library`
}

tasks.withType<JavaCompile> {
    options.release.set(17)
    options.encoding = "UTF-8"
}

dependencies {
    implementation("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.truth:truth:1.4.5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
