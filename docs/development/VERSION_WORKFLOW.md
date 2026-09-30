# Version Update Workflow

This document defines the **mandatory checklist** for every version release to ensure consistency across all documentation.

## Pre-Release Checklist

### 1. Code Cleanup (ALWAYS FIRST)
- [ ] Delete Zone.Identifier files: `find . -name "*Zone.Identifier*" -type f -delete`
- [ ] Review code for commented-out sections
- [ ] Fix formatting issues
- [ ] Run checkstyle and address critical warnings

### 2. Version Update (version.properties)
- [ ] Update `version.properties` with new version number
- [ ] Determine version bump type:
  - **MAJOR (x.0.0)**: Breaking changes, major architectural changes
  - **MINOR (x.y.0)**: New features, significant fixes, scope changes
  - **PATCH (x.y.z)**: Bug fixes, documentation updates, minor tweaks

### 3. Update ALL Documentation References

#### Repository Root README.md (CRITICAL - /modules/ignition-module-python3/README.md)
- [ ] **Line 3**: Update "Current Version: vX.Y.Z" in header
- [ ] **Line 11**: Update "Latest Release: vX.Y.Z" section header
- [ ] **Line 13-19**: Update "New in vX.Y.Z" bullet list with latest changes
- [ ] **Line 38**: Update build output path with current version
- [ ] **Line 538**: Update "Output" path with current version

#### Module README.md (CRITICAL - MODULE_README.md - 8 locations)
- [ ] **Line 3**: Update "Current Version: vX.Y.Z" in header
- [ ] **Line 10**: Update Designer IDE version reference if applicable
- [ ] **Line 122**: Update build output path (should be `python3-integration-signed.modl`)
- [ ] **Line 136**: Update "Designer IDE (vX.Y.Z - Current)" section header
- [ ] **Line 140-151**: Update architecture/features with current version notes
- [ ] **Line 451**: Update Python3IDE_vX.java filename in project structure
- [ ] **Line 471**: Update version.properties comment
- [ ] **Line 501-526**: Update Roadmap section:
  - Update "Current Release: vX.Y.Z" header
  - Move completed items from "Planned" to "Completed" section
  - Add new completed features with version tags

#### version.properties
- [ ] Update `version.major`, `version.minor`, `version.patch`

#### Python3IDE_v2.java (or current main IDE file)
- [ ] Update status bar message: `statusBar.setStatus("Ready - vX.Y.Z (...)", ...)`

#### V2_STATUS_SUMMARY.md
- [ ] Update "Current Version: vX.Y.Z" at top
- [ ] Update "What Works Now (vX.Y.Z)" section header
- [ ] Add new version entry to "Version History (v2.0.x)" section
- [ ] Update completion percentages if features added

#### Changelog (README.md bottom section)
- [ ] Add new version entry at TOP of changelog
- [ ] Follow format:
  ```markdown
  ### X.Y.Z (Description)
  - **NEW**: New features
  - **FIXED**: Bug fixes
  - **IMPROVED**: Enhancements
  ```
- [ ] Use past tense for all entries
- [ ] Include file references where applicable

### 4. Build Verification
- [ ] Run clean build: `./gradlew clean build --no-daemon`
- [ ] Verify build succeeds
- [ ] Note checkstyle warning count (include in commit message)
- [ ] Check module file exists: `build/libs/python3-integration-signed.modl`

### 5. Git Commit
- [ ] Stage all changes: `git add -A`
- [ ] Create commit with EXACT format:

```bash
git commit -m "$(cat <<'EOF'
Release vX.Y.Z - [Short Description]

Version: X.Y.Z-1 → X.Y.Z (MAJOR/MINOR/PATCH)

[2-3 sentence description of what changed]

Changes:
- [Bullet list of key changes]
- [Include file names and line numbers where relevant]
- [Group by category: NEW, FIXED, IMPROVED]

Files Modified:
- path/to/file1.java (description)
- path/to/file2.java (description)
- version.properties (X.Y.Z-1 → X.Y.Z)
- README.md (changelog + version references updated)

Build: Successful (checkstyle: N warnings)

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

### 6. Push to GitHub
- [ ] Push to master: `git push`
- [ ] Verify push succeeded
- [ ] Check GitHub web interface shows correct version in README.md

---

## Post-Release Verification

### GitHub Web Interface Check
1. Navigate to: https://github.com/Gaskony-Ignition/ignition-module-python3
2. Verify README.md shows:
   - [ ] Correct version in header (line 3)
   - [ ] Correct version in "Designer IDE" section (line 136)
   - [ ] Correct version in Roadmap "Current Release" (line 501)
   - [ ] New changelog entry at top of changelog section
3. Verify docs/ directory:
   - [ ] V2_STATUS_SUMMARY.md shows current version
   - [ ] V2_FEATURE_COMPARISON_AND_ROADMAP.md is up to date

---

## Quick Reference: Files to Update

Every version release MUST update these files:

1. **version.properties** - Version number
2. **Repository Root README.md** (`/modules/ignition-module-python3/README.md`) - 5 locations
3. **Module README.md** (`MODULE_README.md`) - 8+ locations
4. **Python3IDE_v2.java** - Status bar message
5. **V2_STATUS_SUMMARY.md** - Version header, version history
6. **Changelog** - New entry in module README.md

---

## Common Mistakes to Avoid

❌ **DON'T:**
- Skip updating README.md header version
- Forget to update roadmap section
- Leave stale version references (v1.x when at v2.x)
- Commit without updating changelog
- Push without verifying GitHub shows correct version

✅ **DO:**
- Follow this checklist for EVERY release
- Use grep to find all version references: `grep -r "v2\.[0-9]" README.md`
- Double-check GitHub web interface after push
- Keep this workflow document updated

---

## Version Numbering Guidelines

### MAJOR (X.0.0)
- Breaking API changes
- Complete architecture rewrites
- Removal of deprecated features
- Example: v1.17.2 → v2.0.0 (architecture refactor)

### MINOR (X.Y.0)
- New features
- Significant enhancements
- Non-breaking API additions
- New UI panels or major components
- Example: v2.0.8 → v2.0.9 (UX fixes - could be MINOR if significant)
- Example: v2.0.9 → v2.1.0 (if adding syntax checking would be MINOR)

### PATCH (X.Y.Z)
- Bug fixes
- Documentation updates
- Minor UI tweaks
- Performance improvements (non-breaking)
- Example: v2.0.9 → v2.0.10 (small bug fix)

---

**Remember:** Consistency across documentation is critical. When users see "v2.0.9" on GitHub, ALL references should match!
