# syntax=docker/dockerfile:1.7

#build with the same Java release and Maven Wrapper used by contributors/CI
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

#cache dependency resolution until pom.xml or the wrapper changes
COPY .mvn/ .mvn/
COPY config/ config/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw --batch-mode --no-transfer-progress \
       -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress \
    -DskipTests clean package

#runtime image contains no compiler, Maven installation, or source tree
FROM eclipse-temurin:25-jre-alpine

#pull patched Alpine packages even when the base-image layer is cached
RUN apk upgrade --no-cache \
    && addgroup -S -g 10001 tentflow \
    && adduser -S -D -H -u 10001 -G tentflow tentflow

WORKDIR /app
COPY --from=build --chown=tentflow:tentflow \
    /workspace/target/tentflow-*.jar /app/tentflow.jar
COPY --chown=tentflow:tentflow \
    docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod 0555 /app/docker-entrypoint.sh

USER 10001:10001
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
    CMD wget -q -O /dev/null \
    "http://127.0.0.1:${PORT:-8080}/readyz" || exit 1

ENTRYPOINT ["/app/docker-entrypoint.sh"]