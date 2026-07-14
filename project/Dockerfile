# syntax=docker/dockerfile:1.7
FROM quay.io/centos/centos:stream9 AS build

RUN dnf -y install --setopt=install_weak_deps=False java-21-openjdk-devel maven && \
    dnf clean all && rm -rf /var/cache/dnf

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
    PATH="/usr/lib/jvm/java-21-openjdk/bin:${PATH}"

WORKDIR /workspace
COPY app/pom.xml ./pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline
COPY app/config ./config
COPY app/src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -Pci-quality verify

FROM quay.io/centos/centos:stream9

LABEL org.opencontainers.image.title="Carzonrent Spring Boot QA Environment" \
      org.opencontainers.image.description="CentOS Stream 9 Apache reverse proxy with Spring Boot QA dashboard" \
      org.opencontainers.image.vendor="Carzonrent Pvt Ltd" \
      org.opencontainers.image.version="2.0.0" \
      org.opencontainers.image.source="Printkeep/project" \
      environment="qa"

ARG BUILD_VERSION=2.0.0
ARG GIT_COMMIT_ID=unknown
ARG BUILD_TIMESTAMP=unknown
ARG DOCKER_IMAGE_TAG=local
ARG JENKINS_BUILD_NUMBER=local
LABEL org.opencontainers.image.revision="${GIT_COMMIT_ID}" \
      org.opencontainers.image.created="${BUILD_TIMESTAMP}" \
      org.opencontainers.image.ref.name="${DOCKER_IMAGE_TAG}"
ENV BUILD_VERSION=${BUILD_VERSION} \
    GIT_COMMIT_ID=${GIT_COMMIT_ID} \
    BUILD_TIMESTAMP=${BUILD_TIMESTAMP} \
    DOCKER_IMAGE_TAG=${DOCKER_IMAGE_TAG} \
    JENKINS_BUILD_NUMBER=${JENKINS_BUILD_NUMBER} \
    CONTAINER_NAME=qa.carzonrent \
    JAVA_HOME=/usr/lib/jvm/jre-21-openjdk \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom" \
    SPRING_PROFILES_ACTIVE=qa \
    SPRING_APPLICATION_NAME=qa-dashboard \
    SERVER_ADDRESS=127.0.0.1 \
    SERVER_PORT=8085 \
    CARZONRENT_ENVIRONMENT=QA \
    LOG_LEVEL_ROOT=INFO \
    LOG_LEVEL_APP=INFO
ENV PATH="${JAVA_HOME}/bin:${PATH}"
RUN groupadd -g 10001 printgroup && \
    useradd -u 10001 -g printgroup -m -s /sbin/nologin printuser

RUN dnf -y install --allowerasing --setopt=install_weak_deps=False \
        httpd \
        mod_ssl \
        java-21-openjdk-headless \
        curl && \
    dnf clean all && \
    rm -rf /var/cache/dnf \
        /etc/httpd/conf.d/autoindex.conf \
        /etc/httpd/conf.d/welcome.conf \
        /etc/httpd/conf.d/ssl.conf && \
    sed -i 's/Listen 80/Listen 8080/' /etc/httpd/conf/httpd.conf && \
    sed -i 's/User apache/User printuser/' /etc/httpd/conf/httpd.conf && \
    sed -i 's/Group apache/Group printgroup/' /etc/httpd/conf/httpd.conf && \
    mkdir -p /opt/carzonrent/runtime /run/httpd /var/log/carzonrent /tmp/carzonrent /var/log/httpd && \
    chown -R printuser:printgroup /opt/carzonrent /var/log/carzonrent /tmp/carzonrent /run/httpd /var/log/httpd && \
    chmod 0770 /opt/carzonrent /opt/carzonrent/runtime /var/log/carzonrent /tmp/carzonrent /run/httpd /var/log/httpd

COPY apache/qa.carzonrent.conf /etc/httpd/conf.d/qa.carzonrent.conf
COPY --from=build /workspace/target/qa-dashboard-*.jar /opt/carzonrent/runtime/qa-dashboard.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

RUN chmod 0755 /usr/local/bin/docker-entrypoint.sh && \
    chmod 0644 /etc/httpd/conf.d/qa.carzonrent.conf && \
    chown printuser:printgroup /opt/carzonrent/runtime/qa-dashboard.jar

USER 10001

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/health/readiness >/dev/null && \
        curl --fail --silent --show-error http://127.0.0.1:${SERVER_PORT}/health/liveness >/dev/null && \
        curl --fail --silent --show-error http://127.0.0.1:8080/info >/dev/null || exit 1

STOPSIGNAL SIGTERM
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["/usr/sbin/httpd","-DFOREGROUND"]
