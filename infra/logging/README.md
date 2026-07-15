# RSyslog Centralized Logging Setup

**System**: Print Quota Management System  
**Topology**: Active-Active Log Aggregation to Master VM  
**Target OS**: CentOS Stream 9  
**Last Updated**: 2026-07

---

## 1. Setup Instructions

### On Master VM (Aggregator Server)

**Step 1** — Copy configuration file to `/etc/rsyslog.d/`:
```bash
sudo cp rsyslog-master.conf /etc/rsyslog.d/printkeep-master.conf
```

**Step 2** — Create log output directory and set ownership:
```bash
sudo mkdir -p /var/log/print-quota/aggregated/
sudo chown -R rsyslog:rsyslog /var/log/print-quota/aggregated/
```

**Step 3** — Restart and enable RSyslog daemon:
```bash
sudo systemctl restart rsyslog
```

---

### On Worker VMs (App Instance Clients)

**Step 1** — Copy configuration file to `/etc/rsyslog.d/`:
```bash
sudo cp rsyslog-worker.conf /etc/rsyslog.d/printkeep-worker.conf
```

**Step 2** — Edit `/etc/rsyslog.d/printkeep-worker.conf` and replace `<MASTER_IP>` with the private IPv4 address of the Master VM.

**Step 3** — Restart RSyslog:
```bash
sudo systemctl restart rsyslog
```

---

## 2. Distributed Request Tracing and Troubleshooting

Because requests are distributed across two nodes behind a load balancer, standard log tracking requires searching across all worker log streams.

Since every request carries a unique `correlationId` (added at the entry filter `MdcCorrelationFilter`), you can search all incoming request steps across the cluster from the Master VM.

### Example Search Command
To trace a specific print job submission (`correlationId = 5ea619e0-28b9-4fca-873b-b72e9121a92e`) across all worker instances:

```bash
# Grep correlationId across all host log files in the aggregated folder
grep -r "5ea619e0-28b9-4fca-873b-b72e9121a92e" /var/log/print-quota/aggregated/
```

### Expected Output Structure
```
/var/log/print-quota/aggregated/worker1_app.log: 10:45:12.105 [main] INFO c.p.q.c.p.s.PrinterProxyService - [CorrID: 5ea619e0-28b9-4fca-873b-b72e9121a92e] - Print job ALLOWED. Forwarding stream...
/var/log/print-quota/aggregated/worker1_app.log: 10:45:12.450 [main] INFO c.p.q.c.p.s.PrinterProxyService - [CorrID: 5ea619e0-28b9-4fca-873b-b72e9121a92e] - Forwarding complete. Relayed response...
```
If a request fails on one node and fails over to another, this search command will show the exact trace from both nodes chronological to the timestamp.
