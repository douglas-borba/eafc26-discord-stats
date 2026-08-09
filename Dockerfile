# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-noble AS build

WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-noble

ENV APP_WEB_NETWORK_ENABLED=true \
    EAFC_DASHBOARD_AUTO_OPEN=false \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=20.0 -XX:+ExitOnOutOfMemoryError"

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

RUN useradd --create-home --shell /usr/sbin/nologin eafc && \
    mkdir -p "/home/eafc/Library/Application Support/EAFC26DiscordStats" && \
    chown -R eafc:eafc /app /home/eafc

USER eafc
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --quiet --spider http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
