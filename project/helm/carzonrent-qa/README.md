# Carzonrent QA Helm Chart

This chart deploys the existing Carzonrent QA Apache reverse proxy plus private Spring Boot backend container to Kubernetes.

## Install

```powershell
helm upgrade --install qa .\project\helm\carzonrent-qa `
  --namespace carzonrent-qa `
  --create-namespace `
  --set image.repository=registry.example.com/carzonrent-qa `
  --set image.tag=2.0.0-123-db1cf2c4dea3 `
  --wait --timeout 5m
```

## Important Values

- `image.repository`
- `image.tag`
- `replicaCount`
- `ingress.host`
- `resources`
- `autoscaling`
- `config`
- `build`

## Probes

The chart uses:

- startup: `/health/liveness`
- readiness: `/health/readiness`
- liveness: `/health/liveness`

All probes go through Apache port 80, preserving the deployment architecture.
