ARG BUILDPLATFORM
ARG TARGETPLATFORM
#===================================================================
# Build stage: GraalVM 24 + Native Image
# ===================================================================
FROM --platform=${BUILDPLATFORM} ghcr.io/graalvm/native-image-community:23-ol9 AS build

# show platforms
ARG BUILDPLATFORM
ARG TARGETPLATFORM
RUN echo "Building on: ${BUILDPLATFORM}, targeting: ${TARGETPLATFORM}"

WORKDIR /app


# Install only wget & xz
USER root
RUN microdnf install --nodocs -y \
      wget xz make gcc findutils \
    && microdnf clean all

# -----------------------------
# 1) Install & build musl from source
# -----------------------------
#RUN wget -q https://musl.libc.org/releases/musl-1.2.5.tar.gz \
#      -O /tmp/musl-1.2.5.tar.gz && \
#    tar -xzf /tmp/musl-1.2.5.tar.gz -C /tmp && \
#    cd /tmp/musl-1.2.5 && \
#    ./configure --prefix=/usr/local/musl --exec-prefix=/usr/local && \
#    make && make install

# -----------------------------
# 2) Symlink the single musl-gcc into the two names GraalVM expects
# -----------------------------
#RUN ln -sf /usr/local/bin/musl-gcc /usr/local/bin/x86_64-linux-musl-gcc && \
#    ln -sf /usr/local/bin/musl-gcc /usr/local/bin/aarch64-linux-musl-gcc

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
# Runtime stage: minimal --static-nolibc binary
# =================================================================== # gcr.io/distroless/static:nonroot  #(for --static )
FROM gcr.io/distroless/base-nossl:nonroot

ARG TARGETPLATFORM
LABEL platform=${TARGETPLATFORM}

WORKDIR /app
COPY --from=build /app/target/access-management-upx ./access-management

USER nonroot:nonroot

EXPOSE 8080
CMD ["./access-management"]
