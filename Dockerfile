# syntax=docker/dockerfile:1

# ---- Stage 1: build the Spring Boot executable jar ----
# Full JDK 21 toolchain. We build with the committed Gradle wrapper so the image
# build matches local and CI builds exactly (Gradle 9.6.1, Spring Boot 4.1.0).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the wrapper and build scripts first so this layer caches independently of
# source-only changes.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

# Copy the application sources.
COPY src ./src

# Produce the boot jar. Tests run in CI (ci.yml), not in the image build, so we
# skip them here: this keeps image builds fast and requires no RIOT_API_KEY.
RUN chmod +x ./gradlew && ./gradlew --no-daemon clean bootJar -x test

# ---- Stage 2: slim runtime ----
# JRE-only base — no compiler or build tooling ships in the final image.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as an unprivileged user rather than root.
RUN useradd --system --uid 10001 --shell /usr/sbin/nologin appuser
USER appuser

# Copy only the built artifact from the build stage.
COPY --from=build /workspace/build/libs/*.jar app.jar

# Required runtime configuration (12-factor; no secrets are baked into the image):
#   RIOT_API_KEY       Riot Games API key      -> bound to riot.apiKey
#   ANTHROPIC_API_KEY  Anthropic API key       -> Spring AI model integration
# Supply them at run time, e.g.:
#   docker run --rm -p 8080:8080 \
#     -e RIOT_API_KEY=RGAPI-xxxx \
#     -e ANTHROPIC_API_KEY=sk-ant-xxxx \
#     ghcr.io/<owner>/riot-api-mcp-server:latest

# Spring Boot serves the MCP SSE endpoint (/mcp/messages) on 8080 by default.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
