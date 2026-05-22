FROM cgr.dev/chainguard/maven:latest-dev AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
# verify = compile + test + JaCoCo coverage check (>=76%) + OWASP scan (CVSS>=0) + package.
# Build fails if any check does not pass.
RUN mvn -B verify && ls -lh target

FROM cgr.dev/chainguard/jre:latest
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
