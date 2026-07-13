FROM quay.io/centos/centos:stream9

LABEL org.opencontainers.image.title="Carzonrent Spring Boot QA Environment" \
      org.opencontainers.image.description="CentOS Stream 9 Apache reverse proxy with Spring Boot QA dashboard" \
      org.opencontainers.image.vendor="Carzonrent Pvt Ltd" \
      org.opencontainers.image.version="2.0.0" \
      environment="qa"

ARG BUILD_VERSION=2.0.0
ENV BUILD_VERSION=${BUILD_VERSION} \
    JAVA_HOME="/usr/lib/jvm/java-21-openjdk" \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=qa
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN dnf -y install --allowerasing --setopt=install_weak_deps=False \
        httpd \
        mod_ssl \
        java-21-openjdk \
        java-21-openjdk-devel \
        maven \
        curl \
        vim \
        iproute \
        net-tools && \
    dnf clean all && \
    rm -rf /var/cache/dnf \
        /etc/httpd/conf.d/autoindex.conf \
        /etc/httpd/conf.d/welcome.conf \
        /etc/httpd/conf.d/ssl.conf

COPY apache/qa.carzonrent.conf /etc/httpd/conf.d/qa.carzonrent.conf
COPY app/ /opt/carzonrent/app/
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

WORKDIR /opt/carzonrent/app
RUN mvn -B -DskipTests package && \
    mkdir -p /opt/carzonrent/runtime && \
    cp target/qa-dashboard-*.jar /opt/carzonrent/runtime/qa-dashboard.jar && \
    rm -rf /root/.m2/repository /opt/carzonrent/app/target && \
    chmod 0755 /usr/local/bin/docker-entrypoint.sh && \
    chown -R apache:apache /opt/carzonrent

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=25s --retries=3 \
    CMD curl --fail --silent --show-error http://127.0.0.1/health >/dev/null && \
        curl --fail --silent --show-error http://127.0.0.1:8085/health >/dev/null || exit 1

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["/usr/sbin/httpd","-DFOREGROUND"]
