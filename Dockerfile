# Pinned to JDK 26 to match <java.version> in pom.xml. Do NOT use a floating
# tag here: this build previously used cgr.dev/chainguard/maven:latest-dev,
# which rolled onto OpenJDK 27 when 27 went GA (2026-09-15) and broke every
# pipeline with "ExceptionInInitializerError: com.sun.tools.javac.tree.EndPosTable"
# — Lombok patches javac internals and no Lombok release supports 27 yet.
# CI builds with --pull --no-cache, so a floating tag is re-resolved every run.
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests, JaCoCo coverage check, and OWASP dependency-check are intentionally
# skipped here to keep image builds fast and self-contained (no NVD download).
# Run `mvn verify` locally or in CI to exercise the full gate.
RUN mvn -B -DskipTests clean package && ls -lh target

# Pinned for the same reason as the build stage. Java 26 bytecode does run on a
# newer JRE, but an unpinned runtime drifts independently of what we compile to.
FROM eclipse-temurin:26-jre
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
