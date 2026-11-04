# Map basemap builder (MT2, decision 163 D3): ghcr.io/epsuchti/foodsharing-tiles-builder:<version.txt>, published by
# .github/workflows/tiles-image.yml for linux/amd64 + linux/arm64. Runs build-basemap.sh: as the monthly K3s CronJob
# (hosting repo, chart pmtiles-basemap) or through server/deploy/tiles.sh on a compose host. Build context: this folder.

# Pinned Protomaps basemaps profile (tiles 4.15.2), built the same way as its own tiles/Dockerfile.
FROM maven:3.9-eclipse-temurin-21 AS basemaps
ARG BASEMAPS_REPO=https://github.com/protomaps/basemaps.git
ARG BASEMAPS_COMMIT=ca93fc06efbaff1c5f069d06edbe5de18839c1c8
RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/* \
    && git init -q /src \
    && git -C /src remote add origin "${BASEMAPS_REPO}" \
    && git -C /src fetch -q --depth 1 origin "${BASEMAPS_COMMIT}" \
    && git -C /src checkout -q FETCH_HEAD
WORKDIR /src/tiles
RUN mvn -B -q dependency:go-offline && mvn -B -q -DskipTests package \
    && cp target/protomaps-basemap-HEAD-with-deps.jar /protomaps-basemap.jar

# pmtiles CLI for the extract method (static Go binary).
FROM protomaps/go-pmtiles:v1.31.2 AS pmtiles

FROM eclipse-temurin:21-jre
ARG TILES_BUILDER_VERSION=unknown
LABEL org.opencontainers.image.title="foodsharing-tiles-builder" \
      org.opencontainers.image.source="https://github.com/Epsuchti/foodsharing-platform-2.0" \
      org.opencontainers.image.version="${TILES_BUILDER_VERSION}"

# bash, coreutils and util-linux (flock) come with the base image; curl for the downloads.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 tiles \
    && useradd --system --uid 10001 --gid tiles --home-dir /tmp --shell /usr/sbin/nologin tiles \
    && mkdir -p /tiles/basemap /tiles/work \
    && chown -R 10001:10001 /tiles

COPY --from=pmtiles /go-pmtiles /usr/local/bin/pmtiles
COPY --from=basemaps /protomaps-basemap.jar /opt/basemaps/protomaps-basemap.jar
COPY --chmod=755 build-basemap.sh /usr/local/bin/build-basemap.sh
COPY version.txt /opt/basemaps/version.txt

ENV TILES_OUT=/tiles/basemap \
    TILES_WORK=/tiles/work \
    BASEMAPS_JAR=/opt/basemaps/protomaps-basemap.jar \
    HOME=/tmp

USER 10001:10001
WORKDIR /tiles/work
ENTRYPOINT ["/usr/local/bin/build-basemap.sh"]
