Act as a Principal DevOps Engineer, Principal Java Platform Engineer, Senior Linux Administrator, Apache HTTP Server expert, Docker Engineer, and Enterprise Infrastructure Architect.

You are responsible for implementing Phase 1 of an enterprise deployment project.

====================================================================
WORKING MODE
====================================================================

You have approximately 80% autonomy.

You MAY:

- Inspect the entire project.
- Refactor project files.
- Modify Java code.
- Modify Spring Boot configuration.
- Modify Apache configuration.
- Modify Dockerfiles.
- Create scripts.
- Create documentation.
- Build Docker images.
- Build Maven projects.
- Recreate project containers.
- Execute verification scripts.
- Diagnose failures.
- Retry automatically until the project passes verification.

You MUST request confirmation before:

- Editing the Windows hosts file.
- Publishing to privileged ports (80 or 443).
- Changing Docker Desktop settings.
- Changing firewall rules.
- Installing software on the Windows host.
- Stopping unrelated Docker containers.
- Making any change outside this project.

Always explain why confirmation is required.

====================================================================
OBJECTIVE
====================================================================

Implement ONLY Phase 1.

Do NOT implement Kubernetes.

Do NOT implement Jenkins.

Do NOT split services into multiple containers.

Do NOT redesign the architecture.

The objective is to build a clean enterprise QA environment that mirrors the deployment architecture used by Carzonrent.

====================================================================
TARGET ARCHITECTURE
====================================================================

                Browser
                     │
      http://qa.carzonrent.com
                     │
             Docker Port Mapping
                     │
                     ▼
        CentOS Stream 9 Container
                     │
                     ▼
        Apache HTTP Server (Port 80)
                     │
             Reverse Proxy
                     │
                     ▼
Spring Boot (127.0.0.1:8085)

The backend must NEVER be publicly accessible.

Only Apache should be exposed.

====================================================================
OPERATING SYSTEM
====================================================================

Use:

CentOS Stream 9

Base image:

quay.io/centos/centos:stream9

====================================================================
JAVA
====================================================================

Java 21

Spring Boot 3.3.x

Use Maven.

====================================================================
SPRING BOOT
====================================================================

Run Spring Boot only on

127.0.0.1

Port

8085

Provide:

GET /

GET /health

GET /info

Use Thymeleaf.

Generate an enterprise QA dashboard.

Display:

- Environment
- Hostname
- Java Version
- Spring Boot Version
- Operating System
- Apache Status
- Reverse Proxy Status
- Backend Address
- Deployment Status
- Current Time

====================================================================
APACHE
====================================================================

Apache must be the ONLY public web server.

Configure Apache exactly as follows.

Do NOT redesign these directives.

Keep:

SSLProxyEngine On

Timeout 2400

ProxyTimeout 2400

ProxyBadHeader Ignore

ProxyPass /
http://127.0.0.1:8085/

ProxyPassReverse /
http://127.0.0.1:8085/

Enable every Apache module required for reverse proxying.

Apache must remain the foreground process.

====================================================================
DOCKER
====================================================================

Create a Docker image using

CentOS Stream 9

Install

- Java 21
- Apache HTTP Server
- curl

Only install packages required for Phase 1.

Avoid unnecessary utilities.

Create a startup script that

1. Starts Spring Boot

2. Starts Apache

Apache remains PID 1.

Container name

qa.carzonrent

Hostname

qa.carzonrent

====================================================================
NETWORKING
====================================================================

Goal:

Application should ultimately be reachable as

http://qa.carzonrent.com

If this requires

- editing the Windows hosts file

or

- publishing host port 80

stop and request confirmation before making those changes.

Until then, continue using the current port mapping.

Explain every networking decision.

====================================================================
HEALTH
====================================================================

Implement

Docker HEALTHCHECK

The container should only become healthy when

Apache

AND

Spring Boot

are both functioning.

====================================================================
VERIFICATION
====================================================================

Create or update a verification script.

Verify

- Container running
- Container healthy
- Apache configuration valid
- Proxy modules loaded
- Reverse proxy working
- Spring Boot reachable internally
- Backend NOT externally exposed
- Docker networking
- Port mapping
- HTTP 200
- Health endpoint
- Info endpoint
- CSS loading
- 404 handling

Generate a final verification report.

====================================================================
DOCUMENTATION
====================================================================

Generate documentation explaining

- Why CentOS Stream 9 was chosen
- Why Docker is used
- Difference between Image and Container
- Why Apache exists
- What a Reverse Proxy is
- Why Spring Boot runs on 127.0.0.1
- Why Apache listens on Port 80
- How ProxyPass works
- How ProxyPassReverse works
- Complete request lifecycle from Browser to Spring Boot and back
- Docker networking
- Port mapping
- Health checks
- Verification process

====================================================================
REVIEW MODE
====================================================================

Do NOT simply make it work.

Continuously review the implementation like an enterprise QA reviewer.

After every major step ask yourself:

- Is this production-like?
- Is this maintainable?
- Is this understandable?
- Does it match the requested architecture?
- Is it secure?
- Is there unnecessary complexity?

If improvements are possible within Phase 1, implement them.

====================================================================
OUT OF SCOPE
====================================================================

Do NOT implement

- Kubernetes
- Jenkins
- CI/CD
- PostgreSQL integration
- LDAP integration
- Redis
- Prometheus
- Grafana
- HTTPS certificates beyond Apache configuration
- Multi-container architecture
- High availability
- Load balancing

Those belong to later phases.

====================================================================
FINAL DELIVERABLE
====================================================================

At the end provide:

1. Architecture diagram

2. Directory structure

3. Explanation of every major component

4. Verification report

5. Remaining work for Phase 2

Do not proceed to Phase 2 automatically.




Act as a Principal DevOps Engineer, Principal Container Platform Engineer, Senior Linux Administrator, Docker SME, Java Platform Architect, and Enterprise Infrastructure Engineer.

You are responsible for implementing ONLY Phase 2 of this project.

Phase 1 is COMPLETE.

Do NOT redesign the architecture.

Improve the implementation while preserving the existing deployment model.

====================================================================
WORKING MODE
====================================================================

You have approximately 80% autonomy.

You MAY:

- Inspect the project.
- Modify Java code.
- Modify Spring Boot configuration.
- Improve Dockerfiles.
- Improve startup scripts.
- Improve Apache configuration where appropriate.
- Improve documentation.
- Optimize the build.
- Optimize container startup.
- Improve verification scripts.
- Rebuild Docker images.
- Restart project containers.
- Create additional project files.

You MUST ask for confirmation before:

- Editing the Windows hosts file.
- Publishing privileged host ports.
- Installing software on the Windows host.
- Editing Docker Desktop configuration.
- Editing firewall rules.
- Removing unrelated Docker containers.
- Making any system-wide networking changes.

====================================================================
CURRENT ARCHITECTURE (DO NOT CHANGE)
====================================================================

                Browser
                     │
             qa.carzonrent.com
                     │
             Docker Port Mapping
                     │
                     ▼
        CentOS Stream 9 Container
                     │
                     ▼
        Apache HTTP Server (Port 80)
                     │
             Reverse Proxy
                     │
                     ▼
Spring Boot (127.0.0.1:8085)

Keep the backend private.

Apache remains the only public entry point.

Keep the existing reverse proxy configuration:

SSLProxyEngine On

Timeout 2400

ProxyTimeout 2400

ProxyBadHeader Ignore

ProxyPass /
http://127.0.0.1:8085/

ProxyPassReverse /
http://127.0.0.1:8085/

Do not redesign these directives.

====================================================================
OBJECTIVE
====================================================================

Convert the existing implementation into a production-quality Docker deployment while preserving the architecture.

This phase is about container engineering and runtime quality.

Not CI/CD.

Not Kubernetes.

====================================================================
DOCKER IMPROVEMENTS
====================================================================

Review the Dockerfile.

Improve:

- Build reproducibility
- Layer ordering
- Build cache efficiency
- Image size
- Package cleanup
- Runtime optimization
- Startup reliability

Use a multi-stage build if appropriate.

Remove unnecessary runtime dependencies.

Only keep software actually required at runtime.

Document every optimization.

====================================================================
LINUX IMPROVEMENTS
====================================================================

Review:

- Filesystem layout
- Directory permissions
- Ownership
- Runtime directories
- Temporary files
- Log locations
- Startup sequence

Ensure the container follows Linux best practices.

====================================================================
PROCESS MANAGEMENT
====================================================================

Review how processes start.

Ensure:

- Spring Boot starts correctly
- Apache starts correctly
- Graceful shutdown works
- SIGTERM is handled correctly
- Zombie processes cannot accumulate

If appropriate, introduce a lightweight init process such as tini.

Explain why.

====================================================================
SPRING BOOT
====================================================================

Review the application.

Improve:

- External configuration
- Logging
- Profiles
- Environment variables
- Build information
- Version information

Replace custom health implementation with Spring Boot Actuator if appropriate.

Keep:

127.0.0.1:8085

Do not expose 8085.

====================================================================
APACHE
====================================================================

Do not redesign the reverse proxy.

Instead review:

- Module loading
- Configuration organization
- Logging
- KeepAlive
- Compression readiness
- Security headers (where appropriate)
- Startup reliability

Document recommendations.

====================================================================
SECURITY
====================================================================

Review:

Container user

Root usage

File permissions

Secrets

Environment variables

Network exposure

Attack surface

Implement improvements that are appropriate for Phase 2.

====================================================================
OBSERVABILITY
====================================================================

Improve logging.

Ensure:

Application logs

Apache logs

Container logs

are understandable.

Prefer stdout/stderr where appropriate.

Add useful startup information.

====================================================================
CONFIGURATION
====================================================================

Move configuration toward environment-driven deployment.

Review:

Ports

Application name

Environment

Log level

Java options

Do not hardcode values that should be configurable.

====================================================================
HEALTHCHECK
====================================================================

Review the Docker HEALTHCHECK.

Ensure it verifies:

Apache

Spring Boot

Reverse proxy

Health endpoint

Only report healthy when the complete request path is functioning.

====================================================================
DOCUMENTATION
====================================================================

Expand the documentation.

Explain:

Image lifecycle

Container lifecycle

Docker layers

ENTRYPOINT

CMD

PID 1

Signal handling

Bridge networking

Loopback networking

Why Spring Boot binds to 127.0.0.1

Why Apache is public

How Docker forwards packets

====================================================================
VERIFICATION
====================================================================

Extend the verification suite.

Verify:

Container

Health

Apache

Spring Boot

Reverse proxy

Process tree

Container user

Environment variables

Signal handling (where possible)

Configuration correctness

Logs

Health endpoint

Info endpoint

404 handling

====================================================================
SELF REVIEW
====================================================================

Continuously review the project as if you were:

- Principal DevOps Engineer
- Principal Docker Engineer
- Senior Linux Administrator

After each major improvement ask:

Is this more production ready?

Is it simpler?

Is it easier to maintain?

Is it more secure?

Is it closer to enterprise standards?

====================================================================
OUT OF SCOPE
====================================================================

Do NOT implement:

Jenkins

CI/CD

SonarQube

JaCoCo

GitHub Actions

Kubernetes

Helm

Prometheus

Grafana

PostgreSQL

LDAP

Redis

Load balancing

Service discovery

Multi-container architecture

Those belong to later phases.

====================================================================
FINAL DELIVERABLE
====================================================================

Provide:

1. Updated architecture diagram

2. Docker optimization summary

3. Linux improvements

4. Security improvements

5. Container engineering improvements

6. Verification results

7. Remaining work before Phase 3

Do not begin Phase 3 automatically.



Act as a Principal DevOps Engineer, Principal CI/CD Architect, Senior Jenkins Administrator, Java Platform Architect, Enterprise Build Engineer, and Release Engineering Lead.

You are responsible for implementing ONLY Phase 3 of this project.

Phase 1 and Phase 2 are COMPLETE.

Do NOT redesign the application architecture.

Do NOT introduce Kubernetes.

The objective of this phase is to transform the project into an enterprise-grade automated CI/CD pipeline suitable for a QA environment.

====================================================================
WORKING MODE
====================================================================

You have approximately 80% autonomy.

You MAY:

- Inspect the project.
- Modify build configuration.
- Modify Maven configuration.
- Modify Docker build process.
- Create Jenkins pipelines.
- Create deployment scripts.
- Improve verification scripts.
- Improve documentation.
- Build Docker images.
- Execute tests.
- Optimize build performance.

You MUST request confirmation before:

- Installing Jenkins on the host.
- Installing Docker plugins globally.
- Editing host networking.
- Editing firewall rules.
- Editing Windows services.
- Making changes outside this project.

Always explain why confirmation is required.

====================================================================
CURRENT ARCHITECTURE (DO NOT CHANGE)
====================================================================

Browser

↓

qa.carzonrent.com

↓

Docker Port Mapping

↓

Apache HTTP Server

↓

Reverse Proxy

↓

Spring Boot

127.0.0.1:8085

Keep:

SSLProxyEngine On

Timeout 2400

ProxyTimeout 2400

ProxyBadHeader Ignore

ProxyPass /
http://127.0.0.1:8085/

ProxyPassReverse /
http://127.0.0.1:8085/

Do NOT redesign this architecture.

====================================================================
OBJECTIVE
====================================================================

Convert the existing project into an enterprise-style CI/CD workflow.

The deployment target remains the existing QA Docker environment.

Kubernetes is NOT part of this phase.

====================================================================
BUILD PIPELINE
====================================================================

Review and improve the Maven lifecycle.

Ensure the pipeline performs:

- Clean
- Compile
- Unit Tests
- Integration Tests
- Package
- Build Docker Image
- Container Verification

Optimize build caching where appropriate.

Document every stage.

====================================================================
JENKINS
====================================================================

Create an enterprise Jenkins Pipeline.

Pipeline stages should include:

Checkout

↓

Environment Validation

↓

Compile

↓

Unit Tests

↓

Integration Tests

↓

Static Analysis

↓

Package

↓

Docker Build

↓

Container Verification

↓

Deploy QA

↓

Post Deployment Verification

The pipeline should fail immediately if any stage fails.

====================================================================
DOCKER AUTOMATION
====================================================================

Automate:

Docker Build

Docker Image Tagging

Container Replacement

Container Health Verification

Image Cleanup

Do not require manual docker commands after pipeline execution.

====================================================================
QUALITY GATES
====================================================================

Introduce quality gates.

Review and integrate where appropriate:

JaCoCo

SpotBugs

Checkstyle

PMD

CycloneDX SBOM

Dependency vulnerability analysis

If SonarQube is not available, prepare the project for future integration without requiring it.

====================================================================
VERSIONING
====================================================================

Implement a consistent versioning strategy.

Every build should produce:

Application Version

Git Commit ID

Build Timestamp

Docker Image Tag

Display build information in:

/info

Dashboard

Documentation

====================================================================
DEPLOYMENT
====================================================================

Automate deployment to the QA Docker container.

Deployment workflow:

Build Image

↓

Stop Existing Container

↓

Create New Container

↓

Run Health Checks

↓

Run Verification Suite

↓

Mark Deployment Successful

If deployment fails:

Automatically restore the previous working container where possible.

Document rollback strategy.

====================================================================
TEST AUTOMATION
====================================================================

Ensure verification executes automatically after deployment.

Include:

Apache Verification

Reverse Proxy Verification

Spring Boot Verification

Health Endpoint

Info Endpoint

HTTP 404 Handling

Container Health

Docker Networking

Port Mapping

Dashboard Availability

====================================================================
OBSERVABILITY
====================================================================

Improve deployment visibility.

Display:

Build Number

Build Time

Application Version

Container ID

Image Tag

Java Version

Spring Boot Version

Deployment Status

Environment

====================================================================
DOCUMENTATION
====================================================================

Expand documentation.

Explain:

Jenkins Pipeline

Maven Lifecycle

Artifact Generation

Docker Build Process

Image Versioning

Deployment Flow

Rollback Strategy

Verification Flow

CI/CD Architecture

====================================================================
SELF REVIEW
====================================================================

Continuously review the implementation as if you were:

Principal DevOps Engineer

Principal Release Engineer

Senior CI/CD Architect

Ask after every major change:

Is this reproducible?

Is this automatable?

Is this maintainable?

Would another engineer understand this pipeline?

Would this pass an enterprise QA review?

====================================================================
OUT OF SCOPE
====================================================================

Do NOT implement:

Kubernetes

Helm

Ingress

Prometheus

Grafana

Service Mesh

Horizontal Scaling

Load Balancing

PostgreSQL Clustering

LDAP Clustering

High Availability

Those belong to Phase 4 and Phase 5.

====================================================================
FINAL DELIVERABLE
====================================================================

Provide:

1. CI/CD Architecture Diagram

2. Jenkins Pipeline Diagram

3. Build Flow

4. Deployment Flow

5. Rollback Strategy

6. Versioning Strategy

7. Verification Results

8. Remaining work before Phase 4

Do not begin Phase 4 automatically.



Act as a Principal Site Reliability Engineer (SRE), Principal DevOps Engineer, Principal Security Engineer, Senior Linux Administrator, Enterprise Java Architect, Apache HTTP Server Specialist, Enterprise Observability Engineer, and Production Operations Lead.

You are responsible for implementing ONLY Phase 4.

Phase 1, Phase 2, and Phase 3 are COMPLETE.

Do NOT implement Kubernetes.

Do NOT redesign the deployment architecture.

The objective of this phase is to harden the application for enterprise QA and prepare it for production operations.

====================================================================
WORKING MODE
====================================================================

You have approximately 80% autonomy.

You MAY:

- Inspect the project.
- Improve Spring Boot.
- Improve Apache configuration.
- Improve Docker configuration.
- Improve security.
- Improve monitoring.
- Improve logging.
- Improve documentation.
- Improve deployment scripts.
- Improve Jenkins integration.
- Improve verification scripts.

You MUST ask for confirmation before:

- Installing software on the Windows host.
- Editing firewall rules.
- Changing Docker Desktop configuration.
- Editing Windows networking.
- Editing the hosts file.
- Making changes outside the project directory.

Always explain why confirmation is required.

====================================================================
CURRENT ARCHITECTURE (DO NOT CHANGE)
====================================================================

                Browser
                     │
             qa.carzonrent.com
                     │
             Docker Port Mapping
                     │
                     ▼
        Apache HTTP Server (Port 80)
                     │
             Reverse Proxy
                     │
                     ▼
Spring Boot (127.0.0.1:8085)

Keep the reverse proxy configuration exactly as follows:

SSLProxyEngine On

Timeout 2400

ProxyTimeout 2400

ProxyBadHeader Ignore

ProxyPass /
http://127.0.0.1:8085/

ProxyPassReverse /
http://127.0.0.1:8085/

Do not redesign this architecture.

====================================================================
OBJECTIVE
====================================================================

Transform the existing project into an enterprise-quality QA deployment suitable for operational use.

Focus on:

- Observability
- Monitoring
- Logging
- Security
- Operational readiness
- Documentation
- Reliability

====================================================================
SPRING BOOT
====================================================================

Improve the application by implementing:

Spring Boot Actuator

Health groups

Readiness endpoint

Liveness endpoint

Build information

Git information

Metrics

Application information

Structured logging

Global exception handling

Request logging

Correlation IDs

Environment-aware configuration

Profile management

Configuration validation

====================================================================
APACHE
====================================================================

Review and improve Apache.

Keep the existing reverse proxy configuration.

Add where appropriate:

Security headers

HTTP response headers

Compression

KeepAlive tuning

Access logging

Error logging

VirtualHost cleanup

Request logging

Log formatting

Graceful restart support

Document every improvement.

====================================================================
SECURITY
====================================================================

Perform a complete security review.

Review:

HTTP Headers

Server Tokens

Server Signature

Directory Listings

Permissions

Container user

Secrets

Configuration

Dependency versions

Input validation

Exception handling

Information leakage

Management endpoints

Implement improvements where appropriate.

Document every security decision.

====================================================================
LOGGING
====================================================================

Implement enterprise logging.

Review:

Spring Boot logs

Apache logs

Docker logs

Health logs

Deployment logs

Startup logs

Shutdown logs

Container logs

Use structured logging where possible.

Prefer stdout/stderr for container compatibility.

====================================================================
OBSERVABILITY
====================================================================

Prepare the project for enterprise monitoring.

Integrate:

Spring Boot Actuator

Micrometer

Prometheus endpoint readiness

Application metrics

JVM metrics

HTTP metrics

Thread metrics

Memory metrics

CPU metrics

Health metrics

Do NOT install Prometheus.

Do NOT install Grafana.

Prepare the project so they can be added later without architectural changes.

====================================================================
CONFIGURATION
====================================================================

Review configuration management.

Move configuration toward:

Environment variables

External configuration

Profiles

Application properties

Configuration validation

Secrets placeholders

====================================================================
RELIABILITY
====================================================================

Improve operational reliability.

Review:

Graceful shutdown

Timeouts

Health checks

Readiness

Liveness

Startup sequence

Restart behavior

Failure handling

====================================================================
DOCUMENTATION
====================================================================

Expand documentation.

Explain:

Actuator

Readiness

Liveness

Metrics

Observability

Structured Logging

Correlation IDs

Security Headers

Enterprise Logging

Production Hardening

Operational Monitoring

====================================================================
VERIFICATION
====================================================================

Expand the verification suite.

Verify:

Application startup

Apache startup

Reverse proxy

Health endpoint

Readiness endpoint

Liveness endpoint

Metrics endpoint

Logging

Build information

Git information

HTTP headers

Security headers

Configuration

Container health

Graceful shutdown

Restart behavior

====================================================================
SELF REVIEW
====================================================================

Continuously review the implementation as if you were:

Principal SRE

Principal Security Engineer

Production Operations Lead

After every improvement ask:

Would this survive production?

Can Operations troubleshoot it?

Can SRE monitor it?

Can Security approve it?

Can another engineer maintain it?

Is this enterprise quality?

====================================================================
OUT OF SCOPE
====================================================================

Do NOT implement:

Kubernetes

Helm

Ingress

Horizontal Pod Autoscaler

Cluster Autoscaler

Service Mesh

Prometheus Server

Grafana Server

ELK Stack

Distributed Tracing

Multiple Containers

High Availability

Those belong to Phase 5.

====================================================================
FINAL DELIVERABLE
====================================================================

Provide:

1. Updated Architecture Diagram

2. Observability Architecture

3. Security Review Report

4. Logging Architecture

5. Operational Readiness Report

6. Verification Report

7. Production Readiness Assessment

8. Remaining work before Phase 5

Do not begin Phase 5 automatically.



Act as a Principal Cloud Architect, Principal Kubernetes Engineer, Senior Platform Engineer, Senior Site Reliability Engineer (SRE), Enterprise DevOps Architect, Red Hat OpenShift Consultant, and Production Infrastructure Lead.

You are responsible for implementing ONLY Phase 5.

Phase 1 through Phase 4 are COMPLETE.

The objective is to migrate the existing enterprise QA deployment into a Kubernetes-ready enterprise platform without changing the business application.

====================================================================
WORKING MODE
====================================================================

You have approximately 80% autonomy.

You MAY:

- Inspect the project.
- Modify deployment configuration.
- Create Kubernetes manifests.
- Create Helm charts.
- Create Kustomize overlays.
- Create deployment documentation.
- Improve observability integration.
- Improve container deployment.
- Improve verification.
- Improve CI/CD deployment workflow.

You MUST ask for confirmation before:

- Installing Kubernetes locally.
- Installing Minikube.
- Installing Kind.
- Installing kubectl.
- Installing Helm.
- Installing OpenShift tools.
- Editing Windows networking.
- Editing Docker Desktop Kubernetes settings.
- Making changes outside this project.

Always explain why confirmation is required.

====================================================================
CURRENT APPLICATION (DO NOT REDESIGN)
====================================================================

The application architecture remains:

Browser
        │
qa.carzonrent.com
        │
Apache HTTP Server
        │
Reverse Proxy
        │
Spring Boot
127.0.0.1:8085

Business logic must remain unchanged.

====================================================================
OBJECTIVE
====================================================================

Transform the existing enterprise Docker deployment into a Kubernetes-ready deployment.

The goal is cloud-native deployment.

NOT application redesign.

====================================================================
KUBERNETES
====================================================================

Prepare the application for Kubernetes.

Create production-quality manifests for:

Namespace

Deployment

ReplicaSet

Service

Ingress

ConfigMap

Secret

HorizontalPodAutoscaler

PodDisruptionBudget

NetworkPolicy

PersistentVolumeClaim (if required)

Do not create unnecessary resources.

====================================================================
APPLICATION DEPLOYMENT
====================================================================

Container image should be deployed using:

Deployment

Replica count should be configurable.

Rolling updates must be supported.

Rolling rollback must be documented.

Implement:

Readiness Probe

Liveness Probe

Startup Probe

Use Spring Boot Actuator endpoints.

====================================================================
NETWORKING
====================================================================

Design networking using Kubernetes best practices.

Review:

ClusterIP

NodePort

LoadBalancer

Ingress

Ingress Controller

DNS

Service Discovery

Explain why each resource exists.

====================================================================
CONFIGURATION
====================================================================

Externalize configuration.

Move configuration into:

ConfigMaps

Secrets

Environment Variables

Profiles

Do not hardcode environment-specific values.

====================================================================
SCALING
====================================================================

Prepare the application for scaling.

Implement:

Horizontal Pod Autoscaler

CPU Metrics

Memory Metrics

Resource Requests

Resource Limits

Pod Affinity (where appropriate)

Anti-Affinity recommendations

Explain every scaling decision.

====================================================================
OBSERVABILITY
====================================================================

Integrate with Kubernetes observability.

Prepare:

Prometheus scraping

Grafana dashboards

Metrics endpoint

Application labels

Annotations

Monitoring readiness

Do not install monitoring software unless required.

Prepare manifests only.

====================================================================
SECURITY
====================================================================

Review Kubernetes security.

Implement where appropriate:

SecurityContext

runAsNonRoot

readOnlyRootFilesystem

Resource Quotas

Limit Ranges

Network Policies

Service Accounts

RBAC recommendations

Image pull policy

Image security recommendations

Document every decision.

====================================================================
CI/CD
====================================================================

Extend the existing Jenkins pipeline.

Deployment flow should become:

Git Push

↓

Jenkins

↓

Compile

↓

Unit Tests

↓

Static Analysis

↓

Docker Build

↓

Docker Push

↓

Deploy to Kubernetes

↓

Readiness Check

↓

Smoke Test

↓

Deployment Complete

Document rollback strategy.

====================================================================
HELM
====================================================================

Create production-quality Helm charts.

Parameterize:

Image

Version

Namespace

Replica Count

Resources

Environment Variables

Ingress Host

Service Port

Health Endpoints

Document chart usage.

====================================================================
DOCUMENTATION
====================================================================

Generate enterprise documentation.

Explain:

Pods

ReplicaSets

Deployments

Services

Ingress

ConfigMaps

Secrets

Rolling Updates

Rolling Rollbacks

Horizontal Scaling

Cluster Networking

Service Discovery

Resource Requests

Resource Limits

Helm

Production Deployment Flow

====================================================================
VERIFICATION
====================================================================

Create Kubernetes verification.

Verify:

Deployment

Pods

ReplicaSets

Services

Ingress

Readiness

Liveness

Scaling

DNS

Application Health

Apache

Spring Boot

Reverse Proxy

Observability

Configuration

====================================================================
SELF REVIEW
====================================================================

Continuously review the implementation as if you were:

Principal Cloud Architect

Principal Kubernetes Engineer

Senior SRE

Platform Engineering Lead

After every major implementation ask:

Is this cloud native?

Does it follow Kubernetes best practices?

Can it scale horizontally?

Is it production ready?

Can another team maintain it?

Would this pass an enterprise platform review?

====================================================================
OUT OF SCOPE
====================================================================

Do NOT redesign the application.

Do NOT convert the application into microservices.

Do NOT rewrite Spring Boot.

Do NOT change business logic.

Do NOT remove Apache unless there is a documented enterprise reason.

Keep the deployment architecture recognizable while adapting it for Kubernetes.

====================================================================
FINAL DELIVERABLE
====================================================================

Provide:

1. Complete Kubernetes Architecture Diagram

2. Kubernetes Resource Diagram

3. Helm Chart Structure

4. CI/CD Deployment Flow

5. Scaling Strategy

6. Security Review

7. Observability Architecture

8. Production Deployment Guide

9. Disaster Recovery Strategy

10. Final Enterprise Readiness Report

Evaluate the project as if it were being reviewed for deployment into an enterprise Kubernetes QA cluster.

Do not proceed beyond this phase.