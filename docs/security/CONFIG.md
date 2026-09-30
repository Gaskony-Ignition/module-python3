# Security configuration

Practical settings for hardening a production deployment. The trust model
and vulnerability reporting policy live in [SECURITY.md](../../SECURITY.md);
this page is the how-to.

## Admin API key

REST callers without an authenticated Ignition session authenticate with an
admin key.

```bash
# Generate a 32+ character key
openssl rand -hex 32

# Configure in ignition.conf
wrapper.java.additional.200=-Dignition.python3.admin.apikey=<generated-key>
```

The key is compared in constant time to resist timing attacks. HTTPS is
required for admin-key auth by default:

```bash
wrapper.java.additional.400=-Dignition.python3.admin.requirehttps=true
```

## Process pool and resource limits

| Limit | Default | Override |
|-------|---------|----------|
| Memory per process | 2048 MB | `-Dignition.python3.max.memory.mb=<n>` |
| CPU time per execution | 60 s | `-Dignition.python3.max.cpu.seconds=<n>` |
| Code size per request | 1 MB | not configurable |
| Pool size | 3–20 processes | `-Dignition.python3.poolsize=<n>` |

Resource limits guard against accidents (runaway loops, memory leaks), not
malicious use by an authenticated caller — see the trust model in
[SECURITY.md](../../SECURITY.md).

## Audit logging

Every execution is logged to `data/python3-integration/audit/`, rotated
daily, and to the Gateway's own SLF4J log (`wrapper.log`).

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "user": "UNAUTHENTICATED",
  "sourceIP": "LOCAL",
  "securityMode": "DESIGNER_ADMIN",
  "codeHash": "a1b2c3d4e5f6...",
  "modulesUsed": "os, sys, requests",
  "success": true,
  "durationMs": 125,
  "resultSize": 1024
}
```

`user`/`sourceIP` are currently always `UNAUTHENTICATED`/`LOCAL` — see
"Known security considerations" in [SECURITY.md](../../SECURITY.md). Code
hash (SHA-256), modules imported, success/failure and duration are real and
useful for forensics.

```bash
tail -f data/python3-integration/audit/audit-$(date +%Y-%m-%d).log
```

## Rate limiting

Per-IP limiter: 100 requests per minute, HTTP 429 once exceeded. Not
user-configurable.

## Hardening checklist

- [ ] Admin key is 32+ characters, generated with `openssl rand -hex 32`,
      not committed anywhere
- [ ] HTTPS enforced for admin-key auth (`admin.requirehttps=true`)
- [ ] Gateway runs as a dedicated service account, not root/Administrator
- [ ] Gateway runs in a container or VM scoped to this module's blast radius
- [ ] Designer and Administrator roles are limited to people who should be
      able to run arbitrary code on this Gateway
- [ ] REST API is reachable only from trusted networks
- [ ] Audit log directory is included in log retention/backup
- [ ] Admin keys are rotated on a schedule and immediately after any
      suspected compromise
