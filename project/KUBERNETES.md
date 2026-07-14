# Phase 5 Kubernetes Readiness Guide

Phase 5 migrates the existing enterprise QA container deployment into Kubernetes-ready platform assets. The business application and in-container architecture remain unchanged:

```text
Browser
  |
  v
Ingress qa.carzonrent.com
  |
  v
Service ClusterIP :80
  |
  v
Pod
  |
  v
Apache HTTP Server :80
  |
  | ProxyPass / http://127.0.0.1:8085/
  v
Spring Boot 127.0.0.1:8085
```

Apache remains the only public HTTP entry point in the pod. Spring Boot remains private on pod loopback.

## Kubernetes Resource Diagram

```text
Namespace carzonrent-qa
  |
  +-- ResourceQuota
  +-- LimitRange
  +-- ServiceAccount
  +-- ConfigMap
  +-- Secret
  +-- Deployment
  |     |
  |     +-- ReplicaSet
  |           |
  |           +-- Pods x N
  |
  +-- Service ClusterIP
  +-- Ingress
  +-- HorizontalPodAutoscaler
  +-- PodDisruptionBudget
  +-- NetworkPolicy
```

## Helm Chart Structure

```text
project/helm/carzonrent-qa/
  Chart.yaml
  values.yaml
  templates/
    _helpers.tpl
    namespace.yaml
    serviceaccount.yaml
    configmap.yaml
    secret.yaml
    deployment.yaml
    service.yaml
    ingress.yaml
    hpa.yaml
    pdb.yaml
    networkpolicy.yaml
    resourcequota.yaml
    limitrange.yaml
    NOTES.txt
```

## Resource Rationale

- `Namespace`: isolates QA platform objects.
- `Deployment`: manages pods, rolling updates, and ReplicaSets.
- `ReplicaSet`: created and owned by the Deployment.
- `Service`: stable ClusterIP endpoint for pods.
- `Ingress`: HTTP entry point for `qa.carzonrent.com`.
- `ConfigMap`: externalizes non-secret environment configuration.
- `Secret`: placeholder for future sensitive settings; no real secrets are committed.
- `HorizontalPodAutoscaler`: scales on CPU and memory utilization.
- `PodDisruptionBudget`: keeps at least one pod available during voluntary disruptions.
- `NetworkPolicy`: restricts pod ingress to port 80 and permits DNS egress.
- `ResourceQuota` and `LimitRange`: protect namespace capacity and default resource behavior.
- `PersistentVolumeClaim`: intentionally not created because the app is stateless and logs to stdout/stderr.

## Networking Strategy

The chart uses `ClusterIP` for internal service discovery because traffic should enter through Ingress, not direct node ports. `NodePort` is not used by default because it exposes node-level ports and bypasses standard enterprise ingress controls. `LoadBalancer` is left to the ingress controller or cloud platform.

The Ingress maps `qa.carzonrent.com` to the service on port 80. DNS should point the hostname to the ingress controller address. TLS is parameterized but disabled by default because certificate management depends on the enterprise cluster.

## Configuration Strategy

Configuration is environment-driven:

- Spring profile
- server address and port
- application/environment names
- logging levels
- build and Git metadata
- image tag and Jenkins build number

The backend address remains loopback-only through application validation.

## Scaling Strategy

The default replica count is `2` to support rolling updates and basic resilience. HPA is enabled by default with:

- CPU target: 70 percent
- Memory target: 80 percent
- Minimum replicas: 2
- Maximum replicas: 5

Resource requests and limits are set so the scheduler and HPA have useful signals. Pod anti-affinity is not enabled by default to avoid unsafe empty selectors; production clusters should enable topology-aware spread rules once node labels and availability zones are known.

## Security Review

Implemented:

- Service account with token automount disabled.
- Pod `RuntimeDefault` seccomp profile.
- Container `allowPrivilegeEscalation: false`.
- Linux capabilities dropped with `NET_BIND_SERVICE` added for Apache port 80.
- NetworkPolicy for ingress and DNS egress.
- Namespace quota and limit range.
- Secrets handled through Kubernetes Secret templates, with placeholder values only.
- Prometheus scraping is opt-in through annotations and does not expose Spring Boot directly.

Known limitation:

The current image starts Apache on port 80. Full `runAsNonRoot` and `readOnlyRootFilesystem` should be implemented in a future image-hardening phase by moving Apache to a non-privileged port or redesigning the container entrypoint. This chart does not claim those settings because they would be misleading for the current image.

## Observability Architecture

The chart exposes existing Phase 4 observability through Kubernetes:

- `/health/readiness` for readiness probe
- `/health/liveness` for liveness and startup probes
- `/metrics` for Actuator metric discovery
- `/prometheus` for future Prometheus scraping
- structured logs to stdout/stderr for cluster log collectors
- labels and annotations for platform discovery

Prometheus and Grafana are not installed by this phase.

## CI/CD Deployment Flow

```text
Git Push
  -> Jenkins
  -> Compile
  -> Unit Tests
  -> Static Analysis
  -> Docker Build
  -> Docker Push
  -> Helm Render Validation
  -> Helm Upgrade --Install
  -> Rollout Status
  -> Kubernetes Verification
  -> Deployment Complete
```

The Jenkinsfile supports `DEPLOY_TARGET=docker` for the existing QA Docker deployment and `DEPLOY_TARGET=kubernetes` for cluster deployment. Kubernetes deployment assumes `kubectl`, `helm`, cluster credentials, and registry credentials already exist on the Jenkins agent.

## Helm Usage

Render locally:

```powershell
helm template qa .\project\helm\carzonrent-qa `
  --namespace carzonrent-qa `
  --set image.repository=registry.example.com/carzonrent-qa `
  --set image.tag=2.0.0-123-db1cf2c4dea3
```

Install or upgrade:

```powershell
helm upgrade --install qa .\project\helm\carzonrent-qa `
  --namespace carzonrent-qa `
  --create-namespace `
  --set image.repository=registry.example.com/carzonrent-qa `
  --set image.tag=2.0.0-123-db1cf2c4dea3 `
  --wait --timeout 5m
```

Rollback:

```powershell
helm rollback qa 1 --namespace carzonrent-qa --wait
```

Kubernetes-native rollback:

```powershell
kubectl -n carzonrent-qa rollout undo deployment/qa-carzonrent-qa
```

## Verification

Offline chart asset verification:

```powershell
.\project\verify-kubernetes-assets.ps1
```

Cluster verification:

```powershell
.\project\verify-kubernetes.ps1 -Namespace carzonrent-qa -ReleaseName qa -IngressHost qa.carzonrent.com
```

The cluster verifier checks namespace, deployment, pods, ReplicaSet, Service, Ingress, HPA, PDB, NetworkPolicy, ConfigMap, Secret, readiness, liveness, and Prometheus endpoint readiness.

## Disaster Recovery Strategy

- Use immutable image tags for every release.
- Keep Helm release history.
- Roll back with `helm rollback` or `kubectl rollout undo`.
- Recreate the namespace from chart values and a known image tag.
- Keep Secrets externalized and backed by the enterprise secret source.
- Treat ConfigMap and values files as version-controlled recovery inputs.

## Enterprise Readiness Assessment

Ready for enterprise Kubernetes QA review:

- Deployment is parameterized and reproducible.
- Rolling updates and rollbacks are supported.
- Health probes are actuator-backed.
- HPA, PDB, NetworkPolicy, quota, and limit range are included.
- Observability endpoints are exposed for future monitoring.
- CI/CD has a Kubernetes deployment path.

Not yet production complete:

- TLS/certificate automation must be integrated with the enterprise ingress controller.
- Image should be refactored for full non-root and read-only root filesystem operation.
- Real registry, cluster, and secret manager integrations must be supplied by the platform team.
- Prometheus/Grafana resources should be added when the monitoring stack is selected.
