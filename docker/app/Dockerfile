# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy the build descriptors
COPY pom.xml .
COPY ipp-codec/pom.xml ipp-codec/
COPY print-quota-core/pom.xml print-quota-core/

# Copy the source folders
COPY ipp-codec/src ipp-codec/src
COPY print-quota-core/src print-quota-core/src

# Build application jar (skip tests for container image assembly)
RUN mvn clean package -DskipTests -Djacoco.skip=true

# Stage 2: Runtime image
FROM quay.io/centos/centos:stream9

# Install Java 21 OpenJDK Headless and clean up dnf caches to minimize image size
RUN dnf install -y java-21-openjdk-headless && \
    dnf clean all && \
    rm -rf /var/cache/dnf

# Create a non-privileged system group and user
RUN groupadd -g 10001 printgroup && \
    useradd -u 10001 -g printgroup -m -s /sbin/nologin printuser

WORKDIR /app

# Copy the built jar from Stage 1
COPY --from=builder /build/print-quota-core/target/print-quota-core-1.0.0.jar app.jar

# Enforce secure file ownership
RUN mkdir -p /app/logs /app/reports /app/spool /app/certs && \
    chown -R printuser:printgroup /app

# Switch to the non-root user
USER 10001:10001

# Document port binding
EXPOSE 8080

# Environment variables for container-aware JVM memory tuning and garbage collection
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
ENV LOG_PATH="/app/logs"

# Startup command propagating JVM options
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
