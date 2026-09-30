# Python 3 Integration - Backup & Restore Procedures

**Audience:** System Administrators, DevOps

---

## Table of Contents

1. [Overview](#overview)
2. [What to Backup](#what-to-backup)
3. [Backup Procedures](#backup-procedures)
4. [Restore Procedures](#restore-procedures)
5. [Disaster Recovery](#disaster-recovery)
6. [Migration Procedures](#migration-procedures)
7. [Testing Backups](#testing-backups)

---

## Overview

The Python 3 Integration module stores data in multiple locations that require regular backups. This guide covers comprehensive backup and restore procedures.

### Backup Strategy

- **Frequency:** Daily (automated)
- **Retention:** 30 days (recommended)
- **Storage:** Off-server (network drive or cloud)
- **Testing:** Monthly restore test

---

## What to Backup

### 1. Module Files

**Location:** `<ignition>/user-lib/modules/`
**Files:** `Python3Integration-2.6.0.modl`
**Size:** ~48MB
**Importance:** HIGH (module reinstallation)

This file allows you to reinstall the exact module version if needed.

---

### 2. Configuration Files

**Location:** `<ignition>/data/ignition.conf`
**Content:**
- Admin API key
- Pool size configuration
- Resource limits
- HTTPS settings

**Importance:** CRITICAL (security configuration)

**Example:**
```properties
wrapper.java.additional.200=-Dignition.python3.admin.apikey=a1b2c3d4e5f6...
wrapper.java.additional.202=-Dignition.python3.poolsize=5
wrapper.java.additional.203=-Dignition.python3.max.memory.mb=2048
```

---

### 3. Python Scripts (Script Repository)

**Location:** `<ignition>/data/python3-scripts/`
**Content:** User-created Python scripts and folders
**Size:** Varies (typically < 100MB)
**Importance:** HIGH (user data)

**Example Structure:**
```
python3-scripts/
├── Data Processing/
│   ├── import_csv.py
│   └── export_excel.py
├── Utilities/
│   └── backup.py
└── script1.py
```

---

### 4. Audit Logs

**Location:** `<ignition>/data/python3-integration/audit/`
**Content:** Daily audit log files (JSON)
**Size:** Varies (typically 10-100MB/day)
**Importance:** HIGH (compliance, forensics)

**Example:**
```
audit/
├── audit-2025-10-01.log
├── audit-2025-10-02.log
├── ...
└── audit-2025-10-20.log
```

---

### 5. Gateway Backup (Ignition Standard)

**Method:** Ignition Gateway Backup
**Content:** Full Gateway configuration, users, tags, projects, etc.
**Importance:** CRITICAL (complete system state)

**Location:** `<ignition>/backups/` (configurable)

---

## Backup Procedures

### Automated Daily Backup

Create a comprehensive backup script:

```bash
#!/bin/bash
# File: /opt/backup/python3-backup.sh
#
# Daily backup script for Python 3 Integration module
# Schedule with cron: 0 2 * * * /opt/backup/python3-backup.sh

# Configuration
IGNITION_DIR="/usr/local/ignition"
BACKUP_DIR="/mnt/backups/python3-integration"
RETENTION_DAYS=30
DATE=$(date +%Y-%m-%d)
BACKUP_PATH="$BACKUP_DIR/$DATE"

# Create backup directory
mkdir -p "$BACKUP_PATH"

# Log function
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$BACKUP_PATH/backup.log"
}

log "Starting Python 3 Integration backup"

# 1. Backup configuration
log "Backing up configuration..."
cp "$IGNITION_DIR/data/ignition.conf" "$BACKUP_PATH/ignition.conf"

# Extract only Python3-related config
grep "python3" "$IGNITION_DIR/data/ignition.conf" > "$BACKUP_PATH/python3-config.conf"

# 2. Backup module file
log "Backing up module file..."
MODULE_FILE=$(ls -t "$IGNITION_DIR/user-lib/modules/Python3Integration-*.modl" | head -1)
if [ -f "$MODULE_FILE" ]; then
    cp "$MODULE_FILE" "$BACKUP_PATH/"
    log "Module file backed up: $(basename $MODULE_FILE)"
else
    log "WARNING: Module file not found"
fi

# 3. Backup Python scripts
log "Backing up Python scripts..."
if [ -d "$IGNITION_DIR/data/python3-scripts" ]; then
    tar -czf "$BACKUP_PATH/python3-scripts.tar.gz" \
        -C "$IGNITION_DIR/data" python3-scripts/
    SCRIPT_COUNT=$(find "$IGNITION_DIR/data/python3-scripts" -name "*.py" | wc -l)
    log "Scripts backed up: $SCRIPT_COUNT files"
else
    log "WARNING: Script directory not found"
fi

# 4. Backup audit logs
log "Backing up audit logs..."
if [ -d "$IGNITION_DIR/data/python3-integration/audit" ]; then
    tar -czf "$BACKUP_PATH/audit-logs.tar.gz" \
        -C "$IGNITION_DIR/data/python3-integration" audit/
    LOG_SIZE=$(du -sh "$IGNITION_DIR/data/python3-integration/audit" | cut -f1)
    log "Audit logs backed up: $LOG_SIZE"
else
    log "WARNING: Audit log directory not found"
fi

# 5. Backup metadata (versions, stats)
log "Collecting metadata..."
cat > "$BACKUP_PATH/metadata.txt" <<EOF
Backup Date: $(date)
Gateway Version: $(grep "wrapper.version" "$IGNITION_DIR/data/ignition.conf" | cut -d= -f2)
Module Version: $(basename $MODULE_FILE .modl | cut -d- -f2)
Python Version: $(python3 --version 2>&1)
Hostname: $(hostname)
IP Address: $(hostname -I | awk '{print $1}')

Pool Configuration:
$(grep "python3.poolsize" "$IGNITION_DIR/data/ignition.conf" || echo "  Default (3)")

Resource Limits:
$(grep "PYTHON3_MAX" "$IGNITION_DIR/data/ignition.conf" || echo "  Default")

Script Count: $SCRIPT_COUNT
Audit Log Size: $LOG_SIZE
EOF

cat "$BACKUP_PATH/metadata.txt" >> "$BACKUP_PATH/backup.log"

# 6. Verify backup integrity
log "Verifying backup integrity..."
BACKUP_SIZE=$(du -sh "$BACKUP_PATH" | cut -f1)
FILE_COUNT=$(find "$BACKUP_PATH" -type f | wc -l)

if [ "$FILE_COUNT" -ge 5 ]; then
    log "Backup verification PASSED: $FILE_COUNT files, $BACKUP_SIZE total"
else
    log "ERROR: Backup verification FAILED: Only $FILE_COUNT files found"
    exit 1
fi

# 7. Clean up old backups
log "Cleaning up old backups (retention: $RETENTION_DAYS days)..."
find "$BACKUP_DIR" -maxdepth 1 -type d -mtime +$RETENTION_DAYS -exec rm -rf {} \;
REMAINING=$(find "$BACKUP_DIR" -maxdepth 1 -type d | wc -l)
log "Cleanup complete: $REMAINING backup(s) retained"

# 8. Create checksum
log "Creating checksum..."
cd "$BACKUP_PATH"
sha256sum * > checksums.txt

log "Backup complete: $BACKUP_PATH"
log "Total size: $BACKUP_SIZE"

# Optional: Upload to cloud storage
# aws s3 sync "$BACKUP_PATH" s3://my-bucket/python3-backups/$DATE/
# log "Uploaded to S3"

exit 0
```

**Setup Cron Job:**
```bash
# Edit crontab
crontab -e

# Add daily backup at 2 AM
0 2 * * * /opt/backup/python3-backup.sh

# Add weekly verification test (Sundays at 3 AM)
0 3 * * 0 /opt/backup/verify-backup.sh
```

---

### Manual Backup (Quick)

For immediate backup before maintenance:

```bash
#!/bin/bash
# Quick manual backup

DATE=$(date +%Y-%m-%d_%H-%M-%S)
BACKUP_DIR="/tmp/python3-backup-$DATE"

mkdir -p "$BACKUP_DIR"

# Backup essentials
cp /usr/local/ignition/data/ignition.conf "$BACKUP_DIR/"
tar -czf "$BACKUP_DIR/scripts.tar.gz" -C /usr/local/ignition/data python3-scripts/
tar -czf "$BACKUP_DIR/audit.tar.gz" -C /usr/local/ignition/data/python3-integration audit/

echo "Quick backup complete: $BACKUP_DIR"
ls -lh "$BACKUP_DIR"
```

---

### Cloud Backup (AWS S3 Example)

```bash
#!/bin/bash
# Upload backups to AWS S3

BACKUP_DATE=$(date +%Y-%m-%d)
LOCAL_BACKUP="/mnt/backups/python3-integration/$BACKUP_DATE"
S3_BUCKET="s3://my-company-backups/ignition/python3-integration"

# Upload to S3
aws s3 sync "$LOCAL_BACKUP" "$S3_BUCKET/$BACKUP_DATE/" \
    --storage-class STANDARD_IA \
    --server-side-encryption AES256

# Verify upload
aws s3 ls "$S3_BUCKET/$BACKUP_DATE/"

# Set lifecycle policy (auto-delete after 90 days)
# Configure in AWS Console or via CLI
```

---

## Restore Procedures

### Full Restore (Disaster Recovery)

**Scenario:** Complete system failure, restore from backup

**Prerequisites:**
- Fresh Ignition Gateway installed (same version)
- Backup files accessible
- Python 3.11+ installed

**Steps:**

```bash
#!/bin/bash
# Full restore procedure

BACKUP_DATE="2025-10-20"  # Specify backup date
BACKUP_PATH="/mnt/backups/python3-integration/$BACKUP_DATE"
IGNITION_DIR="/usr/local/ignition"

echo "=== Full Restore Procedure ==="
echo "Backup Date: $BACKUP_DATE"
echo "Target Gateway: $IGNITION_DIR"
echo ""
read -p "Continue? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "Restore cancelled"
    exit 0
fi

# 1. Stop Gateway
echo "Stopping Gateway..."
cd "$IGNITION_DIR"
./gwcmd.sh --stop

# 2. Restore configuration
echo "Restoring configuration..."
# Backup current config
cp data/ignition.conf data/ignition.conf.backup

# Extract Python3 config and append
grep "python3" "$BACKUP_PATH/ignition.conf" >> data/ignition.conf

echo "Configuration restored"

# 3. Restore module
echo "Restoring module..."
MODULE_FILE=$(ls "$BACKUP_PATH"/Python3Integration-*.modl)
cp "$MODULE_FILE" user-lib/modules/

echo "Module restored: $(basename $MODULE_FILE)"

# 4. Restore Python scripts
echo "Restoring Python scripts..."
rm -rf data/python3-scripts/
tar -xzf "$BACKUP_PATH/python3-scripts.tar.gz" -C data/

SCRIPT_COUNT=$(find data/python3-scripts -name "*.py" | wc -l)
echo "Scripts restored: $SCRIPT_COUNT files"

# 5. Restore audit logs
echo "Restoring audit logs..."
mkdir -p data/python3-integration/audit/
tar -xzf "$BACKUP_PATH/audit-logs.tar.gz" -C data/python3-integration/

echo "Audit logs restored"

# 6. Verify checksums
echo "Verifying integrity..."
cd "$BACKUP_PATH"
sha256sum -c checksums.txt

if [ $? -eq 0 ]; then
    echo "Integrity check PASSED"
else
    echo "WARNING: Integrity check FAILED"
fi

# 7. Start Gateway
echo "Starting Gateway..."
cd "$IGNITION_DIR"
./gwcmd.sh --start

# 8. Wait for Gateway to start
echo "Waiting for Gateway to start..."
sleep 30

# 9. Verify module loaded
echo "Verifying module..."
curl -s http://localhost:8088/data/python3integration/api/v1/health | jq

echo ""
echo "=== Restore Complete ==="
echo "Please verify:"
echo "1. Open Gateway web UI: http://localhost:8088"
echo "2. Check Modules page for Python 3 Integration"
echo "3. Test Designer IDE: Tools → Python 3 IDE"
echo "4. Verify scripts loaded in Script Browser"
```

---

### Partial Restore (Scripts Only)

**Scenario:** Restore accidentally deleted scripts

```bash
#!/bin/bash
# Restore scripts only

BACKUP_DATE="2025-10-20"
BACKUP_PATH="/mnt/backups/python3-integration/$BACKUP_DATE"
IGNITION_DIR="/usr/local/ignition"

echo "Restoring scripts from $BACKUP_DATE..."

# Extract scripts to temp directory first
TEMP_DIR="/tmp/restore-scripts"
mkdir -p "$TEMP_DIR"
tar -xzf "$BACKUP_PATH/python3-scripts.tar.gz" -C "$TEMP_DIR"

echo "Scripts extracted to: $TEMP_DIR/python3-scripts"
echo ""
echo "Available scripts:"
find "$TEMP_DIR/python3-scripts" -name "*.py"

echo ""
read -p "Copy all scripts to Gateway? (yes/no): " confirm

if [ "$confirm" == "yes" ]; then
    # Backup current scripts
    tar -czf "$IGNITION_DIR/data/python3-scripts-backup-$(date +%Y-%m-%d).tar.gz" \
        -C "$IGNITION_DIR/data" python3-scripts/

    # Restore scripts
    cp -r "$TEMP_DIR/python3-scripts/"* "$IGNITION_DIR/data/python3-scripts/"

    echo "Scripts restored successfully"
else
    echo "Manual restore:"
    echo "  Source: $TEMP_DIR/python3-scripts"
    echo "  Target: $IGNITION_DIR/data/python3-scripts"
fi

# Cleanup
rm -rf "$TEMP_DIR"
```

---

### Restore Audit Logs (Compliance/Forensics)

**Scenario:** Retrieve audit logs from specific date range

```bash
#!/bin/bash
# Restore audit logs for investigation

START_DATE="2025-10-01"
END_DATE="2025-10-20"
OUTPUT_DIR="/tmp/audit-investigation"

mkdir -p "$OUTPUT_DIR"

echo "Restoring audit logs from $START_DATE to $END_DATE..."

# Loop through date range
current="$START_DATE"
while [ "$current" != "$END_DATE" ]; do
    BACKUP_PATH="/mnt/backups/python3-integration/$current"

    if [ -f "$BACKUP_PATH/audit-logs.tar.gz" ]; then
        echo "Extracting $current..."
        tar -xzf "$BACKUP_PATH/audit-logs.tar.gz" -C "$OUTPUT_DIR"
    else
        echo "WARNING: Backup not found for $current"
    fi

    # Increment date
    current=$(date -I -d "$current + 1 day")
done

echo "Audit logs extracted to: $OUTPUT_DIR"
echo ""
echo "Analysis commands:"
echo "  Total executions: cat $OUTPUT_DIR/audit/*.log | wc -l"
echo "  Failed executions: grep '\"success\":false' $OUTPUT_DIR/audit/*.log | wc -l"
echo "  By user: grep -o '\"user\":\"[^\"]*\"' $OUTPUT_DIR/audit/*.log | sort | uniq -c"
```

---

## Disaster Recovery

### Recovery Time Objective (RTO)

**Target:** < 1 hour (Gateway fully operational)

**Timeline:**
- Ignition Gateway install: 15 minutes
- Module restore: 5 minutes
- Configuration restore: 5 minutes
- Data restore: 15 minutes
- Testing & verification: 20 minutes

### Recovery Point Objective (RPO)

**Target:** < 24 hours (data loss limited to last 24 hours)

**Strategy:** Daily backups at 2 AM

### DR Runbook

```
1. Install Ignition Gateway (same version)
   - Download from Inductive Automation
   - Install on fresh server
   - Complete initial setup wizard

2. Restore Gateway Backup
   - Navigate to Config → Backup/Restore
   - Upload latest gateway backup (.gwbk file)
   - Restart Gateway

3. Restore Python 3 Integration
   - Run full restore script (see above)
   - Verify module loaded
   - Test health endpoint

4. Verify Functionality
   - Test Designer IDE
   - Test REST API
   - Verify pool stats
   - Check audit logs

5. Update DNS/Load Balancer
   - Point production DNS to new server
   - Update firewall rules
   - Notify users

6. Monitor
   - Watch logs for errors
   - Check pool health
   - Verify audit logging
```

---

## Migration Procedures

### Migrating to New Server

**Scenario:** Move Python 3 Integration to new Gateway server

```bash
#!/bin/bash
# Migration script: Old server → New server

# On OLD server: Create migration package
OLD_IGNITION="/usr/local/ignition"
MIGRATION_PKG="/tmp/python3-migration-$(date +%Y-%m-%d).tar.gz"

echo "Creating migration package on OLD server..."

tar -czf "$MIGRATION_PKG" \
    -C "$OLD_IGNITION" \
    user-lib/modules/Python3Integration-*.modl \
    data/python3-scripts \
    data/python3-integration/audit

# Extract config
grep "python3" "$OLD_IGNITION/data/ignition.conf" > /tmp/python3-config.conf
tar -rf "$MIGRATION_PKG" -C /tmp python3-config.conf

echo "Migration package created: $MIGRATION_PKG"
echo "Transfer this file to new server"

# On NEW server: Apply migration package
NEW_IGNITION="/usr/local/ignition"

echo "Applying migration package on NEW server..."

# Extract package
tar -xzf "$MIGRATION_PKG" -C "$NEW_IGNITION"

# Append config
cat /tmp/python3-config.conf >> "$NEW_IGNITION/data/ignition.conf"

# Restart Gateway
cd "$NEW_IGNITION"
./gwcmd.sh -r

echo "Migration complete"
echo "Verify at: http://$(hostname):8088"
```

---

## Testing Backups

### Monthly Backup Test

Restore backups to test environment monthly:

```bash
#!/bin/bash
# Monthly backup verification test

TEST_GATEWAY="/opt/ignition-test"
LATEST_BACKUP=$(ls -td /mnt/backups/python3-integration/* | head -1)

echo "=== Backup Verification Test ==="
echo "Testing backup: $LATEST_BACKUP"
echo "Test Gateway: $TEST_GATEWAY"

# Restore to test environment
./restore-procedure.sh "$LATEST_BACKUP" "$TEST_GATEWAY"

# Automated tests
echo "Running automated tests..."

# Test 1: Module loads
curl -s http://localhost:8089/data/python3integration/api/v1/health | jq -e '.healthy == true'
TEST1=$?

# Test 2: Scripts exist
SCRIPT_COUNT=$(find "$TEST_GATEWAY/data/python3-scripts" -name "*.py" | wc -l)
if [ "$SCRIPT_COUNT" -gt 0 ]; then
    TEST2=0
else
    TEST2=1
fi

# Test 3: Audit logs intact
LOG_SIZE=$(du -sh "$TEST_GATEWAY/data/python3-integration/audit" | cut -f1)
if [ ! -z "$LOG_SIZE" ]; then
    TEST3=0
else
    TEST3=1
fi

# Results
echo ""
echo "=== Test Results ==="
echo "Module Health: $([ $TEST1 -eq 0 ] && echo 'PASS' || echo 'FAIL')"
echo "Scripts Restored: $([ $TEST2 -eq 0 ] && echo "PASS ($SCRIPT_COUNT files)" || echo 'FAIL')"
echo "Audit Logs: $([ $TEST3 -eq 0 ] && echo "PASS ($LOG_SIZE)" || echo 'FAIL')"

if [ $TEST1 -eq 0 ] && [ $TEST2 -eq 0 ] && [ $TEST3 -eq 0 ]; then
    echo ""
    echo "✓ All tests PASSED - Backup is valid"
    exit 0
else
    echo ""
    echo "✗ Some tests FAILED - Investigate backup"
    exit 1
fi
```

---

## Backup Checklist

### Daily (Automated)
- [ ] Python scripts backed up
- [ ] Audit logs backed up
- [ ] Configuration backed up
- [ ] Backups uploaded to off-site storage
- [ ] Old backups cleaned up (30+ days)

### Weekly (Manual Review)
- [ ] Verify backup script ran successfully
- [ ] Check backup sizes (no anomalies)
- [ ] Review backup logs for errors
- [ ] Verify off-site storage accessible

### Monthly (Testing)
- [ ] Restore backup to test environment
- [ ] Verify all components work
- [ ] Update DR runbook if needed
- [ ] Test restore procedures

### Quarterly (Audit)
- [ ] Full DR drill (complete restore)
- [ ] Update backup retention policy
- [ ] Review and update backup scripts
- [ ] Document any issues found

---

## Recovery Scenarios

| Scenario | Recovery Procedure | Est. Time | Data Loss |
|----------|-------------------|-----------|-----------|
| Accidental script deletion | Partial restore (scripts only) | 5 minutes | None |
| Module corruption | Reinstall module from backup | 10 minutes | None |
| Configuration loss | Restore ignition.conf | 5 minutes | None |
| Audit log corruption | Restore audit logs from backup | 15 minutes | < 24 hours |
| Complete Gateway failure | Full restore from backup | 60 minutes | < 24 hours |
| Server hardware failure | DR procedure (new server) | 2-4 hours | < 24 hours |

---

## Additional Resources

- **Gateway Backup Guide:** Ignition Manual → Backup/Restore
- **AWS S3 CLI:** https://docs.aws.amazon.com/cli/latest/reference/s3/
- **Disaster Recovery Planning:** SECURITY_CONFIG_GUIDE.md

---

*This guide was created for Python 3 Integration v2.6.0 - Last updated October 2025*
