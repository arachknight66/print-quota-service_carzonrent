# ADR-005: Active Directory User Synchronization

## Context
Querying Active Directory over the network for every single incoming print job is highly inefficient, introduces latency spikes, and risks print outages if the directory server is temporarily unreachable.

## Decision
We decouple directory lookups from print operations. We sync AD users into the PostgreSQL database periodically via a scheduled task (`IdentitySynchronizationService`). User metadata lookups use Caffeine Cache for fast lookups.

## Consequences
- **Pros**: Reduced print validation latency (local database queries vs network LDAP calls). Local fallback printing if AD drops.
- **Cons**: User detail updates in AD are not immediately visible until the next sync trigger.
