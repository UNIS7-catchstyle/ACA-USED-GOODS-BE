# ---- Build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Dependency layer: only these files invalidate the cache below, so a source-only
# change reuses the already-resolved Gradle dependency cache from this layer.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
# gradlew is checked out with CRLF line endings on Windows dev machines, which
# breaks its shebang inside this Linux image ("gradlew: not found") — normalize
# to LF before making it executable.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src
# Tests are expected to have already run in CI before this image is built — skipped
# here so image builds stay fast and don't need a database available at build time.
RUN ./gradlew bootJar -x test --no-daemon && cp build/libs/*.jar app.jar

# ---- Run stage ----
# alpine keeps the run image well under the full Ubuntu-based "17-jre" tag (~730MB
# vs ~250MB here) — the app has no native deps that need glibc.
FROM eclipse-temurin:17-jre-alpine AS run
WORKDIR /app

RUN addgroup -S spring && adduser -S -G spring spring
COPY --from=build /app/app.jar app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8080

# Lets the deploy target (Railway env var, docker run -e, etc.) inject JVM flags
# such as -Xmx without rebuilding the image. exec replaces the shell with the java
# process so it receives SIGTERM directly and Spring Boot's graceful shutdown runs.
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
