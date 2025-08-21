# Stage 1: The Build Stage
FROM maven:3.9-eclipse-temurin-21 AS build
LABEL authors="Biswajit Behera"
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# ---

# Stage 2: The Final Runtime Stage
FROM openjdk:21-slim

# Create a dedicated directory for the app
WORKDIR /app

# Create a non-root user and give it ownership of the app directory
RUN addgroup --system spring && adduser --system --ingroup spring spring
RUN chown -R spring:spring /app

# Switch to the non-root user
USER spring

# Copy the JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# ENTRYPOINT to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]