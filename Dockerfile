# ===================================================================
# Build stage: GraalVM 23 + Native Image with Maven Wrapper
# ===================================================================
FROM --platform=$BUILDPLATFORM ghcr.io/graalvm/native-image-community:23 AS build

# Argumentos de build (OpenTelemetry e perfil)
ARG OTEL_TRACES_EXPORTER
ARG OTEL_METRICS_EXPORTER
ARG OTEL_LOGS_EXPORTER
ARG OTEL_COLLECTOR_ENDPOINT
ARG OTEL_SERVICE_NAME
ARG OTEL_ENABLED
ARG SERVICE_PROFILE

# Configuração do diretório e ambiente
ENV SPRING_PROFILES_ACTIVE=${SPRING_ACTIVE_PROFILE}

# Copiar código fonte e configurações Maven
COPY mvnw ./mvnw
COPY .mvn ./.mvn
COPY src ./src
COPY pom.xml ./pom.xml
RUN chmod +x mvnw

# Compilar aplicação e gerar executável nativo
RUN ./mvnw -Pnative clean native:compile -DskipTests -Dnative-image.options="--strict-image-heap --gc=G1 -H:MaxHeapSizePercent=25 -march=native"

# ===================================================================
# Runtime stage: minimal distroless with C/C++ runtimes
# ===================================================================
FROM --platform=$BUILDPLATFORM gcr.io/distroless/cc-debian12:nonroot AS runtime

# Variáveis de ambiente do OpenTelemetry (ajuste conforme necessidade)
ENV OTEL_SERVICE_NAME=${OTEL_SERVICE_NAME}
ENV OTEL_COLLECTOR_ENDPOINT=${OTEL_COLLECTOR_ENDPOINT}
ENV SPRING_ACTIVE_PROFILE=${SPRING_ACTIVE_PROFILE}

# Diretório de trabalho
WORKDIR /app


# Copiar executável nativo do estágio de build
COPY --from=build /app/target/access-management /app/access-management


# Expor porta e executar aplicação
EXPOSE ${SERVICE_PORT}
CMD ["/app/access-management"]
