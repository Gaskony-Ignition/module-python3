# Security Policy

## Supported versions

Security fixes are released against the latest published version only.
Update to the current release before reporting an issue against an older one.

---

## Trust model

The module exposes scripting functions (`system.python3.exec`, `eval`, …) and
a REST API at `/data/python3integration/...` that run **arbitrary Python 3
code on the Gateway host**.

**The security model mirrors Ignition's own: authoring is the boundary**
(`docs/PROJECT_CHARTER.md` §2). A person with Designer access can already
execute arbitrary Jython — and therefore arbitrary code — on the Gateway;
Python 3 is neither more nor less gated than that.

- **REST endpoints** (`/exec`, `/eval`, `/call-module`, `/call-script`,
  `/packages/install`, `/packages/uninstall`, `/distributions/install`,
  `/distributions/uninstall`) require an authenticated caller via Bearer
  session token or admin API key. `/auth/session` mints tokens only for
  callers whose Ignition session holds the `Administrator` or `Designer`
  role. Unauthenticated callers receive 401/403.
- **The Designer** communicates with the Gateway over the platform's
  authenticated module-RPC channel. Its script authoring/exec/eval path
  requires an authenticated Designer session; Vision/Perspective runtime
  client sessions are rejected even though they hold a valid Gateway session.
- **Scripting bindings** (`system.python3.exec`/`eval`/`callModule`/
  `callScript`), called from project Jython, check
  `RoleResolver.requireScriptingAllowed()` at the entry point. The default
  policy is allow (matching Jython's own trust level); a Gateway
  administrator can disable `system.python3.*` fleet-wide with
  `ignition.python3.scriptingFunctions.allowed=false` (or the equivalent
  `IGNITION_PYTHON3_SCRIPTING_ALLOWED` environment variable).
- `python_bridge.py` does not validate or restrict Python source. Every
  authenticated caller has full Python capabilities, gated only on the Java
  side.

**The real runtime threat is injection, not access:** a Perspective page
must never feed end-user input into `exec`/`eval`. Author a saved script and
call it with typed arguments (`system.python3.callScript`) instead — the same
discipline as SQL-injection guidance.

For real isolation between users and Gateway-host privilege, deploy the
Gateway in a container or VM whose blast radius matches your trust
requirements — OS-level isolation is the only meaningful security boundary
for arbitrary-code execution.

Configuration details (admin key, HTTPS, resource limits, audit logging,
rate limiting): [docs/security/CONFIG.md](docs/security/CONFIG.md).

## Security features

1. **Role-based access control** — Designer integrates with Ignition
   security roles; REST execution requires an Administrator/Designer-issued
   session token; scripting functions are allowed by default with a
   Gateway-wide Administrator opt-out.
2. **Process isolation** — Python code executes in an isolated subprocess
   pool with limited lifetime and resource allocation, and no direct access
   to the Java heap or Ignition runtime classes.
3. **Module signing** — every release is cryptographically signed; the
   Gateway verifies the signature before install.
4. **Network security** — REST access control via Ignition's security
   layer, required API token authentication, session-based Designer auth,
   and CSRF protection on browser-issued requests.

There is no in-process Python sandbox (AST validation, import whitelists)
and none is planned — every known approach is bypassable, and an unbypassable
filter would just duplicate OS-level isolation. Syntax checking in the
Designer IDE is a developer convenience, not a security control.

---

## Reporting a vulnerability

Use GitHub Security Advisories — do not open a public issue:

1. Go to <https://github.com/Gaskony-Ignition/ignition-module-python3/security/advisories>
2. Click **New draft security advisory**
3. Provide a clear description, reproduction steps, affected versions and
   impact assessment
4. Submit privately

### Response timeline

| Stage | Timeline |
|-------|----------|
| Initial response | Within 48 hours |
| Validation | Within 1 week |
| Fix development | 2–4 weeks, depending on severity |
| Patch release | Within 4 weeks for critical issues |
| Public disclosure | After the patch is released and users notified |

### Severity

| Level | Response | Fix |
|-------|----------|-----|
| Critical - RCE, privilege escalation, data breach | 24–48 hours | Emergency patch within 1 week |
| High — auth bypass, significant weakness | Within 1 week | Patch within 2–3 weeks |
| Medium — information disclosure | Within 2 weeks | Next regular release |
| Low — hardening opportunities | Within 1 month | Future release |

### Disclosure

Coordinated disclosure: report privately, a fix is developed in the private
repository, an advisory is sent to users before public disclosure, and
disclosure follows 90 days or patch release, whichever is sooner. Security
researchers are credited in the advisory on request and are not pursued for
good-faith reports that allow time to fix and do not exploit beyond a proof
of concept.

---

## Security best practices

### For module users

- Always run the latest version; subscribe to GitHub security advisories
- Limit Designer access to trusted users
- Use HTTPS for Gateway connections; restrict REST API access to trusted
  networks
- Review Gateway and audit logs regularly
- Disable unused REST endpoints, size the process pool to need, and set
  execution timeouts appropriately

### For module developers

- All code changes are reviewed; security-critical changes get extra
  scrutiny
- Write security-focused tests for authentication and authorisation
- Keep dependencies current and review their advisories
- Validate all user input; follow OWASP guidelines

---

## Known security considerations

1. **Python code execution is the product.** Administrator/Designer-role
   users can execute any Python code on the Gateway host. Limit those roles
   to trusted users and isolate the Gateway host (container/VM) so that
   "execute any Python" maps to "compromise this Gateway host" and nothing
   more.
2. **Process isolation is OS-level only.** Python subprocesses run with
   Gateway user privileges and can access the Gateway filesystem. Use a
   dedicated service account with limited permissions.
3. **The REST API allows remote code execution** once authenticated; rotate
   session tokens regularly and restrict admin API key access to trusted
   operators.
4. **The audit log does not capture caller identity.**
   `Python3SecurityUtils.getCurrentUser()`/`getSourceIP()` are unimplemented
   stubs that always return `null`, so every audit event is written with
   `user=UNAUTHENTICATED`, `sourceIP=LOCAL` regardless of who authenticated.
   This is not an authorisation bypass — the role gates are separate code
   and still enforce — but per-user attribution is not available from the
   audit trail alone. Correlate `python3-audit-*.log` entries with the
   Gateway's own access/session logs by timestamp if per-user attribution is
   needed for compliance.

---

## Contact

- **GitHub Issues:** <https://github.com/Gaskony-Ignition/ignition-module-python3/issues>
- **Discussions:** <https://github.com/Gaskony-Ignition/ignition-module-python3/discussions>

For security vulnerabilities, use [Reporting a Vulnerability](#reporting-a-vulnerability) instead.
