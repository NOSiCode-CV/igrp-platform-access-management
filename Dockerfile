# Estágio de construção com GraalVM e Maven para gerar a imagem nativa
FROM --platform=$BUILDPLATFORM ghcr.io/graalvm/native-image-community:21-ol9 AS build

# Build arguments with defaults
ARG TARGETPLATFORM
ARG BUILDPLATFORM
# Argumentos de build (OpenTelemetry e perfil)
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

ENV APP_HOME=/app
ENV SPRING_PROFILES_ACTIVE=${SPRING_ACTIVE_PROFILE}

# Install Maven efficiently with cleanup
RUN microdnf install -y maven findutils && microdnf clean all

WORKDIR $APP_HOME

# Copy dependency files first for optimal Docker layer caching
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build optimized native executable with all performance flags
RUN mvn -Pnative clean native:compile -DskipTests \
    -Dspring.native.remove-unused-autoconfig=true \
    -Dspring.native.remove-yaml-support=true \
    -Dspring-boot.native-image.args="--no-fallback,--install-exit-handlers,--enable-preview,--gc=G1,-H:+ReportExceptionStackTraces,-H:+PrintGCDetails,-J-Dspring.spel.ignore=true,-J-Dspring.native.remove-unused-autoconfig=true" && \
    # Find and rename the native executable (handles dynamic artifact names)
    find target -name "*" -type f -executable -not -name "*.so" | head -1 | xargs -I {} cp {} target/${APP_NAME} && \
    # Strip debug symbols for smaller size
    strip target/${APP_NAME} 2>/dev/null || true && \
    # Verify and display final binary info
    ls -lh target/${APP_NAME} && \
    file target/${APP_NAME}

# ===================================================================
# Runtime stage - Ultra-minimal distroless image
# ===================================================================
FROM gcr.io/distroless/base-debian12:nonroot

# Runtime environment variables from build args
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

# Health check for native executable (lightweight)
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD ["/app/app", "--health"] || exit 1

# Run as non-root user for security
USER nonroot

# Execute native binary with optimal settings
ENTRYPOINT ["./app"]
