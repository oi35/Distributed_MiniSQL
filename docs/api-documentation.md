# Distributed MiniSQL API Documentation

## Overview

The Distributed MiniSQL system exposes APIs at two levels:
- **gRPC APIs**: Inter-module RPC interfaces (Master to RegionServer, Master to Client, RegionServer to Client)
- **CLI Tool API**: Command-line interface for cluster and table management

---

## 1. gRPC API Reference

gRPC interfaces are defined using Protocol Buffers (proto3) in `minisql-common/src/main/proto/`.

### 1.1 MasterService (Master to RegionServer)

**Package**: `minisql.master.MasterService`

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `RegisterRegionServer` | `RegisterRegionServerRequest` | `RegisterRegionServerResponse` | RegionServer registers with Master |
| `SendHeartbeat` | `HeartbeatRequest` | `HeartbeatResponse` | Periodic heartbeat |
| `UnregisterRegionServer` | `UnregisterRegionServerRequest` | `UnregisterRegionServerResponse` | Graceful unregister |
| `ReportRegionOnline` | `ReportRegionOnlineRequest` | `ReportRegionOnlineResponse` | Report region online |
| `ReportRegionClosed` | `ReportRegionClosedRequest` | `ReportRegionClosedResponse` | Report region closed |
| `ReportRegionSplit` | `ReportRegionSplitRequest` | `ReportRegionSplitResponse` | Report region split |
| `ReportMigrationProgress` | `ReportMigrationProgressRequest` | `ReportMigrationProgressResponse` | Report migration progress |
| `ReportRegionFailure` | `ReportRegionFailureRequest` | `ReportRegionFailureResponse` | Report region failure |
| `AdminOperation` | `AdminOperationRequest` | `AdminOperationResponse` | Generic admin operation |

### 1.2 ClientMasterService (Master to Client)

**Package**: `minisql.master.ClientMasterService`

#### Table DDL

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `CreateTable` | `CreateTableRequest` | `CreateTableResponse` | Create new table |
| `DropTable` | `DropTableRequest` | `DropTableResponse` | Drop table |
| `GetTableSchema` | `GetTableSchemaRequest` | `GetTableSchemaResponse` | Get table schema |
| `ListTables` | `ListTablesRequest` | `ListTablesResponse` | List all tables |

#### Route Queries

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `GetRouteTable` | `GetRouteTableRequest` | `GetRouteTableResponse` | Get full route table |
| `GetRouteForKey` | `GetRouteForKeyRequest` | `GetRouteForKeyResponse` | Get route for key |
| `GetRoutesForRange` | `GetRoutesForRangeRequest` | `GetRoutesForRangeResponse` | Get routes for range |
| `ReportStaleRoute` | `ReportStaleRouteRequest` | `ReportStaleRouteResponse` | Report stale route |

#### Cluster Info

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `GetClusterHealth` | `GetClusterHealthRequest` | `GetClusterHealthResponse` | Cluster health status |
| `GetClusterStats` | `GetClusterStatsRequest` | `GetClusterStatsResponse` | Cluster statistics |

### 1.3 AdminService (CLI to Master)

**Package**: `minisql.master.AdminService`

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `ListServers` | `ListServersRequest` | `ListServersResponse` | List RegionServers |
| `TriggerBalance` | `TriggerBalanceRequest` | `TriggerBalanceResponse` | Trigger load balancing |

### 1.4 RegionServerService (RegionServer to Client)

**Package**: `minisql.regionserver.RegionServerService`

#### Single Row Operations

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `Put` | `PutRequest` | `PutResponse` | Insert/update single row |
| `Get` | `GetRequest` | `GetResponse` | Get single row |
| `Delete` | `DeleteRequest` | `DeleteResponse` | Delete single row |
| `Exists` | `ExistsRequest` | `ExistsResponse` | Check row existence |

#### Batch Operations

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `BatchPut` | `BatchPutRequest` | `BatchPutResponse` | Batch insert |
| `BatchGet` | `BatchGetRequest` | `BatchGetResponse` | Batch get |
| `BatchDelete` | `BatchDeleteRequest` | `BatchDeleteResponse` | Batch delete |

#### Scan and Query

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `Scan` | `ScanRequest` | `ScanResponse` (stream) | Range scan |
| `Query` | `QueryRequest` | `QueryResponse` | Advanced query |

#### Region Management

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `OpenRegion` | `OpenRegionRequest` | `OpenRegionResponse` | Open region |
| `CloseRegion` | `CloseRegionRequest` | `CloseRegionResponse` | Close region |
| `MigrateRegion` | `MigrateRegionRequest` | `MigrateRegionResponse` | Migrate region |

#### Replication

| Method | Request | Response | Description |
|--------|---------|----------|-------------|
| `GetReplicationLog` | `GetReplicationLogRequest` | `ReplicationLogEntry` (stream) | Get WAL logs |
| `ApplyReplicationLog` | `ApplyReplicationLogRequest` | `ApplyReplicationLogResponse` | Apply WAL logs |

---

## 2. CLI Tool API Reference

### 2.1 Overview

The `minisql-admin` CLI tool connects to the Master node via gRPC and provides cluster management and table administration commands.

**Module**: `minisql-admin`
**Main Class**: `com.minisql.admin.MiniSqlAdmin`
**Build**: `cd minisql-admin && mvn package -DskipTests`
**Output**: `target/minisql-admin-*-jar-with-dependencies.jar`

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
address: string         # host:port format
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

### 5.1 MiniSQLClient

**Package**: `com.minisql.client`

**Factory Method**:
```java
public static MiniSQLClient connect(String masterAddress)
```

**Constructors**:
```java
// With ConnectionManager
public MiniSQLClient(ManagedChannel masterChannel, ConnectionManager connectionManager)

// With ConnectionManager and custom ExecutorService
public MiniSQLClient(ManagedChannel masterChannel, ConnectionManager connectionManager,
                     ExecutorService scanExecutor)
```

**Methods**:

| Method | gRPC Call | Return Type | Description |
|--------|-----------|-------------|-------------|
| `put(table, key, columns)` | RegionServerService.Put | `PutResult` | Insert/update a row |
| `get(table, key, columns)` | RegionServerService.Get | `GetResult` | Get a row by key |
| `delete(table, key)` | RegionServerService.Delete | `DeleteResult` | Delete a row |
| `exists(table, key)` | RegionServerService.Exists | `boolean` | Check if key exists |
| `scan(table, startKey, endKey, limit, columns)` | RegionServerService.Scan | `List<ScanRow>` | Range scan |
| `close()` | - | - | Shutdown and release resources |

**Result Types**:

```java
public static final class PutResult {
    boolean isSuccess();
    long getSequenceId();
    ErrorCode getErrorCode();
    String getErrorMessage();
}

public static final class GetResult {
    boolean isFound();
    Map<String, ByteString> getColumns();
    long getTimestamp();
    ErrorCode getErrorCode();
    String getErrorMessage();
}

public static final class DeleteResult {
    boolean isSuccess();
    boolean didExist();
    long getSequenceId();
    ErrorCode getErrorCode();
    String getErrorMessage();
}

public static final class ScanRow {
    ByteString getKey();
    Map<String, ByteString> getColumns();
    long getTimestamp();
}
```

### 5.2 AdminGrpcClient

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

### 5.3 Usage Example

```java
// Client SDK example
MiniSQLClient client = MiniSQLClient.connect("localhost:8000");
try {
    // Write data
    Map<String, ByteString> columns = new HashMap<>();
    columns.put("name", ByteString.copyFromUtf8("alice"));
    MiniSQLClient.PutResult result = client.put("users",
        ByteString.copyFromUtf8("key1"), columns);

    // Read data
    MiniSQLClient.GetResult data = client.get("users",
        ByteString.copyFromUtf8("key1"), null);
} finally {
    client.close();
}
```

```java
// Admin CLI example
AdminGrpcClient admin = new AdminGrpcClient("localhost", 8000);
try {
    admin.printClusterHealth();
    admin.printServerList();
    admin.printTableList();
} finally {
    admin.shutdown();
}
```
