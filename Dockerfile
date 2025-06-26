# ===================================================================
# Build stage: GraalVM 21 + Native Image with Maven Wrapper
# ===================================================================
FROM ghcr.io/graalvm/native-image-community:23-muslib-ol9 AS build

# Diretório de trabalho
WORKDIR /app

# Configuração do diretório e ambiente
ARG SPRING_ACTIVE_PROFILE
ENV SPRING_PROFILES_ACTIVE=${SPRING_ACTIVE_PROFILE}

# Copiar código fonte e configurações Maven
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn/ .mvn/
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src

# Set memory-optimized environment variables
ENV MAVEN_OPTS="-Xmx12g -Xms4g -XX:+UseG1GC -XX:+UseStringDeduplication"
ENV JAVA_TOOL_OPTIONS="-Xmx12g -Xms4g"

# Compilar aplicação e gerar executável nativo completo e statico
RUN ./mvnw -Pnative clean package -DskipTests

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
CMD ["./access-management"]
