FROM cgr.dev/chainguard/maven:latest-dev AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package && ls -lh target

FROM cgr.dev/chainguard/wolfi-base AS runtime
WORKDIR /app

# Install shell and tools
RUN apk add --no-cache bash curl busybox-extras

COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
