# API Documentation Reference

This document maps all REST endpoints, Spring Actuator paths, and JSON communication schemes available in the system.

---

## 1. REST Endpoint Catalogue

### Get System Health
- **Method**: `GET`
- **URI**: `/actuator/health`
- **Description**: Returns overall system status check. Includes liveness, readiness, and individual components.
- **Headers**:
  - `X-Correlation-ID`: Trace UUID token.
- **Response (200 OK - Healthy)**:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "ldap": {
      "status": "UP",
      "details": {
        "url": "ldap://localhost:389",
        "base": "dc=company,dc=local"
      }
    }
  }
}
```

### Get Custom Health Groups

#### Liveness Check
- **URI**: `/actuator/health/liveness`
- **Description**: Lightweight check to verify the JVM is responsive.
- **Response**: `{"status": "UP"}`

#### Readiness Check
- **URI**: `/actuator/health/readiness`
- **Description**: Evaluates if the service is ready to handle print traffic. Verifies Postgres database and LDAP directory connections.
- **Response**: `{"status": "UP"}`

#### Startup Check
- **URI**: `/actuator/health/startup`
- **Description**: Verifies database migration is finished and application startup is complete.
- **Response**: `{"status": "UP"}`

---

## 2. Container System Information

### Get Container Info
- **Method**: `GET`
- **URI**: `/actuator/info`
- **Description**: Exposes hostname, CPU cores, JVM memory limits, and OS versions for the active container runtime.
- **Response (200 OK)**:
```json
{
  "app": {
    "name": "print-quota-service",
    "version": "1.0.0"
  },
  "container": {
    "hostname": "print-quota-service-5c4d",
    "osName": "Linux",
    "osVersion": "5.15.0-generic",
    "javaVersion": "21.0.11",
    "maxMemoryMb": 1024,
    "availableProcessors": 4
  }
}
```

---

## 3. Request Correlation Headers

All API requests pass through the MDC tracing filter.
- **Request Header**: `X-Correlation-ID` (Optional). If not supplied, the system generates a unique UUID.
- **Response Header**: `X-Correlation-ID`. The header is set on the response to allow clients to tie logs to requests.
- **Log Format**: The correlation ID binds to Logback MDC (`correlationId`), printing as `[CorrID: <uuid>]` in stdout logs.

---

## 4. Error Responses Matrix

When errors occur, they are resolved by `GlobalExceptionHandler` returning structured JSON error schemas:

| Status Code | Scenario | JSON Response Schema |
|---|---|---|
| `400 Bad Request` | Malformed parameters, missing requesting-user-name, parsing errors | `{"status":400,"error":"Bad Request","message":"Validation failed","correlationId":"<uuid>"}` |
| `404 Not Found` | Requesting unknown Actuator paths | `{"status":404,"error":"Not Found","message":"No static resource actuator/invalid","correlationId":"<uuid>"}` |
| `500 Internal Error` | Database connection failures, unexpected pipeline crashes | `{"status":500,"error":"Internal Server Error","message":"Database timeout","correlationId":"<uuid>"}` |
| `503 Service Unavailable` | LDAP context pool exhausted | `{"status":503,"error":"Service Unavailable","message":"LDAP directory connection failed","correlationId":"<uuid>"}` |
