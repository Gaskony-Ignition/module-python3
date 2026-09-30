#!/bin/bash
# CI/CD Summary Generation Script
# Generates a comprehensive summary of CI/CD results for artifact archiving

set -e

SUMMARY_FILE="${1:-ci_summary.log}"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S UTC')

echo "========================================" > "$SUMMARY_FILE"
echo "CI/CD PIPELINE SUMMARY" >> "$SUMMARY_FILE"
echo "========================================" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"
echo "Generated: $TIMESTAMP" >> "$SUMMARY_FILE"
echo "Workflow: ${GITHUB_WORKFLOW:-Unknown}" >> "$SUMMARY_FILE"
echo "Run ID: ${GITHUB_RUN_ID:-Unknown}" >> "$SUMMARY_FILE"
echo "Run Number: ${GITHUB_RUN_NUMBER:-Unknown}" >> "$SUMMARY_FILE"
echo "Commit SHA: ${GITHUB_SHA:-Unknown}" >> "$SUMMARY_FILE"
echo "Branch: ${GITHUB_REF_NAME:-Unknown}" >> "$SUMMARY_FILE"
echo "Actor: ${GITHUB_ACTOR:-Unknown}" >> "$SUMMARY_FILE"
echo "Event: ${GITHUB_EVENT_NAME:-Unknown}" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"

# Build Information
echo "========================================" >> "$SUMMARY_FILE"
echo "BUILD INFORMATION" >> "$SUMMARY_FILE"
echo "========================================" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"

if [ -f "build.gradle.kts" ]; then
    VERSION=$(grep '^version = ' build.gradle.kts | head -1 | sed 's/version = "\(.*\)"/\1/')
    echo "Module Version: ${VERSION}" >> "$SUMMARY_FILE"
fi

echo "Java Version: ${JAVA_VERSION:-17}" >> "$SUMMARY_FILE"
echo "Python Version: ${PYTHON_VERSION:-3.11}" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"

# Test Results
echo "========================================" >> "$SUMMARY_FILE"
echo "TEST RESULTS" >> "$SUMMARY_FILE"
echo "========================================" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"

if [ -d "python3-integration/gateway/build/test-results/test" ]; then
    TOTAL_TESTS=$(find python3-integration/gateway/build/test-results/test -name "*.xml" -exec grep -oh 'tests="[0-9]*"' {} \; | grep -oh '[0-9]*' | awk '{s+=$1} END {print s}')
    FAILED_TESTS=$(find python3-integration/gateway/build/test-results/test -name "*.xml" -exec grep -oh 'failures="[0-9]*"' {} \; | grep -oh '[0-9]*' | awk '{s+=$1} END {print s}')
    SKIPPED_TESTS=$(find python3-integration/gateway/build/test-results/test -name "*.xml" -exec grep -oh 'skipped="[0-9]*"' {} \; | grep -oh '[0-9]*' | awk '{s+=$1} END {print s}')
    PASSED_TESTS=$((TOTAL_TESTS - FAILED_TESTS - SKIPPED_TESTS))

    echo "Total Tests: ${TOTAL_TESTS:-0}" >> "$SUMMARY_FILE"
    echo "Passed: ${PASSED_TESTS:-0}" >> "$SUMMARY_FILE"
    echo "Failed: ${FAILED_TESTS:-0}" >> "$SUMMARY_FILE"
    echo "Skipped: ${SKIPPED_TESTS:-0}" >> "$SUMMARY_FILE"
    echo "" >> "$SUMMARY_FILE"

    if [ "${FAILED_TESTS:-0}" -gt 0 ]; then
        echo "❌ TESTS FAILED" >> "$SUMMARY_FILE"
    else
        echo "✅ ALL TESTS PASSED" >> "$SUMMARY_FILE"
    fi
else
    echo "⚠️  No test results found" >> "$SUMMARY_FILE"
fi

echo "" >> "$SUMMARY_FILE"

# Code Coverage
echo "========================================" >> "$SUMMARY_FILE"
echo "CODE COVERAGE" >> "$SUMMARY_FILE"
echo "========================================" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"

if [ -f "python3-integration/gateway/build/reports/jacoco/test/html/index.html" ]; then
    COVERAGE=$(grep -oP 'Total.*?<td class="ctr2">\K[0-9]+(?=%)' python3-integration/gateway/build/reports/jacoco/test/html/index.html | head -1)
    echo "Overall Coverage: ${COVERAGE}%" >> "$SUMMARY_FILE"

    MIN_COVERAGE=5
    if [ "${COVERAGE:-0}" -ge "$MIN_COVERAGE" ]; then
        echo "✅ Coverage meets minimum threshold (${MIN_COVERAGE}%)" >> "$SUMMARY_FILE"
    else
        echo "❌ Coverage below minimum threshold (${MIN_COVERAGE}%)" >> "$SUMMARY_FILE"
    fi
else
    echo "⚠️  No coverage report found" >> "$SUMMARY_FILE"
fi

echo "" >> "$SUMMARY_FILE"

# Code Quality
echo "========================================" >> "$SUMMARY_FILE"
echo "CODE QUALITY CHECKS" >> "$SUMMARY_FILE"
echo "========================================" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"

# Checkstyle
if [ -d "python3-integration/build/reports/checkstyle" ]; then
    CHECKSTYLE_ERRORS=$(find python3-integration/build/reports/checkstyle -name "*.xml" -exec grep -oh 'severity="error"' {} \; | wc -l)
    CHECKSTYLE_WARNINGS=$(find python3-integration/build/reports/checkstyle -name "*.xml" -exec grep -oh 'severity="warning"' {} \; | wc -l)
    echo "Checkstyle Errors: ${CHECKSTYLE_ERRORS}" >> "$SUMMARY_FILE"
    echo "Checkstyle Warnings: ${CHECKSTYLE_WARNINGS}" >> "$SUMMARY_FILE"

    if [ "${CHECKSTYLE_ERRORS}" -eq 0 ]; then
        echo "✅ No Checkstyle errors" >> "$SUMMARY_FILE"
    else
        echo "❌ Checkstyle errors found" >> "$SUMMARY_FILE"
    fi
else
    echo "⚠️  Checkstyle not run" >> "$SUMMARY_FILE"
fi

echo "" >> "$SUMMARY_FILE"

# Security Scan
echo "========================================" >> "$SUMMARY_FILE"
echo "SECURITY SCAN" >> "$SUMMARY_FILE"
echo "========================================" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"

if [ -f "python3-integration/build/reports/dependency-check-report.html" ]; then
    VULNERABILITIES=$(grep -oh 'Total.*vulnerabilities' python3-integration/build/reports/dependency-check-report.html | grep -oh '[0-9]*' | head -1)
    echo "Dependencies Scanned: ✓" >> "$SUMMARY_FILE"
    echo "Vulnerabilities Found: ${VULNERABILITIES:-0}" >> "$SUMMARY_FILE"

    if [ "${VULNERABILITIES:-0}" -eq 0 ]; then
        echo "✅ No vulnerabilities detected" >> "$SUMMARY_FILE"
    else
        echo "⚠️  Vulnerabilities detected - review report" >> "$SUMMARY_FILE"
    fi
else
    echo "⚠️  Security scan not run" >> "$SUMMARY_FILE"
fi

echo "" >> "$SUMMARY_FILE"

# Build Artifacts
echo "========================================" >> "$SUMMARY_FILE"
echo "BUILD ARTIFACTS" >> "$SUMMARY_FILE"
echo "========================================" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"

if [ -d "python3-integration/build/libs" ]; then
    echo "Module Files:" >> "$SUMMARY_FILE"
    find python3-integration/build/libs -name "*.modl" -exec ls -lh {} \; | awk '{print "  - " $9 " (" $5 ")"}' >> "$SUMMARY_FILE"
else
    echo "⚠️  No build artifacts found" >> "$SUMMARY_FILE"
fi

echo "" >> "$SUMMARY_FILE"

# Final Status
echo "========================================" >> "$SUMMARY_FILE"
echo "PIPELINE STATUS" >> "$SUMMARY_FILE"
echo "========================================" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"

if [ "${GITHUB_JOB_STATUS:-success}" = "success" ]; then
    echo "✅ PIPELINE SUCCEEDED" >> "$SUMMARY_FILE"
else
    echo "❌ PIPELINE FAILED" >> "$SUMMARY_FILE"
fi

echo "" >> "$SUMMARY_FILE"
echo "========================================" >> "$SUMMARY_FILE"
echo "END OF SUMMARY" >> "$SUMMARY_FILE"
echo "========================================" >> "$SUMMARY_FILE"

# Display summary to console
cat "$SUMMARY_FILE"

# Exit with success
exit 0
