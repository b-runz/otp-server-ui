# --- Build stage ---
FROM eclipse-temurin:26-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :otp-utils:jar :otp-domain-core:jar :otp-astar:jar :otp-street:jar :otp-raptor:jar :otp-routing:jar --no-daemon
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
