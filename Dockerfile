# syntax=docker/dockerfile:1
#
# Multi-stage build for the Personal Finance Manager API.
#
# Stage 1 (build): compiles the application with Maven. The pom.xml is copied and
# `dependency:go-offline` is run BEFORE the source tree is copied, so that the dependency layer is
# cached by Docker and only re-downloaded when pom.xml actually changes.
#
# Stage 2 (runtime): a slim JRE-only image that runs the fat jar as a non-root user. No build
# tooling, no source, no Maven cache end up in the final image.

# ---------------------------------------------------------------------------
# Stage 1: build
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependency layer: copy only the POM first so Docker can cache the downloaded dependencies
# across rebuilds that only change application source.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Now copy the source and build the application, skipping tests (tests run in CI, not in the
# image build).
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------------------------------------------------------------------------
# Stage 2: runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime

# Run as a dedicated, unprivileged user rather than root.
RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown -R spring:spring /app
USER spring

# Render (and most PaaS targets) inject PORT at runtime; application.yml already reads
# server.port: ${PORT:8080}. Keep the documented default for local `docker run`.
ENV PORT=8080
EXPOSE 8080

# Memory-safe defaults for a 512 MB container: cap the JVM heap as a percentage of the
# container's memory limit instead of a fixed -Xmx, and use the low-overhead serial collector,
# which is appropriate for a small, single-core free-tier instance.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"

# eclipse-temurin:21-jre (Debian/Ubuntu-based) does not ship curl or wget, and installing one
# just for a healthcheck would add a package-manager layer to a minimal runtime image. Render's
# own `healthCheckPath` (see render.yaml) already polls /actuator/health over HTTP, so a Docker
# HEALTHCHECK is intentionally omitted rather than shipped broken.

ENTRYPOINT ["java", "-jar", "app.jar"]
