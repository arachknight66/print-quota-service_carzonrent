# Phase 3 CI/CD Architecture

Phase 3 automates the existing QA Docker deployment. It does not introduce Kubernetes, Helm, load balancing, or a new application architecture.

## CI/CD Architecture

```text
Developer Commit
      |
      v
Jenkins Pipeline
      |
      +--> Maven clean/compile/test/verify
      +--> Quality gates: JaCoCo, Checkstyle, PMD, SpotBugs, CycloneDX SBOM
      +--> Docker image build with build metadata
      +--> QA container replacement
      +--> Health and verification suite
      |
      v
QA Docker Runtime
      |
      v
Apache :80 -> Spring Boot 127.0.0.1:8085
```

## Jenkins Pipeline

```text
Checkout
  -> Environment Validation
  -> Compile
  -> Unit Tests
  -> Integration Tests
  -> Static Analysis
  -> Package
  -> Docker Build
  -> Container Verification
  -> Deploy QA
  -> Post Deployment Verification
```

The pipeline fails immediately when any stage exits non-zero. `disableConcurrentBuilds()` prevents overlapping deployments to the same QA container.

## Kubernetes Deployment Path

Phase 5 extends the Jenkinsfile with `DEPLOY_TARGET`.

- `DEPLOY_TARGET=docker`: preserves the existing local Docker QA deployment flow.
- `DEPLOY_TARGET=kubernetes`: validates the Helm chart, optionally pushes the image, deploys with `helm upgrade --install`, waits for rollout, and runs `verify-kubernetes.ps1`.

The Kubernetes path assumes the Jenkins agent already has Docker, Helm, kubectl, cluster credentials, and registry credentials. Installing or configuring those tools is intentionally outside the project pipeline.

## Maven Lifecycle

- `clean compile`: validates source compilation.
- `test`: runs unit-level Spring MVC tests through Surefire.
- `failsafe:integration-test failsafe:verify`: reserves the integration-test stage for future `*IT` tests.
- `-Pci-quality verify`: runs tests plus quality gates and SBOM generation.
- `package -DskipTests`: packages the already verified artifact for archiving.

## Quality Gates

The `ci-quality` Maven profile includes:

- JaCoCo coverage report and minimum instruction coverage gate.
- Checkstyle import and brace rules.
- PMD focused best-practice and error-prone rules.
- SpotBugs high-confidence bytecode analysis.
- CycloneDX SBOM generation in XML and JSON.
- OWASP Dependency Check configuration prepared but skipped by default until a managed NVD mirror/API policy is available.

SonarQube is intentionally not required in Phase 3. The generated reports can be fed into SonarQube later.

## Versioning Strategy

Each pipeline run computes:

- Application version from `project/app/pom.xml`.
- Git commit ID from `git rev-parse --short=12 HEAD`.
- UTC build timestamp.
- Jenkins build number.
- Docker image tag: `<app-version>-<jenkins-build-number>-<git-commit>`.

The same values are passed as Docker build args, image labels, container environment variables, `/info` fields, and dashboard values.

## Docker Build Process

The Dockerfile keeps the Phase 2 multi-stage model. The build stage runs `mvn -Pci-quality verify` using Java 21 before producing the runtime jar. The runtime image contains CentOS Stream 9, Apache, mod_ssl, Java 21 headless, curl, and the application artifact.

## Deployment Flow

`deploy-qa.ps1` automates QA deployment:

1. Preserve the current `qa.carzonrent` container as `qa.carzonrent.previous`.
2. Start the new image as `qa.carzonrent`.
3. Wait for Docker health to become `healthy`.
4. Run `verify.ps1`.
5. Remove the rollback container after successful verification.
6. Optionally remove old unused `carzonrent-qa` image tags.

The script keeps host port `8081` by default. Publishing host port `80` or editing the Windows hosts file remains a host-level change requiring confirmation.

## Rollback Strategy

If the new container fails to start, become healthy, or pass verification, the script removes the failed candidate and restores `qa.carzonrent.previous` back to `qa.carzonrent`. Rollback is possible whenever a previous container existed.

## Verification Flow

The verification suite checks:

- Container running and healthy.
- Apache configuration and proxy modules.
- Spring Boot loopback binding.
- Backend port `8085` not published.
- Reverse proxy path.
- `/`, `/health`, `/info`, CSS, and 404.
- Process supervision and container user.
- Logs and startup messages.
- Java 21 runtime.
- Build metadata in `/info`.
- Docker networking and port mapping.
