# ADR-002: Docker Deployment

## Context
Deploying directly onto a CentOS host complicates dependency setups, runtime resource monitoring, and horizontal scaling. It also raises container isolation concerns.

## Decision
We enforce full containerization:
- Use a multi-stage Docker build to build application binaries inside a Maven container and copy the result into a minimal runtime CentOS Stream 9 image (`quay.io/centos/centos:stream9`).
- Run the container as an unprivileged system user (`UID/GID 10001`) and drop default Linux kernel privileges (`cap_drop: ALL`).

## Consequences
- **Pros**: Isolated runtime dependencies. Enforced least-privilege container execution. Portability across development, testing, and production.
- **Cons**: Image generation adds a step to compile verification pipelines.
