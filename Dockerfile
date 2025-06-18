# ===================================================================
# Build stage: GraalVM 23 + Native Image with Maven Wrapper
# ===================================================================
FROM ghcr.io/graalvm/native-image-community:21 AS build

# Configuração do diretório e ambiente
ARG SPRING_ACTIVE_PROFILE
ENV SPRING_PROFILES_ACTIVE=${SPRING_ACTIVE_PROFILE}

# Copiar código fonte e configurações Maven
COPY mvnw ./mvnw
COPY .mvn ./.mvn
COPY pom.xml ./pom.xml
RUN chmod +x mvnw && ./mvnw dependency:go-offline

COPY src ./src
# Compilar aplicação e gerar executável nativo
RUN ./mvnw -Pnative clean native:compile -DskipTests -Dnative-image.options="--strict-image-heap --gc=G1 -H:MaxHeapSizePercent=25 -march=native"

# ===================================================================
# Runtime stage: minimal distroless with C/C++ runtimes
# ===================================================================
FROM gcr.io/distroless/cc-debian12:nonroot AS runtime

# Variáveis de ambiente do OpenTelemetry (ajuste conforme necessidade)
ARG SPRING_ACTIVE_PROFILE
ARG SERVICE_PORT=8080
ENV SPRING_PROFILES_ACTIVE=${SPRING_ACTIVE_PROFILE}
ENV OTEL_SERVICE_NAME=${OTEL_SERVICE_NAME}
ENV OTEL_COLLECTOR_ENDPOINT=${OTEL_COLLECTOR_ENDPOINT}

# Diretório de trabalho
WORKDIR /app

# Copiar executável nativo do estágio de build
COPY --from=build /app/target/access-management /app/access-management

HEALTHCHECK --interval=30s --timeout=5s CMD [ "curl", "-f", "http://localhost:${SERVICE_PORT}/actuator/health" ]

# Expor porta e executar aplicação
EXPOSE ${SERVICE_PORT}
CMD ["/app/access-management"]
