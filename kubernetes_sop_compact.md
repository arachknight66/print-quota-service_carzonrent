# Standard Operating Procedure: Kubernetes Cluster Provisioning & Configuration

**Document Reference:** SOP-OPS-K8S-001  
**Target OS:** CentOS Stream 9  
**Deployment Tool:** kubeadm  
**Runtime:** containerd  
**CNI:** Calico  

---

## 1. Introduction

### 1.1 Purpose
This Standard Operating Procedure (SOP) defines the operational workflow for provisioning, configuring, and validating a generic production-ready Kubernetes cluster using `kubeadm` on CentOS Stream 9 hosts.

### 1.2 Scope
This document covers the end-to-end setup of a single-control-plane cluster with two worker nodes. It details OS-level tuning, dependency installations, networking configurations, deployment of core components, basic resource allocation, and troubleshooting procedures.

### 1.3 Cluster Architecture Diagram
The architecture establishes a centralized Control Plane orchestrating node-level agents (`kubelet`) on dedicated Worker Nodes. Communication is encrypted via Mutual TLS (mTLS) over port 6443.

```
       [ DevOps Administrator / CI-CD ]
                      |
                      | kubectl (Port 6443)
                      v
 +------------------------------------------+
 |           k8s-controlplane               |
 |  +------------------------------------+  |
 |  |           kube-apiserver           |  |
 |  +------------------------------------+  |
 |    | (mTLS)          |              |    |
 |    v                 v              v    |
 | [ etcd ]     [ Scheduler ]    [ Controller ]
 +------------------------------------------+
      |                  |
      | (Port 10250)     | (Port 10250)
      v                  v
 +------------------+   +------------------+
 |    k8s-worker1   |   |    k8s-worker2   |
 |  +------------+  |   |  +------------+  |
 |  |  kubelet   |  |   |  |  kubelet   |  |
 |  +------------+  |   |  +------------+  |
 |  +------------+  |   |  +------------+  |
 |  | containerd |  |   |  | containerd |  |
 |  +------------+  |   |  +------------+  |
 |  +------------+  |   |  +------------+  |
 |  | kube-proxy |  |   |  | kube-proxy |  |
 |  +------------+  |   |  +------------+  |
 +------------------+   +------------------+
```

---

## 2. Prerequisites

### 2.1 Hardware Requirements
Ensure each host meets the minimal hardware baselines. The scheduler will fail to assign workloads if resources are over-committed at the system level.

| Node Role | CPU (vCPU Cores) | RAM (GB) | Disk Space (GB) | Hostname |
| :--- | :---: | :---: | :---: | :--- |
| **Control Plane** | 2 Cores (Dedicated) | 4 GB | 50 GB (SSD preferred) | `k8s-controlplane.local` |
| **Worker Node 1** | 2 Cores | 4 GB | 50 GB | `k8s-worker1.local` |
| **Worker Node 2** | 2 Cores | 4 GB | 50 GB | `k8s-worker2.local` |

### 2.2 Network Requirements & Static IPs
Dynamic IP shifts will invalidate cluster TLS certificates, breaking communication. Static IPs are mandatory.

- **Pod Subnet CIDR Range:** `172.16.0.0/16`
- **Service Subnet CIDR Range:** `10.96.0.0/16`

| FQDN | Hostname | IP Address | Subnet Mask | Gateway |
| :--- | :--- | :--- | :--- | :--- |
| `k8s-controlplane.local` | `k8s-controlplane` | `192.168.10.10` | `255.255.255.0` | `192.168.10.1` |
| `k8s-worker1.local` | `k8s-worker1` | `192.168.10.11` | `255.255.255.0` | `192.168.10.1` |
| `k8s-worker2.local` | `k8s-worker2` | `192.168.10.12` | `255.255.255.0` | `192.168.10.1` |

### 2.3 Hostname and Local DNS Resolution
Hostnames must conform to RFC 1123 constraints (lowercase letters, numbers, and hyphens only).

Execute on the respective hosts:
```bash
# On Control Plane
sudo hostnamectl set-hostname k8s-controlplane.local

# On Worker 1
sudo hostnamectl set-hostname k8s-worker1.local

# On Worker 2
sudo hostnamectl set-hostname k8s-worker2.local
```

Ensure all nodes can resolve each other. Append cluster records to `/etc/hosts` on **all nodes**:
```bash
sudo bash -c 'cat <<EOF >> /etc/hosts
192.168.10.10 k8s-controlplane.local k8s-controlplane
192.168.10.11 k8s-worker1.local k8s-worker1
192.168.10.12 k8s-worker2.local k8s-worker2
EOF'
```

### 2.4 Time Synchronization
Cryptographic handshakes fail if clock skew between the nodes exceeds 500ms. Install and enable `chrony` on **all nodes**:
```bash
# Install chrony client
sudo dnf install -y chrony

# Start and enable the chrony service
sudo systemctl enable --now chronyd
```
*Verification:*
```bash
chronyc sources
```

### 2.5 Firewall Configuration
The firewall must allow control-plane APIs and CNI VXLAN routing. Execute the following on the **Control Plane**:
```bash
sudo firewall-cmd --permanent --add-port={6443,2379-2380,10250,10257,10259}/tcp
sudo firewall-cmd --permanent --add-port=4789/udp
sudo firewall-cmd --reload
```
Execute the following on **both Worker Nodes**:
```bash
sudo firewall-cmd --permanent --add-port={10250,30000-32767}/tcp
sudo firewall-cmd --permanent --add-port=4789/udp
sudo firewall-cmd --reload
```

### 2.6 SELinux and Swap Configuration
SELinux blocks system container namespace interactions. Swap bypasses Kubernetes memory limit enforcement, which can destabilize host nodes. Both must be adjusted on **all nodes**.

Disable SELinux:
```bash
# Force permissive mode immediately
sudo setenforce 0
# Persist settings across reboots
sudo sed -i 's/^SELINUX=enforcing$/SELINUX=permissive/' /etc/selinux/config
```

Disable Swap:
```bash
# Turn off swap immediately
sudo swapoff -a
# Remove swap mounts from permanent file table
sudo sed -i '/swap/d' /etc/fstab
```

### 2.7 Kernel Modules and sysctl Properties
The kernel must allow bridge netfilter actions so host iptables rules apply to bridged container packets.

Run on **all nodes**:
```bash
# Load overlay and bridge netfilter modules
sudo modprobe overlay
sudo modprobe br_netfilter

# Persist modules configuration
sudo bash -c 'cat <<EOF > /etc/modules-load.d/k8s.conf
overlay
br_netfilter
EOF'

# Configure network forwarding parameters
sudo bash -c 'cat <<EOF > /etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF'

# Apply configuration changes immediately
sudo sysctl --system
```

---

## 3. Installing Kubernetes Components

### 3.1 Container Runtime (containerd)
Kubernetes executes pods through container runtimes conforming to the Container Runtime Interface (CRI). Install containerd on **all nodes**:

```bash
# Add Docker CE repository
sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo

# Install containerd
sudo dnf install -y containerd.io
```

#### Configure Systemd Cgroup Driver
Using cgroupfs for containers while the host system init system uses systemd creates double cgroup structures, which degrades host stability under stress. Set containerd to use the systemd driver:
```bash
# Generate default configuration
sudo mkdir -p /etc/containerd
containerd config default | sudo tee /etc/containerd/config.toml > /dev/null

# Set SystemdCgroup parameter to true
sudo sed -i 's/SystemdCgroup = false/SystemdCgroup = true/g' /etc/containerd/config.toml

# Start runtime service
sudo systemctl daemon-reload
sudo systemctl enable --now containerd
```
*Verification:*
```bash
sudo systemctl status containerd --no-pager
```

### 3.2 Kubernetes Packages (kubeadm, kubelet, kubectl)
Add the official Kubernetes packages to the repository manager on **all nodes**:

```bash
# Create repository definition
sudo bash -c 'cat <<EOF > /etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=https://pkgs.k8s.io/core/stable/v1.30/rpm/
enabled=1
gpgcheck=1
gpgkey=https://pkgs.k8s.io/core/stable/v1.30/rpm/repodata/repomd.xml.key
exclude=kubelet kubeadm kubectl cri-tools kubernetes-cni
EOF'

# Install components bypassing repository exclusions
sudo dnf install -y kubelet kubeadm kubectl --disableexcludes=kubernetes

# Lock versions to prevent accidental runtime mismatches
sudo dnf install -y python3-dnf-plugin-versionlock
sudo dnf versionlock add kubelet kubeadm kubectl
```

Enable the `kubelet` agent service on all nodes:
```bash
sudo systemctl enable --now kubelet
```

---

## 4. Configuring the Control Plane (Master Node)

### 4.1 Cluster Initialization
Run this step **only** on the Control Plane node (`k8s-controlplane.local`):

```bash
sudo kubeadm init \
  --apiserver-advertise-address=192.168.10.10 \
  --pod-network-cidr=172.16.0.0/16 \
  --service-cidr=10.96.0.0/16
```
- `--apiserver-advertise-address`: The local interface IP the api-server binds to.
- `--pod-network-cidr`: The virtual network block allocated to Pod IP addresses.
- `--service-cidr`: The virtual IP network block used by internal services.

### 4.2 Configure Local kubectl Access
To authorize admin operations using CLI tools, run the following commands as a non-root user:
```bash
mkdir -p $HOME/.kube
sudo cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
sudo chown $(id -u):$(id -g) $HOME/.kube/config
```

### 4.3 Verification
Verify that the control plane APIs are accessible:
```bash
kubectl cluster-info
```

*Common Mistake:* Running `kubectl` commands as root without exporting `KUBECONFIG` or copying files to `$HOME/.kube/config`. This will cause connection refused errors.

---

## 5. Configuring Worker Nodes

### 5.1 Join Workers to Cluster
Copy the exact `kubeadm join` command outputted by the initialization step, and run it on **both Worker Nodes**:

```bash
sudo kubeadm join 192.168.10.10:6443 \
  --token abcdef.1234567890abcdef \
  --discovery-token-ca-cert-hash sha256:7a9289d0b674892bcf7429184a29a0fbc55f9a6501a4e12e96b7adcf6ef16e53
```

> [!TIP]
> If your token expires, generate a new connection string on the control plane node:
> `kubeadm token create --print-join-command`

### 5.2 Verification
On the Control Plane node, check that the workers registered:
```bash
kubectl get nodes
```
Expected Output:
```
NAME                    STATUS     ROLES           AGE     VERSION
k8s-controlplane.local   NotReady   control-plane   5m20s   v1.30.0
k8s-worker1.local       NotReady   <none>          45s     v1.30.0
k8s-worker2.local       NotReady   <none>          38s     v1.30.0
```
Nodes will remain `NotReady` until a Container Network Interface (CNI) is configured to handle inter-node routing.

---

## 6. Installing the CNI Plugin

### 6.1 Container Network Interface Comparison
Pods must be able to resolve and reach each other across different hosts.

- **Flannel:** Simple, lightweight VXLAN overlay. Lacks NetworkPolicy support.
- **Cilium:** eBPF-driven performance routing. Complex to administer, requires modern kernels.
- **Calico:** High-performance routing supporting both overlay (VXLAN/IPIP) and flat BGP routing. Fully supports NetworkPolicies.

### 6.2 Installing Calico CNI
Install the Calico operator and apply network custom resource manifests configured for our Pod CIDR block (`172.16.0.0/16`):

```bash
# Deploy operator structure
kubectl create -f https://raw.githubusercontent.com/projectcalico/calico/v3.27.3/manifests/tigera-operator.yaml

# Download the custom resource template
curl -O https://raw.githubusercontent.com/projectcalico/calico/v3.27.3/manifests/custom-resources.yaml

# Edit default CIDR range to match 172.16.0.0/16
sed -i 's|cidr: 192.168.0.0/16|cidr: 172.16.0.0/16|g' custom-resources.yaml

# Apply the custom resource manifest
kubectl create -f custom-resources.yaml
```

### 6.3 Verification
Track the status of the network controllers:
```bash
kubectl get pods -n calico-system -w
```
Wait for all system pods to show `Running`. Verify that the nodes now report a `Ready` status:
```bash
kubectl get nodes
```

---

## 7. Post-Installation Cluster Configuration

### 7.1 Setup Workspace Namespaces
Create a namespace named `staging` and set it as the default target for the current kubectl shell environment:

```yaml
# namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: staging
```
Apply the configuration:
```bash
kubectl apply -f namespace.yaml
# Set active namespace target
kubectl config set-context --current --namespace=staging
```

### 7.2 Label Nodes
Assign metadata tags to target worker nodes to handle workload placements:
```bash
kubectl label nodes k8s-worker1.local hardware=ssd
```

### 7.3 Taints and Tolerations
Taints repel pods from nodes. Prevent standard workloads from running on `k8s-worker2.local` unless they explicitly tolerate the taint:
```bash
kubectl taint nodes k8s-worker2.local workload=special:NoSchedule
```

### 7.4 ConfigMap and Secrets
Store environment configuration variables and secrets:

```yaml
# configmap-secret.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
  namespace: staging
data:
  API_URL: "https://api.internal.local"
---
apiVersion: v1
kind: Secret
metadata:
  name: app-secret
  namespace: staging
type: Opaque
data:
  # Base64 encoded: "AdminPassword"
  DB_PASS: QWRtaW5QYXNzd29yZA==
```
Apply the configurations:
```bash
kubectl apply -f configmap-secret.yaml
```

### 7.5 Role-Based Access Control (RBAC)
RBAC binds authorization rules to subjects (Users, Groups, or ServiceAccounts). Roles define API permissions within a namespace, while RoleBindings bind those permissions to subjects.

```yaml
# rbac-developer.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: dev-sa
  namespace: staging
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: dev-role
  namespace: staging
rules:
- apiGroups: [""]
  resources: ["pods", "services"]
  verbs: ["get", "list", "watch", "create", "update", "delete"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: dev-role-binding
  namespace: staging
subjects:
- kind: ServiceAccount
  name: dev-sa
  namespace: staging
roleRef:
  kind: Role
  name: dev-role
  apiGroup: rbac.authorization.k8s.io
```
Apply the configuration:
```bash
kubectl apply -f rbac-developer.yaml
```

---

## 8. Deploying a Test Application

### 8.1 Deployment Specification
Create an NGINX frontend configuration using the config values, secrets, and node selectors configured in previous steps:

```yaml
# nginx-test.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-test
  namespace: staging
spec:
  replicas: 2
  selector:
    matchLabels:
      app: nginx-web
  template:
    metadata:
      labels:
        app: nginx-web
    spec:
      nodeSelector:
        hardware: ssd
      containers:
      - name: nginx
        image: nginx:1.25-alpine
        ports:
        - containerPort: 80
        env:
        - name: API_ENDPOINT
          valueFrom:
            configMapKeyRef:
              name: app-config
              key: API_URL
        - name: DATABASE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: app-secret
              key: DB_PASS
---
apiVersion: v1
kind: Service
metadata:
  name: nginx-test-svc
  namespace: staging
spec:
  type: NodePort
  ports:
  - port: 80
    targetPort: 80
    nodePort: 32080
  selector:
    app: nginx-web
```
Apply the application deployment:
```bash
kubectl apply -f nginx-test.yaml
```

### 8.2 Verification
Verify the application state and access the web endpoint:
```bash
# Check running pods
kubectl get pods -l app=nginx-web

# Query the NodePort service
curl http://192.168.10.11:32080
```

---

## 9. Cluster Validation

Execute the validation runbook to confirm cluster health:

```bash
# 1. Check health statuses of host nodes
kubectl get nodes -o wide

# 2. Check all cluster components status across all namespaces
kubectl get pods -A

# 3. Verify internal routing endpoints
kubectl get svc -n staging

# 4. Run internal resolution test
kubectl run dns-test --rm -i --tty --image=busybox --restart=Never -- nslookup kubernetes.default
```

### Health Checklist

- [ ] Node Statuses = `Ready`
- [ ] CoreDNS Pod Statuses = `Running`
- [ ] Calico Node Pods = `Running`
- [ ] Local DNS lookup = `Resolves successfully`
- [ ] Application curl = `Returns HTTP 200`

---

## 10. Troubleshooting

### 10.1 Node Status is NotReady
* **Symptoms:** Node is reported as `NotReady`.
* **Possible Cause:** Kubelet stopped running, or the host filesystem is full.
* **Diagnosis:**
  ```bash
  systemctl status kubelet
  journalctl -u kubelet -n 50 --no-pager
  df -h
  ```
* **Resolution:** Clear disk space or restart the system daemon: `sudo systemctl restart kubelet`.

### 10.2 Pods Stuck in Pending
* **Symptoms:** Pods remain in `Pending` state indefinitely.
* **Possible Cause:** Missing scheduling requirements, such as taints, tolerations, or unsatisfied node affinity rules.
* **Diagnosis:**
  ```bash
  kubectl describe pod <pod-name>
  ```
  Check the `Events` log section for scheduling failure messages.

### 10.3 ImagePullBackOff / ErrImagePull
* **Symptoms:** Pod logs show registry connection timeouts or credential errors.
* **Possible Cause:** Typo in image name, or missing private repository secrets.
* **Diagnosis:**
  ```bash
  kubectl describe pod <pod-name>
  ```
* **Resolution:** Correct the image name or define an `imagePullSecrets` array in the deployment manifest.

### 10.4 Worker Node Join Failure
* **Symptoms:** Join command errors out or worker node fails to register with the control plane.
* **Possible Cause:** Expired bootstrap token, or blocked firewall ports.
* **Diagnosis:**
  ```bash
  # Check connection to API Server
  curl -k https://192.168.10.10:6443/version
  ```
* **Resolution:** Generate a new bootstrap token on the control plane, and verify that firewalld rules are configured to permit traffic over port 6443.

---

## 11. Useful kubectl Commands

### Nodes & Workloads
```bash
# Retrieve nodes
kubectl get nodes -o wide

# Check pod resource utilization (requires Metrics Server)
kubectl top pods

# Retrieve pods in all namespaces
kubectl get pods -A
```

### Inspecting & Diagnostics
```bash
# Inspect resource details
kubectl describe pod <pod-name>

# View container logs
kubectl logs <pod-name>

# View previous container logs after a crash
kubectl logs <pod-name> --previous

# Check cluster events sorted by time
kubectl get events --sort-by='.metadata.creationTimestamp'
```

### Context Management
```bash
# View active contexts
kubectl config get-contexts

# Set default namespace target permanently
kubectl config set-context --current --namespace=<target-namespace>
```
---
*End of Standard Operating Procedure Document*
