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
# Compilar aplicação e gerar executável nativo completo e statico
RUN ./mvnw --no-transfer-progress clean package -Dpackaging=native-image -DbuildArgs="--static --libc=musl,-Os"

# ===================================================================
# Runtime stage: minimal distroless with C/C++ runtimes
# ===================================================================
FROM scratch

# Variáveis de ambiente do OpenTelemetry (ajuste conforme necessidade)
ARG SPRING_ACTIVE_PROFILE
ARG SERVICE_PORT=8080
ENV SPRING_PROFILES_ACTIVE=${SPRING_ACTIVE_PROFILE}

# Diretório de trabalho
WORKDIR /app

# Copiar executável nativo do estágio de build
COPY --from=build /app/target/access-management /app/access-management

# Expor porta e executar aplicação
EXPOSE ${SERVICE_PORT}
CMD ["/app/access-management"]
