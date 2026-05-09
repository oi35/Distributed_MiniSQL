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
- **Master Server**: Cluster management, metadata storage, routing, load balancing
- **RegionServer**: Data storage, query execution, region lifecycle management
- **CLI Admin Tool**: Command-line interface for cluster administration
- **Client SDK**: Java library for application access

---

## 2. Quick Start

### 2.1 Prerequisites

- Java 11+ Runtime
- Apache Zookeeper 3.8+ (for Master HA and metadata persistence)
- MySQL 8.0+ (for RegionServer data storage)

### 2.2 Building the Project

```bash
# Build all modules
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
# Using Maven
cd minisql-master
mvn exec:java -Dexec.mainClass="com.minisql.master.MasterServer"

# Or using the built JAR
java -jar target/minisql-master-1.0-SNAPSHOT.jar 8000 master-1 localhost:2181
```

**Step 3**: Start RegionServer(s)

```bash
# Each RegionServer needs a unique ID and port
cd minisql-regionserver
mvn exec:java -Dexec.mainClass="com.minisql.regionserver.RegionServerMain" \
  -Dexec.args="rs-001 8001"

# Start additional RegionServers on different ports
mvn exec:java -Dexec.mainClass="com.minisql.regionserver.RegionServerMain" \
  -Dexec.args="rs-002 8002"
```

### 2.4 Verifying the Cluster

```bash
# Check cluster status
java -jar minisql-admin/target/minisql-admin-*-jar-with-dependencies.jar cluster status

# List RegionServer nodes
java -jar minisql-admin/target/minisql-admin-*-jar-with-dependencies.jar cluster nodes
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
  Region 1: [0, 10000)     → Server rs-001 (primary)
  Region 2: [10000, 20000)  → Server rs-002 (primary)
  Region 3: [20000, MAX)    → Server rs-003 (primary)
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
// Create a client connection
MiniSQLClient client = new MiniSQLClient("localhost:8000");
client.connect();
```

### 4.2 Creating a Table

```java
// Define table schema
TableSchema schema = TableSchema.newBuilder()
    .setTableName("users")
    .addColumns(ColumnSchema.newBuilder()
        .setName("user_id").setType("BIGINT").setNullable(false).build())
    .addColumns(ColumnSchema.newBuilder()
        .setName("username").setType("VARCHAR(50)").setNullable(false).build())
    .addColumns(ColumnSchema.newBuilder()
        .setName("email").setType("VARCHAR(100)").setNullable(true).build())
    .setPrimaryKey("user_id")
    .build();

// Create table with 3 replicas
client.createTable(schema, 1, 3);
```

### 4.3 Writing Data

```java
// Insert a row
PutRequest request = PutRequest.newBuilder()
    .setTableName("users")
    .setKey(ByteString.copyFromUtf8("10001"))
    .putColumns("user_id", ByteString.copyFromUtf8("10001"))
    .putColumns("username", ByteString.copyFromUtf8("alice"))
    .putColumns("email", ByteString.copyFromUtf8("alice@example.com"))
    .build();

PutResponse response = client.put(request);
if (response.getSuccess()) {
    System.out.println("Data written successfully");
}
```

### 4.4 Reading Data

```java
// Get a row by key
GetRequest request = GetRequest.newBuilder()
    .setTableName("users")
    .setKey(ByteString.copyFromUtf8("10001"))
    .build();

GetResponse response = client.get(request);
if (response.getSuccess()) {
    System.out.println("Value: " + response.getColumnsMap());
}
```

### 4.5 Scanning Data

```java
// Scan a range of rows
ScanRequest request = ScanRequest.newBuilder()
    .setTableName("users")
    .setStartKey(ByteString.copyFromUtf8("10000"))
    .setEndKey(ByteString.copyFromUtf8("20000"))
    .setLimit(100)
    .build();

Iterator<ScanResponse> results = client.scan(request);
while (results.hasNext()) {
    ScanResponse row = results.next();
    System.out.println("Row: " + row.getKey().toStringUtf8());
}
```

### 4.6 Deleting Data

```java
DeleteRequest request = DeleteRequest.newBuilder()
    .setTableName("users")
    .setKey(ByteString.copyFromUtf8("10001"))
    .build();

DeleteResponse response = client.delete(request);
```

---

## 5. CLI Admin Tool

The `minisql-admin` tool provides administrative access to the cluster.

### 5.1 Running the Tool

```bash
# Using the built JAR with dependencies
java -jar minisql-admin/target/minisql-admin-*-jar-with-dependencies.jar [options] <command>

# Quick alias (Linux/Mac)
alias minisql-admin='java -jar /path/to/minisql-admin-*-jar-with-dependencies.jar'
```

### 5.2 Cluster Management

```bash
# Check cluster health
minisql-admin cluster status

# View cluster statistics
minisql-admin cluster stats

# List RegionServer nodes
minisql-admin cluster nodes

# Trigger load balancing
minisql-admin cluster balance
```

### 5.3 Table Management

```bash
# List all tables
minisql-admin table list

# View table schema
minisql-admin table describe users

# View table route information
minisql-admin table route users
```

### 5.4 Connecting to Remote Clusters

```bash
minisql-admin --host 192.168.1.100 --port 8000 cluster status
```

---

## 6. Monitoring

### 6.1 Health Checks

The cluster health status reflects:
- **HEALTHY**: All servers online and functioning
- **DEGRADED**: Some servers dead but system operational
- **CRITICAL**: No servers registered or major failures

### 6.2 Viewing Server Statistics

```bash
# Get server stats with load scores
minisql-admin cluster stats

# View detailed node info
minisql-admin cluster nodes
```

### 6.3 Logging

Log format: `[timestamp] [level] [component] [thread] message`

```bash
# View Master logs
tail -f minisql-master/logs/master.log

# View RegionServer logs
tail -f minisql-regionserver/logs/regionserver-rs-001.log
```

---

## 7. Common Operations

### 7.1 Adding a New RegionServer

Start a new RegionServer instance — it will automatically register with the Master:

```bash
java -jar minisql-regionserver/target/minisql-regionserver-1.0-SNAPSHOT.jar rs-003 8003
```

### 7.2 Gracefully Stopping a RegionServer

```bash
# Send SIGTERM for graceful shutdown
kill -15 <regionserver-pid>
```

The RegionServer will:
1. Unregister from Master
2. Close all hosted Regions
3. Preserve data for reassignment

### 7.3 Triggering Load Balancing

```bash
minisql-admin cluster balance
```

The Master will:
1. Analyze current load distribution
2. Generate migration plans for overloaded servers
3. Execute migrations without service interruption

---

## 8. Troubleshooting

### 8.1 Connection Issues

**Symptom**: `io.grpc.StatusRuntimeException: UNAVAILABLE`
**Cause**: Master or RegionServer not running
**Fix**: Verify services are running and reachable

```bash
# Check if Master is listening
netstat -an | grep 8000

# Check RegionServer connectivity
telnet localhost 8001
```

### 8.2 Region Not Found

**Symptom**: `ERROR_REGION_NOT_FOUND`
**Cause**: Stale route cache or Region migrated
**Fix**: Refresh client route cache

```java
client.refreshRouteTable("users");
```

### 8.3 Table Creation Fails

**Symptom**: `ERROR_TABLE_ALREADY_EXISTS`
**Cause**: Table with same name exists
**Fix**: Use a different table name or drop the existing one

### 8.4 Performance Issues

**If queries are slow**:
1. Check cluster health: `minisql-admin cluster status`
2. Check region distribution: `minisql-admin table route <table>`
3. Trigger load balancing: `minisql-admin cluster balance`
4. Check server load: `minisql-admin cluster nodes`

---

## 9. Best Practices

### 9.1 Table Design

- Choose a primary key that distributes data evenly
- Avoid monotonically increasing keys (they create hot spots)
- Use VARCHAR keys when natural distribution is needed

### 9.2 Cluster Sizing

- Minimum 3 RegionServers for production
- 1 Master + 1 Standby for high availability
- Zookeeper ensemble of 3 nodes minimum

### 9.3 Performance Tuning

- Set appropriate Region split thresholds
- Configure replica count based on read/write ratio
- Use batch operations for bulk data loading
- Monitor and balance load regularly

### 9.4 Fault Tolerance

- Configure at least 3 replicas for important data
- Monitor server health metrics
- Have spare capacity for failover

---

## 10. Limitations

- **Educational use only** — not intended for production
- **Simplified Paxos** — may have edge cases under partition
- **No cross-table transactions** — single row atomicity only
- **Limited SQL** — basic CRUD operations with range queries

---

## 11. Getting Help

- **Architecture Guide**: See `docs/superpowers/specs/2026-04-15-distributed-minisql-design.md`
- **API Docs**: See `docs/api-documentation.md`
- **Deployment Guide**: See `docs/deployment-guide.md`
- **Team Division**: See `docs/team-division.md`
