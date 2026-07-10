# ADR-009: MDC Correlation IDs for Request Tracing

## Context
Tracing a specific print request's logs across asynchronous execution pipelines or multiple threads is difficult.

## Decision
We implement `MdcCorrelationFilter` which extracts or generates a unique correlation ID (`X-Correlation-ID`) for every request. This ID is bound to Logback's Thread MDC context.

## Consequences
- **Pros**: Clear end-to-end request tracing in logs. Facilitates troubleshooting.
- **Cons**: Small overhead for filter interception and thread context management.
