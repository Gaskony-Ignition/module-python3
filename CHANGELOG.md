# Changelog

All notable changes to the Python 3 Integration module for Ignition 8.3+.

**Format:** Based on [Keep a Changelog](https://keepachangelog.com/)
**Versioning:** [Semantic Versioning](https://semver.org/)

---

## [4.6.2] - 2026-08-10
### Fixed
- Package/distribution install/uninstall routes now require ADMIN/DESIGNER_ADMIN and CSRF validation, matching `/exec`; previously any authenticated user could trigger arbitrary code via package build hooks.

### Documentation
- `SECURITY.md` now lists the package and distribution endpoints as requiring authentication.

## [4.6.1] - 2026-07-30
### Fixed
- Designer `rsyntaxtextarea`/`autocomplete`/`rstaui`/`flatlaf` moved to `modlImplementation` so they actually bundle into the `.modl` instead of silently falling back to the platform's older copies; dead `flatlaf` dependency removed entirely.
- Designer `slf4j-api` pinned to 2.0.12 to match the platform's actual bundled copy.

### Changed
- Gson moved to `modlImplementation` 2.14.0; `jakarta-servlet` bumped to 6.0.0; `commons-compress` to 1.28.0; test stack bumped to `junit-jupiter` 5.14.4, `mockito` 5.23.0, with an explicit `junit-platform-launcher` 1.14.4 pin.

## [4.6.0] - 2026-07-30
### Added
- `GatewayHook` overrides `isMakerEditionCompatible()` to return true, so the module now runs on Ignition Maker Edition.

## [4.5.3] - 2026-07-06
### Fixed
- Dark-mode Script Console syntax colours are now built programmatically in Java instead of loading an XML theme resource that silently failed under the Designer's module classloader.

## [4.5.2] - 2026-07-06
### Fixed
- Uninstalling an individually PyPI-installed package (not a catalogue bundle) now falls back to a direct pip uninstall instead of failing with "Failed to uninstall".

## [4.5.1] - 2026-07-06
### Fixed
- Package install/uninstall now runs against every installed Python distribution, not just the default.
- Search-PyPI Install button now shows a spinner/progress toast during long installs.

## [4.5.0] - 2026-07-06
### Changed
- Saved scripts moved from a single `index.json` blob to per-script `.py` files + `.meta.json` sidecars with filesystem hot-reload (no module restart needed to pick up gateway-side edits).
- Existing `index.json` stores auto-migrate to the per-file layout on first startup.
- Script signatures are now recomputed from file contents on load.

## [4.4.0] - 2026-07-06
### Added
- In-app Help dialog in the Script Console explaining the full authoring/calling workflow.

### Fixed
- Diagnostics dialog showed frozen/fake data for execution stats, impact level, health score and Python 3 CPU%; all now sourced from live pool metrics.
- Pool size read as 0 in the Designer due to a field-name mismatch (`poolSize` vs `totalSize`).
- `datascience` bundle packages (numpy/pandas/matplotlib) failed to import under the memory cap because multi-threaded OpenBLAS reserved excessive virtual address space; single-threaded BLAS env vars now set and default memory cap raised to 2048 MB.
- Script Console output was wiped on every run and the first run could be clipped; output now accumulates with newest first, and the pane opens at a readable split.

## [4.3.6] - 2026-07-04
### Fixed
- Web UI package installs for any bundle except "jedi" failed with a missing-wheel error; `installPackage` now scans the module's actual bundled wheel directory as the source of truth and falls back to PyPI install for anything not bundled for the platform.

## [4.3.5] - 2026-07-04
### Fixed
- A clean gateway with no system Python could never start the process pool: checksum pins for distribution downloads were never populated, and the tarball extractor's blanket symlink ban rejected legitimate in-tree symlinks required by real CPython distributions. Both fixed; absolute/escaping link targets are still refused.

## [4.3.3] - 2026-07-04
### Removed
- Deleted the unreachable legacy standalone `Python3IDE` cluster (36 files, ~9,000 lines) — only ever instantiated by the dev test harness.

### Added
- Ctrl+F Find/Replace wired into the Script Console (previously implemented only in the deleted IDE cluster).

## [4.3.2] - 2026-07-04
### Fixed
- Diagnostics dialog now follows the console's theme toggle live, and the Environment tab's tables/buttons/labels now re-theme correctly in light mode.

## [4.3.1] - 2026-07-04
### Fixed
- Web UI Packages page always showed "0 installed / 0 total" because REST routes were mounted before the deferred-init thread created the package/pool managers; those services are now rewired into the live request context once created.

## [4.3.0] - 2026-07-03
### Changed
- Runtime `system.python3.*` scripting is now allowed by default (previously deny-by-default); administrators can disable fleet-wide via `ignition.python3.scriptingFunctions.allowed=false`.
- Designer exec/eval now use dedicated trusted RPC entry points that skip the runtime gate but keep full validation/audit.
- RPC gate hardened to also reject non-Designer client sessions.

### Added
- 8 new RPC methods mirroring the REST diagnostics/versions/distributions/completions endpoints.
- Diagnostics dialog and a read-only Environment tab added to the Script Console.
- `docs/getting-started/INTEGRATION_GUIDE.md`.

### Removed
- Designer slimmed by ~3,800 lines: Packages dialog, Python version install/uninstall dialog, Shell Command Mode/terminal, pool-size control and the Gateway-URL override setting removed — all owned by the Gateway web UI now.

### Changed
- Jedi autocomplete now requires explicit Ctrl+Space with a 1.5s fetch cap; syntax-error squiggles restored via RPC.

## [4.2.0] - 2026-07-01
### Fixed
- Designer "(Gateway unavailable)" / broken Project Browser and Script Console: the Designer's REST client carried no authenticated session. Designer↔Gateway calls now go over authenticated module RPC instead.

### Added
- `Python3Rpc` module-RPC interface and gateway `Python3RpcHandler` covering script list/load/save/delete, exec/eval, version, pool stats and health.

## [4.1.0] - 2026-06-29
### Fixed
- Subprocess stderr pipe-buffer deadlock: a dedicated stderr-drain thread now runs per executor so a chatty Python process can't fill the OS pipe buffer and block.

### Removed
- Deleted the dead, unreachable `InputValidator` "sandbox" (would have blocked legitimate Python 3 usage had it ever been wired in) and its tests.
- Removed the redundant `EnhancedAuditLogger` and unused `*WithContext` executor methods.
- Removed ~3,400 lines of confirmed-dead code: `AdaptivePoolSizer`, `ExecutorHealthMetrics`, `PriorityExecutionRequest`, `ExecutionPriority`, `ResultCache`, `ResourceLimits`, standalone `RateLimiter`.

### Documentation
- New `docs/architecture/ARCHITECTURE.md` with Mermaid diagrams, replacing the stale `OVERVIEW.md`.

## [4.0.1] - 2026-05-21
### Fixed
- Gateway web UI showed a stale version because `version.properties` was only bundled into `designer.jar`, not on the gateway classpath. Moved to the common scope so both gateway and designer resolve the same file.

## [4.0.0] - 2026-05-16 — UNRELEASED
### Breaking
- Java packages renamed from `com.inductiveautomation.ignition.examples.python3.*` to `com.gaskony.python3.*`; any customer code referencing the old FQNs needs updating.
- `RESTRICTED` execution mode removed (bypassable AST filter); callers must migrate to `NORMAL` and rely on OS-level isolation.

### Changed
- Module ID unchanged; in-place upgrade keeps the same persisted state directory.

## [3.12.14] - 2026-05-14
### Fixed
- `requiredFrameworkVersion` changed from `"8.3"` to `"8"` — the 8.3.6 module-XML parser treats it as an integer and rejected the old value, blocking install entirely.
- Refreshed hardcoded version fallbacks in `DesignerHook.java` and `InformationDialog.java`.

## [3.12.1] - 2026-03-07
### Fixed
- Moved test dependencies out of the root `subprojects {}` block (version catalog accessors aren't available there) into gateway/designer build files.

### Changed
- Standardised `auto-tag.yml` version extraction and `tsconfig.webpack.json` test excludes to match the other modules; JaCoCo CSV output disabled.

## [3.12.13] - 2026-05-09
### Added
- Accessibility baseline: `prefers-reduced-motion`, skip link, accessible toast/`Modal` primitives, `jsx-a11y` lint rule.

### Changed
- Visibility-aware polling for `GlobalStatusBar` (suspends when tab hidden).
- `.gitignore` standardised to track `package-lock.json`.
- Documentation sweep: stale version references, dead links and Australian-English fixes.

### Refactored
- `Python3IDE` god class reduced from 3,854 to 646 lines via five extracted manager collaborators.

### Security
- Replaced the fake `RESTRICTED` sandbox with an Administrator role gate; removed the AST validator/filter and all silent-demotion paths.

### Tests
- `pr-checks.yml` gained a `gradle-check` job running JaCoCo/Checkstyle/SpotBugs; one `Thread.sleep` replaced with Awaitility.

## [3.12.12] - 2026-04-30
### Added
- Diagnostics log viewer with severity filtering; redesigned error boundary with retry.

### Changed
- Cross-module CSS variable standardisation; async Gateway startup so process-pool warm-up no longer blocks boot; migrated to Ignition-bundled Gson.

### Fixed
- Blocked pip argument injection in package install/uninstall.
- Deleted the residual `execShell` shell-injection sink.
- Added tar-slip protection, size caps and pinned SHA-256 verification on package extraction.

### Security
- `/auth/session` tokens now bound to the calling user's Ignition roles instead of "any authenticated user".
- Hardened `.gitignore` against committing signing/credential files.

### Notes
- Rolls up intermediate releases 3.12.2–3.12.11, which were not separately tagged.

## [3.11.0] - 2026-03-04
### Changed
- Flattened the Gradle project structure: moved `python3-integration/` contents to the repository root; consolidated docs and CI path references.

## [3.9.0] - 2026-03-03
### Changed
- Aligned Designer IDE visual style with the other modules: card headers, bordered content sections, and a combined diagnostics+logs filter toolbar.

## [3.8.3] - 2026-02-23
### Fixed
- Web UI package install/uninstall failed with 401 (only CSRF token sent, not the Bearer token); package catalog never showed installed status.

## [3.8.2] - 2026-02-22
### Changed
- Rewrote `Python3ScriptModule.properties` scripting-function docs with usage guidance, examples, parameter types and common pitfalls; documented the previously-missing `getDistributionInfo`.

## [3.8.1] - 2026-02-22
### Fixed
- Gateway Web UI always showed a hardcoded stale module version because `/api/v1/version` never returned a `moduleVersion` field.

## [3.8.0] - 2026-02-22
### Added
- Gateway-scope test coverage raised from 19% to 51.7% (649 tests across 17 classes).

## [3.7.1] - 2026-02-22
### Added
- Extracted `CsrfProtection` and `IpWhitelist` into independently-testable classes with dedicated tests.

### Changed
- `Python3RestEndpoints` reduced from ~1,338 to ~1,066 lines.

## [3.7.0] - 2026-02-22
### Added
- Split `Python3RestEndpoints`'s 37 handler methods into `ExecutionHandlers`, `ScriptAndPackageHandlers` and `MonitoringHandlers`, plus a shared `EndpointContext` dependency holder.

### Changed
- `Python3RestEndpoints` reduced from 3,177 to ~1,338 lines.

## [3.6.15] - 2026-02-22
### Removed
- Deleted the permanently-disabled `handleShellExec` method (403-only since v2.9.0); its route constant remains so the path 404s cleanly.

## [3.6.14] - 2026-02-22
### Added
- `withHandler` wrapper applying security headers and exception handling to all 41 REST endpoints, removing ~800 lines of duplicated try/catch/finally.

## [3.6.13] - 2026-02-22
### Added
- New single-source-of-truth classes: `ApiEndpoints`, `JsonFields`, `PoolConfig` (common scope); `PreferenceKeys`, `ComponentThemeHelper`, `UiComponentFactory`, `Themeable`, `BaseModuleDialog` (designer scope); `ApiResponse` (gateway scope).

## [3.6.12] - 2026-02-22
### Fixed
- Removed `ThemeManager.applyDarkDialogTheme()`/`applyLightDialogTheme()`, which set 50+ global `UIManager` keys and polluted the whole Designer JVM; replaced with direct component-level colour calls. Rule established: never call `UIManager.put()` in this module.
- Enriched `Python3ScriptModule.properties` with full parameter/return documentation.

## [3.6.11] - 2026-02-22
### Changed
- `SectionPanel` gained card-style gradient headers; `DiagnosticsPanel` now implements a `Themeable` interface; dark dialog implementations consolidated onto a common base.

## [3.6.10] - 2026-02-22
### Added
- `ModernTheme.LIGHT_PRIMARY` / `LIGHT_SUCCESS` semantic colour constants.

### Fixed
- `InformationDialog` hardcoded fonts replaced with `ModernTheme` constants.

## [3.6.9] - 2026-02-21
### Added
- 32 new `ModernTheme` constants; all inline `Color`/`Font` construction across designer scope replaced with theme references.

## [3.6.8] - 2026-02-21
### Fixed
- Duplicate Script Console appeared on Designer reopen (registered in both `setupProject()` and `startup()`).
- Designer theme pollution scoped FlatLaf `UIManager` calls to module dialogs only.
- CSRF for the Designer REST client fixed via an `X-Source` header bypass.

### Added
- Logs tab in diagnostics showing recent gateway log entries; floating card-style section headers.

## [3.6.7] - 2026-02-21
### Fixed
- Version number missing from the Tools menu item.
- `PackagesDialog` infinite recursion crash (`getRestClient()` called itself).
- Rename/delete popup dialogs appeared behind the Designer window for lack of a parent frame.
- Light-mode output text was too light; darkened and made theme-aware on switch.

## [3.6.6] - 2026-02-21
### Fixed
- CSRF validation blocked Designer REST client operations (packages, delete, rename) when Bearer token acquisition failed; `X-Source` header now recognised as a secondary bypass.
- Output text was unreadable in light mode; split-pane divider stayed dark in light mode; run button was taller than other toolbar buttons.

### Changed
- Complete VS Code Dark+ palette overhaul across `ModernTheme`, the RSTA syntax theme and `InformationDialog`; scrollbars made invisible.

## [3.6.5] - 2026-02-21
### Fixed
- Theme toggle got stuck on light mode; script delete/rename failed (changed HTTP DELETE to POST for servlet compatibility); Packages dialog was inaccessible after IDE consolidation; version display showed a stale hardcoded value.

### Added
- `PackagesDialog(Frame, Python3RestClient)` constructor and a Packages button on the Script Console toolbar.

## [3.6.4] - 2026-02-21
### Fixed
- Package install/uninstall replaced a fragile subprocess-code workaround with proper REST endpoint calls.
- Script rename/delete failed for names with spaces/special characters; added URL decoding in gateway handlers.
- Script Console theme switching now updates all components consistently.

## [3.6.3] - 2026-02-20
### Fixed
- PyPI install sent a malformed version string to pip; HTTP timeout raised to 5 minutes for large packages.
- Designer script/folder rename lacked an auth header, causing silent 403s.
- Project Browser's script tree collapsed every 30 seconds during auto-refresh; now diffs signatures before rebuilding.
- Dark theme reset to white on window creation due to a stray `updateComponentTreeUI` call.

### Added
- Light/dark theme toggle button on the Script Console, persisted across sessions.

## [3.6.2] - 2026-02-20
### Fixed
- Script Console window had a white JFrame background under the dark theme; split-orientation toggle button was broken.

### Added
- Package install now falls back to direct PyPI download when a package isn't in the bundled catalogue.

### Changed
- Removed the legacy "Python 3 IDE" menu entry; Script Console is now the single Designer entry point.

## [3.6.1] - 2026-02-20
### Fixed
- Reverted a FlatLaf wrapping change that prevented the IDE and Script Console windows from opening.
- CSRF token validation failures on pool-size and package install fixed by generating the CSRF token before the API token.
- Packages catalog parsing error from a map-vs-array response mismatch.

### Added
- Full-width heading bar; logs pause/resume and a default module-name filter.

## [3.5.4] - 2026-02-20
### Fixed
- Designer REST client authentication used the CSRF UUID as the Bearer token instead of the HMAC API token, breaking Script Console and Project Browser connectivity.
- Pool size control now surfaces HTTP error codes instead of always returning 200.

### Changed
- Script Console UI given thinner scrollbars, softer borders and increased padding.

## [3.5.3] - 2026-02-20
### Fixed
- Script Console crashed with a NullPointerException on theme apply (missing null checks for `JTextPane`-based fields).
- Designer Project Browser lost access after prior auth changes; Bearer tokens are now accepted.

### Changed
- Folder creation now persists a placeholder script on the Gateway so folders survive a refresh; nested folder creation added.

## [3.5.2] - 2026-02-20
### Fixed
- CSRF token validation broke the IDE, package install and pool resize; session endpoint now accepts the web-UI client ID.
- CSRF token expiry extended to match the 8-hour session token.
- PyPI metadata route was missing its `:name` path parameter.
- Pool health status and `/health` endpoint status field were missing.

### Changed
- All REST endpoints now require Gateway login; logs rewritten to read from the SQLite `system_logs.idb` (works in Docker, unlike `wrapper.log`).

## [3.5.1] - 2026-02-20
### Fixed
- Designer REST client blocked on auth-token failure, breaking Project Browser and Script Console; `ensureValidToken()` is now non-throwing.

### Changed
- Script Console redesigned to match the Web GUI with a merged output/error panel; split-orientation toggle added to both Designer and Web IDE.

## [3.5.0] - 2026-02-20
### Added
- Logs tab with level filtering, search, pagination and auto-refresh; Save As button; inline pool-size control; expanded PyPI metadata search.

### Changed
- Save button now auto-saves without prompting when a script is loaded.
- CPU/RAM metrics fixed to use `OperatingSystemMXBean`/`MemoryMXBean` instead of inaccurate execution-ratio/heap-only calculations.

### Removed
- Circuit Breaker and Active Alerts panels removed from Diagnostics (not useful data).

## [3.4.0] - 2026-02-20
### Added
- "Python 3 Scripts" top-level node in the Designer's native Project Browser: browse, create, rename, delete and open scripts with context menus, auto-refresh and offline handling.

## [3.3.0] - 2026-02-19
### Added
- New lightweight Designer Script Console (RSyntaxTextArea editor, theme toggle, version selector, load/save, output/error tabs, keyboard shortcuts).
- Full-text PyPI keyword search from the Gateway Web UI Packages tab.

### Fixed
- Status bar layout bled under the sidebar; CPU/RAM metrics showed zero due to a field-name mismatch.

### Removed
- Non-functional Terminal tab and its xterm dependencies removed from the Gateway Web UI.

## [3.1.0] - 2026-02-11
### Added
- Multi-version Python management: install/uninstall/manage Python 3.9–3.13 from the Designer IDE, with per-version process pools and a version selector in the toolbar.

### Changed
- Repository renamed from `ignition-module-python3-java` to `ignition-module-python3`; GitHub org references updated to `Gaskony-Ignition`.

## [3.0.0] - 2025-11-24
### Changed
- Milestone release marking production maturity; no functional changes over v2.15.10.

## [2.15.10] - 2025-11-21
### Fixed
- Package operations failed with "No such file or directory: 'pip3'"; switched to `sys.executable -m pip` for portability.
- Drag-and-drop of a script onto a folder replaced the folder instead of moving into it.
- Script signature verification is now optional (`ignition.python3.enforce.signatures`, default false) so old/invalid signatures don't block loading.

## [2.15.9] - 2025-11-21
### Fixed
- Dead `execute_shell` handler in `python_bridge.py` now returns a proper error.
- Memory leaks in CSRF token map, rate-limiter map and the static timeout executor thread pool.
- Timing-attack vulnerability in `secureEquals()` (removed early exit on length mismatch).

### Changed
- Bumped `commons-compress` to 1.27.1 (CVE-2024-25710, CVE-2024-26308) and `slf4j` to 2.0.16; removed `mockito-inline`.

## [2.15.8] - 2025-11-19
### Removed
- Recent Scripts folder feature removed entirely, along with `RecentScriptsManager`.

## [2.15.7] - 2025-11-18
### Fixed
- Phantom "Recent" folder creation caused by an unnecessary tree refresh on script load.

## [2.15.6] - 2025-11-17
### Fixed
- Recent-folder and metadata-panel display issues; improved folder navigation stability.

## [2.15.4] - 2025-11-16
### Fixed
- Recent-folder persistence issue; script name was not visible in the UI.

## [2.15.3] - 2025-11-15
### Fixed
- Phantom Recent-folder creation; context-menu actions no longer act on virtual folders.

## [2.15.2] - 2025-10-30
### Changed
- Reorganised Packages dialog into a two-column layout for better usability.

## [2.15.1] - 2025-10-29
### Fixed
- Installed-packages table rendering issue fixed with a proper `TableCellRenderer`/editor; experimental warning banner removed.

## [2.15.0] - 2025-10-29
### Added
- Scrollable PyPI search results; functional installed-packages table with uninstall support.

## [2.12.0] - 2025-10-28
### Added
- Virtual environment (venv) support with automatic detection and `VIRTUAL_ENV` propagation to Python subprocesses.

## [2.11.3] - 2025-10-28
### Fixed
- Emoji button icons rendered as rectangles on some platforms; replaced with Unicode symbols.

## [2.11.2] - 2025-10-28
### Fixed
- Settings dialog layout and button truncation; Packages dialog scrolling scoped to just the table; button/connection-status icons rendered as rectangles.

### Changed
- Script creation now requires metadata input before the script is created.

## [2.11.1] - 2025-10-28
### Added
- Smoke tests for all 7 manager classes (184 tests total).

## [2.11.0] - 2025-10-28
### Added
- Extracted 7 manager classes (`AutoSaveManager`, `SearchManager`, `ScriptImportExportManager`, `ExecutionManager`, `KeyboardShortcutsManager`, `ScriptTransferManager`, `CommandPaletteManager`) via dependency injection.

### Changed
- `Python3IDE.java` reduced from 4,390 to 3,727 lines.

## [2.10.0] - 2025-10-25
### Changed
- Converted 8 data classes to Java 17 records for immutability and reduced boilerplate.

## [2.9.0] - 2025-10-22
### Security
- Critical security fixes; enhanced input validation and sanitisation.

### Changed
- Batch-updated dependencies to latest secure versions.

## [2.8.0] - 2025-10-20
### Added
- Command Palette (Ctrl+Shift+P); Recent Scripts quick access; enhanced button hierarchy; collapsible sidebar (Ctrl+B); inline AST-based syntax error markers; smart auto-save every 30 seconds.

## [2.7.0] - 2025-10-18
### Added
- Settings dialog with theme/font controls; Info dialog; Packages dialog; Web UI theme matching.

### Fixed
- Dialog theming consistency and font-size persistence across sessions.

## [2.6.0] - 2025-10-17
### Added
- AST-based Python syntax validation; Designer IDE `DESIGNER_ADMIN` mode integration.

### Security
- Role-based access control checks added before code execution.

## [2.5.26] - 2025-10-16
### Fixed
- `RTextScrollPane` gutter border colour issue; reverted the v2.5.25 change that caused a visual regression.

## [2.5.25] - 2025-10-16
### Fixed
- Attempted fix for white-rectangle UI artifacts; introduced a gutter-border regression (fixed in 2.5.26).

## [2.5.22-2.5.24] - 2025-10-15
### Fixed
- Focus-border and editor-border visual artifacts; tab repositioning.

## [2.5.21] - 2025-10-14
### Added
- Execution mode tabs (Code / Terminal).

### Fixed
- CPU-percentage display accuracy.

## [2.5.20] - 2025-10-14
### Fixed
- RAM/CPU data parsing from process statistics; `RTextScrollPane` white-rectangle artifact.

## [2.5.19] - 2025-10-13
### Added
- RAM and CPU metrics in the diagnostics panel.

## [2.5.18] - 2025-10-13
### Fixed
- Tab-switching behaviour; removed `TitledBorder` usage for consistency.

## [2.5.17] - 2025-10-13
### Added
- Custom tab component with zero-gap borders.

## [2.5.10-2.5.16] - 2025-10-12 to 2025-10-13
### Fixed
- Various white-line/border artifacts and component spacing/theme-consistency issues.

## [2.5.8-2.5.9] - 2025-10-11
### Added
- Interactive shell mode (Python REPL) with command history.

## [2.5.0-2.5.7] - 2025-10-10
### Added
- Shell Command Mode for terminal commands with pip integration.

## [2.0.15] - 2024-12-20
### Fixed
- Theme system fixes; Python version detection rebuilt.

## [2.0.14] - 2024-12-19
### Added
- Enhanced logging for debugging; file-chooser theme consistency.

## [2.0.13] - 2024-12-18
### Removed
- Experimental v2 code path removed and consolidated into the main implementation.

## [2.0.12] - 2024-12-17
### Added
- `DarkDialog` base class for theme-aware dialogs throughout the IDE.

## [2.0.0-2.0.11] - 2024-12-01 to 2024-12-16
### Changed
- Complete architecture refactor from the v1.x monolith: main class reduced from 2,676 to 490 lines, split into managers, UI panels and orchestration.

## [1.17.2] - 2024-11-30
### Deprecated
- Final v1.x release before the v2.0.0 refactor.

---

## Earlier Versions

For version history prior to v1.17.2, see git commit history:
```bash
git log --oneline --all --before="2024-11-30"
```

---

## Version Numbering Guide

**MAJOR.MINOR.PATCH** (Semantic Versioning)

- **MAJOR** (x.0.0): Breaking changes, major new features, architectural changes
- **MINOR** (1.x.0): New features, significant fixes, scope changes, API additions
- **PATCH** (1.0.x): Bug fixes, documentation updates, minor tweaks

---

## Links

- **Repository:** https://github.com/Gaskony-Ignition/ignition-module-python3
- **Documentation:** [python3-integration/docs/](python3-integration/docs/)
- **Architecture:** [python3-integration/docs/V2_ARCHITECTURE_GUIDE.md](python3-integration/docs/V2_ARCHITECTURE_GUIDE.md)
- **Testing:** [python3-integration/docs/TESTING_GUIDE.md](python3-integration/docs/TESTING_GUIDE.md)
