# ADR-004: PostgreSQL Database Selection

## Context
The print management system needs an ACID-compliant transactional persistence engine that supports locking, indexing, and scalable tables.

## Decision
We select PostgreSQL 16 as the core storage engine. It provides ACID transactions, transactional indexes, and row-level pessimistic locking options.

## Consequences
- **Pros**: Outstanding reliability under high-load writes. Complies with security policies. Integrates with Docker.
- **Cons**: Adds operational complexity for database backups and replication.
