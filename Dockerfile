# Build stage: compiles and packages the jar, webroot included.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Run stage: only the runtime and the jar.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /build/target/tdse-webframework.jar app.jar

# Defaults for the container. The platform normally overrides PORT.
ENV PORT=8080 \
    APP_ENV=production \
    GREETING_PREFIX=Hello

EXPOSE 8080

# The server binds to every interface, so the platform can reach it.
CMD ["java", "-jar", "app.jar"]
