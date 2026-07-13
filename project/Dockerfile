FROM quay.io/centos/centos:stream9

LABEL org.opencontainers.image.title="Carzonrent QA Environment" \
      org.opencontainers.image.description="CentOS Stream 9 Apache reverse proxy with a local QA backend" \
      org.opencontainers.image.vendor="Carzonrent Pvt Ltd" \
      org.opencontainers.image.version="1.0.0" \
      environment="qa"

ARG BUILD_VERSION=1.0.0
ENV BUILD_VERSION=${BUILD_VERSION} \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN dnf -y install --allowerasing --setopt=install_weak_deps=False \
        httpd \
        mod_ssl \
        curl \
        vim \
        iproute \
        iputils \
        net-tools \
        python3 && \
    dnf clean all && \
    rm -rf /var/cache/dnf \
        /etc/httpd/conf.d/autoindex.conf \
        /etc/httpd/conf.d/welcome.conf \
        /etc/httpd/conf.d/ssl.conf

COPY apache/qa.carzonrent.conf /etc/httpd/conf.d/qa.carzonrent.conf
COPY backend/ /opt/carzonrent/backend/
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

RUN chmod 0755 /usr/local/bin/docker-entrypoint.sh /opt/carzonrent/backend/server.py && \
    chown -R apache:apache /opt/carzonrent

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD curl --fail --silent --show-error http://127.0.0.1/health >/dev/null && \
        curl --fail --silent --show-error http://127.0.0.1:8085/health >/dev/null || exit 1

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["/usr/sbin/httpd","-DFOREGROUND"]
