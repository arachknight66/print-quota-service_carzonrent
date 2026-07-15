# Apache HTTP Server Load Balancer Operational Guide

**System**: Print Quota Management System  
**Topology**: Active-Active Print Keep Application Workers  
**Target OS**: CentOS Stream 9 (Master VM)  
**Last Updated**: 2026-07

---

## 1. Installation on Master VM

Run the following commands on the master VM to install Apache HTTP Server (`httpd`):

```bash
# Update repository package metadata
sudo dnf makecache

# Install Apache httpd package
sudo dnf install -y httpd
```

Ensure the proxy modules (`mod_proxy`, `mod_proxy_balancer`, `mod_proxy_http`, and `mod_proxy_hcheck`) are loaded by confirming the config directives in your `/etc/httpd/conf/httpd.conf`.

---

## 2. Configuration Setup

Copy the configured [httpd.conf](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/infra/apache/httpd.conf) to the server's directory:

```bash
sudo cp httpd.conf /etc/httpd/conf/httpd.conf
```

### Pre-requisite Edits
Open `/etc/httpd/conf/httpd.conf` and replace `<WORKER1_IP>` and `<WORKER2_IP>` with the actual private IPv4 addresses of your two worker VM instances:

```apache
    BalancerMember "tcp://10.10.5.11:8080"
    BalancerMember "tcp://10.10.5.12:8080"
```

---

## 3. Reloading Configuration (Graceful Reload)

Whenever changes are made to the configuration, reload the service without dropping active TCP connections:

```bash
# Validate config syntax first (critical to do before reloading!)
sudo apachectl configtest

# If syntax is OK, trigger graceful reload
sudo systemctl reload httpd
```

---

## 4. Operational and Troubleshooting Notes

### TCP Passthrough for mTLS (Port 8080)
To comply with the enterprise security architecture:
- Port `8080` (printers) operates in **TCP Passthrough mode** using `tcp://` BalancerMembers.
- Apache acts as a Layer 4 forwarder for this port, passing raw TCP connection blocks to the backends. It does not terminate SSL/TLS.
- **Troubleshooting Implication**: Because the TLS layer is passed raw to the Spring Boot backends, Apache access logs (`logs/access_log`) *cannot* parse or display HTTP headers, URL paths, or request details for print submissions on port 8080. All Layer-7 failures, CN authentication warnings, or rejected print trace logs must be searched directly inside the consolidated worker logs (see logging guide).

### HTTP Operations (Port 8081)
Port `8081` is configured in **HTTP Mode** (`http://`) to manage administrative and self-service APIs. This permits full HTTP logging for employee balance lookups (`/api/v1/me/quota`) and health scraping.
Active health checking is managed by `mod_proxy_hcheck` using the `/actuator/health/readiness` endpoint.

---

## 5. Automated Health Monitoring Script

The [monitor-apache.sh](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/infra/apache/monitor-apache.sh) script monitors Apache backend health and triggers alerts.

To schedule the script to run every 1 minute:
```bash
sudo crontab -e
```

Add the following entry:
```cron
* * * * * /bin/bash /opt/printkeep/infra/apache/monitor-apache.sh >> /var/log/printkeep/monitor.log 2>&1
```
