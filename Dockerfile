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

WORKDIR /app

# 1. Create the user and group first.
RUN addgroup --system spring && adduser --system --ingroup spring --home /home/spring spring

# 2. Create the data directory.
RUN mkdir -p /app/data

# 3. Copy the application JAR into the work directory.
COPY --from=build /app/target/*.jar app.jar

# 4. CRITICAL FIX: Change ownership of the ENTIRE app directory and the data directory.
#    This ensures the running user owns its own working directory.
RUN chown -R spring:spring /app

# 5. NOW, switch to the non-root user.
USER spring

# Set environment variables (still good practice, though not strictly needed for history anymore)
ENV SPRING_SHELL_HISTORY_PATH=/app/data/spring-shell.log

# ENTRYPOINT to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]