ARG BUILDPLATFORM
ARG TARGETPLATFORM
#===================================================================
# Build stage: GraalVM 24 + Native Image
# ===================================================================
FROM --platform=${BUILDPLATFORM} ghcr.io/graalvm/native-image-community:23 AS build

# show platforms
ARG BUILDPLATFORM
ARG TARGETPLATFORM
RUN echo "Building on: ${BUILDPLATFORM}, targeting: ${TARGETPLATFORM}"

WORKDIR /app

# copy only what's needed for mvnw bootstrap
COPY mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./

RUN chmod +x mvnw && \
    ./mvnw --batch-mode dependency:go-offline -B

COPY src/ src/

# give Maven more heap
ENV MAVEN_OPTS="-Xmx12g -Xms4g -XX:+UseG1GC -XX:+UseStringDeduplication"
ENV JAVA_TOOL_OPTIONS="-Xmx12g -Xms4g"

# version for UPX
ARG UPX_VERSION=5.0.1

# build native image and then UPX-compress it
RUN set -eux; \
    # pick arch/profile based on target
    case "${TARGETPLATFORM}" in \
      "linux/arm64") ARCH=arm64; ;; \
      "linux/amd64") ARCH=amd64; ;; \
      *)              ARCH=amd64; ;; \
    esac; \
    echo "→ Building for ${ARCH}"; \
    ./mvnw --batch-mode \
      -Pnative \
      -Dbuild.target=${ARCH} \
      clean package -DskipTests; \
    \
    # UPX compress
    UPX_ARCHIVE="upx-${UPX_VERSION}-${ARCH}_linux.tar.xz"; \
    wget -q https://github.com/upx/upx/releases/download/v${UPX_VERSION}/${UPX_ARCHIVE}; \
    tar -xJf ${UPX_ARCHIVE}; \
    mv "upx-${UPX_VERSION}-${ARCH}_linux"/upx /usr/local/bin/upx; \
    rm -rf ${UPX_ARCHIVE} "upx-${UPX_VERSION}-${ARCH}_linux"; \
    upx --lzma --best -o target/access-management-upx target/access-management; \
    ls -lh target/access-management-upx

# ===================================================================
# Runtime stage: minimal static binary
# =================================================================== # gcr.io/distroless/static:nonroot
FROM gcr.io/distroless/base-nossl:nonroot

ARG TARGETPLATFORM
LABEL platform=${TARGETPLATFORM}

WORKDIR /app
COPY --from=build /app/target/access-management-upx ./access-management

USER nonroot:nonroot

EXPOSE 8080
CMD ["./access-management"]
