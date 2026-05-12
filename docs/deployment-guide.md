# Distributed MiniSQL Deployment Guide

## Overview

This guide covers deployment of the Distributed MiniSQL system in various environments, from single-node development setup to multi-node production-like clusters.

---

## 1. Environment Requirements

### 1.1 Hardware Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| CPU | 2 cores | 4+ cores |
| RAM | 4 GB | 8+ GB |
| Disk | 20 GB | 50+ GB SSD |
| Network | 1 Gbps | 10 Gbps |

### 1.2 Software Requirements

| Software | Version | Purpose |
|----------|---------|---------|
| Java JDK | 11+ | Runtime environment |
| Apache Maven | 3.6+ | Build system |
| Apache Zookeeper | 3.8+ | Coordinator / metadata store |
| MySQL | 8.0+ | Optional: RegionServer data storage |

### 1.3 Network Requirements

| Port | Service | Purpose |
|------|---------|---------|
| 2181 | Zookeeper | Client connections |
| 2888 | Zookeeper | Follower connections |
| 3888 | Zookeeper | Leader election |
| 8000 | Master Server | gRPC port |
| 8001-8010 | RegionServer(s) | gRPC ports |

---

## 2. Build from Source

### 2.1 Clone and Build

```bash
# Clone the repository
git clone <repository-url>
cd Distributed-MiniSQL

# Build all modules (with tests)
mvn clean install

# Build without tests (faster for deployment)
mvn clean install -DskipTests

# Build specific module
mvn clean install -pl minisql-master -am -DskipTests
```

### 2.2 Build Output

After building, the following artifacts are produced:

| Module | Artifact | Location |
|--------|----------|----------|
| minisql-common | `minisql-common-1.0-SNAPSHOT.jar` | `minisql-common/target/` |
| minisql-master | `minisql-master-1.0-SNAPSHOT.jar` | `minisql-master/target/` |
| minisql-master (fat) | `minisql-master-1.0-SNAPSHOT-jar-with-dependencies.jar` | `minisql-master/target/` |
| minisql-regionserver | `minisql-regionserver-1.0-SNAPSHOT.jar` | `minisql-regionserver/target/` |
| minisql-regionserver (fat) | `minisql-regionserver-1.0-SNAPSHOT-jar-with-dependencies.jar` | `minisql-regionserver/target/` |
| minisql-admin (fat) | `minisql-admin-*-jar-with-dependencies.jar` | `minisql-admin/target/` |

---

## 3. Single-Node Development Deployment

### 3.1 Start Zookeeper

```bash
# Download and extract Zookeeper
wget https://dlcdn.apache.org/zookeeper/zookeeper-3.8.3/apache-zookeeper-3.8.3-bin.tar.gz
tar -xzf apache-zookeeper-3.8.3-bin.tar.gz

# Configure
cp conf/zoo_sample.cfg conf/zoo.cfg
# Edit zoo.cfg: dataDir=/tmp/zookeeper

# Start Zookeeper
bin/zkServer.sh start
```

### 3.2 Start Master Server

Master supports both environment variables and command-line arguments:

**Method A: Environment variables**
```bash
cd minisql-master
MASTER_PORT=8000 MASTER_ID=master-1 ZK_CONNECT=localhost:2181 \
  mvn exec:java -Dexec.mainClass="com.minisql.master.MasterServer"
```

**Method B: Command-line arguments (port, serverId, zkConnect)**
```bash
cd minisql-master
mvn exec:java -Dexec.mainClass="com.minisql.master.MasterServer" \
  -Dexec.args="8000 master-1 localhost:2181"
```

**Method C: Run fat JAR directly**
```bash
cd minisql-master
mvn package -DskipTests
java -jar target/minisql-master-1.0-SNAPSHOT-jar-with-dependencies.jar
```

**Environment variables**:
| Variable | Default | Description |
|----------|---------|-------------|
| `MASTER_PORT` | 8000 | gRPC server port |
| `MASTER_ID` | auto | Unique master identifier |
| `ZK_CONNECT` | localhost:2181 | Zookeeper connection string |

### 3.3 Start RegionServers

RegionServer uses **in-memory storage** by default -- no MySQL required.

```bash
# Terminal 1: RegionServer 1 (using Maven)
cd minisql-regionserver
mvn exec:java -Dexec.mainClass="com.minisql.regionserver.RegionServerMain" \
  -Dexec.args="rs-001 8001"
```

```bash
# Terminal 2: RegionServer 2
cd minisql-regionserver
mvn exec:java -Dexec.mainClass="com.minisql.regionserver.RegionServerMain" \
  -Dexec.args="rs-002 8002"
```

RegionServer startup process:
1. Read config from `regionserver.conf` (or use defaults)
2. Connect to Master and register
3. Start gRPC service on the specified port
4. Begin heartbeat reporting

Configuration file: `minisql-regionserver/src/main/resources/regionserver.conf`

```properties
# Key configuration items
regionserver.id=rs-001
regionserver.port=8001
master.host=127.0.0.1
master.port=8000
master.heartbeat.interval.ms=3000
storage.backend=memory    # memory or mysql
wal.enabled=true
```

### 3.4 Verify Deployment

```bash
# Build the admin CLI tool
cd minisql-admin
mvn package -DskipTests

# Check cluster status
java -jar target/minisql-admin-*-jar-with-dependencies.jar cluster status

# Verify nodes are registered
java -jar target/minisql-admin-*-jar-with-dependencies.jar cluster nodes
```

**Expected output**:
```
Cluster Health: HEALTHY
  Total Servers: 2
  Online Servers: 2
  Total Regions: 0
  Online Regions: 0
```

---

## 4. Multi-Node Cluster Deployment

> **Note**: Multi-node deployment is a planned feature. All services can run on a single machine for development and testing.

### 4.1 Deployment Topology

```
                    +-------------------+
                    |   Admin Client    |
                    +---------+---------+
                              |
    +----------------+--------+--------+----------------+
    |                |        |        |                |
+---v----+     +----v---+ +--v-----+ +--v-----+  +----v----+
|Zookeeper|    | Master  | | RS-001 | | RS-002 |  | RS-003  |
| (node0) |    | (node1) | | (node2)| | (node3)|  | (node4) |
+---------+    +---------+ +--------+ +--------+  +---------+
```

### 4.2 Node Layout (5 machines)

| Node | IP | Services | Ports |
|------|----|----------|-------|
| node0 | 10.0.1.10 | Zookeeper | 2181 |
| node1 | 10.0.1.11 | Master | 8000 |
| node2 | 10.0.1.12 | RegionServer | 8001 |
| node3 | 10.0.1.13 | RegionServer | 8001 |
| node4 | 10.0.1.14 | RegionServer | 8001 |

### 4.3 Step-by-Step Deployment

1. Install Java 11 and Maven on all nodes, build the project.
2. Start Zookeeper on node0.
3. Start Master on node1 (connect to node0's Zookeeper).
4. Start RegionServers on node2-4 (connect to node1's Master).

---

## 5. MySQL Database Setup (optional)

If using MySQL as RegionServer storage backend:

### 5.1 Create Databases

```sql
-- One database per RegionServer
CREATE DATABASE minisql_rs001 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE minisql_rs002 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE minisql_rs003 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create user and grant permissions
CREATE USER 'minisql'@'%' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON minisql_rs001.* TO 'minisql'@'%';
FLUSH PRIVILEGES;
```

### 5.2 Configure RegionServer

Edit `regionserver.conf`:
```properties
storage.backend=mysql
mysql.host=localhost
mysql.port=3306
mysql.database=minisql
mysql.username=root
mysql.password=password
```

### 5.3 HikariCP Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| maximumPoolSize | 10 | Max connections |
| connectionTimeout | 30000 | Connection timeout (ms) |

---

## 6. Configuration Reference

### 6.1 Master Configuration

| Parameter | Default | Environment Variable | Description |
|-----------|---------|---------------------|-------------|
| Port | 8000 | `MASTER_PORT` | gRPC server port |
| Server ID | auto | `MASTER_ID` | Unique master identifier |
| ZK Connect | localhost:2181 | `ZK_CONNECT` | Zookeeper connection string |
| Heartbeat Timeout | 30000ms | - | RegionServer heartbeat timeout |
| Heartbeat Interval | 3000ms | - | Heartbeat check interval |
| Monitor Interval | 10000ms | - | Cluster health check interval |

### 6.2 RegionServer Configuration

See `minisql-regionserver/src/main/resources/regionserver.conf`:

| Parameter | Default | Description |
|-----------|---------|-------------|
| regionserver.id | required | Unique RegionServer identifier |
| regionserver.port | 8001 | gRPC server port |
| master.host | 127.0.0.1 | Master server address |
| master.port | 8000 | Master server gRPC port |
| master.heartbeat.interval.ms | 3000 | Heartbeat interval |
| storage.backend | memory | Storage backend (memory/mysql) |
| wal.enabled | true | Enable WAL |
| wal.path | ./wal | WAL directory |

---

## 7. Monitoring

### 7.1 Health Checks

```bash
# Simple health check
java -jar minisql-admin-jar-with-dependencies.jar \
  --host localhost --port 8000 cluster status
```

### 7.2 Log Monitoring

```bash
# Monitor Master logs in real-time
tail -f logs/master.log | grep -E "(ERROR|WARN)"

# Monitor RegionServer logs
tail -f logs/regionserver-rs-001.log | grep -E "(ERROR|WARN)"
```

### 7.3 Metrics Collection

```bash
# Collect periodic stats
watch -n 10 'java -jar minisql-admin-*-jar-with-dependencies.jar cluster stats'
```

---

## 8. Troubleshooting

### 8.1 Master Won't Start

**Check**:
1. Zookeeper is running: `echo ruok | nc localhost 2181`
2. Port is available: `netstat -an | grep 8000`
3. Zookeeper connection string is correct

### 8.2 RegionServer Won't Register

**Check**:
1. Master is running and reachable
2. Server ID is not already taken
3. Port is not in use
4. Network connectivity between RegionServer and Master

### 8.3 Windows TCP Port Stuck

On Windows, after killing a Java process, the TCP port may remain in LISTENING state:

```bash
# Find the zombie process
netstat -ano | findstr :8000

# Force kill
taskkill /F /PID <pid>
```

---

## 9. Security Considerations

- Change default MySQL passwords in production
- Use `storage.backend=memory` for development/testing (no MySQL needed)
- Keep Zookeeper ports internal to the cluster network
- Use firewall rules to restrict port access

---

## Appendix A: Quick Start Script

```bash
#!/bin/bash
# deploy-dev.sh - Deploy MiniSQL cluster on single machine

set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

echo "Building project..."
mvn clean install -DskipTests -q

echo "Starting Zookeeper..."
bin/zkServer.sh start 2>/dev/null || true

echo "Starting Master..."
MASTER_PORT=8000 MASTER_ID=master-1 ZK_CONNECT=localhost:2181 \
  java -jar minisql-master/target/minisql-master-*-jar-with-dependencies.jar &
MASTER_PID=$!
sleep 3

echo "Starting RegionServers..."
java -jar minisql-regionserver/target/minisql-regionserver-*-jar-with-dependencies.jar rs-001 8001 &
RS1_PID=$!
java -jar minisql-regionserver/target/minisql-regionserver-*-jar-with-dependencies.jar rs-002 8002 &
RS2_PID=$!
sleep 3

echo "Verifying cluster..."
java -jar minisql-admin/target/minisql-admin-*-jar-with-dependencies.jar cluster status

echo "Deployment complete!"
echo "Master PID: $MASTER_PID"
echo "RS-001 PID: $RS1_PID"
echo "RS-002 PID: $RS2_PID"
```

---

## Appendix B: Management Scripts

The project provides start/stop scripts for both Linux/Mac and Windows:

| Script | Location | Description |
|--------|----------|-------------|
| `start-master.sh` / `start-master.bat` | `minisql-master/` | Start Master Server |
| `stop-master.sh` / `stop-master.bat` | `minisql-master/` | Stop Master Server gracefully |
| `start-regionserver.sh` / `start-regionserver.bat` | `minisql-regionserver/` | Start RegionServer |
| `stop-regionserver.sh` / `stop-regionserver.bat` | `minisql-regionserver/` | Stop RegionServer gracefully |

Start scripts now run services in background and write PID files to `logs/*.pid` for reliable shutdown.

```bash
# Start Master (background, with PID file)
cd minisql-master && ./start-master.sh

# Start RegionServer (background, with PID file)
cd minisql-regionserver && ./start-regionserver.sh

# Stop services gracefully
cd minisql-master && ./stop-master.sh
cd minisql-regionserver && ./stop-regionserver.sh
```

## Appendix C: Bootstrap Tools

The `bootstrap/` directory contains quick-start helper tools:

- **ZkStart.java**: Start an embedded Zookeeper instance (for testing)
- **GrpcTest.java**: gRPC connection test utility
