FROM cgr.dev/chainguard/maven:latest-dev AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests, JaCoCo coverage check, and OWASP dependency-check are intentionally
# skipped here to keep image builds fast and self-contained (no NVD download).
# Run `mvn verify` locally or in CI to exercise the full gate.
RUN mvn -B -DskipTests clean package && ls -lh target

FROM cgr.dev/chainguard/jre:latest
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
