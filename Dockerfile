# ---- Build stage ----
FROM eclipse-temurin:23-jdk-alpine AS build
WORKDIR /app

COPY pom.xml ./
RUN ./mvnw -B -q dependency:go-offline || mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package && ls -lh target

# ---- Runtime stage ----
FROM eclipse-temurin:23-jre-alpine AS runtime
WORKDIR /app

COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]