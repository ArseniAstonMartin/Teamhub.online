# syntax=docker/dockerfile:1.7
# Multi-stage build for Coolify/production (Java 21, no Nixpacks).
# Build context: backend/ (set Coolify root directory to backend, build pack Dockerfile).

FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Gradle metadata first — cached until build files change.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.lockfile ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Warm Gradle wrapper/dependency cache (persisted by BuildKit cache mount).
RUN --mount=type=cache,id=teamhub-gradle,target=/root/.gradle \
    ./gradlew --no-daemon --version

COPY src ./src

RUN --mount=type=cache,id=teamhub-gradle,target=/root/.gradle \
    ./gradlew --no-daemon bootJar -x test -q

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system teamhub && useradd --system --gid teamhub teamhub

COPY --from=builder /app/build/libs/*.jar /app/app.jar

USER teamhub

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
