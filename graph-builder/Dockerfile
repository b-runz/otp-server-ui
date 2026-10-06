# graph-builder's own build.gradle.kts sets java.toolchain.languageVersion = 26, but Gradle
# 8.10.2 itself cannot launch on a JDK as new as 26 (confirmed directly: basing the whole build
# stage on eclipse-temurin:26-jdk makes Gradle's own launcher fail before it ever reaches a build
# script). Gradle itself runs on 17 (same as every other module in this repo); JDK 26 is provided
# separately, purely for the toolchain :graph-builder:installDist resolves against.
FROM eclipse-temurin:26-jdk AS jdk26

FROM eclipse-temurin:17-jdk AS build
COPY --from=jdk26 /opt/java/openjdk /opt/jdk-26
WORKDIR /src
COPY . .
RUN ./gradlew :otp-utils:jar :otp-domain-core:jar :otp-astar:jar :otp-street:jar :otp-raptor:jar :otp-routing:jar --no-daemon

# Points graph-builder's own toolchain resolution at the real JDK 26 install above. The committed
# graph-builder/gradle.properties' own installations.paths is a Windows-only local dev path, not
# meaningful in a container -- removed here (in-container copy only, never written back to the
# host file) to eliminate any ambiguity about which properties file Gradle would otherwise prefer,
# and the correct path appended to the root gradle.properties instead.
RUN rm -f graph-builder/gradle.properties
RUN echo "org.gradle.java.installations.paths=/opt/jdk-26" >> gradle.properties \
    && echo "org.gradle.java.installations.auto-detect=false" >> gradle.properties
RUN ./gradlew :graph-builder:installDist --no-daemon

# --- Runtime stage ---
FROM eclipse-temurin:26-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/graph-builder/build/install/graph-builder /app
COPY graph-builder/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh
ENTRYPOINT ["/app/entrypoint.sh"]
