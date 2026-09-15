# Known issues

### Execution counters on `GET /api/v1/metrics` are frozen at 0

Feeds the Dashboard's "Execution Metrics" card and the Diagnostics page's
"EXECUTION METRICS" panel. `Python3MetricsCollector.getMetrics()`
(`gateway/src/main/java/com/gaskony/python3/gateway/Python3MetricsCollector.java`)
reads its own `totalExecutions`/`successfulExecutions`/`failedExecutions`
`AtomicLong` fields, and nothing in production code calls
`recordExecution()`/`recordFailure()` on that object — it is disconnected from
every execution path (REST, RPC, web UI), not merely gated by auth mode. The
Process Pool's "Active" counter is live and does move; "Total Executions"
does not. Fix: source counts from the process pool's own live
`MetricsCollector` (`processPool.getMetricsCollector()`), the same pattern
already used for `getGatewayImpact()`.

### Audit log never records caller identity

`Python3SecurityUtils.getCurrentUser()`/`getSourceIP()` are unimplemented
stubs that always return `null`, so every audit event is written with
`user=UNAUTHENTICATED`, `sourceIP=LOCAL` regardless of who authenticated. Not
an authorisation bypass — the role gates are separate code and still enforce
— but per-user attribution is unavailable from the audit trail alone. See
[SECURITY.md](../SECURITY.md).

### Autocomplete fails silently when `jedi` is missing

If a Python distribution's venv has no `jedi` installed,
`Python3CompletionProvider` returns zero completions and the popup never
appears — no error in the Designer, no user-facing warning; the only
evidence is `ModuleNotFoundError: No module named 'jedi'` in the gateway log.
`jedi` should be installed as part of provisioning a Python version, and a
missing dependency should surface as a visible "completions unavailable"
state rather than silence.

### Audit log concatenates source lines without a separator

`Action=PYTHON_CHECK_SYNTAX, Details=import pandas as pdpd.` is
`import pandas as pd` followed by `pd.`, run together with no separator.
Cosmetic, but it is the audit trail, which should be readable literally.
