# ===================================================================
# Build stage: GraalVM 21 + Native Image with Maven Wrapper
# ===================================================================
ARG BUILDPLATFORM
ARG TARGETPLATFORM

FROM --platform=$BUILDPLATFORM  ghcr.io/graalvm/native-image-community:23-muslib-ol9 AS build

# Platform information for debugging
ARG BUILDPLATFORM
ARG TARGETPLATFORM
RUN echo "Building on: $BUILDPLATFORM, targeting: $TARGETPLATFORM"

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

# Compilar aplicação com flags específicos por plataforma
RUN case "${TARGETPLATFORM}" in \
      "linux/arm64") MARCH_FLAG="-march=armv8-a" ;; \
      "linux/amd64") MARCH_FLAG="-march=x86-64" ;; \
      *) MARCH_FLAG="" ;; \
    esac && \
    echo "Using march flag: $MARCH_FLAG" && \
    if [ -n "$MARCH_FLAG" ]; then \
      ./mvnw -Pnative clean package -DskipTests -Dgraalvm.native.additional-build-args="$MARCH_FLAG"; \
    else \
      ./mvnw -Pnative clean package -DskipTests; \
    fi
# ===================================================================
# Runtime stage: minimal static binary
# ===================================================================
FROM --platform=$TARGETPLATFORM gcr.io/distroless/static:nonroot

# Platform information
ARG TARGETPLATFORM
LABEL platform=$TARGETPLATFORM

# Diretório de trabalho
WORKDIR /app

# Copy the native executable (artifactId assumed "access-management")
COPY --from=build /app/target/access-management /app/access-management

# Set proper ownership and permissions
USER nonroot:nonroot

# Expor porta e executar aplicação
EXPOSE 8080
CMD ["./access-management"]
