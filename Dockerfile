# ===================================================================
# Build stage: GraalVM 21 + Native Image with Maven Wrapper
# ===================================================================
FROM ghcr.io/graalvm/native-image-community:21-muslib-ol9 AS build

# Diretório de trabalho
WORKDIR /app

# Configuração do diretório e ambiente
ARG SPRING_ACTIVE_PROFILE
ENV SPRING_PROFILES_ACTIVE=${SPRING_ACTIVE_PROFILE}

# Copiar código fonte e configurações Maven
COPY mvnw ./mvnw
COPY .mvn ./.mvn
COPY pom.xml ./pom.xml
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src
# Compilar aplicação e gerar executável nativo completo e statico
RUN ./mvnw -Pnative clean package -DskipTests -Dnative-image.options="--static --libc=musl -O2 --no-fallback"

# ===================================================================
# Runtime stage: minimal static binary
# ===================================================================
FROM gcr.io/distroless/static:nonroot

# Diretório de trabalho
WORKDIR /app

# Copy the native executable (artifactId assumed "access-management")
COPY --from=build /app/target/access-management /app/access-management

# Expor porta e executar aplicação
EXPOSE 8080
CMD ["./app/access-management"]
