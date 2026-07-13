# ==============================================================================
# Dockerfile for Carzonrent Quality Assurance (QA) Environment
# Base Image: CentOS Stream 9
# Description: Local simulated QA web server environment hosting Apache HTTP Server.
# ==============================================================================

# Use official CentOS Stream 9 base image
FROM quay.io/centos/centos:stream9

# Metadata Labels
LABEL maintainer="DevOps Engineering Team <devops@carzonrent.com>" \
      environment="QA" \
      project="Carzonrent Web Infrastructure" \
      version="1.0.0"

# Update packages and install Apache HTTP Server and required utility packages
RUN dnf -y update && \
    dnf -y install --allowerasing \
        httpd \
        curl \
        iproute \
        iputils \
        vim \
        net-tools && \
    dnf clean all

# Copy website files (HTML and CSS) to Apache document root
COPY index.html /var/www/html/
COPY style.css /var/www/html/

# Expose web service port
EXPOSE 80

# Run Apache HTTP Server in the foreground to keep the container active
CMD ["/usr/sbin/httpd", "-DFOREGROUND"]
