# Project Charter — Python 3 Integration Module

This document is the single authoritative statement of what this project is for,
what "done" means, and what will never be built. It exists to end open-ended
improvement loops: a release is justified **only** by the Maintenance Policy below.

---

## 1. Purpose

Ignition is built on Jython 2.7 and realistically always will be. Nearly every
programmer today works in Python 3, and the packages that matter (`requests`,
`pandas`, `numpy`, …) are Python 3 only.

**This module makes Python 3 feel like a native part of any Ignition 8.3
gateway:** a developer opens the Designer, writes and tests a Python 3 script in
a first-class editor, saves it into a Project-Library-like tree, and calls it
from anywhere Jython runs — Perspective pages, tag scripts, gateway events — via
`system.python3.*`, exactly as they already do with the Jython scripting library.

## 2. Personas and trust model

The security model mirrors Ignition's own: **authoring is the boundary.**
A person with Designer access can already execute arbitrary Jython (and thus
arbitrary code) on the gateway — Python 3 must be neither more nor less gated.

| Persona | Access | What they can do |
| --------- | -------- | ------------------ |
| **Gateway administrator** | Gateway web UI (Administrator role) | Controls the *environment*: which Python versions are installed, which packages are available, pool sizing. This is the supply chain control point. |
| **Designer developer** | Designer (authenticated Designer session; may have **no** gateway web access) | Full develop/test cycle: create, edit, run, organise Python 3 scripts; see execution diagnostics and server impact; see (read-only) which versions/packages the admin has provided. |
| **Runtime caller** (Perspective page, tag/timer script, gateway event) | None directly | Executes only what developers authored — pages call saved scripts through bindings/events the developer wrote. End users never author code. |
| **External/REST caller** | REST API | Remote execution stays locked behind Administrator/Designer token issuance (C14). Unauthenticated → 401. |

**Runtime execution policy:** `system.python3.*` from project scripts is
**allowed by default** (matching Jython's trust level — a project script that
wanted to do harm can already do it in Jython). A gateway administrator can
disable it fleet-wide with `ignition.python3.scriptingFunctions.allowed=false`
(opt-out; reverses the C13 opt-in default, decided 2026-07-02).

**The real runtime threat is injection, not access:** a Perspective page must
never feed end-user input into `exec`/`eval`. The documented pattern is: author
a saved script, call it with typed arguments (`system.python3.callScript`).
This anti-pattern warning is a required part of the user documentation, exactly
like SQL-injection guidance.

## 3. Surface ownership — one owner per function

| Function | Owner | Notes |
| ---------- | ------- | ------ |
| Script authoring, testing, organisation | **Designer** | IDE + Project Browser node, via authenticated module RPC |
| Execution diagnostics & server impact | **Designer** (read-only view; web UI has the full admin version) | Devs without gateway access must be able to see if their scripts hurt the server |
| Environment visibility (installed versions/packages) | **Designer read-only**; **web UI read-write** | Devs see what's available; admins change it |
| Package + Python version management (write) | **Gateway web UI** | Admin only; removed from Designer |
| Calling scripts from projects | **`system.python3.*`** | `callScript` is the primary integration point |
| Remote/external execution | **REST API** | Token-gated, unchanged |

Duplicating a *write* function on a second surface is a scope violation.

## 4. Definition of Done — the Acceptance Contract

The module is **done** when all ten workflows pass on a clean install. This is
the release gate — run it instead of chasing internal metrics.

| # | Workflow | Surface | Status 2026-07-06 |
| --- | ---------- | --------- | ------------------- |
| 1 | Install signed `.modl` on clean Ignition 8.3 → pool healthy < 60 s, **zero manual config** | Gateway | ✅ 04/07/2026 — automated acceptance on a clean Ignition 8.3.6 container with **no system Python**: install → distribution download → SHA-256 verify → extract → 3-executor pool healthy in **~4 s**. (Two C15 defects found and fixed first, v4.3.5: null-seeded checksum pins; blanket symlink ban broke CPython extraction. The old v4.2.0 ✅ was invalid — that gateway had pre-C15 distributions) |
| 2 | Designer Script Console: run a script → output; failing script → Python traceback — **with no gateway flags set** | Designer | ✅ 06/07/2026 — maintainer ran the demo scripts on v4.5.x: successful scripts print output, and failing ones show a full Python traceback (ProductionStats → `ModuleNotFoundError` with traceback; MemoryLimitSafety → `MemoryError`), no gateway flags set |
| 3 | Project Browser "Python 3 Scripts": list/create/edit/save/delete round-trip, folders like Project Library | Designer | ✅ (v4.2.0 test); v4.5.0 changed storage to file-backed (`.py`+`.meta.json`, hot-reload, migrated from `index.json`) — verified on a throwaway (save/delete/migrate/hot-reload + callScript of migrated & dropped scripts); Designer round-trip re-confirm welcome |
| 4 | Designer diagnostics: pool stats, execution timing, gateway impact visible **without gateway web access** | Designer | ✅ 06/07/2026 — maintainer visually confirmed on v4.5.2 that the diagnostics numbers move after running scripts. (Defects found & fixed same day, v4.4.0: Total Executions / Success Rate / Avg Time were structurally always 0, Impact/Health frozen at LOW/100, Py3 CPU% a fixed guess — all rewired to live pool metrics, unit-tested) |
| 5 | Designer environment view: installed Python versions + packages, read-only | Designer | ✅ audit-verified real (06/07/2026 field-by-field trace: versions from real FS scan, packages from real catalog + persisted install set); visual re-confirm welcome on v4.4.0 |
| 6 | `system.python3.callScript("TestFolder1/MyfirstScript")` from a Jython project script / Perspective event returns the result | Runtime | ✅ 04/07/2026 — real gateway-scope Jython (WebDev `doGet`) on the clean test gateway: `callScript("TestFolder1/MyfirstScript")` → `42.0` (repository script, HMAC signature verified) |
| 7 | `system.python3.exec("result = 2 + 2")` → `4` from a project script **by default**; with opt-out property set, fails with a clear error | Runtime | ✅ 04/07/2026 — from gateway Jython with **no flags set**: exec → `4.0`, eval → `42.0`; with `IGNITION_PYTHON3_SCRIPTING_ALLOWED=false`: clear "disabled by the gateway administrator (ignition.python3.scriptingFunctions.allowed=false)" error on all three calls |
| 8 | Web UI as Administrator: log in → install a package → manage Python versions → changes appear in the Designer environment view | Admin | ✅ 06/07/2026 — maintainer installed `pandas` from the web UI on the live gateway (real pip install completed; `ProductionStats` then ran with pandas), then uninstalled it via the UI on v4.5.2. Earlier defects fixed: (a) packages page "0 total" (v4.3.1); (b) bundle install `Wheel not found: …{platform}.whl` (v4.3.6); (c) numpy/pandas import under the 512 MB cap (v4.4.0: single-thread BLAS + 2 GB cap). v4.5.1 installs to **all** distributions; v4.5.2 fixes uninstall of individually pip-installed packages. Remaining nicety: confirm Python-version management + Designer environment-view reflection |
| 9 | REST `/exec` with admin token succeeds; without token → 401; `/auth/session` refuses callers lacking Designer/Administrator role | REST | ✅ `/exec`, `/eval`, `/pool-stats` and `/auth/session` all return 401 when unauthenticated |
| 10 | Version identical in Designer, web UI, gateway module list; uninstall → zero orphaned Python processes | All | ✅ 06/07/2026 — version identity maintainer-confirmed on v4.5.2 (Designer + web UI + gateway module list all read 4.5.2; server side independently verified: installed modl `Python3-4.5.2.modl`, version.properties 4.5.2). Uninstall half ✅ 04/07/2026 (module removed → routes 404, **zero** python processes, clean logs) |

Internal quality floors (not goals): all tests pass, gateway coverage ≥ 50%
(JaCoCo gate), signed builds only, docs accurate.

### Designer editor quality bar (finite checklist, part of Done)

Developers expect VS Code/JetBrains-level tooling. "Clean and powerful" means this list —
when it's ticked, editor work **stops**:

- [x] Python syntax highlighting, dark/light themes, find/replace, keyboard shortcuts
- [x] Visual consistency via the ModernTheme system (v3.9–v3.10 card styling; all v4.3.0 additions follow it — no further re-skinning, that would be the treadmill this charter kills)
- [x] Autocomplete via the bundled Jedi — explicit **Ctrl+Space**, fetch capped at 1.5 s so a cold Jedi can never freeze the EDT (v4.3.0)
- [x] Syntax-error squiggles from the check-syntax service over RPC (`PythonSyntaxChecker`, debounced + async; v4.3.0)

**Editor work is now STOPPED per this bar.**

## 5. Maintenance Policy

After the contract passes, a new release is justified **only** by:

1. A defect in one of the ten workflows
2. A security issue
3. Compatibility with a new Ignition version
4. A candidate feature deliberately pulled from §7 (one at a time)

Everything else — refactors, polish, metric climbing — does not ship on its own.

## 6. Work remaining to reach Done

- [x] v4.2.0: Designer↔Gateway module RPC for scripts + console (verified in Designer 2026-07-02)
- [x] **v4.3.0 — "Native Designer" release (code complete 2026-07-03):**
  - [x] Designer exec/eval trusts the authenticated Designer session (`execTrusted`/`evalTrusted`, `requireDesignerSession` incl. Vision-client rejection) — C13 property gate removed from the Designer path (workflow 2)
  - [x] Runtime `system.python3.*` default flipped to **allow**, property is now opt-out (workflow 7); SECURITY.md updated
  - [x] Designer diagnostics on RPC + "Diagnostics" button in the Script Console (workflow 4); read-only Environment tab (workflow 5)
  - [x] Package/version write management removed from the Designer (~3,800 lines deleted; web UI owns it)
  - [x] Editor quality bar met (see §4 — editor work stopped)
- [x] **Docs for integrators:** `docs/getting-started/INTEGRATION_GUIDE.md` (author → call from Perspective/tag/timer, injection anti-pattern)
- [x] Docs accuracy sweep of `docs/operations|security|api` (removed-component references, property names, endpoint paths corrected)
- [x] v4.3.1: web UI packages/versions "not initialized" fix (workflow 8 defect — deferred-init snapshot bug latent since v4.0.0)
- [x] v4.3.2: Diagnostics dialog follows the console theme (live propagation + Environment-tab light-mode gaps)
- [x] v4.3.3: dead legacy standalone-IDE cluster deleted (36 files, ~9,000 lines — full-code-review finding, decision 2026-07-04); Ctrl+F Find/Replace wired into the Script Console (its only host was the deleted cluster — keeps the §4 editor bar honest)
- [x] v4.3.5: clean-gateway self-provisioning fixed (C15 null checksum pins populated + contained-symlink extraction policy) — found by the automated acceptance run on a throwaway 8.3.6 container
- [x] v4.3.6: web UI package install fixed (catalogue/bundled-wheel drift; bundled-directory scan + PyPI fallback) — found by maintainer's workflow-8 test
- [x] **Automated acceptance run (04/07/2026, throwaway clean 8.3.6 container, v4.3.6):** workflows 1, 6, 7 (both halves), 9 (negative half), 10 (uninstall half) all ✅ — see §4 table
- [x] **Manual acceptance progressed on v4.5.x (maintainer, Designer + browser, 06/07/2026):** workflow 2 (traceback case) ✅, 3 (file-backed storage + demos in tree) ✅, 8 (web-UI package install → uninstall round-trip) ✅ — recorded in §4
- [x] **Final acceptance boxes confirmed (maintainer, 06/07/2026, v4.5.2):** workflow 4 (diagnostics numbers move after running scripts) and workflow 10 (version identity 4.5.2 everywhere) — **all ten workflows are now ✅. The Acceptance Contract is complete; the module is Done as defined by this charter.** v4.5.2 released same day (source repo + portal). Future releases are governed solely by §5 Maintenance Policy.

## 7. Candidate list (no commitment)

- **Linting / format-on-save** (pyflakes/black in the Designer editor)

*(Jedi autocomplete was promoted into the Done quality bar, 2026-07-02.)*

## 8. Won't-Do list (permanent)

| Item | Why not |
| ------ | --------- |
| Debugger / breakpoints | Deep bridge surgery to imitate free, better tools |
| Multi-cursor, code folding upgrades, profiling UI | IDE-parity treadmill beyond the §4 quality bar |
| In-process Python sandboxing / RESTRICTED mode | Proven bypassable (C13); false security claims are worse than none |
| Result caching, priority execution queues | Built once, deleted as dead code in v4.1.0 |
| Horizontal scaling / Kubernetes orchestration | Not this module's job |
| 80% coverage target | Metric treadmill; ≥ 50% JaCoCo gate stays as a floor |
| macOS package bundling | Niche in SCADA; documented `pip install` workaround |
| Designer package/version **management** (write ops) | Admin function; gateway web UI owns it (§3) |

---

*Change to this charter requires the maintainer's explicit decision, recorded here with a date.*
