# --- Build stage ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :backend:installDist --no-daemon

# --- Runtime stage ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/backend/build/install/backend /app
COPY --from=build /src/frontend/dist /app/frontend-dist
ENV FRONTEND_DIST_PATH=/app/frontend-dist
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/bin/backend"]
