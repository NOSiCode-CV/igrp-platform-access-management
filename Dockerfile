# Estágio de construção com GraalVM e Maven para gerar a imagem nativa
FROM ghcr.io/graalvm/native-image-community:21-ol9 AS build

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
RUN microdnf install -y maven 

# Compilar aplicação e gerar executável nativo
RUN mvn -Pnative clean native:compile -DskipTests
RUN ls -l /app/target

# --- Estágio de Runtime (Imagem Minimalista) ---
FROM ubuntu:22.04

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
