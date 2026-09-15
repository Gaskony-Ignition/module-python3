# Release Checklist

**Purpose:** Ensure consistent, high-quality releases for every version

This checklist should be completed for EVERY release, no matter how small.

---

## 📋 Pre-Release Checklist

### 1. Code Quality ✅

- [ ] **All tests passing locally**
  ```bash
  ./gradlew clean test --no-daemon
  ```
  Expected: All 622+ tests pass

- [ ] **Code coverage acceptable** (current: 51.7%, target 80%)
  ```bash
  ./gradlew jacocoTestReport
  open gateway/build/reports/jacoco/test/html/index.html
  ```

- [ ] **No critical TODOs** in production code
  ```bash
  grep -rn "TODO.*CRITICAL\|FIXME.*URGENT" --include="*.java" .
  ```

- [ ] **Code style consistent**
  ```bash
  ./gradlew checkstyleMain
  ```

---

### 2. Version Management ✅

- [ ] **Increment version.properties** (3 files!)
  ```
  Files:
    version.properties
    common/src/main/resources/version.properties
    designer/src/main/resources/version.properties
  Current: 4.1.0
  New: _______
  ```

- [ ] **Update DesignerHook.java fallback version** (CRITICAL!)
  ```
  File: designer/src/main/java/.../DesignerHook.java
  Line: ~200
  return "4.1.0";  // ALWAYS UPDATE THIS WITH NEW RELEASES
  ```

- [ ] **Update build.gradle.kts description**
  ```
  File: build.gradle.kts
  Line: 35
  moduleDescription.set("...v2.15.10...")
  ```

- [ ] **Add CHANGELOG.md entry**
  ```
  File: CHANGELOG.md
  Format: [Version] - YYYY-MM-DD
  ```

- [ ] **Update documentation version references**
  ```
  Files to update:
  - README.md version badge
  - Any docs/* file that states a version number
  ```

---

### 3. Documentation ✅

- [ ] **README.md current**
  - Version badges updated
  - Latest release section updated
  - Features list accurate

- [ ] **CHANGELOG.md entry complete**
  - Type: MAJOR/MINOR/PATCH
  - Fixed/Changed/Added sections
  - Files changed list
  - Breaking changes noted (if any)

- [ ] **Delete Zone.Identifier files**
  ```bash
  find . -name "*Zone.Identifier*" -type f -delete
  ```

- [ ] **Remove commented code**
  ```bash
  # Manual review of recent changes
  git diff main
  ```

- [ ] **Remove debug statements**
  ```bash
  grep -rn "System.out.println\|console.log" --include="*.java" .
  ```

---

### 4. Security ✅

- [ ] **Dependency check passing**
  ```bash
  ./gradlew dependencyCheckAnalyze
  open build/reports/dependency-check-report.html
  ```
  Expected: No CVSS >= 7.0

- [ ] **No hardcoded secrets**
  ```bash
  grep -ri "password\|secret\|api[_-]key" --include="*.java" --include="*.properties" .
  ```

- [ ] **Security documentation current**
  - SECURITY.md
  - docs/security/CONFIG.md

---

### 5. Build & Test ✅

- [ ] **Clean build successful**
  ```bash
  ./gradlew clean build --no-daemon
  ```

- [ ] **Module file created**
  ```bash
  ls -lh build/libs/*.modl
  ```
  Expected: Python3-{version}-signed.modl

- [ ] **Module signed correctly**
  ```bash
  unzip -l build/libs/*-signed.modl | grep CERTIFIC
  ```

- [ ] **Module size reasonable** (~10-20MB)
  ```bash
  du -h build/libs/*.modl
  ```

---

## 📝 Git Workflow

### 6. Commit Changes ✅

- [ ] **Stage all changes**
  ```bash
  git add -A
  ```

- [ ] **Review changes**
  ```bash
  git status
  git diff --cached
  ```

- [ ] **Create commit with proper format**
  ```bash
  git commit -m "Release vX.Y.Z - [Title]

  Version: A.B.C → X.Y.Z (MAJOR/MINOR/PATCH)

  [Description of changes]

  Changes:
  - [Change 1]
  - [Change 2]

  Files Changed:
  - [File list]

  Build Status:
  ✅ All 198 tests passing
  ✅ Module compiled successfully
  ✅ Ready for production deployment

  🤖 Generated with [Claude Code](https://claude.com/claude-code)

  Co-Authored-By: Claude <noreply@anthropic.com>"
  ```

- [ ] **Push to GitHub**
  ```bash
  git push origin main
  ```

---

## 🚀 Post-Release

### 7. GitHub Release ✅

- [ ] **Create GitHub release tag**
  - Tag: `v2.15.10`
  - Title: `v2.15.10 - [Title]`
  - Description: Copy from CHANGELOG.md

- [ ] **Upload .modl file to release**
  ```bash
  # Attach: build/libs/Python3-2.15.10-signed.modl
  ```

- [ ] **Mark as latest release** (if stable)

---

### 8. Verification ✅

- [ ] **Test installation in clean Ignition**
  - Install in Gateway
  - Verify module loads
  - Test basic functionality

- [ ] **Smoke test in Designer**
  - Open Python 3 IDE
  - Connect to Gateway
  - Execute simple script
  - Test package management

- [ ] **Test script console**
  ```python
  result = system.python3.exec("result = 2 + 2")
  print(result)  # Should print: 4
  ```

---

## 🔄 Communication

### 9. Update Stakeholders ✅

- [ ] **Update project board** (if applicable)
- [ ] **Notify users** of new release
- [ ] **Update documentation site** (if applicable)
- [ ] **Post release notes** (GitHub Discussions, forum, etc.)

---

## ⚠️ Rollback Plan

If issues found after release:

1. **Immediate:**
   - Mark release as "Pre-release" on GitHub
   - Document issue in release notes

2. **Quick fix:**
   - Create hotfix branch
   - Fix issue
   - Release patch version (e.g., 2.15.11)

3. **Major issue:**
   - Revert to previous version
   - Remove faulty release from GitHub
   - Investigate root cause

---

## 📊 Release Metrics

Track for each release:

| Metric | Target | Actual |
|--------|--------|--------|
| **Test Pass Rate** | 100% | ____% |
| **Code Coverage** | 19%+ | ____% |
| **Build Time** | <3 min | ____ |
| **Module Size** | <20MB | ____MB |
| **Breaking Changes** | 0 (PATCH) | ____ |

---

## ✅ Sign-Off

**Release Manager:** ________________
**Date:** ________________
**Version:** ________________
**Status:** ☐ APPROVED  ☐ REJECTED

**Notes:**
_________________________________________________________________
_________________________________________________________________

---

## 🔗 Resources

- **Version Workflow:** `docs/development/VERSION_WORKFLOW.md`
- **Testing Guide:** `docs/development/TESTING.md`
- **CHANGELOG Format:** https://keepachangelog.com/
- **Semantic Versioning:** https://semver.org/

---

**Maintained By:** Development Team
