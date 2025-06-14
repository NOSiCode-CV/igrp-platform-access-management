# =================================================================== 
# Build stage using vegardit/graalvm-maven (includes GraalVM + Maven)
# ===================================================================
FROM --platform=$BUILDPLATFORM vegardit/graalvm-maven:latest-java21 AS build

# Argumentos de build (OpenTelemetry e perfil)
ARG OTEL_TRACES_EXPORTER
ARG OTEL_METRICS_EXPORTER
ARG OTEL_LOGS_EXPORTER
ARG OTEL_COLLECTOR_ENDPOINT
ARG OTEL_SERVICE_NAME
ARG OTEL_ENABLED
ARG SERVICE_PROFILE

# Configuração do diretório e ambiente
ENV APP_HOME=/app
ENV SPRING_PROFILES_ACTIVE=${SPRING_ACTIVE_PROFILE}

# Copiar código fonte e configurações Maven
COPY src $APP_HOME/src
COPY pom.xml $APP_HOME/pom.xml
WORKDIR $APP_HOME

# Instalar Maven (caso não esteja presente na imagem)
# RUN microdnf install -y maven 

# Compilar aplicação e gerar executável nativo
RUN mvn -Pnative clean native:compile -DskipTests
RUN ls -l /app/target

# ===================================================================
# Runtime stage - Ultra-minimal distroless image
# ===================================================================
FROM gcr.io/distroless/base-debian12:nonroot AS runtime

# Variáveis de ambiente do OpenTelemetry (ajuste conforme necessidade)
ENV OTEL_SERVICE_NAME=${OTEL_SERVICE_NAME}
ENV OTEL_COLLECTOR_ENDPOINT=${OTEL_COLLECTOR_ENDPOINT}
ENV SPRING_ACTIVE_PROFILE=${SPRING_ACTIVE_PROFILE}

# Diretório de trabalho
WORKDIR /app



# Copiar executável nativo do estágio de build
COPY --from=build /app/target/{{baseConfig.artifact}} /app/{{baseConfig.artifact}}


# Expor porta e executar aplicação
EXPOSE ${SERVICE_PORT}
CMD ["/app/{{baseConfig.artifact}}"]
