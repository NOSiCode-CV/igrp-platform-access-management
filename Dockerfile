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
      wget xz make gcc \
    && microdnf clean all

# -----------------------------
# 1) Install & build musl from source
# -----------------------------
RUN wget -q https://musl.libc.org/releases/musl-1.2.5.tar.gz \
      -O /tmp/musl-1.2.5.tar.gz && \
    tar -xzf /tmp/musl-1.2.5.tar.gz -C /tmp && \
    cd /tmp/musl-1.2.5 && \
    ./configure --prefix=/usr/local/musl --exec-prefix=/usr/local && \
    make && make install

# -----------------------------
# 2) Symlink the single musl-gcc into the two names GraalVM expects
# -----------------------------
RUN ln -sf /usr/local/bin/musl-gcc /usr/local/bin/x86_64-linux-musl-gcc && \
    ln -sf /usr/local/bin/musl-gcc /usr/local/bin/aarch64-linux-musl-gcc

# Download the proper JDK static-lib bundle
ARG JDK_TAG=26+3-jvmci-b01

# 1. Decide which debug asset to pull
RUN set -eux; \
    if [ "${TARGETPLATFORM}" = "linux/arm64" ]; then \
      ASSET="labsjdk-ce-${JDK_TAG}-debug-linux-aarch64.tar.gz"; \
      SUBPATH="linux-aarch64"; \
    else \
      ASSET="labsjdk-ce-${JDK_TAG}-debug-linux-amd64.tar.gz"; \
      SUBPATH="linux-amd64"; \
    fi; \
    # 2. Download it
    wget -q "https://github.com/graalvm/labs-openjdk/releases/download/${JDK_TAG}/${ASSET}" \
      -O "/tmp/${ASSET}"; \
    # 3. Compute the top-level directory inside the tarball
    DIR_NAME="${ASSET%.tar.gz}"; \
    # 4. Create the destination for the static musl libs
    DEST="/usr/lib64/graalvm/graalvm-community-java23/lib/static/${SUBPATH}/musl"; \
    mkdir -p "${DEST}"; \
    # 5. Extract only that subfolder, stripping the first two components
    #    so that e.g. `.../${DIR_NAME}/lib/static/${SUBPATH}/musl/*.a`
    #    lands directly in ${DEST}
    tar -xzf "/tmp/${ASSET}" \
      --strip-components=2 \
      -C "${DEST}" \
      "${DIR_NAME}/lib/static/${SUBPATH}/musl"; \
    rm "/tmp/${ASSET}"




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
# =================================================================== #  gcr.io/distroless/base-nossl:nonroot #(for --static-nolibc )
FROM gcr.io/distroless/static:nonroot

ARG TARGETPLATFORM
LABEL platform=${TARGETPLATFORM}

WORKDIR /app
COPY --from=build /app/target/access-management-upx ./access-management

USER nonroot:nonroot

EXPOSE 8080
CMD ["./access-management"]
