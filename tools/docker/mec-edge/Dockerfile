ARG JAVA_VERSION=21

### Build edge binary (includes MEC bundles via io.mec.edge.* isEdge flag)
FROM --platform=$BUILDPLATFORM eclipse-temurin:${JAVA_VERSION}-alpine AS build_edge

RUN apk add --no-cache dos2unix bash

COPY tools/docker/edge/root/ /opt/root/

RUN find /opt/root/ -type f -print0 | xargs -0 dos2unix

WORKDIR /src

ENV OEMS_EDGE_OUTPUT=/opt/openems-edge.jar
RUN --mount=type=bind,target=.,readwrite \
    --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-build-cache buildEdge

### Runtime base
FROM ghcr.io/linuxserver/baseimage-alpine:edge AS base_container

ARG JAVA_VERSION

RUN mkdir -p /etc/apk/keys && \
    wget -O /etc/apk/keys/adoptium.rsa.pub https://packages.adoptium.net/artifactory/api/security/keypair/public/repositories/apk && \
    touch /etc/apk/repositories && \
    printf '%s\n' 'https://packages.adoptium.net/artifactory/apk/alpine/main' >> /etc/apk/repositories

RUN apk add --update --no-cache \
    temurin-${JAVA_VERSION}-jre rsync dos2unix

### Final image
FROM base_container

COPY --from=build_edge --chown=abc:abc /opt/root/ /
COPY --from=build_edge --chown=abc:abc /opt/openems-edge.jar /opt/openems/openems-edge.jar

RUN find /etc/s6-overlay/ -type f -name 'run' -exec chmod +x {} \;

RUN echo "MEC Intelligence Edge $(date +%Y%m%d)" | tee /app/version

# IEX credentials — pass via environment at runtime, never bake into image
ENV IEX_USER="" IEX_PASS=""

# MEC tariff override directory (mount a host directory to override bundled YAMLs)
VOLUME /etc/mec/tariff

VOLUME /var/opt/openems/config
VOLUME /var/opt/openems/data

EXPOSE 8080 8085
