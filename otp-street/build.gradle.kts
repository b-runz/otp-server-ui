plugins {
    `java-library`
    `java-test-fixtures`
}

tasks.withType<JavaCompile> {
    options.release.set(17)
    options.encoding = "UTF-8"
}

dependencies {
    implementation(project(":otp-domain-core"))
    implementation(project(":otp-astar"))
    implementation(project(":otp-utils"))
    implementation("org.locationtech.jts:jts-core:1.20.0")
    implementation("net.sf.trove4j:trove4j:3.0.3")
    implementation("com.google.guava:guava:33.7.1-jre")
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")
    implementation("org.slf4j:slf4j-api:2.0.19")
    implementation("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.truth:truth:1.4.5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testFixturesImplementation("org.locationtech.jts:jts-core:1.20.0")
    testFixturesImplementation("com.google.code.findbugs:jsr305:3.0.2")
    testFixturesImplementation(platform("org.junit:junit-bom:6.1.2"))
    testFixturesImplementation("org.junit.jupiter:junit-jupiter-api")
    testFixturesApi(project(":otp-astar"))
    testFixturesApi(project(":otp-domain-core"))
}

tasks.test {
    useJUnitPlatform()
}
