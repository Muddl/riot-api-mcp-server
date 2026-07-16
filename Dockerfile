# syntax=docker/dockerfile:1

# ---- Stage 1: build the Spring Boot executable jar ----
# Full JDK 21 toolchain. We build with the committed Gradle wrapper so the image
# build matches local and CI builds exactly (Gradle 9.6.1, Spring Boot 4.1.0).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Which server module to package. One image per server; the monorepo builds them all.
ARG SERVER_MODULE=lol-mcp-server

# Copy the wrapper and build scripts first so this layer caches independently of
# source-only changes. buildSrc holds the shared convention plugin every module applies.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
COPY buildSrc ./buildSrc

# Libraries the server modules depend on, then the server sources.
COPY riot-api-core ./riot-api-core
COPY riot-account-core ./riot-account-core
COPY ${SERVER_MODULE} ./${SERVER_MODULE}

# Produce the boot jar. Tests run in CI (ci.yml), not in the image build, so we
# skip them here: this keeps image builds fast and requires no RIOT_API_KEY.
RUN chmod +x ./gradlew && ./gradlew --no-daemon :${SERVER_MODULE}:bootJar -x test

# ---- Stage 2: slim runtime ----
# JRE-only base — no compiler or build tooling ships in the final image.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

ARG SERVER_MODULE=lol-mcp-server

# Provenance: which library versions are baked into this image. The libraries are consumed by
# project reference, so nothing else records them — without these labels, "core 0.1.0 has a flaw,
# which images embed it?" means rebuilding and guessing. See ADR-0010.
# Defaults are `dev` so a local `docker build` with no args still succeeds; CI passes real values.
ARG SERVER_VERSION=dev
ARG RIOT_API_CORE_VERSION=dev
ARG RIOT_ACCOUNT_CORE_VERSION=dev

LABEL org.opencontainers.image.title="${SERVER_MODULE}" \
      org.opencontainers.image.version="${SERVER_VERSION}" \
      org.opencontainers.image.source="https://github.com/Muddl/riot-api-mcp-server" \
      org.opencontainers.image.licenses="MIT" \
      com.muddl.riot.riot-api-core.version="${RIOT_API_CORE_VERSION}" \
      com.muddl.riot.riot-account-core.version="${RIOT_ACCOUNT_CORE_VERSION}"

# Run as an unprivileged user rather than root.
RUN useradd --system --uid 10001 --shell /usr/sbin/nologin appuser
USER appuser

# Copy only the built artifact from the build stage.
COPY --from=build /workspace/${SERVER_MODULE}/build/libs/*.jar app.jar

# Required runtime configuration (12-factor; no secrets are baked into the image):
#   RIOT_API_KEY  Riot Games API key -> bound to riot.apiKey
# Supply it at run time, e.g.:
#   docker run --rm -p 8080:8080 \
#     -e RIOT_API_KEY=RGAPI-xxxx \
#     ghcr.io/<owner>/lol-mcp-server:latest

# The container runs the SSE profile: stdio transport is for local clients that spawn the
# process directly, which is not how a container is consumed. SSE serves /mcp/messages on 8080.
ENV SPRING_PROFILES_ACTIVE=sse
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
