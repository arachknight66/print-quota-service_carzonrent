# Printer Routing Engine

This document details the routing logic resolving logical spooler printer names to physical destination URIs.

---

## 1. Printer Routing Diagram (Mermaid Diagram)

```mermaid
graph TD
    Spooler[Print Spooler Request] -->|Target printerName: LaserJet_5| Proxy[IppProxyController]
    Proxy --> Service[PrinterRoutingService]
    Service -->|Look Up in Map| Config[PrinterConfig]
    
    subgraph Config Map (application.yml)
        LaserJet_5[LaserJet_5 -> http://printer1:631/ipp/print]
        OfficeJet_3[OfficeJet_3 -> http://printer2:631/ipp/print]
    end
    
    Service -->|Resolved URL| Client[IppHttpClient]
    Client -->|Direct IPP HTTP Post| Printer1[LaserJet 5 Physical Printer]
```

---

## 2. Configuration Properties

Routing targets are defined in environment configurations (`application.yml`):
```yaml
app:
  proxy:
    printers:
      LaserJet_5: "http://printer1.company.local:631/ipp/print"
      OfficeJet_3: "https://printer2.company.local:631/ipp/print"
```

These parameters can be overridden using environment variables at container boot:
- `APP_PROXY_PRINTERS_LASERJET_5=http://prod-printer-5:631/ipp/print`

---

## 3. Failure & Failover Routing Considerations

- **Unknown Printers**: If a logical printer name does not map to any configured printer, the proxy short-circuits the pipeline and returns a binary `client-error-not-found` (0x0406) status response immediately.
- **Dynamic Failover (Planned)**: Future iterations of the `PrinterRoutingService` will support listing array values for target printers. If the primary printer is offline, the client will fail over to alternative printers.
