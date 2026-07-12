# Support Handbook (v1.0 GA)

This document provides tier-1 and tier-2 IT support engineers with incident resolution procedures, error code tables, and escalation paths.

---

## 1. Known Error Codes & Meanings

| Module | Code | Meaning | Resolution |
| --- | --- | --- | --- |
| **IPP Proxy** | `0x0401` | Client Out of Quota | Request quota increase via manager. |
| **IPP Proxy** | `0x0500` | Internal Server Error | Check application logs for database timeouts. |
| **Active Directory** | `LDAP Sync Warn` | User DistinguishedName missing | Verify AD user object has valid properties filled. |
| **Database** | `Lock Timeout` | Pessimistic Write Lock conflict | Check for active long-running operations. |
| **SMTP Mailer** | `SMTP Failure` | Email gateway socket timeout | Verify gateway port reachability. |

---

## 2. Common Incidents & Resolution Procedures

### Incident: User unable to print ("Hold for authentication")
- **Possible Cause**: User has exceeded monthly page quota, or the user object is disabled in Active Directory.
- **Resolution**:
  1. Check user page balance: `GET /api/v1/admin/quotas?username=jdoe`.
  2. If used pages equal allocated pages, user is blocked. Ask manager for authorization and run:
     - `POST /api/v1/admin/quotas/{userId}/adjust` with body `{"adjustment": 50}`.

### Incident: Target printer not printing
- **Possible Cause**: Physical printer is offline or IP address has changed.
- **Resolution**:
  1. Verify physical printer power and network connection.
  2. Ping printer IP from host container console.
  3. Validate logical target mapping inside `application.yml` or Compose file.

---

## 3. Support Escalation Matrix

If the incident cannot be resolved by standard procedures, escalate according to:

- **Tier-1 Helpdesk**: First contact. Handles quota checks, account resets, and printer mapping validation.
- **Tier-2 NetOps / SysAdmin**: Escalate here if Active Directory sync fails or physical printers remain unreachable.
- **Tier-3 DevOps / DBA Architect**: Escalate here if database locking deadlocks occur or Java heap OOM errors are logged.
