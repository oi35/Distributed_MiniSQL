---
name: region-management
description: Implement and debug Region lifecycle management including split, merge, and migration
type: skill
---

# Region Management Skill

Use this skill when working on Region lifecycle operations.

## When to Use

- Implementing Region split logic
- Implementing Region migration
- Debugging Region state issues
- Handling Region assignment

## Region Lifecycle

### States

```
OFFLINE → OPENING → ONLINE → SPLITTING → ONLINE (2 new regions)
                  ↓
              CLOSING → OFFLINE
```

## Region Split

### When to Split

- Region size exceeds threshold (256MB)
- Triggered by Master's load balancer
- Manual split via admin command

### Split Workflow

**Step 1: Prepare**
```java
// Mark region as SPLITTING
region.setState(RegionState.SPLITTING);
// Stop accepting new writes
region.setReadOnly(true);
```

**Step 2: Find Split Point**
```java
// Get median key from MySQL
byte[] splitKey = findMedianKey(region);
```

**Step 3: Create Child Regions**
```java
RegionInfo left = new RegionInfo(
    generateRegionId(),
    region.getTableName(),
    region.getStartKey(),
    splitKey
);

RegionInfo right = new RegionInfo(
    generateRegionId(),
    region.getTableName(),
    splitKey,
    region.getEndKey()
);
```

**Step 4: Copy Data**
```java
// Copy data to new MySQL tables
copyDataToRegion(region, left);
copyDataToRegion(region, right);
```

**Step 5: Update Metadata**
```java
// Report to Master
master.reportRegionSplit(region.getId(), left, right);
// Master updates route table
```

**Step 6: Activate Children**
```java
// Mark children as ONLINE
left.setState(RegionState.ONLINE);
right.setState(RegionState.ONLINE);
// Delete parent region
deleteRegion(region);
```

## Region Migration

### When to Migrate

- Load balancing
- RegionServer failure
- Manual migration

### Migration Workflow

**Step 1: Prepare Target**
```java
// Create region on target RegionServer
targetRS.openRegion(region);
```

**Step 2: Sync Data**
```java
// Copy data from source to target
while (hasMoreData) {
    List<Row> batch = sourceRS.scanRegion(region, lastKey, batchSize);
    targetRS.bulkLoad(region, batch);
    lastKey = batch.get(batch.size() - 1).getKey();
}
```

**Step 3: Sync WAL**
```java
// Apply incremental WAL logs
List<LogEntry> logs = sourceRS.getWALSince(region, syncPoint);
targetRS.applyLogs(region, logs);
```

**Step 4: Switch Primary**
```java
// Update metadata
master.updateRegionLocation(region.getId(), targetRS.getId());
// Notify clients to refresh cache
```

**Step 5: Cleanup**
```java
// Close region on source
sourceRS.closeRegion(region.getId());
```

## Common Issues

### Issue: Split Fails Midway

**Recovery:**
1. Check which step failed
2. If before metadata update: retry split
3. If after metadata update: cleanup and use new regions

### Issue: Migration Data Loss

**Prevention:**
- Always use WAL for incremental sync
- Verify data count before switching
- Keep source region until confirmed

### Issue: Region State Inconsistent

**Fix:**
```java
// Force state reconciliation
master.reconcileRegionState(regionId);
```

## Testing

### Unit Test: Split Logic
```java
@Test
void testRegionSplit() {
    Region region = createTestRegion(1000); // 1000 rows
    List<Region> children = region.split();
    assertEquals(2, children.size());
    assertEquals(500, children.get(0).getRowCount());
    assertEquals(500, children.get(1).getRowCount());
}
```

### Integration Test: Migration
```java
@Test
void testRegionMigration() {
    Region region = createRegionOnRS1();
    migrateRegion(region, rs1, rs2);
    // Verify data
    assertEquals(getDataFromRS1(region), getDataFromRS2(region));
}
```
