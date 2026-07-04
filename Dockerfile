############################
# Build stage
############################
FROM maven:3.9-eclipse-temurin-25-alpine AS build

WORKDIR /workspace

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# The executable bit is not guaranteed to survive checkout on every platform.
RUN chmod +x mvnw

# Isolated so dependency resolution is cached independently of source changes.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B dependency:go-offline

COPY src src

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -DskipTests clean package \
    && java -Djarmode=tools -jar target/*.jar extract \
    --layers --launcher --destination target/extracted


############################
# Runtime stage
# Distroless has no shell, package manager, or chown; nonroot is uid/gid 65532.
############################
FROM gcr.io/distroless/java25-debian13:nonroot

LABEL org.opencontainers.image.title="blacklist-hub"
LABEL org.opencontainers.image.description="Blacklist Hub Slack bot"
LABEL org.opencontainers.image.vendor="ICG"

WORKDIR /app

# Ownership is set here because the runtime image cannot chown afterwards.
COPY --from=build --chown=65532:65532 /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=65532:65532 /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=65532:65532 /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=65532:65532 /workspace/target/extracted/application/ ./

USER 65532:65532

EXPOSE 8080

# Consumed directly by the JVM, which the shell-less runtime requires; overridable at deploy time.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
