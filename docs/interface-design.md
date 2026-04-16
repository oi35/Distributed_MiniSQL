# 分布式MiniSQL gRPC接口设计文档

## 1. 设计理念

本接口设计采用**混合方案**，结合了显式操作和泛型方法的优点：

### 核心原则
- **常用操作显式化**：核心、高频操作使用专用方法，保证类型安全和清晰性
- **扩展操作泛型化**：不常用的管理操作使用泛型方法，保持接口简洁
- **教学价值优先**：方法名清晰表达意图，便于理解分布式系统交互
- **实现友好**：每个方法对应明确的处理逻辑，便于测试和调试

### 方法统计
- **Master ↔ RegionServer**: 7个显式方法 + 1个泛型方法
- **Master ↔ Client**: 11个显式方法
- **RegionServer ↔ Client**: 11个显式方法 + 1个泛型方法 + 3个管理方法
- **总计**: 34个方法

---

## 2. 接口概览

### 2.1 Master ↔ RegionServer 接口

**服务**: `MasterService`

#### 显式方法（核心操作）

| 方法 | 用途 | 调用频率 |
|------|------|---------|
| `RegisterRegionServer` | RegionServer注册 | 启动时一次 |
| `SendHeartbeat` | 心跳上报 | 每3秒 |
| `UnregisterRegionServer` | 注销 | 关闭时一次 |
| `ReportRegionOnline` | 报告Region上线 | Region打开时 |
| `ReportRegionClosed` | 报告Region关闭 | Region关闭时 |
| `ReportRegionSplit` | 报告Region分裂 | 分裂完成时 |
| `ReportMigrationProgress` | 报告迁移进度 | 迁移过程中 |
| `ReportRegionFailure` | 报告Region故障 | 故障发生时 |

#### 泛型方法（管理操作）

| 方法 | 用途 | 支持的操作 |
|------|------|-----------|
| `AdminOperation` | 管理操作 | ForceGC, CompactRegion, FlushRegion, DebugDump |

---

### 2.2 Master ↔ Client 接口

**服务**: `ClientMasterService`

#### 表DDL操作

| 方法 | 用途 |
|------|------|
| `CreateTable` | 创建表 |
| `DropTable` | 删除表 |
| `GetTableSchema` | 获取表结构 |
| `ListTables` | 列出所有表 |

#### 路由查询

| 方法 | 用途 |
|------|------|
| `GetRouteTable` | 获取完整路由表 |
| `GetRouteForKey` | 获取单个键的路由 |
| `GetRoutesForRange` | 获取范围路由 |
| `ReportStaleRoute` | 报告路由过期 |

#### 集群信息

| 方法 | 用途 |
|------|------|
| `GetClusterHealth` | 获取集群健康状态 |
| `GetClusterStats` | 获取集群统计信息 |

---

### 2.3 RegionServer ↔ Client 接口

**服务**: `RegionServerService`

#### 单行操作

| 方法 | 用途 |
|------|------|
| `Put` | 插入/更新单行 |
| `Get` | 查询单行 |
| `Delete` | 删除单行 |
| `Exists` | 检查行是否存在 |

#### 批量操作

| 方法 | 用途 |
|------|------|
| `BatchPut` | 批量插入 |
| `BatchGet` | 批量查询 |
| `BatchDelete` | 批量删除 |

#### 范围扫描

| 方法 | 用途 | 特点 |
|------|------|------|
| `Scan` | 范围扫描 | 流式返回 |

#### 高级查询（泛型）

| 方法 | 用途 | 支持的操作 |
|------|------|-----------|
| `Query` | 高级查询 | Count, Aggregate(SUM/AVG/MIN/MAX), Filter |

#### Region管理（Master调用）

| 方法 | 用途 |
|------|------|
| `OpenRegion` | 打开Region |
| `CloseRegion` | 关闭Region |
| `MigrateRegion` | 迁移Region |

#### 副本同步（RegionServer间）

| 方法 | 用途 | 特点 |
|------|------|------|
| `GetReplicationLog` | 获取WAL日志 | 流式返回 |
| `ApplyReplicationLog` | 应用WAL日志 | 批量应用 |

---

## 3. 使用示例

### 3.1 RegionServer启动流程

```java
// 1. 连接Master
ManagedChannel channel = ManagedChannelBuilder
    .forAddress("master-host", 8000)
    .usePlaintext()
    .build();

MasterServiceGrpc.MasterServiceBlockingStub masterStub = 
    MasterServiceGrpc.newBlockingStub(channel);

// 2. 注册
RegisterRegionServerRequest request = RegisterRegionServerRequest.newBuilder()
    .setServerId("rs-001")
    .setHost("192.168.1.10")
    .setPort(8001)
    .setTotalMemoryMb(8192)
    .setCpuCores(4)
    .setDiskCapacityMb(102400)
    .build();

RegisterRegionServerResponse response = masterStub.registerRegionServer(request);

if (response.getSuccess()) {
    String assignedId = response.getAssignedServerId();
    int heartbeatInterval = response.getHeartbeatIntervalMs();
    
    logger.info("Registered as {}, heartbeat interval: {}ms", 
                assignedId, heartbeatInterval);
    
    // 3. 启动心跳线程
    startHeartbeatScheduler(assignedId, heartbeatInterval);
} else {
    logger.error("Registration failed: ", response.getErrorMessage());
}
```

### 3.2 心跳发送

```java
// 定期执行（每3秒）
void sendHeartbeat() {
    HeartbeatRequest request = HeartbeatRequest.newBuilder()
        .setServerId(serverId)
        .setTimestamp(System.currentTimeMillis())
        .addAllRegionIds(getLocalRegionIds())
        .setMetrics(ServerMetrics.newBuilder()
            .setRegionCount(localRegions.size())
            .setTotalSizeBytes(calculateTotalSize())
            .setCpuUsage(getCpuUsage())
            .setMemoryUsage(getMemoryUsage())
            .setActiveConnections(getConnectionCount())
            .setQps(getQps())
            .build())
        .build();
    
    HeartbeatResponse response = masterStub.sendHeartbeat(request);
    
    if (response.getAcknowledged()) {
        // 处理Master下发的命令
        for (RegionCommand cmd : response.getCommandsList()) {
            handleCommand(cmd);
        }
    }
}

void handleCommand(RegionCommand cmd) {
    switch (cmd.getType()) {
        case OPEN_REGION:
            openRegion(cmd.getRegionInfo());
            break;
        case CLOSE_REGION:
            closeRegion(cmd.getRegionId());
            break;
        case MIGRATE_REGION:
            migrateRegion(cmd.getRegionId(), cmd.getTargetServer());
            break;
    }
}
```

### 3.3 客户端创建表

```java
// 1. 连接Master
ClientMasterServiceGrpc.ClientMasterServiceBlockingStub masterStub = 
    ClientMasterServiceGrpc.newBlockingStub(masterChannel);

// 2. 定义表结构
TableSchema schema = TableSchema.newBuilder()
    .setTableName("users")
    .addColumns(ColumnSchema.newBuilder()
        .setName("user_id")
        .setType("BIGINT")
        .setNullable(false)
        .build())
    .addColumns(ColumnSchema.newBuilder()
        .setName("username")
        .setType("VARCHAR(50)")
        .setNullable(false)
        .build())
    .addColumns(ColumnSchema.newBuilder()
        .setName("email")
        .setType("VARCHAR(100)")
        .setNullable(true)
        .build())
    .setPrimaryKey("user_id")
    .build();

// 3. 创建表
CreateTableRequest request = CreateTableRequest.newBuilder()
    .setSchema(schema)
    .setInitialRegionCount(1)
    .setReplicaCount(3)
    .build();

CreateTableResponse response = masterStub.createTable(request);

if (response.getSuccess()) {
    logger.info("Table created successfully");
    logger.info("Initial regions: {}", response.getInitialRegionsList());
    logger.info("Route table version: {}", response.getRouteTableVersion());
} else {
    logger.error("Failed to create table: {} - {}", 
                 response.getErrorCode(), 
                 response.getErrorMessage());
}
```

### 3.4 客户端查询路由并写入数据

```java
// 1. 查询路由
GetRouteForKeyRequest routeRequest = GetRouteForKeyRequest.newBuilder()
    .setTableName("users")
    .setKey(ByteString.copyFrom(serializeKey(12345L)))
    .build();

GetRouteForKeyResponse routeResponse = masterStub.getRouteForKey(routeRequest);

if (!routeResponse.getSuccess()) {
    logger.error("Failed to get route: {}", routeResponse.getErrorMessage());
    return;
}

RouteEntry route = routeResponse.getRoute();
String regionId = route.getRegionId();
String serverAddress = route.getPrimaryAddress();

// 2. 连接RegionServer
ManagedChannel rsChannel = ManagedChannelBuilder
    .forTarget(serverAddress)
    .usePlaintext()
    .build();

RegionServerServiceGrpc.RegionServerServiceBlockingStub rsStub = 
    RegionServerServiceGrpc.newBlockingStub(rsChannel);

// 3. 写入数据
PutRequest putRequest = PutRequest.newBuilder()
    .setTableName("users")
    .setRegionId(regionId)
    .setKey(ByteString.copyFrom(serializeKey(12345L)))
    .putColumns("user_id", ByteString.copyFrom(serializeLong(12345L)))
    .putColumns("username", ByteString.copyFromUtf8("alice"))
    .putColumns("email", ByteString.copyFromUtf8("alice@example.com"))
    .setSyncReplicas(true)
    .setTimestamp(System.currentTimeMillis())
    .build();

PutResponse putResponse = rsStub.put(putRequest);

if (putResponse.getSuccess()) {
    logger.info("Data written successfully, WAL sequence: {}", 
                putResponse.getSequenceId());
} else {
    if (putResponse.getErrorCode() == ErrorCode.STALE_ROUTE) {
        // 路由过期，需要刷新
        logger.warn("Stale route detected, refreshing...");
        refreshRouteCache("users");
        // 重试
    } else {
        logger.error("Failed to write: {} - {}", 
                     putResponse.getErrorCode(), 
                     putResponse.getErrorMessage());
    }
}
```

### 3.5 客户端范围扫描

```java
// 1. 获取范围路由
GetRoutesForRangeRequest routesRequest = GetRoutesForRangeRequest.newBuilder()
    .setTableName("users")
    .setStartKey(ByteString.copyFrom(serializeKey(10000L)))
    .setEndKey(ByteString.copyFrom(serializeKey(20000L)))
    .build();

GetRoutesForRangeResponse routesResponse = 
    masterStub.getRoutesForRange(routesRequest);

// 2. 对每个Region执行扫描
for (RouteEntry route : routesResponse.getRoutesList()) {
    String serverAddress = route.getPrimaryAddress();
    ManagedChannel rsChannel = getOrCreateChannel(serverAddress);
    
    RegionServerServiceGrpc.RegionServerServiceBlockingStub rsStub = 
        RegionServerServiceGrpc.newBlockingStub(rsChannel);
    
    // 3. 扫描该Region
    ScanRequest scanRequest = ScanRequest.newBuilder()
        .setTableName("users")
        .setRegionId(route.getRegionId())
        .setStartKey(route.getStartKey())
        .setEndKey(route.getEndKey())
        .setLimit(1000)
        .addColumns("user_id")
        .addColumns("username")
        .build();
    
    Iterator<ScanResponse> results = rsStub.scan(scanRequest);
    
    // 4. 处理结果
    while (results.hasNext()) {
        ScanResponse row = results.next();
        processRow(row.getKey(), row.getColumnsMap());
        
        if (!row.getHasMore()) {
            break;
        }
    }
}
```

### 3.6 Region分裂流程

```java
// RegionServer检测到Region超过阈值
if (region.getSizeBytes() > SPLIT_THRESHOLD) {
    // 1. 执行本地分裂
    byte[] splitKey = findSplitKey(region);
    RegionInfo leftRegion = createLeftRegion(region, splitKey);
    RegionInfo rightRegion = createRightRegion(region, splitKey);
    
    // 2. 报告给Master
    ReportRegionSplitRequest request = ReportRegionSplitRequest.newBuilder()
        .setServerId(serverId)
        .setParentRegionId(region.getRegionId())
        .setSplitKey(ByteString.copyFrom(splitKey))
        .setLeftRegion(leftRegion)
        .setRightRegion(rightRegion)
        .build();
    
    ReportRegionSplitResponse response = masterStub.reportRegionSplit(request);
    
    if (response.getAcknowledged()) {
        // 3. Master可能指示将右Region迁移到其他服务器
        String targetServer = response.getTargetServerForRight();
        if (!targetServer.isEmpty() && !targetServer.equals(serverId)) {
            logger.info("Master requests migration of right region to {}", 
                        targetServer);
            // 启动迁移流程
            startMigration(rightRegion.getRegionId(), targetServer);
        }
    }
}
```

### 3.7 使用泛型方法进行管理操作

```java
// 管理员触发Region压缩
AdminOperationRequest request = AdminOperationRequest.newBuilder()
    .setServerId("rs-001")
    .setCompactRegion(CompactRegionOperation.newBuilder()
        .setRegionId("region-123")
        .build())
    .build();

AdminOperationResponse response = masterStub.adminOperation(request);

if (response.getSuccess()) {
    logger.info("Compaction completed: {}", response.getMessage());
} else {
    logger.error("Compaction failed: {}", response.getMessage());
}
```

---

## 4. 错误处理

### 4.1 错误码设计

所有响应都包含 `ErrorCode` 枚举，标准错误码：

- `OK`: 成功
- `INVALID_ARGUMENT`: 参数错误
- `NOT_FOUND`: 资源不存在
- `ALREADY_EXISTS`: 资源已存在
- `UNAVAILABLE`: 服务不可用
- `DEADLINE_EXCEEDED`: 超时

业务错误码：

- `REGION_NOT_FOUND`: Region不存在
- `REGION_NOT_ONLINE`: Region未上线
- `TABLE_NOT_FOUND`: 表不存在
- `DUPLICATE_KEY`: 主键冲突
- `STALE_ROUTE`: 路由过期

### 4.2 错误处理示例

```java
PutResponse response = rsStub.put(request);

switch (response.getErrorCode()) {
    case OK:
        logger.info("Success");
        break;
    
    case STALE_ROUTE:
        // 路由过期，刷新缓存并重试
        refreshRouteCache(tableName);
        retry();
        break;
    
    case REGION_NOT_ONLINE:
        // Region不在线，等待后重试
        Thread.sleep(1000);
        retry();
        break;
    
    case DUPLICATE_KEY:
        // 主键冲突，业务逻辑处理
        handleDuplicateKey();
        break;
    
    default:
        logger.error("Unexpected error: {} - {}", 
                     response.getErrorCode(), 
                     response.getErrorMessage());
}
```

---

## 5. 性能优化建议

### 5.1 连接池管理

```java
// 为每个RegionServer维护连接池
class ConnectionPool {
    private Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    
    public ManagedChannel getChannel(String address) {
        return channels.computeIfAbsent(address, addr -> 
            ManagedChannelBuilder.forTarget(addr)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .build()
        );
    }
}
```

### 5.2 路由缓存

```java
class RouteCache {
    private Map<String, RouteTable> cache = new ConcurrentHashMap<>();
    
    public RouteEntry getRoute(String tableName, byte[] key) {
        RouteTable table = cache.get(tableName);
        if (table == null) {
            table = fetchFromMaster(tableName);
            cache.put(tableName, table);
        }
        return findRouteForKey(table, key);
    }
    
    public void invalidate(String tableName) {
        cache.remove(tableName);
    }
}
```

### 5.3 批量操作

```java
// 使用批量操作减少RPC次数
List<RowData> rows = new ArrayList<>();
for (User user : users) {
    rows.add(RowData.newBuilder()
        .setKey(serializeKey(user.getId()))
        .putColumns("username", serialize(user.getName()))
        .putColumns("email", serialize(user.getEmail()))
        .build());
}

BatchPutRequest request = BatchPutRequest.newBuilder()
    .setTableName("users")
    .setRegionId(regionId)
    .addAllRows(rows)
    .setSyncReplicas(true)
    .build();

BatchPutResponse response = rsStub.batchPut(request);
logger.info("Batch inserted {} rows", response.getSuccessCount());
```

---

## 6. 设计优势总结

### 6.1 相比纯显式方法的优势

✅ **接口更简洁** - 34个方法 vs 48个方法  
✅ **易于扩展** - 管理操作通过泛型方法添加  
✅ **保持核心清晰** - 常用操作仍然显式  

### 6.2 相比纯泛型方法的优势

✅ **类型安全** - 核心操作有强类型保证  
✅ **易于发现** - IDE自动完成显示所有核心操作  
✅ **易于调试** - 方法名清晰，日志易读  

### 6.3 教学价值

✅ **清晰的交互流程** - 每个方法对应一个明确的操作  
✅ **易于理解** - 方法名本身就是文档  
✅ **便于实现** - 每个方法对应一个处理器  
✅ **便于测试** - 每个方法可以独立测试  

---

## 7. 下一步工作

1. **生成Java代码**
   ```bash
   cd minisql-common
   mvn clean compile
   ```

2. **实现服务端**
   - MasterServiceImpl
   - ClientMasterServiceImpl
   - RegionServerServiceImpl

3. **实现客户端SDK**
   - MiniSQLClient
   - RouteCache
   - ConnectionPool

4. **编写测试**
   - 单元测试
   - 集成测试
   - 端到端测试

---

## 8. 参考资料

- [gRPC官方文档](https://grpc.io/docs/)
- [Protocol Buffers指南](https://developers.google.com/protocol-buffers)
- [HBase RPC设计](https://hbase.apache.org/)
- [分布式MiniSQL架构设计](../superpowers/specs/2026-04-15-distributed-minisql-design.md)
