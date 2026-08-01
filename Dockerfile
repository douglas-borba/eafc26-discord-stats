# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-noble AS build

WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM mcr.microsoft.com/playwright/java:v1.47.0-noble

ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    APP_WEB_NETWORK_ENABLED=true \
    EAFC_DASHBOARD_AUTO_OPEN=false

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

RUN mkdir -p "/home/pwuser/Library/Application Support/EAFC26DiscordStats" && \
    chown -R pwuser:pwuser /app /home/pwuser

USER pwuser
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --quiet --spider http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
