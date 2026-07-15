# Kubernetes Production Deployment Guide

**System**: Print Quota Management System  
**Target Platform**: Kubernetes (EKS, GKE, AKS, or bare-metal K3s/microk8s)  
**Namespace**: `print-quota`  
**Last Updated**: 2026-07

---

## 1. Setup and Preparation

Ensure you have `kubectl` configured and connected to the target Kubernetes cluster.

### Step 1 — Create the Namespace
```bash
kubectl create namespace print-quota
```

### Step 2 — Verify and Deploy Configs and Secrets
Edit the [configmap-secrets.yaml](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/infra/k8s/configmap-secrets.yaml) to supply your production-grade credentials (DB passwords, LDAP bind passwords, etc.), then apply it:

```bash
kubectl apply -f configmap-secrets.yaml
```

---

## 2. Deploying Stateful Components

### Step 1 — PostgreSQL (StatefulSet)
This deploys PostgreSQL with a 10Gi Persistent Volume Claim (PVC) to guarantee data persistence:

```bash
kubectl apply -f postgres-statefulset.yaml
```

### Step 2 — OpenLDAP (Deployment)
This deploys the Active Directory sync source:

```bash
kubectl apply -f ldap-deployment.yaml
```

Wait until both database and LDAP pods report as `Running` and `Ready`:
```bash
kubectl get pods -n print-quota -w
```

---

## 3. Deploying the Application

### Step 1 — Build and Push the Container Image
Ensure you build your production JAR and compile the Docker image:

```bash
# Compile host Jar
./mvnw clean package -pl print-quota-core -Djacoco.skip=true

# Build Docker container image
docker build -t your-registry.company.local/print-quota-core:latest -f docker/app/Dockerfile .

# Push image to corporate registry
docker push your-registry.company.local/print-quota-core:latest
```

### Step 2 — Deploy the App
Edit the image path in [app-deployment.yaml](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/infra/k8s/app-deployment.yaml) to point to your registry path, then apply it:

```bash
kubectl apply -f app-deployment.yaml
```

---

## 4. Service Exposure and Load Balancing

Expose the print quota application to external printer clients:

```bash
kubectl apply -f app-service.yaml
```

### Preservation of Client CN and source IP
In [app-service.yaml](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/infra/k8s/app-service.yaml), we set:
```yaml
  externalTrafficPolicy: Local
```
This forces the Kubernetes ingress/cloud load balancer node to forward TCP packets directly to the host hosting the target pod *without* masking the client IP with SNAT. This is critical for matching client SSL certificates during mTLS negotiations.

---

## 5. Operations and Troubleshooting

### View Application Logs
```bash
kubectl logs -f deployment/print-quota-app -n print-quota -c print-quota-app
```

### Run database migrations validation
Database schema migrations run automatically via Liquibase inside the container startup sequence. Verify tables are initialized by checking the startup logs.
