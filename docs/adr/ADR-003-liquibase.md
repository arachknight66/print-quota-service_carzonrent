# ADR-003: Liquibase Database Migrations

## Context
Manual database schema execution or relying on Hibernate schema updates (`hbm2ddl.auto=update`) can cause production database anomalies, lack version history, and break replication environments.

## Decision
We use Liquibase as the system schema management tool. Changes are declared as immutable SQL/YAML changeSets. Schema executions run automatically on application startup.

## Consequences
- **Pros**: Clear version tracking. Repeatable migration scripts. Prevents schema drift across environments.
- **Cons**: Developers must write XML/YAML database changeSets for every table modification.
