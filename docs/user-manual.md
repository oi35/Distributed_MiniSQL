# Distributed MiniSQL User Manual

## Overview

Distributed MiniSQL is an educational distributed database system with a Master-RegionServer architecture. It supports range-based sharding, replica management via Paxos consensus, and distributed query execution.

---

## 1. System Architecture

```
                    +-------------------+
                    |   CLI Admin Tool  |
                    +--------+----------+
                             |
                    +--------v----------+
                    |   Master Server   |
                    |  (cluster mgmt)   |
                    +--+-----+-----+----+
                       |     |     |
              +--------v+  +-v------+--+  +--v--------+
              |Region Svr|  |Region Svr|  |Region Svr  |
              |  (node1) |  |  (node2) |  |  (node3)   |
              +----------+  +----------+  +------------+
```

**Components**:
- **Master Server**: Cluster management, metadata storage (Zookeeper), routing, load balancing
- **RegionServer**: Data storage (in-memory or MySQL), query execution, region lifecycle management
- **CLI Admin Tool**: Command-line interface for cluster administration
- **Client SDK**: Java library for application access

---

## 2. Quick Start

### 2.1 Prerequisites

- Java 11+ Runtime
- Apache Zookeeper 3.8+ (for Master HA and metadata persistence)
- MySQL 8.0+ (optional, for RegionServer MySQL backend)

### 2.2 Building the Project

```bash
# Build all modules (with tests)
mvn clean install

# Build without tests (faster)
mvn clean install -DskipTests

# Build specific modules only
mvn clean install -pl minisql-common,minisql-master -am -DskipTests
```

### 2.3 Starting the System

**Step 1**: Start Zookeeper (if not already running)

```bash
bin/zkServer.sh start
```

**Step 2**: Start Master Server

```bash
cd minisql-master

# Using Maven (default port 8000)
mvn exec:java -Dexec.mainClass="com.minisql.master.MasterServer"

# With custom port and ID
MASTER_PORT=9000 MASTER_ID=master-01 mvn exec:java \
  -Dexec.mainClass="com.minisql.master.MasterServer"

# Or using command-line arguments
mvn exec:java -Dexec.mainClass="com.minisql.master.MasterServer" \
  -Dexec.args="8000 master-1 localhost:2181"
```

**Step 3**: Start RegionServer(s)

```bash
# Each RegionServer needs a unique ID and port
cd minisql-regionserver
mvn exec:java -Dexec.mainClass="com.minisql.regionserver.RegionServerMain" \
  -Dexec.args="rs-001 8001"
```

RegionServer默认使用内存存储后端（`storage.backend=memory`），无需MySQL即可运行。

### 2.4 Verifying the Cluster

```bash
# Build the admin CLI tool
cd minisql-admin && mvn package -DskipTests

# Check cluster status
java -jar target/minisql-admin-*-jar-with-dependencies.jar cluster status

# List RegionServer nodes
java -jar target/minisql-admin-*-jar-with-dependencies.jar cluster nodes
```

---

## 3. Data Model

### 3.1 Tables

Tables are defined with a schema (columns, types, primary key) and are automatically sharded into Regions.

**Supported column types**:
- `BIGINT` - 64-bit signed integer
- `VARCHAR(N)` - Variable-length string (max N characters)
- `DOUBLE` - 64-bit floating point
- `BOOLEAN` - Boolean value

### 3.2 Regions

Each table is divided into **Regions** based on primary key ranges:

```
Table "users" (primary key: user_id)
  Region 1: [0, 10000)     => Server rs-001 (primary)
  Region 2: [10000, 20000)  => Server rs-002 (primary)
  Region 3: [20000, MAX)    => Server rs-003 (primary)
```

Each Region has:
- **Primary replica**: Handles read/write for the region
- **Replica(s)**: Backup copies for high availability

### 3.3 Key Concepts

- **Row Key**: The primary key value used for data distribution
- **Region Split**: Automatic split when a Region exceeds size threshold
- **Region Migration**: Moving a Region between servers for load balancing

---

## 4. Client SDK Usage

### 4.1 Connecting to the Cluster

```java
// Create a client connection (auto-manages channel lifecycle)
MiniSQLClient client = MiniSQLClient.connect("localhost:8000");
```

### 4.2 Writing Data

```java
import com.google.protobuf.ByteString;

// Insert a row - returns PutResult
Map<String, ByteString> columns = new HashMap<>();
columns.put("user_id", ByteString.copyFromUtf8("10001"));
columns.put("username", ByteString.copyFromUtf8("alice"));
columns.put("email", ByteString.copyFromUtf8("alice@example.com"));

MiniSQLClient.PutResult result = client.put("users",
    ByteString.copyFromUtf8("10001"), columns);

if (result.isSuccess()) {
    System.out.println("Data written, WAL seq: " + result.getSequenceId());
}
```

### 4.3 Reading Data

```java
// Get a row by key - returns GetResult
MiniSQLClient.GetResult result = client.get("users",
    ByteString.copyFromUtf8("10001"), null); // null = all columns

if (result.isFound()) {
    System.out.println("Value: " + result.getColumns());
}
```

### 4.4 Scanning Data

```java
// Scan a range of rows - returns List<ScanRow>
List<MiniSQLClient.ScanRow> rows = client.scan("users",
    ByteString.copyFromUtf8("10000"),   // startKey
    ByteString.copyFromUtf8("20000"),   // endKey
    100,                                // limit
    null);                              // null = all columns

for (MiniSQLClient.ScanRow row : rows) {
    System.out.println("Row: " + row.getKey().toStringUtf8());
}
```

### 4.5 Deleting Data

```java
MiniSQLClient.DeleteResult result = client.delete("users",
    ByteString.copyFromUtf8("10001"));
```

### 4.6 Checking Key Existence

```java
boolean exists = client.exists("users", ByteString.copyFromUtf8("10001"));
```

---

## 5. CLI Admin Tool

The `minisql-admin` tool provides administrative access to the cluster.

### 5.1 Building the Tool

```bash
cd minisql-admin
mvn package -DskipTests
# Output: target/minisql-admin-*-jar-with-dependencies.jar (~19MB)
```

### 5.2 Running the Tool

```bash
# Using the built JAR with dependencies
java -jar minisql-admin/target/minisql-admin-*-jar-with-dependencies.jar [options] <command>

# Quick alias (Linux/Mac)
alias minisql-admin='java -jar /path/to/minisql-admin-*-jar-with-dependencies.jar'
```

### 5.3 Cluster Management

```bash
# Check cluster health
minisql-admin cluster status
# Output: Cluster Health: HEALTHY / DEGRADED / CRITICAL
#   Total Servers: 3
#   Online Servers: 3

# View cluster statistics
minisql-admin cluster stats
# Output: Total Tables, Regions, Data Size, Rows, etc.

# List RegionServer nodes
minisql-admin cluster nodes
# Output: Server ID, Address, State, Load Score, Regions, etc.

# Trigger load balancing
minisql-admin cluster balance
# Output: Balance Result, Plans Generated
```

### 5.4 Table Management

```bash
# List all tables
minisql-admin table list

# View table schema
minisql-admin table describe users

# View table route information
minisql-admin table route users
```

### 5.5 Connecting to Remote Clusters

```bash
minisql-admin --host 192.168.1.100 --port 8000 cluster status
```

---

## 6. Testing

### 6.1 Running Tests

```bash
# Run all tests across all modules
mvn test

# Run tests for a specific module
cd minisql-master && mvn test

# Run a single test class
cd minisql-master && mvn test -Dtest=LoadBalancerTest

# Run a single test method
cd minisql-master && mvn test -Dtest=LoadBalancerTest#testBalancedAssignment

# Run integration tests (fast profile, uses embedded Zookeeper)
cd minisql-master && mvn test -Pintegration-fast
```

### 6.2 Code Coverage

JaCoCo 0.8.13 generates coverage reports during `mvn test`:

| Module | Report Location |
|--------|----------------|
| minisql-master | `target/site/jacoco/index.html` |
| minisql-regionserver | `target/site/jacoco/index.html` |
| minisql-client | `target/site/jacoco/index.html` |
| minisql-admin | `target/site/jacoco/index.html` |

### 6.3 Current Test Status (306 tests, all passing)

| Module | Tests | Coverage |
|--------|-------|----------|
| minisql-master | 189 | 67% (balance 93%) |
| minisql-regionserver | 53 | WAL 70%, replication 49% |
| minisql-client | 48 | route 91%, core 83% |
| minisql-admin | 16 | 77% |

---

## 7. Monitoring

### 7.1 Health Checks

The cluster health status reflects:
- **HEALTHY**: All servers online and functioning
- **DEGRADED**: Some servers dead but system operational
- **CRITICAL**: No servers registered or major failures

### 7.2 Viewing Server Statistics

```bash
# Get server stats with load scores
minisql-admin cluster stats

# View detailed node info
minisql-admin cluster nodes
```

### 7.3 Logging

Log format: `[timestamp] [level] [component] [thread] message`

```bash
# View Master logs
tail -f minisql-master/logs/master.log

# View RegionServer logs
tail -f minisql-regionserver/logs/regionserver-rs-001.log
```

---

## 8. Common Operations

### 8.1 Adding a New RegionServer

Start a new RegionServer instance -- it will automatically register with the Master:

```bash
cd minisql-regionserver
mvn exec:java -Dexec.mainClass="com.minisql.regionserver.RegionServerMain" \
  -Dexec.args="rs-003 8003"
```

### 8.2 Gracefully Stopping a RegionServer

```bash
# Send SIGTERM for graceful shutdown
kill -15 <regionserver-pid>
```

The RegionServer will:
1. Unregister from Master
2. Close all hosted Regions
3. Preserve data for reassignment

### 8.3 Triggering Load Balancing

```bash
minisql-admin cluster balance
```

The Master will:
1. Analyze current load distribution
2. Generate migration plans for overloaded servers
3. Execute migrations without service interruption

---

## 9. Troubleshooting

### 9.1 Connection Issues

**Symptom**: `io.grpc.StatusRuntimeException: UNAVAILABLE`
**Cause**: Master or RegionServer not running
**Fix**: Verify services are running and reachable

```bash
# Check if Master is listening
netstat -an | grep 8000

# Check RegionServer connectivity
telnet localhost 8001
```

### 9.2 Region Not Found

**Symptom**: `ERROR_REGION_NOT_FOUND`
**Cause**: Stale route cache or Region migrated
**Fix**: The client SDK automatically retries once after refreshing the route cache on stale route errors.

### 9.3 Table Creation Fails

**Symptom**: `ERROR_TABLE_ALREADY_EXISTS`
**Cause**: Table with same name exists
**Fix**: Use a different table name or drop the existing one

### 9.4 Performance Issues

**If queries are slow**:
1. Check cluster health: `minisql-admin cluster status`
2. Check region distribution: `minisql-admin table route <table>`
3. Trigger load balancing: `minisql-admin cluster balance`
4. Check server load: `minisql-admin cluster nodes`

---

## 10. Best Practices

### 10.1 Table Design

- Choose a primary key that distributes data evenly
- Avoid monotonically increasing keys (they create hot spots)
- Use VARCHAR keys when natural distribution is needed

### 10.2 Cluster Sizing

- Minimum 3 RegionServers for production
- 1 Master + 1 Standby for high availability
- Zookeeper ensemble of 3 nodes minimum

### 10.3 Performance Tuning

- Set appropriate Region split thresholds
- Configure replica count based on read/write ratio
- Use batch operations for bulk data loading
- Monitor and balance load regularly

### 10.4 Fault Tolerance

- Configure at least 3 replicas for important data
- Monitor server health metrics
- Have spare capacity for failover

---

## 11. Limitations

- **Educational use only** -- not intended for production
- **Simplified Paxos** -- may have edge cases under partition
- **No cross-table transactions** -- single row atomicity only
- **Limited SQL** -- basic CRUD operations with range queries

---

## 12. Getting Help

- **Architecture Guide**: See `docs/superpowers/specs/2026-04-15-distributed-minisql-design.md`
- **API Docs**: See `docs/api-documentation.md`
- **Deployment Guide**: See `docs/deployment-guide.md`
- **Team Division**: See `docs/team-division.md`
