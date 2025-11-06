# First stage: Build the application
FROM maven:3.9-eclipse-temurin-21-alpine AS build

# Set working directory
WORKDIR /app

# Copy Maven configuration files first to leverage Docker cache for dependencies
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Download dependencies (helps avoid re-downloading if they haven’t changed)
RUN mvn -B dependency:go-offline

# Copy the source code
COPY src /app/src

# Build the application (skip tests to speed up the process)
RUN mvn -B clean package -DskipTests


# Second stage: Create Image for runtime
FROM eclipse-temurin:21-jre-alpine

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Set working directory
WORKDIR /home/app

# Copy the JAR file from the build stage
COPY --from=build /app/target/ip-blocklist-api-0.0.1-SNAPSHOT.jar app.jar

# Expose the port the app will run on
EXPOSE 8080

# Switch to non-root user
USER appuser

# Command to run the application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
