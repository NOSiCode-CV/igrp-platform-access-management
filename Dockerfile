# ===================================================================
# STRATEGY 2: RELIABLE PRE-BUILT MAVEN + GRAALVM NATIVE IMAGE
# Using vegardit/graalvm-maven - guaranteed to work!
# ===================================================================

# Build stage - Pre-built Maven + GraalVM image
FROM --platform=$BUILDPLATFORM vegardit/graalvm-maven:latest-java21 AS build

# Build arguments with defaults
ARG TARGETPLATFORM
ARG BUILDPLATFORM
ARG OTEL_TRACES_EXPORTER=none
ARG OTEL_METRICS_EXPORTER=none
ARG OTEL_LOGS_EXPORTER=none
ARG OTEL_COLLECTOR_ENDPOINT
ARG OTEL_SERVICE_NAME
ARG OTEL_ENABLED=false
ARG SERVICE_PROFILE=default
ARG SPRING_ACTIVE_PROFILE=default
ARG SERVICE_PORT=8080
ARG APP_NAME=app

# Environment setup
ENV SPRING_PROFILES_ACTIVE=${SPRING_ACTIVE_PROFILE}

# Verify pre-installed tools (this image comes with everything)
RUN echo "=== Verifying Tools ===" && \
    mvn --version && \
    java --version && \
    native-image --version && \
    echo "=== All tools verified ==="

WORKDIR /app

# Copy dependency files first for optimal Docker layer caching
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build native executable - simple and reliable
RUN echo "=== Starting native compilation ===" && \
    mvn -Pnative clean native:compile -DskipTests && \
    echo "=== Native compilation completed ==="

# Find and copy the native executable
RUN echo "=== Finding executable ===" && \
    ls -la target/ && \
    find target -type f -executable -not -name "*.jar" -not -name "*.so" | head -1 > /tmp/executable_path && \
    if [ -s /tmp/executable_path ]; then \
        EXECUTABLE_PATH=$(cat /tmp/executable_path) && \
        echo "Found executable: $EXECUTABLE_PATH" && \
        cp "$EXECUTABLE_PATH" target/${APP_NAME} && \
        chmod +x target/${APP_NAME} && \
        ls -lh target/${APP_NAME}; \
    else \
        echo "No native executable found, checking target contents:" && \
        ls -la target/ && \
        exit 1; \
    fi

# ===================================================================
# Runtime stage - Ultra-minimal distroless image
# ===================================================================
FROM gcr.io/distroless/base-debian12:nonroot

# Runtime environment variables
ARG OTEL_SERVICE_NAME
ARG OTEL_COLLECTOR_ENDPOINT  
ARG OTEL_ENABLED
ARG SPRING_ACTIVE_PROFILE
ARG SERVICE_PORT
ARG APP_NAME

ENV OTEL_SERVICE_NAME=${OTEL_SERVICE_NAME}
ENV OTEL_COLLECTOR_ENDPOINT=${OTEL_COLLECTOR_ENDPOINT}
ENV OTEL_ENABLED=${OTEL_ENABLED}
ENV SPRING_PROFILES_ACTIVE=${SPRING_ACTIVE_PROFILE}
ENV SERVER_PORT=${SERVICE_PORT}

# Set working directory
WORKDIR /app

# Copy native executable with proper ownership
COPY --from=build --chown=nonroot:nonroot /app/target/${APP_NAME} ./app

# Expose application port
EXPOSE ${SERVICE_PORT}

# Health check optimized for native apps
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD ["/app/app", "--help"] || exit 1

# Run as non-root user for security
USER nonroot

# Execute native binary
ENTRYPOINT ["./app"]
