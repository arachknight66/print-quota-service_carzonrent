# Production CentOS Stream 9 Runtime Container for Carzonrent QA Ingress (Phase 2)
FROM quay.io/centos/centos:stream9

# Install Java 21 OpenJDK, Apache HTTP Server, SSL modules, and curl
RUN dnf install -y --allowerasing java-21-openjdk-headless httpd mod_ssl curl && \
    dnf clean all && \
    rm -rf /var/cache/dnf

# Download and verify Tini init process (PID 1)
ENV TINI_VERSION v0.19.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-amd64 /tini
RUN echo "93dcc18adc78c65a028a84799ecf8ad40c936fdfc5f2a57b1acda5a8117fa82c  /tini" | sha256sum -c - && \
    chmod +x /tini

# Generate self-signed certificate for localhost to satisfy mod_ssl default config
RUN openssl req -new -newkey rsa:2048 -days 365 -nodes -x509 \
    -keyout /etc/pki/tls/private/localhost.key \
    -out /etc/pki/tls/certs/localhost.crt \
    -subj "/C=IN/ST=Delhi/L=Delhi/O=Carzonrent/CN=qa.carzonrent"

# Redirect Apache HTTP logs directly to stdout/stderr for container engine capture
RUN ln -sf /dev/stdout /var/log/httpd/access_log && \
    ln -sf /dev/stderr /var/log/httpd/error_log

# Create directories for the application logs, reports, and spool folders
RUN mkdir -p /app/logs /app/reports /app/spool /app/certs

# Create printgroup, printuser, and align with apache group for shared permissions
RUN groupadd -g 10001 printgroup && \
    useradd -u 10001 -g printgroup -G apache -m -s /sbin/nologin printuser

WORKDIR /app

# Copy the pre-built jar directly from host target folder (guarantees host-compiled code matches exactly)
COPY print-quota-core/target/print-quota-core-1.0.0.jar app.jar

# Copy Apache configurations and entrypoint script
COPY docker/app/httpd-proxy.conf /etc/httpd/conf.d/qa-proxy.conf
COPY docker/app/httpd-security.conf /etc/httpd/conf.d/qa-security.conf
COPY docker/app/entrypoint.sh /app/entrypoint.sh

# Set correct execute permissions and folder ownership
RUN chmod +x /app/entrypoint.sh && \
    chown -R printuser:apache /app /var/spool/print-quota 2>/dev/null || true

# Document exposed ports (Apache Port 80)
EXPOSE 80

# Environment variables
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
ENV LOG_PATH="/app/logs"

# Healthcheck checking both Apache and Spring Boot via local curl on Port 80
HEALTHCHECK --interval=10s --timeout=5s --retries=3 \
  CMD curl -f http://127.0.0.1/health || exit 1

# Launch using tini as PID 1
ENTRYPOINT ["/tini", "--", "/app/entrypoint.sh"]
