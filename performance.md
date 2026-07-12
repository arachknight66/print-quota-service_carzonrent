# Performance Tuning Blueprint

This document details the JVM configurations, streaming buffer allocations, and database query optimizations for high-throughput print queue processing.

---

## 1. Streaming Buffer Allocations

- **`IppStreamPartitioner` Buffer**: Reads bytes up to `0x03` tag using a small, local buffer (`4KB`), preventing memory allocations for attributes extraction.
- **`IppHttpClient` Buffer**: Streams binary payload directly to physical printers using `InputStream.transferTo(OutputStream)` which defaults to a `8KB` buffer size. No file loading or string copying is performed.

---

## 2. Database Indexes & Query Optimizations

To prevent table scans under high concurrent log volumes:
- **Index `idx_users_domain_username`**: Speeds up user resolution during print validation.
- **Index `idx_quotas_user_month`**: Ensures the pessimistic write lock operations locate target user quota rows instantly without locking neighboring rows.
- **Partitioning Plan**: Recommended partitioning of `print_logs` table by month for environments processing more than 10,000 prints per week.

---

## 3. JVM GC and Memory Profiling

- **Max RAM Percentage**: Set to `75%` to allow `25%` of container memory for off-heap allocations, native OS operations, thread creation, and Java class files.
- **Garbage Collection (G1GC)**: The `-XX:+UseG1GC` policy divides the heap into equal regions, performing concurrent mark phases to maintain GC pauses below `20ms`.
