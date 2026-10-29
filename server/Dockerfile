# Production image (D19.1). Built by server/deploy/release.sh (buildx, linux/amd64 + linux/arm64) and the CI
# release job from the packaged jar (Angular client included): non-root, read-only friendly, data under /data.
FROM eclipse-temurin:21-jre

ARG APP_VERSION=unknown

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app /data/uploads \
    && chown -R app:app /data

WORKDIR /app

ARG JAR_FILE=target/foodsharing-platform-*.jar
COPY --chown=root:root ${JAR_FILE} /app/app.jar
COPY --chown=root:root deploy/version.txt /app/version.txt

ENV SPRING_PROFILES_ACTIVE=prod \
    APP_VERSION=${APP_VERSION} \
    APP_VERSION_FILE=/app/version.txt \
    UPLOADS_DIR=/data/uploads \
    MANAGEMENT_PORT=9090 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom -Dfile.encoding=UTF-8"

USER app:app
VOLUME ["/data"]
# 8080: application (behind the reverse proxy); 9090: health + Prometheus (internal network only)
EXPOSE 8080 9090

HEALTHCHECK --interval=15s --timeout=3s --start-period=120s --retries=4 \
    CMD curl -fsS "http://127.0.0.1:${MANAGEMENT_PORT}/actuator/health/liveness" > /dev/null || exit 1

# exec: java replaces the shell as PID 1 and receives SIGTERM directly (graceful shutdown)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
