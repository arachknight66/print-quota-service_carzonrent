# IPP Proxy Server Architecture

This document describes the request execution pipeline, HTTP listener, client pool, and IPP response relays.

---

## 1. IPP Request Flow (Mermaid Diagram)

```mermaid
sequenceDiagram
    autonumber
    actor Spooler as Client Print Spooler
    participant Proxy as IppProxyController
    participant Service as PrinterProxyService
    participant Pipeline as PrintProcessingPipeline
    participant Client as IppHttpClient
    actor Printer as Physical Printer

    Spooler->>Proxy: POST /printers/{name} (binary stream)
    Proxy->>Service: proxyPrintJob(inputStream)
    Service->>Service: Parse IPP attributes block (Header)
    Service->>Pipeline: process(Attributes)
    
    alt Rejected (e.g. Quota Exceeded)
        Pipeline-->>Service: REJECT (PipelineResult)
        Service->>Service: Generate RFC 8011 Error Packet
        Service-->>Spooler: Relay Error Bytes (e.g. 0x0401 status)
    else Allowed
        Pipeline-->>Service: ALLOW
        Service->>Client: sendStream(PrinterURI, SequenceInputStream)
        note over Service, Client: Combines Header bytes + raw body stream
        Client->>Printer: POST application/ipp (Streamed chunks)
        Printer-->>Client: IPP Response Stream
        Client-->>Service: Response Stream
        Service-->>Spooler: Relay printer response unchanged
    end
```

---

## 2. Proxy Architecture

```mermaid
graph TD
    subgraph Client Interface
        Controller[IppProxyController]
    end

    subgraph Core Processing
        Service[PrinterProxyService]
        Partitioner[IppStreamPartitioner]
        Pipeline[PrintProcessingPipeline]
    end

    subgraph Outbound Dispatch
        Client[IppHttpClient]
        Router[PrinterRoutingService]
    end

    Controller -->|Request stream| Service
    Service -->|Bytes read| Partitioner
    Service -->|Attributes check| Pipeline
    Service -->|Get URI| Router
    Service -->|Reassembled sequence stream| Client
```

- **`IppProxyController`**: A Spring MVC controller exposing POST `/printers/{printerName}`. Consumes and produces binary `application/ipp` content types.
- **`PrinterProxyService`**: Intercepts the stream, reads the binary attributes headers up to the `0x03` tag using `IppStreamPartitioner`, routes details through Phase 5 evaluations, and invokes `IppHttpClient` to write composite sequence streams when allowed.
- **`IppResponseGenerator`**: Translates internal pipeline exceptions to standard binary RFC 8011 error packets mapping correct status codes.
