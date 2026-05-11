# Distributed MiniSQL API Documentation

## Overview

The Distributed MiniSQL system exposes APIs at two levels:
- **gRPC APIs**: Inter-module RPC interfaces (Master ↔ RegionServer, Master ↔ Client, RegionServer ↔ Client)
- **CLI Tool API**: Command-line interface for cluster and table management

---

## 1. gRPC API Reference

gRPC interfaces are defined using Protocol Buffers (proto3) in `minisql-common/src/main/proto/`.

### 1.1 MasterService (Master ↔ RegionServer)

**Package**: `minisql.master.MasterService`

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `RegisterRegionServer` | `RegisterRegionServerRequest` | `RegisterRegionServerResponse` | RegionServer注册到Master |
| `SendHeartbeat` | `HeartbeatRequest` | `HeartbeatResponse` | RegionServer定期心跳上报 |
| `UnregisterRegionServer` | `UnregisterRegionServerRequest` | `UnregisterRegionServerResponse` | RegionServer优雅注销 |
| `ReportRegionOnline` | `ReportRegionOnlineRequest` | `ReportRegionOnlineResponse` | 报告Region已上线 |
| `ReportRegionClosed` | `ReportRegionClosedRequest` | `ReportRegionClosedResponse` | 报告Region已关闭 |
| `ReportRegionSplit` | `ReportRegionSplitRequest` | `ReportRegionSplitResponse` | 报告Region分裂 |
| `ReportMigrationProgress` | `ReportMigrationProgressRequest` | `ReportMigrationProgressResponse` | 报告迁移进度 |
| `ReportRegionFailure` | `ReportRegionFailureRequest` | `ReportRegionFailureResponse` | 报告Region故障 |
| `AdminOperation` | `AdminOperationRequest` | `AdminOperationResponse` | 通用管理操作 |

### 1.2 ClientMasterService (Master ↔ Client)

**Package**: `minisql.master.ClientMasterService`

#### Table DDL

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `CreateTable` | `CreateTableRequest` | `CreateTableResponse` | 创建新表 |
| `DropTable` | `DropTableRequest` | `DropTableResponse` | 删除表 |
| `GetTableSchema` | `GetTableSchemaRequest` | `GetTableSchemaResponse` | 获取表结构 |
| `ListTables` | `ListTablesRequest` | `ListTablesResponse` | 列出所有表 |

#### Route Queries

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `GetRouteTable` | `GetRouteTableRequest` | `GetRouteTableResponse` | 获取完整路由表 |
| `GetRouteForKey` | `GetRouteForKeyRequest` | `GetRouteForKeyResponse` | 获取指定键的路由 |
| `GetRoutesForRange` | `GetRoutesForRangeRequest` | `GetRoutesForRangeResponse` | 获取范围路由 |
| `ReportStaleRoute` | `ReportStaleRouteRequest` | `ReportStaleRouteResponse` | 报告路由过期 |

#### Cluster Info

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `GetClusterHealth` | `GetClusterHealthRequest` | `GetClusterHealthResponse` | 集群健康状态 |
| `GetClusterStats` | `GetClusterStatsRequest` | `GetClusterStatsResponse` | 集群统计信息 |

### 1.3 AdminService (CLI → Master)

**Package**: `minisql.master.AdminService`

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `ListServers` | `ListServersRequest` | `ListServersResponse` | 获取RegionServer列表 |
| `TriggerBalance` | `TriggerBalanceRequest` | `TriggerBalanceResponse` | 触发手动负载均衡 |

### 1.4 RegionServerService (RegionServer ↔ Client)

**Package**: `minisql.regionserver.RegionServerService`

#### Single Row Operations

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `Put` | `PutRequest` | `PutResponse` | 插入/更新单行 |
| `Get` | `GetRequest` | `GetResponse` | 查询单行 |
| `Delete` | `DeleteRequest` | `DeleteResponse` | 删除单行 |
| `Exists` | `ExistsRequest` | `ExistsResponse` | 检查行是否存在 |

#### Batch Operations

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `BatchPut` | `BatchPutRequest` | `BatchPutResponse` | 批量插入 |
| `BatchGet` | `BatchGetRequest` | `BatchGetResponse` | 批量查询 |
| `BatchDelete` | `BatchDeleteRequest` | `BatchDeleteResponse` | 批量删除 |

#### Scan & Query

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `Scan` | `ScanRequest` | `ScanResponse` (stream) | 范围扫描 |
| `Query` | `QueryRequest` | `QueryResponse` | 高级查询 |

#### Region Management

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `OpenRegion` | `OpenRegionRequest` | `OpenRegionResponse` | 打开Region |
| `CloseRegion` | `CloseRegionRequest` | `CloseRegionResponse` | 关闭Region |
| `MigrateRegion` | `MigrateRegionRequest` | `MigrateRegionResponse` | 迁移Region |

#### Replication

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `GetReplicationLog` | `GetReplicationLogRequest` | `GetReplicationLogResponse` (stream) | 获取WAL日志 |
| `ApplyReplicationLog` | `ApplyReplicationLogRequest` | `ApplyReplicationLogResponse` | 应用WAL日志 |

---

## 2. CLI Tool API Reference

### 2.1 Overview

The `minisql-admin` CLI tool connects to the Master node via gRPC and provides cluster management and table administration commands.

**Module**: `minisql-admin`
**Main Class**: `com.minisql.admin.MiniSqlAdmin`
**Build**: `mvn clean package -pl minisql-admin -am -DskipTests`

### 2.2 Global Options

| Option | Default | Description |
|--------|---------|-------------|
| `--host <host>` | `localhost` | Master server address |
| `--port <port>` | `8000` | Master server port |
| `--help`, `-h` | - | Display help message |

### 2.3 Commands

#### cluster status

Display cluster health status.

```
minisql-admin cluster status
```

**Output fields**:
- `Cluster Health`: HEALTHY / DEGRADED / CRITICAL
- `Total Servers`: Registered server count
- `Online Servers`: Currently online server count
- `Total Regions`: Total region count across all servers
- `Online Regions`: Online region count
- `Issues`: List of cluster issues (if any)

#### cluster stats

Display cluster statistics.

```
minisql-admin cluster stats
```

**Output fields**:
- `Total Tables`: Number of tables
- `Total Regions`: Number of regions
- `Total Data Size`: Total data size (B/KB/MB/GB)
- `Total Rows`: Total row count
- `Avg Region Size`: Average region size in MB
- `Regions Splitting`: Number of regions currently splitting
- `Regions Migrating`: Number of regions currently migrating
- `Total QPS`: Total queries per second

#### cluster nodes

List all registered RegionServer nodes with detailed info.

```
minisql-admin cluster nodes
```

**Output fields per server**:
- `Server`: Server ID
- `Address`: Host:port
- `State`: ONLINE / OFFLINE / DEAD
- `Load Score`: Current load score
- `Regions`: Number of hosted regions
- `Data Size`: Total data size
- `Uptime`: Server uptime (e.g., `2d 3h`, `45m 30s`)
- `Last Heartbeat`: Last heartbeat timestamp

#### cluster balance

Trigger manual load balancing.

```
minisql-admin cluster balance
```

**Output fields**:
- `Balance Result`: SUCCESS / FAILED
- `Message`: Result message
- `Plans Generated`: Number of migration plans generated

#### table list

List all tables in the cluster.

```
minisql-admin table list
```

**Output**: Table names, one per line, with total count.

#### table describe

Display table schema information.

```
minisql-admin table describe <tableName>
```

**Output fields**:
- `Table`: Table name
- `Primary Key`: Primary key column
- `Version`: Schema version
- `Columns`: Table with Name, Type, Nullable, Default

#### table route

Display table route information (region distribution).

```
minisql-admin table route <tableName>
```

**Output fields**:
- `Route Table`: Table name
- `Version`: Route table version
- Per region:
  - `Region`: Region ID
  - `Range`: Key range `[start, end)`
  - `Primary`: Primary server
  - `Replicas`: Replica server list
  - `Replica Addresses`: Replica addresses

### 2.4 Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | Error (invalid args, connection failure, etc.) |

### 2.5 Examples

```bash
# Check cluster health
minisql-admin cluster status

# List all RegionServer nodes
minisql-admin --host 192.168.1.10 cluster nodes

# List all tables
minisql-admin table list

# View table schema
minisql-admin table describe users

# View table route information
minisql-admin table route users

# Trigger load balancing
minisql-admin cluster balance
```

---

## 3. Error Codes

### 3.1 Standard Error Codes

| Code | Value | Description |
|------|-------|-------------|
| `ERROR_OK` | 0 | Success |
| `ERROR_UNKNOWN` | 1 | Unknown error |
| `ERROR_INVALID_ARGUMENT` | 2 | Invalid argument |
| `ERROR_NOT_FOUND` | 3 | Resource not found |
| `ERROR_ALREADY_EXISTS` | 4 | Resource already exists |
| `ERROR_PERMISSION_DENIED` | 5 | Permission denied |
| `ERROR_RESOURCE_EXHAUSTED` | 6 | Resource exhausted |
| `ERROR_FAILED_PRECONDITION` | 7 | Failed precondition |
| `ERROR_ABORTED` | 8 | Operation aborted |
| `ERROR_OUT_OF_RANGE` | 9 | Out of range |
| `ERROR_UNIMPLEMENTED` | 10 | Not implemented |
| `ERROR_INTERNAL` | 11 | Internal error |
| `ERROR_UNAVAILABLE` | 12 | Service unavailable |
| `ERROR_DEADLINE_EXCEEDED` | 13 | Deadline exceeded |

### 3.2 Business Error Codes

| Code | Value | Description |
|------|-------|-------------|
| `ERROR_REGION_NOT_FOUND` | 100 | Region not found |
| `ERROR_REGION_NOT_ONLINE` | 101 | Region not online |
| `ERROR_REGION_SPLITTING` | 102 | Region is splitting |
| `ERROR_TABLE_NOT_FOUND` | 103 | Table not found |
| `ERROR_TABLE_ALREADY_EXISTS` | 104 | Table already exists |
| `ERROR_SERVER_NOT_FOUND` | 105 | Server not found |
| `ERROR_DUPLICATE_KEY` | 106 | Duplicate key |
| `ERROR_STALE_ROUTE` | 107 | Stale route |

---

## 4. Protobuf Message Reference

### 4.1 Common Messages (`common.proto`)

**`TableSchema`**
```
table_name: string
columns: repeated ColumnSchema
primary_key: string
version: int64
create_time: int64
```

**`ColumnSchema`**
```
name: string
type: string ("BIGINT", "VARCHAR(100)", "DOUBLE", etc.)
nullable: bool
default_value: string
comment: string
```

**`RegionInfo`**
```
region_id: string
table_name: string
start_key: bytes
end_key: bytes
primary_server: string
replica_servers: repeated string
state: RegionState
size_bytes: int64
row_count: int64
version: int64
create_time: int64
update_time: int64
```

**`RegionRouteTable`**
```
table_name: string
routes: repeated RouteEntry
version: int64
update_time: int64
```

**`RouteEntry`**
```
region_id: string
start_key: bytes
end_key: bytes
primary_server: string
primary_address: string
replica_servers: repeated string
replica_addresses: repeated string
```

### 4.2 Admin Messages (`master.proto`)

**`ServerDetail`**
```
server_id: string
host: string
port: int32
state: ServerState
load_score: double
region_count: int32
total_size_bytes: int64
last_heartbeat_time: int64
uptime_ms: int64
address: string
```

**`ListServersResponse`**
```
servers: repeated ServerDetail
total_count: int32
online_count: int32
```

**`TriggerBalanceResponse`**
```
success: bool
message: string
plans_generated: int32
```

**`GetClusterHealthResponse`**
```
status: HealthStatus (HEALTHY=0, DEGRADED=1, CRITICAL=2)
total_servers: int32
online_servers: int32
total_regions: int32
online_regions: int32
issues: repeated string
```

**`GetClusterStatsResponse`**
```
total_tables: int32
total_regions: int32
total_data_size_bytes: int64
total_row_count: int64
average_region_size_mb: double
regions_splitting: int32
regions_migrating: int32
total_qps: int64
```

---

## 5. Java Client API

### 5.1 AdminGrpcClient

**Package**: `com.minisql.admin`

**Constructor**:
```java
public AdminGrpcClient(String host, int port)
```

**Methods**:

| Method | gRPC Call | Description |
|--------|-----------|-------------|
| `printClusterHealth()` | `ClientMasterService.GetClusterHealth` | Print cluster health |
| `printClusterStats()` | `ClientMasterService.GetClusterStats` | Print cluster statistics |
| `printServerList()` | `AdminService.ListServers` | Print RegionServer list |
| `triggerBalance()` | `AdminService.TriggerBalance` | Trigger load balancing |
| `printTableList()` | `ClientMasterService.ListTables` | Print table list |
| `printTableSchema(tableName)` | `ClientMasterService.GetTableSchema` | Print table schema |
| `printTableRoute(tableName)` | `ClientMasterService.GetRouteTable` | Print table route info |
| `shutdown()` | - | Shutdown gRPC channel |

### 5.2 Usage Example

```java
AdminGrpcClient client = new AdminGrpcClient("localhost", 8000);
try {
    client.printClusterHealth();
    client.printServerList();
    client.printTableList();
} finally {
    client.shutdown();
}
```
