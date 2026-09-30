plugins {
    `java-library`
}

sourceSets {
    test {
        java {
            srcDir("src/test-fixtures/java")
        }
    }
}

tasks.withType<JavaCompile> {
    options.release.set(17)
    options.encoding = "UTF-8"
}

dependencies {
    implementation(project(":otp-utils"))
    implementation("net.sf.trove4j:trove4j:3.0.3")
    implementation("org.slf4j:slf4j-api:2.0.19")
    implementation("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
