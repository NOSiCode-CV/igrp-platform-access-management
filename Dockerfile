# ===================================================================
# Build stage: GraalVM 21 + Native Image with Maven Wrapper
# ===================================================================
ARG BUILDPLATFORM
ARG TARGETPLATFORM

FROM --platform=$BUILDPLATFORM  ghcr.io/graalvm/native-image-community:23-muslib-ol9 AS build

# Platform information for debugging
ARG BUILDPLATFORM
ARG TARGETPLATFORM

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

# Compilar aplicação - Maven profiles handle platform-specific flags
RUN case "${TARGETPLATFORM}" in \
      "linux/arm64") \
        echo "Building ARM64 binary with armv8-a optimization" && \
        ./mvnw --no-transfer-progress -Pnative clean package -DskipTests -Dbuild.target=arm64 \
        ;; \
      "linux/amd64") \
        echo "Building AMD64 binary with x86-64 optimization" && \
        ./mvnw --no-transfer-progress -Pnative clean package -DskipTests -Dbuild.target=amd64 \
        ;; \
      *) \
        echo "Building with default settings for ${TARGETPLATFORM}" && \
        ./mvnw --no-transfer-progress -Pnative clean package -DskipTests \
        ;; \
    esac

# Install and use UPX
ARG UPX_VERSION=4.2.2
ARG UPX_ARCHIVE=upx-${UPX_VERSION}-amd64_linux.tar.xz
RUN microdnf -y install wget xz && \
    wget -q https://github.com/upx/upx/releases/download/v${UPX_VERSION}/${UPX_ARCHIVE} && \
    tar -xJf ${UPX_ARCHIVE} && \
    rm -rf ${UPX_ARCHIVE} && \
    mv upx-${UPX_VERSION}-amd64_linux/upx . && \
    rm -rf upx-${UPX_VERSION}-amd64_linux
RUN ./upx --lzma --best -o /app/target/access-management-upx  /app/target/access-management
RUN ls -lh /app/target/access-management-upx

# ===================================================================
# Runtime stage: minimal static binary
# ===================================================================
FROM gcr.io/distroless/static:nonroot

# Platform information
ARG TARGETPLATFORM
LABEL platform=$TARGETPLATFORM

# Diretório de trabalho
WORKDIR /app

# Copy the native executable (artifactId assumed "access-management")
COPY --from=build /app/target/access-management-upx /app/access-management

# Set proper ownership and permissions
USER nonroot:nonroot

# Expor porta e executar aplicação
EXPOSE 8080
CMD ["./access-management"]
