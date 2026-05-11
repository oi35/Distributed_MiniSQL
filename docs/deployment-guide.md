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
| MySQL | 8.0+ | RegionServer data storage |

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
git clone https://github.com/your-org/distributed-minisql.git
cd distributed-minisql

# Build all modules
mvn clean install -DskipTests

# Build with tests
mvn clean install

# Build specific module
mvn clean install -pl minisql-master -am -DskipTests
```

### 2.2 Build Output

After building, the following JARs are produced:

| Module | Artifact | Location |
|--------|----------|----------|
| minisql-common | `minisql-common-1.0-SNAPSHOT.jar` | `minisql-common/target/` |
| minisql-master | `minisql-master-1.0-SNAPSHOT.jar` | `minisql-master/target/` |
| minisql-regionserver | `minisql-regionserver-1.0-SNAPSHOT.jar` | `minisql-regionserver/target/` |
| minisql-admin | `minisql-admin-*-jar-with-dependencies.jar` | `minisql-admin/target/` |

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

### 3.2 Create MySQL Databases

Each RegionServer needs its own MySQL database:

```sql
CREATE DATABASE IF NOT EXISTS minisql_rs001 CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS minisql_rs002 CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS minisql_rs003 CHARACTER SET utf8mb4;
```

### 3.3 Start Master Server

```bash
cd minisql-master

# Using Maven
mvn exec:java -Dexec.mainClass="com.minisql.master.MasterServer" \
  -Dexec.args="8000 master-1 localhost:2181"

# Or as a standalone JAR
mvn package -DskipTests
java -jar target/minisql-master-1.0-SNAPSHOT.jar 8000 master-1 localhost:2181
```

**Environment variables** (alternative to command-line args):
```
MASTER_PORT=8000
MASTER_ID=master-1
ZK_CONNECT=localhost:2181
```

### 3.4 Start RegionServers

Start multiple RegionServer processes (each in a separate terminal):

```bash
# Terminal 1: RegionServer 1
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

```bash
# Terminal 3: RegionServer 3
cd minisql-regionserver
mvn exec:java -Dexec.mainClass="com.minisql.regionserver.RegionServerMain" \
  -Dexec.args="rs-003 8003"
```

### 3.5 Verify Deployment

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
  Total Servers: 3
  Online Servers: 3
  Total Regions: 0
  Online Regions: 0
```

---

## 4. Multi-Node Cluster Deployment

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

### 4.2 Node Configuration

**Node Layout** (5 machines):

| Node | IP | Services | Ports |
|------|----|----------|-------|
| node0 | 10.0.1.10 | Zookeeper | 2181 |
| node1 | 10.0.1.11 | Master | 8000 |
| node2 | 10.0.1.12 | RegionServer | 8001 |
| node3 | 10.0.1.13 | RegionServer | 8001 |
| node4 | 10.0.1.14 | RegionServer | 8001 |

### 4.3 Step-by-Step Deployment

**On all nodes** — install prerequisites:

```bash
# Install Java 11
apt-get update
apt-get install -y openjdk-11-jdk maven

# Clone repository
git clone https://github.com/your-org/distributed-minisql.git
cd distributed-minisql

# Build
mvn clean install -DskipTests
```

**On node0** — configure Zookeeper:

```bash
# Edit conf/zoo.cfg
echo "tickTime=2000" >> conf/zoo.cfg
echo "dataDir=/var/lib/zookeeper" >> conf/zoo.cfg
echo "clientPort=2181" >> conf/zoo.cfg
echo "initLimit=5" >> conf/zoo.cfg
echo "syncLimit=2" >> conf/zoo.cfg
echo "server.1=10.0.1.10:2888:3888" >> conf/zoo.cfg

# Start Zookeeper
bin/zkServer.sh start
```

**On node1** — start Master:

```bash
cd minisql-master
MASTER_PORT=8000 MASTER_ID=master-1 ZK_CONNECT=10.0.1.10:2181 \
  java -jar target/minisql-master-1.0-SNAPSHOT.jar
```

**On node2, node3, node4** — start RegionServer:

```bash
cd minisql-regionserver
java -jar target/minisql-regionserver-1.0-SNAPSHOT.jar \
  rs-00X 8001 10.0.1.1X 10.0.1.11:8000
```

**From any machine** — verify the cluster:

```bash
java -jar minisql-admin/target/minisql-admin-*-jar-with-dependencies.jar \
  --host 10.0.1.11 --port 8000 cluster status
```

---

## 5. Docker Deployment

### 5.1 Docker Images

Build Docker images for each component:

```dockerfile
# Dockerfile for Master
FROM openjdk:11-jre-slim
WORKDIR /app
COPY minisql-master/target/minisql-master-1.0-SNAPSHOT.jar .
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "minisql-master-1.0-SNAPSHOT.jar"]
CMD ["8000", "master-1", "zookeeper:2181"]
```

### 5.2 Docker Compose

```yaml
# docker-compose.yml
version: '3.8'

services:
  zookeeper:
    image: zookeeper:3.8
    ports:
      - "2181:2181"

  master:
    build:
      context: .
      dockerfile: Dockerfile.master
    ports:
      - "8000:8000"
    environment:
      - MASTER_PORT=8000
      - MASTER_ID=master-1
      - ZK_CONNECT=zookeeper:2181
    depends_on:
      - zookeeper

  regionserver-1:
    build:
      context: .
      dockerfile: Dockerfile.regionserver
    ports:
      - "8001:8001"
    depends_on:
      - master
    entrypoint: ["java", "-jar", "regionserver.jar", "rs-001", "8001"]

  regionserver-2:
    build:
      context: .
      dockerfile: Dockerfile.regionserver
    ports:
      - "8002:8001"
    depends_on:
      - master
    entrypoint: ["java", "-jar", "regionserver.jar", "rs-002", "8001"]

  regionserver-3:
    build:
      context: .
      dockerfile: Dockerfile.regionserver
    ports:
      - "8003:8001"
    depends_on:
      - master
    entrypoint: ["java", "-jar", "regionserver.jar", "rs-003", "8001"]
```

### 5.3 Running with Docker Compose

```bash
# Build images
docker-compose build

# Start all services
docker-compose up -d

# Check logs
docker-compose logs -f master

# Scale RegionServers
docker-compose up -d --scale regionserver=5

# Stop
docker-compose down
```

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

| Parameter | Default | Description |
|-----------|---------|-------------|
| Server ID | required | Unique RegionServer identifier |
| Port | 8001 | gRPC server port |
| Master Host | localhost | Master server address |
| Master Port | 8000 | Master server gRPC port |
| MySQL Host | localhost | MySQL database host |
| MySQL Port | 3306 | MySQL port |
| MySQL Database | minisql_<server_id> | Database per server |

### 6.3 Zookeeper Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| tickTime | 2000 | Basic time unit (ms) |
| dataDir | /tmp/zookeeper | Data directory |
| clientPort | 2181 | Client connection port |
| initLimit | 5 | Initial sync tolerance |
| syncLimit | 2 | Follower sync tolerance |

---

## 7. High Availability Setup

### 7.1 Master HA with Standby

Run a second Master instance that will take over via Zookeeper election:

```bash
# Primary Master
MASTER_ID=master-1 ZK_CONNECT=10.0.1.10:2181 java -jar master.jar 8000

# Standby Master (same ZK connection, different port)
MASTER_ID=master-2 ZK_CONNECT=10.0.1.10:2181 java -jar master.jar 8001
```

The standby Master monitors the leader via Zookeeper. If the leader fails, the standby takes over automatically.

### 7.2 RegionServer Redundancy

Configure at least 3 replicas per table for fault tolerance:

```java
// Create table with 3 replicas
client.createTable(schema, initialRegions, 3);
```

With 3 replicas, the system tolerates 1 RegionServer failure without data loss.

### 7.3 Zookeeper Ensemble

For production, run a 3-node Zookeeper ensemble:

```bash
# On zk-node-1
echo "1" > /var/lib/zookeeper/myid
# zoo.cfg:
# server.1=10.0.1.10:2888:3888
# server.2=10.0.1.11:2888:3888
# server.3=10.0.1.12:2888:3888

# On zk-node-2 (myid=2)
# On zk-node-3 (myid=3)
```

**Master connection string**: `10.0.1.10:2181,10.0.1.11:2181,10.0.1.12:2181`

---

## 8. MySQL Database Setup

### 8.1 Create Databases

```sql
-- One database per RegionServer
CREATE DATABASE minisql_rs001 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE minisql_rs002 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE minisql_rs003 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create user and grant permissions
CREATE USER 'minisql'@'%' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON minisql_rs001.* TO 'minisql'@'%';
GRANT ALL PRIVILEGES ON minisql_rs002.* TO 'minisql'@'%';
GRANT ALL PRIVILEGES ON minisql_rs003.* TO 'minisql'@'%';
FLUSH PRIVILEGES;
```

### 8.2 Connection Pool Configuration

HikariCP is used for connection pooling (default configuration):

| Parameter | Default | Description |
|-----------|---------|-------------|
| maximumPoolSize | 10 | Max connections |
| minimumIdle | 5 | Min idle connections |
| connectionTimeout | 30000 | Connection timeout (ms) |
| idleTimeout | 600000 | Idle timeout (ms) |
| maxLifetime | 1800000 | Max connection lifetime (ms) |

---

## 9. Monitoring Setup

### 9.1 Health Checks

```bash
# Simple health check script
#!/bin/bash
MASTER_HOST=${1:-localhost}
MASTER_PORT=${2:-8000}

java -jar minisql-admin-jar-with-dependencies.jar \
  --host $MASTER_HOST --port $MASTER_PORT cluster status

if [ $? -eq 0 ]; then
    echo "Cluster is healthy"
else
    echo "Cluster check failed"
    exit 1
fi
```

### 9.2 Log Monitoring

```bash
# Monitor Master logs in real-time
tail -f logs/master.log | grep -E "(ERROR|WARN)"

# Monitor RegionServer logs
tail -f logs/regionserver-rs-001.log | grep -E "(ERROR|WARN)"
```

### 9.3 Metrics Collection

The system exposes cluster statistics via the CLI:

```bash
# Collect periodic stats
watch -n 10 'minisql-admin cluster stats'
```

---

## 10. Backup and Recovery

### 10.1 Metadata Backup

Master metadata is persisted in Zookeeper. Backup Zookeeper data:

```bash
# Zookeeper snapshot backup
cp -r /var/lib/zookeeper/version-2 /backup/zk-$(date +%Y%m%d)
```

### 10.2 Data Backup

Each RegionServer stores data in MySQL. Use standard MySQL backup:

```bash
# Backup a RegionServer database
mysqldump -u minisql -p minisql_rs001 > backup_rs001_$(date +%Y%m%d).sql

# Restore
mysql -u minisql -p minisql_rs001 < backup_rs001_20250101.sql
```

---

## 11. Troubleshooting Deployment

### 11.1 Master Won't Start

**Check**:
1. Zookeeper is running: `echo ruok | nc localhost 2181`
2. Port 8000 is available: `netstat -tlnp | grep 8000`
3. Zookeeper connection string is correct

### 11.2 RegionServer Won't Register

**Check**:
1. Master is running and reachable
2. Server ID is not already taken
3. Port is not in use
4. Network connectivity between RegionServer and Master

### 11.3 MySQL Connection Errors

**Check**:
1. MySQL is running: `mysqladmin ping`
2. Database exists: `mysql -u minisql -p -e "SHOW DATABASES;"`
3. Credentials are correct
4. Host allows remote connections

### 11.4 Performance Issues

**Diagnostic steps**:
1. Check CPU/memory on each node: `htop`
2. Check disk I/O: `iostat -x 1`
3. Check network: `iftop`
4. Check MySQL slow queries log
5. Review Java GC logs

---

## 12. Security Considerations

- Run services under dedicated user accounts (not root)
- Use firewall rules to restrict port access
- Change default MySQL passwords
- Use TLS for gRPC connections in untrusted networks
- Keep Zookeeper ports internal to the cluster network
- Regularly apply security patches to Java and MySQL

---

## Appendix A: Deployment Checklist

- [ ] Java 11+ installed on all nodes
- [ ] Maven installed on build node
- [ ] Zookeeper 3.8+ configured and running
- [ ] MySQL 8.0+ installed with databases created
- [ ] MySQL user created with permissions
- [ ] Network ports open between components
- [ ] Project built successfully
- [ ] Master server starts without errors
- [ ] All RegionServers register with Master
- [ ] Admin CLI can connect and query cluster
- [ ] Can create a table successfully
- [ ] Write and read operations work

## Appendix B: Quick Start Script

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
java -jar minisql-master/target/minisql-master-1.0-SNAPSHOT.jar 8000 master-1 &
MASTER_PID=$!
sleep 3

echo "Starting RegionServers..."
java -jar minisql-regionserver/target/minisql-regionserver-1.0-SNAPSHOT.jar rs-001 8001 &
RS1_PID=$!
java -jar minisql-regionserver/target/minisql-regionserver-1.0-SNAPSHOT.jar rs-002 8002 &
RS2_PID=$!
java -jar minisql-regionserver/target/minisql-regionserver-1.0-SNAPSHOT.jar rs-003 8003 &
RS3_PID=$!
sleep 3

echo "Verifying cluster..."
java -jar minisql-admin/target/minisql-admin-*-jar-with-dependencies.jar cluster status

echo "Deployment complete!"
echo "Master PID: $MASTER_PID"
echo "RS-001 PID: $RS1_PID"
echo "RS-002 PID: $RS2_PID"
echo "RS-003 PID: $RS3_PID"
echo "Run 'kill $MASTER_PID $RS1_PID $RS2_PID $RS3_PID' to stop all"
```
