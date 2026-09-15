# Python 3 Integration - Production Deployment Checklist

**Purpose:** Pre-production deployment verification

---

## Overview

This checklist ensures the Python 3 Integration module is properly configured and secured before production deployment. Complete all items before going live.

**Estimated Time:** 2-3 hours

---

## Pre-Deployment Checklist

### 1. Environment Verification

- [ ] **Gateway Version:** Ignition 8.3 or later installed
- [ ] **Python Version:** Python 3.11+ installed on Gateway server
- [ ] **Disk Space:** At least 10GB free space for logs and scripts
- [ ] **Memory:** At least 4GB RAM available (8GB+ recommended)
- [ ] **CPU:** 4+ cores recommended for production workload
- [ ] **Network:** Gateway accessible on required ports (8088/8043)
- [ ] **SSL Certificate:** Valid SSL certificate installed for HTTPS

**Verification Commands:**
```bash
# Check Python version
python3 --version

# Check disk space
df -h

# Check memory
free -h

# Check CPU
lscpu | grep "CPU(s)"

# Test Gateway connectivity
curl http://localhost:8088/StatusPing
```

---

### 2. Module Installation

- [ ] **Module File:** Downloaded Python3Integration-2.6.0.modl
- [ ] **File Integrity:** Verify checksum (SHA-256)
- [ ] **Upload Module:** Navigate to Config → System → Modules
- [ ] **Install Module:** Click "Install or Upgrade a Module"
- [ ] **Gateway Restart:** Restart Gateway after installation
- [ ] **Verify Installation:** Module appears in module list
- [ ] **Check Version:** Verify version shows 2.6.0
- [ ] **Check Status:** Status shows "Running" (green)

**Verification:**
```bash
# Check module in Gateway logs
tail -f logs/wrapper.log | grep "Python3"

# Expected output:
# INFO  [Python3] Python 3 Integration module startup
# INFO  [Python3] Python version: 3.11.2
# INFO  [Python3] Process pool initialized: 3 processes
```

---

### 3. Security Configuration

#### 3.1 Admin API Key

- [ ] **Generate Key:** Create 32+ character API key
  ```bash
  openssl rand -hex 32
  ```
- [ ] **Configure Key:** Add to `data/ignition.conf`
  ```properties
  wrapper.java.additional.200=-Dignition.python3.admin.apikey=<generated-key>
  ```
- [ ] **Document Key:** Store in password manager (LastPass, 1Password, etc.)
- [ ] **Secure Storage:** Never commit key to version control
- [ ] **Share Securely:** Only share via encrypted channels
- [ ] **Set Rotation Date:** Schedule key rotation in 90 days

**Verification:**
```bash
# Check logs for key confirmation
grep "Admin API key configured" logs/wrapper.log

# Test API key works
curl -X POST https://localhost:8088/data/python3integration/api/v1/exec \
  -H "Authorization: Bearer <api-key>" \
  -H "Content-Type: application/json" \
  -d '{"code": "import os; result = os.getcwd()"}'
```

#### 3.2 HTTPS Configuration

- [ ] **SSL Certificate:** Valid certificate installed (not self-signed)
- [ ] **HTTPS Enabled:** Ignition configured for HTTPS on port 8043
- [ ] **HTTP Disabled:** Port 8088 blocked for external access (firewall)
- [ ] **HTTPS Requirement:** `requirehttps=true` (default, verify not disabled)
- [ ] **Certificate Expiry:** Certificate valid for at least 30 days
- [ ] **Certificate Chain:** Intermediate certificates installed

**Verification:**
```bash
# Test HTTPS
curl https://localhost:8043/StatusPing

# Verify certificate
openssl s_client -connect localhost:8043 -servername localhost

# Check expiry date
echo | openssl s_client -connect localhost:8043 2>/dev/null | \
  openssl x509 -noout -dates
```

#### 3.3 Process Pool Configuration

- [ ] **Pool Size:** Configured based on expected concurrency
  ```properties
  wrapper.java.additional.202=-Dignition.python3.poolsize=<size>
  ```
- [ ] **Small System (2-4 cores):** Pool size 3-5
- [ ] **Medium System (8-16 cores):** Pool size 5-10
- [ ] **Large System (32+ cores):** Pool size 10-20
- [ ] **Pool Health:** All processes healthy after startup

**Verification:**
```bash
# Check pool stats
curl http://localhost:8088/data/python3integration/api/v1/pool-stats | jq

# Expected: healthy == totalSize
```

#### 3.4 Resource Limits

- [ ] **Memory Limit:** Set appropriate limit (2048MB default)
  ```properties
  wrapper.java.additional.203=-Dignition.python3.max.memory.mb=2048
  ```
- [ ] **CPU Limit:** Set execution timeout (60s default)
  ```properties
  wrapper.java.additional.204=-Dignition.python3.max.cpu.seconds=60
  ```
- [ ] **Limits Verified:** Test that limits are enforced

**Verification:**
```bash
# Test memory limit (if configured) — remember all endpoints require auth
# (v4.0.0+); an unauthenticated call fails with 401/403 before Python runs
# and would not actually exercise the memory limit.
curl -X POST https://localhost:8088/data/python3integration/api/v1/exec \
  -H "Authorization: Bearer <api-key>" \
  -H "Content-Type: application/json" \
  -d '{"code": "x = [0] * 1000000000"}'  # Should hit memory limit
```

---

### 4. Network & Firewall Configuration

- [ ] **Internal Access:** Gateway accessible from internal network
- [ ] **External Access:** Firewall rules block external access to port 8088
- [ ] **HTTPS Only:** Only port 8043 (HTTPS) accessible externally
- [ ] **VPN Required:** Remote access requires VPN connection
- [ ] **IP Whitelist:** API access restricted to known IP ranges (optional)
- [ ] **Rate Limiting:** Default rate limit (100/min) acceptable
- [ ] **DDoS Protection:** Gateway behind reverse proxy/load balancer (optional)

**Verification:**
```bash
# Test from external IP (should fail for port 8088)
curl http://<external-ip>:8088/StatusPing

# Test HTTPS from external IP (should work)
curl https://<external-ip>:8043/StatusPing

# Check firewall rules
sudo iptables -L -n | grep 8088
```

---

### 5. Audit Logging

- [ ] **Log Directory:** Verify audit log directory exists
  ```bash
  ls -la data/python3-integration/audit/
  ```
- [ ] **Log Permissions:** Gateway has write access to log directory
- [ ] **Log Rotation:** Daily rotation working (check multiple days)
- [ ] **Log Retention:** Configure retention policy (recommend 90 days)
- [ ] **Log Forwarding:** Logs forwarded to SIEM if required (Splunk, ELK, etc.)
- [ ] **Log Monitoring:** Alerts configured for security events

**Verification:**
```bash
# Check audit logs exist
ls -lh data/python3-integration/audit/audit-*.log

# Check recent entries
tail -f data/python3-integration/audit/audit-$(date +%Y-%m-%d).log

# Verify log format (should be JSON)
head -1 data/python3-integration/audit/audit-$(date +%Y-%m-%d).log | jq
```

---

### 6. Backup & Recovery

- [ ] **Gateway Backup:** Standard Ignition Gateway backup configured
- [ ] **Backup Schedule:** Daily backups enabled
- [ ] **Backup Location:** Backups stored off-server (network drive, cloud)
- [ ] **Backup Retention:** 30-day retention configured
- [ ] **Backup Testing:** Verify backup can be restored (test environment)
- [ ] **Script Repository:** Python scripts backed up separately
  ```bash
  tar -czf scripts-backup.tar.gz data/python3-scripts/
  ```
- [ ] **Recovery Procedure:** Documented and tested

**Verification:**
```bash
# Check Gateway backup status
# Navigate to: Config → Backup/Restore → Gateway Backup

# Manually backup scripts
cd <ignition>/data/
tar -czf python3-scripts-$(date +%Y-%m-%d).tar.gz python3-scripts/

# Verify backup
tar -tzf python3-scripts-$(date +%Y-%m-%d).tar.gz | head -10
```

---

### 7. Monitoring & Alerting

- [ ] **Pool Health Monitoring:** Script/tool monitors pool stats every 5 minutes
- [ ] **Alert on Unhealthy:** Alert if `healthy < totalSize`
- [ ] **Alert on Exhaustion:** Alert if `available == 0` for > 5 minutes
- [ ] **Execution Metrics:** Track average execution time
- [ ] **Error Rate Monitoring:** Alert if error rate > 10%
- [ ] **Disk Space Monitoring:** Alert if disk < 10% free
- [ ] **Gateway Logs:** Monitor for ERROR/WARN messages
- [ ] **Performance Baseline:** Establish baseline metrics

**Verification:**
```bash
# Test monitoring endpoint
curl http://localhost:8088/data/python3integration/api/v1/diagnostics | jq

# Sample monitoring script
watch -n 300 'curl -s http://localhost:8088/data/python3integration/api/v1/pool-stats | jq'
```

---

### 8. User Access & Permissions

- [ ] **Designer Access:** Only administrators have Designer access
- [ ] **API Key Access:** API keys distributed only to authorized systems
- [ ] **Key Inventory:** Document which systems have API keys
- [ ] **User Training:** Designer users trained on Python 3 IDE
- [ ] **Documentation:** User guides distributed to relevant teams
- [ ] **Support Contacts:** IT support knows how to troubleshoot

**Verification:**
```bash
# List Ignition users with Designer access
# Navigate to: Config → Security → Users → Check "Designer" role
```

---

### 9. Testing & Validation

#### 9.1 Functional Testing

- [ ] **Unauthenticated Test:** Verify anonymous requests are rejected (v4.0.0+)
  ```bash
  curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
    -H "Content-Type: application/json" \
    -d '{"code": "import math; result = math.sqrt(16)"}'
  # Should return 403 Forbidden (RESTRICTED mode was removed in v4.0.0)
  ```
- [ ] **ADMIN Mode Test:** Verify Python exec works with API key
  ```bash
  curl -X POST https://localhost:8088/data/python3integration/api/v1/exec \
    -H "Authorization: Bearer <api-key>" \
    -H "Content-Type: application/json" \
    -d '{"code": "import os; result = os.getcwd()"}'
  ```
- [ ] **Designer IDE Test:** Open Designer IDE and execute script
- [ ] **Resource Limits Test:** Verify oversized payloads / runaway loops are caught
  ```bash
  curl -X POST https://localhost:8088/data/python3integration/api/v1/exec \
    -H "Authorization: Bearer <api-key>" \
    -H "Content-Type: application/json" \
    -d '{"code": "while True: pass"}'
  # Should time out after 60s and return a timeout error
  ```

#### 9.2 Security Testing

- [ ] **Unauthenticated Bypass Test:** Verify there is no way around the
  auth gate (there is no AST/whitelist layer to "bypass" — it was removed in
  v4.0.0; this test just confirms unauthenticated calls fail regardless of
  payload content)
  ```bash
  curl -X POST http://localhost:8088/data/python3integration/api/v1/exec \
    -H "Content-Type: application/json" \
    -d '{"code": "__import__(\"os\").system(\"ls\")"}'
  # Should fail with 401/403 (auth missing) — NOT because the payload was
  # inspected and blocked
  ```
- [ ] **Invalid API Key Test:** Verify invalid key rejected
- [ ] **HTTP Test:** Verify ADMIN mode requires HTTPS
- [ ] **Rate Limit Test:** Send 101 requests in 1 minute (should throttle)

#### 9.3 Performance Testing

- [ ] **Load Test:** Send 100 concurrent requests
  ```bash
  # Using Apache Bench
  ab -n 100 -c 10 -p payload.json -T application/json \
    http://localhost:8088/data/python3integration/api/v1/exec
  ```
- [ ] **Response Time:** Average < 200ms for simple calculations
- [ ] **Pool Saturation:** Test with requests > pool size (verify queueing)
- [ ] **Memory Usage:** Verify memory stays within limits

---

### 10. Documentation & Handoff

- [ ] **README Updated:** Module README reflects production config
- [ ] **Runbook Created:** Operations runbook for common issues
- [ ] **Architecture Diagram:** Network/deployment diagram created
- [ ] **Contact List:** On-call contacts documented
- [ ] **Change Log:** Deployment changes documented
- [ ] **Training Complete:** Operations team trained on module
- [ ] **Go-Live Plan:** Deployment schedule and rollback plan documented

**Documents to Prepare:**
1. ✅ DESIGNER_USER_GUIDE.md (complete)
2. ✅ REST_API_GUIDE.md (complete)
3. ✅ SECURITY_CONFIG_GUIDE.md (complete)
4. ✅ SECURITY_GUIDE.md (complete)
5. ✅ DEPLOYMENT_CHECKLIST.md (this document)
6. 📋 MONITORING_GUIDE.md (Day 19)
7. 📋 BACKUP_RESTORE.md (Day 19)

---

## Post-Deployment Verification

### First 24 Hours

- [ ] **Monitor Logs:** Watch for errors or warnings
  ```bash
  tail -f logs/wrapper.log | grep -E "ERROR|WARN"
  ```
- [ ] **Check Pool Health:** Verify all processes healthy
  ```bash
  watch -n 60 'curl -s http://localhost:8088/data/python3integration/api/v1/pool-stats | jq'
  ```
- [ ] **Review Audit Logs:** Check execution patterns
  ```bash
  tail -f data/python3-integration/audit/audit-$(date +%Y-%m-%d).log | jq
  ```
- [ ] **Monitor Performance:** Track response times
- [ ] **Check Disk Usage:** Verify logs not filling disk
  ```bash
  df -h | grep $(pwd)
  ```

### First Week

- [ ] **Daily Log Review:** Check for anomalies daily
- [ ] **Performance Analysis:** Compare against baseline
- [ ] **User Feedback:** Collect feedback from Designer users
- [ ] **Security Review:** Review failed executions
  ```bash
  grep '"success":false' data/python3-integration/audit/*.log | wc -l
  ```
- [ ] **Adjust Pool Size:** Optimize based on actual usage

### First Month

- [ ] **Monthly Security Audit:** Review audit logs for anomalies
- [ ] **Key Rotation:** Plan first API key rotation
- [ ] **Backup Verification:** Verify backups are working
- [ ] **Performance Tuning:** Optimize based on metrics
- [ ] **Update Documentation:** Document any config changes

---

## Rollback Plan

If issues occur, follow this rollback procedure:

### Immediate Rollback (< 5 minutes)

1. **Disable Module:**
   - Navigate to Config → System → Modules
   - Click "Disable" on Python 3 Integration module
   - Restart Gateway

2. **Restore Previous Version:**
   - Upload previous module version (.modl file)
   - Restart Gateway

3. **Restore Scripts (if needed):**
   ```bash
   cd <ignition>/data/
   rm -rf python3-scripts/
   tar -xzf python3-scripts-backup.tar.gz
   ```

### Full Rollback (< 30 minutes)

1. **Gateway Backup Restore:**
   - Navigate to Config → Backup/Restore
   - Select pre-upgrade backup
   - Click "Restore"
   - Restart Gateway

2. **Verify Rollback:**
   - Check module list (Python 3 Integration should be previous version or removed)
   - Test core Gateway functionality
   - Notify users of rollback

---

## Sign-Off

**Deployment Approved By:**

| Role | Name | Signature | Date |
|------|------|-----------|------|
| System Administrator | _____________ | _____________ | ______ |
| Security Officer | _____________ | _____________ | ______ |
| Project Manager | _____________ | _____________ | ______ |
| Operations Lead | _____________ | _____________ | ______ |

**Deployment Date:** ______________

**Deployed By:** ______________

**Production Environment:** ______________

**Gateway URL:** ______________

---

## Quick Reference Commands

```bash
# Check module status
curl http://localhost:8088/data/python3integration/api/v1/health | jq

# Check pool stats
curl http://localhost:8088/data/python3integration/api/v1/pool-stats | jq

# Check Python version
curl http://localhost:8088/data/python3integration/api/v1/version | jq

# Monitor audit logs
tail -f data/python3-integration/audit/audit-$(date +%Y-%m-%d).log | jq

# Monitor Gateway logs
tail -f logs/wrapper.log | grep Python3

# Check disk space
df -h

# Check memory
free -h

# Restart Gateway
./gwcmd.sh -r
```

---

## Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| Module won't start | Check Python installation: `python3 --version` |
| Pool unhealthy | Restart Gateway: `./gwcmd.sh -r` |
| Audit logs not writing | Check directory permissions: `ls -la data/python3-integration/` |
| HTTPS errors | Verify certificate: `openssl s_client -connect localhost:8043` |
| Rate limiting too strict | Increase limit or optimise client requests |
| Performance slow | Increase pool size or add more CPU cores |

---

**Deployment Status:**

- [ ] ✅ **READY FOR PRODUCTION** - All checklist items complete
- [ ] ⚠️ **NEEDS ATTENTION** - Some items incomplete
- [ ] ❌ **NOT READY** - Major issues blocking deployment

**Notes:**
_____________________________________________________________________________________
_____________________________________________________________________________________
_____________________________________________________________________________________

---

*This checklist was created for Python 3 Integration v2.6.0 - Last updated October 2025*
