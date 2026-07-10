# Configuration Reference Blueprint

This document maps all configuration properties, default values, and environment variable bindings.

---

## 1. Spring Boot Profiles

The system provides two active runtime profiles:
1. **`dev`** (Default):
   - Configures JPA hibernate validation to `validate`.
   - Sets packages logging level for `com.printkeep.quota` to `DEBUG`.
   - Exposes console logging only.
2. **`prod`**:
   - Enforces strict pool limits.
   - Activates rolling Logback log files under `/var/log/print-quota/service.log`.
   - Exposes detailed JSON structured log messages.

---

## 2. Configuration Properties Reference

| Property Name | Env Variable | Default Value | Required | Description |
|---|---|---|---|---|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/printquota` | Yes | Database connection string. |
| `spring.datasource.username` | `DB_USERNAME` | `printuser` | Yes | Database username. |
| `spring.datasource.password` | `DB_PASSWORD` | `printpassword` | Yes | Database credentials. |
| `spring.datasource.hikari.maximum-pool-size` | `DB_POOL_MAX` | `10` | No | Maximum database connections pool. |
| `app.ldap.url` | `LDAP_URL` | `ldap://localhost:389` | Yes | Target directory LDAP server URL. |
| `app.ldap.base-dn` | `LDAP_BASE_DN` | `dc=company,dc=local` | Yes | Directory domain base DN. |
| `app.ldap.username` | `LDAP_BIND_DN` | `cn=admin,dc=company,dc=local` | Yes | LDAP service account bind username. |
| `app.ldap.password` | `LDAP_BIND_PASSWORD` | `admin` | Yes | LDAP service account bind password. |
| `app.ldap.search-base` | `LDAP_SEARCH_BASE` | `ou=users` | No | Relative path search directory. |
| `app.ldap.sync.cron` | `LDAP_SYNC_CRON` | `0 0 * * * *` | No | Cron expression for synchronization schedule. |
| `app.ldap.sync.enabled` | `LDAP_SYNC_ENABLED` | `true` | No | Toggle to disable scheduling syncs. |
| `app.quota.multipliers.duplex` | `QUOTA_DUPLEX_MULTIPLIER` | `0.8` | No | Page cost factor reduction for duplex jobs. |
| `app.quota.multipliers.color` | `QUOTA_COLOR_MULTIPLIER` | `2.0` | No | Page cost multiplier for color jobs. |

---

## 3. Docker Compose Environment Variables

When running under Docker Compose, the following variables configure the stack:
- **`DB_NAME`**: Postgres database database schema. Default: `printquota`.
- **`DB_USERNAME`**: Database admin user. Default: `printuser`.
- **`DB_PASSWORD`**: Database password. Default: `printpassword`.
- **`PGADMIN_EMAIL`**: Default admin email for pgAdmin dashboard. Default: `admin@company.local`.
- **`PGADMIN_PASSWORD`**: Default admin credentials for pgAdmin dashboard. Default: `admin`.
