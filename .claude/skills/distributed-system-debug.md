---
name: distributed-system-debug
description: Debug distributed system issues including network failures, consensus problems, and data inconsistencies
type: skill
---

# Distributed System Debugging Skill

Use this skill when debugging distributed system issues in the MiniSQL project.

## When to Use

- Master-RegionServer communication failures
- Region split or migration issues
- Paxos consensus failures
- Data inconsistency between replicas
- Network partition scenarios
- Zookeeper connection problems

## Debugging Workflow

### Step 1: Identify the Symptom

Ask the user:
- What operation failed?
- Which components are involved?
- Any error messages or logs?

### Step 2: Check System State

```bash
# Check Master status
curl http://localhost:8080/status

# Check RegionServer status
curl http://localhost:8001/status

# Check Zookeeper
echo stat | nc localhost 2181
```

### Step 3: Analyze Logs

Look for patterns in logs:
- Connection timeouts
- Consensus failures
- Region state transitions
- Heartbeat failures

### Step 4: Reproduce Locally

Create a minimal test case that reproduces the issue.

### Step 5: Fix and Verify

- Apply fix
- Run integration tests
- Verify in distributed environment

## Common Issues

### Issue 1: Region Not Found

**Symptom:** Client gets "Region not found" error

**Debug:**
1. Check Master's region route table
2. Verify RegionServer has the region loaded
3. Check if region split recently occurred

**Fix:** Refresh client cache or reassign region

### Issue 2: Paxos Timeout

**Symptom:** Write operations timeout

**Debug:**
1. Check network latency between replicas
2. Verify all replicas are alive
3. Check Paxos proposal logs

**Fix:** Increase timeout or fix network issues

### Issue 3: Data Inconsistency

**Symptom:** Different data on replicas

**Debug:**
1. Compare WAL logs on all replicas
2. Check last applied sequence ID
3. Verify replication is working

**Fix:** Trigger manual sync or rebuild replica
