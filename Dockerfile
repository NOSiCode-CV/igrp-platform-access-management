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

# 1) Install curl & download Maven
#ARG MAVEN_VERSION=3.9.4
#RUN microdnf install -y curl tar gzip && \
#    mkdir -p /opt/maven && \
#    curl -fsSL https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
#      | tar -xz -C /opt/maven --strip-components=1 && \
#    ln -s /opt/maven/bin/mvn /usr/local/bin/mvn

# Copiar código fonte e configurações Maven
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn/ .mvn/
COPY pom.xml ./
#RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
#RUN mvn --batch-mode dependency:go-offline

COPY src ./src

# Set memory-optimized environment variables
ENV MAVEN_OPTS="-Xmx12g -Xms4g -XX:+UseG1GC -XX:+UseStringDeduplication"
ENV JAVA_TOOL_OPTIONS="-Xmx12g -Xms4g"

# Compilar aplicação - Maven profiles handle platform-specific flags
RUN case "${TARGETPLATFORM}" in \
      "linux/arm64") \
        echo "🏗️  Building ARM64 binary with armv8-a optimization" && \
        ./mvnw --no-transfer-progress --batch-mode \
          -Ptarget-arm64 \
          clean package \
          -DskipTests \
        ;; \
      "linux/amd64") \
        echo "🏗️  Building AMD64 binary with x86-64 optimization" && \
        ./mvnw --no-transfer-progress --batch-mode \
          -Ptarget-amd64 \
          clean package \
          -DskipTests \
        ;; \
      *) \
        echo "🏗️  Building default/native" && \
        ./mvnw --no-transfer-progress --batch-mode \
          clean package \
          -DskipTests \
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
