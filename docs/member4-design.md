# Client模块完整设计报告

**文档版本：** v1.0
**创建日期：** 2026-05-12
**负责人：** 冯俊翰 - 客户端与分布式查询
**模块状态：** 核心能力已落地 ✅（SQL Gateway / Join / 并行扫描 / 路由缓存）

---

## 目录

1. [概述](#1-概述)
2. [架构设计](#2-架构设计)
3. [核心组件详细设计](#3-核心组件详细设计)
4. [接口设计](#4-接口设计)
5. [数据模型](#5-数据模型)
6. [并发控制](#6-并发控制)
7. [容错与一致性](#7-容错与一致性)
8. [测试设计](#8-测试设计)
9. [性能优化](#9-性能优化)
10. [部署架构](#10-部署架构)
11. [监控和运维](#11-监控和运维)
12. [总结](#12-总结)

---

## 1. 概述

### 1.1 模块定位

Client模块是Distributed MiniSQL面向上层应用的接入层，负责：

- 屏蔽集群拓扑：向Master查询路由，缓存后直连RegionServer
- 提供两层API：低层KV（put/get/delete/scan/exists）与高层SQL（INSERT/SELECT/DELETE/JOIN）
- 分布式查询执行：多Region并行扫描、谓词下推、Hash Join
- 多语言支持：通过Gateway gRPC服务对外暴露，C/C++、Python可以直接调用

### 1.2 设计目标

**功能目标：**
- 支持单表的增、删、查、范围扫描
- 支持JSqlParser解析的简单SELECT语句（含WHERE / ORDER BY / LIMIT / OFFSET）
- 支持两表INNER JOIN（Hash Join）
- 支持跨Region的并行扫描与结果归并
- 支持路由失效自动刷新、一次静默重试

**性能目标：**
- 单表点查询端到端延迟 < 10ms（本地集群）
- 单表范围扫描QPS > 1000
- 两表Join延迟 < 100ms（百万级行，单Region）
- 并行扫描线性扩展至默认16线程

**可靠性目标：**
- Master连接抖动不影响已缓存路由的读写
- 路由过期（`ERROR_STALE_ROUTE`）自动刷新并重试一次
- 任意RegionServer故障仅影响其所属Region，不拖垮其他请求
- 客户端无状态，可安全水平扩展

### 1.3 技术选型

- **语言：** Java 11
- **RPC框架：** gRPC + Protobuf（与Master/RegionServer统一）
- **SQL解析：** JSqlParser 4.x
- **并发：** `CompletableFuture` + 固定线程池
- **日志：** SLF4J
- **测试：** JUnit 4 + Mockito + gRPC in-process server

---

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      应用 / CLI / 其他语言客户端             │
│             Java API 直连          gRPC（Gateway）          │
└──────────────┬───────────────────────────┬──────────────────┘
               │                           │
┌──────────────▼───────────────────────────▼──────────────────┐
│                        Client 模块                          │
│  ┌────────────────────────────────────────────────────────┐ │
│  │                    Gateway Server                       │ │
│  │   ┌──────────────┐    ┌──────────────────────────┐     │ │
│  │   │  Execute     │    │        Ping              │     │ │
│  │   │  (SQL → 结果)│    │  (版本 / 时钟探活)        │     │ │
│  │   └──────┬───────┘    └──────────────────────────┘     │ │
│  └──────────┼─────────────────────────────────────────────┘ │
│             │                                               │
│  ┌──────────▼──────────┐    ┌──────────────────────────┐    │
│  │     SqlExecutor     │───►│      JoinExecutor        │    │
│  │ INSERT/SELECT/DELETE│    │  (两表 Hash INNER JOIN)  │    │
│  └──────────┬──────────┘    └──────────────┬───────────┘    │
│             │                              │                │
│  ┌──────────▼──────────────────────────────▼───────────┐    │
│  │                    MiniSQLClient                    │    │
│  │    put / get / delete / scan / exists  (KV API)     │    │
│  │      路由重试 · 点查/范围 · ParallelScanner         │    │
│  └───┬────────────────┬─────────────────┬──────────────┘    │
│      │                │                 │                   │
│  ┌───▼───────┐  ┌─────▼───────┐  ┌─────▼────────────┐      │
│  │RouteCache │  │TableSchema  │  │ConnectionManager │      │
│  │  (表→区间)│  │   Cache     │  │ (gRPC Channel池) │      │
│  └─────┬─────┘  └─────┬───────┘  └──────┬───────────┘      │
└────────┼──────────────┼─────────────────┼──────────────────┘
         │              │                 │
   ClientMasterService  │         RegionServerService
         ▼              ▼                 ▼
    ┌────────┐     ┌────────┐      ┌──────────────┐
    │ Master │     │ Master │      │ RegionServer │
    └────────┘     └────────┘      └──────────────┘
```

### 2.2 模块依赖关系

```
GatewayServer
    ↓ 依赖
SqlExecutor ──► JoinExecutor
    ↓ 依赖          ↓ 依赖
  MiniSQLClient ◄──┘
    ↓ 依赖 (并列)
RouteCache + TableSchemaCache + ConnectionManager + ParallelScanner
    ↓ 依赖
minisql-common (proto)
```

- `minisql-common`提供`ClientMasterService`、`RegionServerService`的gRPC桩和共享的`TableSchema` / `RouteEntry` / `ErrorCode`
- Client不反向依赖Master/RegionServer实现代码，仅依赖proto契约

### 2.3 包结构

```
com.minisql.client/
├── MiniSQLClient.java              # KV门面 + 路由重试
├── ParallelScanner.java            # 多Region并行扫描
├── ClientDemo.java / CreateTableDemo.java  # 演示样例
├── conn/
│   └── ConnectionManager.java      # gRPC Channel 池
├── route/
│   └── RouteCache.java             # 路由表缓存 + 按 key / range 查找
├── schema/
│   └── TableSchemaCache.java       # 表结构缓存
├── sql/
│   ├── SqlExecutor.java            # SQL 解析 + 分发
│   ├── JoinExecutor.java           # INNER JOIN Hash Join
│   ├── JoinPredicateSplitter.java  # WHERE 谓词按表切分
│   ├── Predicate.java              # 统一谓词抽象
│   ├── PredicateBuilder.java       # JSqlParser 表达式 → Predicate
│   ├── PkRangeExtractor.java       # 从谓词萃取主键扫描区间
│   ├── FilterSerializer.java       # 谓词 → RegionServer 过滤串（下推）
│   ├── OrderByApplier.java         # ORDER BY / LIMIT / OFFSET
│   ├── ValueCodec.java             # 类型化值 ↔ ByteString 编解码
│   ├── DecodedRow.java             # 解码后的行（raw + 解码值）
│   ├── SqlResult.java              # 查询/更新结果统一承载
│   └── TableScanner.java           # 全表扫描迭代器
├── gateway/
│   ├── GatewayServer.java          # 启动入口
│   └── GatewayServiceImpl.java     # Execute / Ping 的 gRPC 实现
└── exception/
    └── MiniSQLClientException.java # 统一带 ErrorCode 的异常
```

---

## 3. 核心组件详细设计

### 3.1 ConnectionManager（gRPC Channel 池）

**职责：**

- 按`host:port`复用`ManagedChannel`，避免每次请求都三次握手 + HTTP/2 启动
- 统一连接参数（`usePlaintext` + `keepAliveTime=30s`）
- 生命周期管理：`close()`时优雅关闭所有Channel，5秒内未退出则强制`shutdownNow`

**核心API：**

```java
public class ConnectionManager implements AutoCloseable {
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final ChannelFactory channelFactory;

    public ManagedChannel getChannel(String address);  // computeIfAbsent 创建
    public int size();
    public void close();

    @FunctionalInterface
    public interface ChannelFactory {
        ManagedChannel create(String address);
    }
}
```

**设计要点：**

- `ConcurrentHashMap` + `computeIfAbsent`保证同一地址只创建一次Channel，免锁
- `ChannelFactory`是注入点，测试中可替换为in-process channel
- `closed`标志避免关闭后再创建新Channel（抛`IllegalStateException`）

### 3.2 RouteCache（路由表缓存）

**职责：**

- 首次访问某张表时向Master拉取完整`RegionRouteTable`
- 按`startKey`升序组织路由条目，支持点查（`findForKey`）和范围查询（`findForRange`）
- 路由失效时一键淘汰（`invalidate(table)`）

**核心API：**

```java
public class RouteCache {
    public RouteEntry lookup(String table, ByteString key);                 // 点查
    public List<RouteEntry> lookupRange(String table, ByteString s, ByteString e);  // 范围
    public void invalidate(String tableName);
    public void invalidateAll();
    public RegionRouteTable refresh(String tableName);
}
```

**关键算法：**

- **无符号字节比较：**`compareUnsigned(ByteString a, ByteString b)`逐字节 & 0xFF 对齐，确保`ByteString`作为主键编码时排序与RegionServer侧一致
- **区间判定：**`[startKey, endKey)`，空`startKey`视为负无穷，空`endKey`视为正无穷
- **范围查询：**线性扫描`entries`，收集所有与`[qStart, qEnd)`重叠的条目 —— Region数量通常不大，O(n)足够；后续可优化为二分

**失效与刷新：**

- `MiniSQLClient`发现Region返回`ERROR_STALE_ROUTE`时调用`invalidate(table)`并重拉
- RPC异常（`RuntimeException`）包装为`MiniSQLClientException(ERROR_UNAVAILABLE)`抛给调用方

### 3.3 TableSchemaCache（表结构缓存）

**职责：**

- 缓存表的`TableSchema`（列名、类型、主键），SQL执行时用于编解码和谓词构造
- 未命中走`ClientMasterService.getTableSchema`拉取

**核心API：**

```java
public class TableSchemaCache {
    public TableSchema get(String tableName);       // 未命中则加载
    public void invalidate(String tableName);
    public void invalidateAll();
}
```

**设计要点：**

- 与`RouteCache`完全对称：`ConcurrentHashMap`缓存、按需加载、可显式失效
- Schema变更场景（DDL）由上层调用`invalidate`驱动；MVP暂不支持Schema热更新推送

### 3.4 MiniSQLClient（KV门面）

**职责：**

- 对外提供与HBase风格一致的KV API：`put / get / delete / exists / scan`
- 封装路由查找 → 建立连接 → 发起gRPC调用 → 路由失效重试的完整链路
- 拥有`RouteCache`、`ConnectionManager`、`ParallelScanner`与扫描线程池的生命周期

**核心API：**

```java
public class MiniSQLClient implements AutoCloseable {
    public static MiniSQLClient connect(String masterAddress);

    public PutResult    put(String table, ByteString key, Map<String, ByteString> columns);
    public GetResult    get(String table, ByteString key, List<String> columns);
    public DeleteResult delete(String table, ByteString key);
    public boolean      exists(String table, ByteString key);

    public List<ScanRow> scan(String table, ByteString startKey, ByteString endKey,
                              int limit, List<String> columns);
    public List<ScanRow> scan(String table, ByteString startKey, ByteString endKey,
                              int limit, List<String> columns, String filter);
}
```

**路由重试机制：**

点写类操作（put / get / delete / exists）共用一套`callWithRouteRetry`模板：

```
1. RouteCache.lookup(table, key)  → RouteEntry
2. 向 route.primaryAddress 发起RPC
   2a. 抛出 RuntimeException → invalidate(table) 并抛 ERROR_UNAVAILABLE
3. 若响应 errorCode == ERROR_STALE_ROUTE：
   3a. invalidate(table)
   3b. 重新 lookup，再发一次请求（只重试一次）
4. 返回最终响应
```

**扫描路径：**

- 先`lookupRange`定位命中的Region列表
- 命中0条：返回空
- 命中1条：当前线程直接流式读取`ScanResponse`
- 命中多条：交由`ParallelScanner`并发

**资源管理：**

- `scanExecutor`默认为`min(CPU * 2, 16)`的固定线程池，守护线程不阻塞JVM退出
- `close()`顺序：关闭Channel池 → 关闭扫描线程池（5秒超时）→ 关闭Master Channel（若由客户端自行建立）
- 所有权通过`ownsMasterChannel` / `ownsScanExecutor`布尔精确追踪，避免重复关闭外部注入的资源

### 3.5 ParallelScanner（多Region并行扫描）

**职责：**

- 把`scan`拆分为每Region一个`CompletableFuture`任务，利用`scanExecutor`并发执行
- 按路由顺序合并结果，尊重整体`limit`

**执行流程：**

```
for each route in routes:
    future[i] = supplyAsync(scanOneRegion(route), executor)
allOf(futures).join()          # 等待全部完成
remaining = limit > 0 ? limit : ∞
for each future (按 routes 顺序):
    取 regionRows, 逐行填入 merged，直到 remaining 用尽
return merged
```

**异常处理：**

- 任一分片失败，`CompletableFuture.join()`抛`CompletionException`
- 原因若为`MiniSQLClientException`则原样抛出（保留`ErrorCode`）
- 其他异常包装为`MiniSQLClientException(ERROR_UNAVAILABLE)`
- MVP语义：**一片失败 = 整次scan失败**，不做部分结果返回；后续可加`allowPartial`开关

**Limit处理：**

- 下推到每个Region的`perRegionLimit`取整体`limit`（宁多勿少，方便合并时裁剪）
- 结果按**Route顺序**拼接，这样当`startKey`跨越多个Region时，外部顺序感知与单Region扫描一致

### 3.6 SqlExecutor（SQL 门面）

**职责：**

- 用JSqlParser解析SQL，分发到`executeInsert / executeSelect / executeDelete`
- 把WHERE编译为`Predicate`，萃取主键范围，构造下推`filter`，然后调`MiniSQLClient.scan`
- JOIN语句委派给`JoinExecutor`

**支持范围：**

| 语句 | 支持 | 说明 |
| --- | --- | --- |
| `INSERT ... VALUES (...)` | ✅ | 必须显式列名，必须含主键 |
| `SELECT ... WHERE pk = ?` | ✅ | 走点查`get` |
| `SELECT ... WHERE ...` | ✅ | 范围扫描 + 客户端过滤（支持ORDER BY/LIMIT/OFFSET）|
| `SELECT ... FROM a JOIN b ON a.x=b.y` | ✅ | INNER JOIN，单个等值ON |
| `DELETE WHERE pk = ?` | ✅ | 点删 |
| `DELETE WHERE ...` | ✅ | 扫描后逐行删（必须有WHERE） |
| `UPDATE / 多表 JOIN / 子查询` | ❌ | 抛`ERROR_UNIMPLEMENTED` |

**SELECT主路径：**

```
1. 解析为 PlainSelect
2. 若含 JOIN → JoinExecutor
3. 提取投影列（* 展开为所有列）
4. WHERE → Predicate
5. 若 WHERE 是 pk = <literal>：走 client.get()  [点查捷径]
6. 否则：
   a. PkRangeExtractor 从 Predicate 萃取 [start, end)
   b. FilterSerializer 序列化 predicate 为 filter 字符串（RegionServer 侧过滤）
   c. client.scan(...)
   d. 客户端兜底：用 predicate 再过滤一次（防御 filter 下推不完整）
   e. ORDER BY / LIMIT / OFFSET → OrderByApplier
```

**下推 + 兜底的策略：**

`filter`尽量下推到RegionServer减少网络传输，但客户端侧仍然做一次完整`predicate.test(decoded)`兜底。这样既享受下推的性能红利，又不依赖RegionServer对所有谓词形态都正确实现——RegionServer如果遇到不识别的filter可以选择放行（返回全部），客户端兜底过滤仍然保证结果正确。

### 3.7 JoinExecutor（Hash INNER JOIN）

**职责：**

- 实现两表等值INNER JOIN，采用客户端Hash Join算法
- 把WHERE按归属切分，**每一侧的谓词各自下推**到对应表的scan，减少数据拉取

**执行流程：**

```
plan = analyze(PlainSelect)          # 左/右表、ON列、投影列
split = JoinPredicateSplitter.split(where, leftSchema, rightSchema)
                                     # leftOnly / rightOnly / postJoin

leftFut  = async scanAll(leftTable,  filter=split.leftOnly)
rightFut = async scanAll(rightTable, filter=split.rightOnly)

buildSide = group leftRows by row[ON-left-col]
for each rightRow:
    for each leftMatch in buildSide[rightRow[ON-right-col]]:
        if split.postJoin.test(joined):  # 无法归属某一边的谓词在 JOIN 后再过滤
            emit project(left, right)

apply ORDER BY / LIMIT / OFFSET
```

**关键决策：**

- **左为 build side，右为 probe side**：`PlainSelect.fromItem`作为build side，`Join.rightItem`作为probe side。未来可根据scan行数统计做代价决策。
- **ON 列类型兼容：**`ValueCodec.findColumn`拿到两端列Schema，用`normalize(type)`归一（INT家族→INT，VARCHAR/CHAR/TEXT→STR，FLOAT/DOUBLE→DBL）后比对，避免`INT vs BIGINT`误判为不兼容
- **投影解析：**支持`a.x`、`alias.y`、非歧义裸列、`*`展开；对歧义裸列显式报错，避免SQL语义不明
- **仅支持单个ON等值：**多于一条ON表达式或非`EqualsTo`直接抛`ERROR_UNIMPLEMENTED`（教学项目定位，避免过早引入复杂度）

### 3.8 Gateway Server（多语言接入）

**职责：**

- 把`MiniSQLClient + SqlExecutor`封装为一个gRPC服务`GatewayService`对外暴露
- 非Java客户端（C/C++、Python）只需生成`gateway.proto`的桩即可接入，不用复刻路由/重试/Join等复杂逻辑

**对外接口（摘要）：**

```protobuf
service GatewayService {
    rpc Execute(ExecuteRequest) returns (ExecuteResponse);  // 统一执行 SQL
    rpc Ping(PingRequest) returns (PingResponse);           // 版本 / 服务器时间
}

message ExecuteResponse {
    bool success = 1;
    ErrorCode error_code = 2;
    string error_message = 3;
    oneof result {
        QueryResult query = 4;   // SELECT / JOIN
        UpdateResult update = 5; // INSERT / DELETE
    }
}
```

**类型化结果：**

`QueryResult.Row`中每个值是`TypedValue`（`oneof`封装int64 / double / string / bytes / bool / null），在Gateway内部由`ValueCodec`解码后转换，避免下游客户端面对原始`ByteString`再做一次解码。

**错误转译：**

- `MiniSQLClientException` → `success=false` + 原始`ErrorCode` + message
- 未知`RuntimeException` → `ERROR_INTERNAL`并记录WARN日志
- 始终保证`onNext` + `onCompleted`，即使出错也不让流悬挂

---

## 4. 接口设计

### 4.1 依赖的 Master 侧 gRPC

```protobuf
service ClientMasterService {
    rpc GetRouteTable(GetRouteTableRequest)     returns (GetRouteTableResponse);
    rpc GetTableSchema(GetTableSchemaRequest)   returns (GetTableSchemaResponse);
    rpc CreateTable(CreateTableRequest)         returns (CreateTableResponse);   // by demo/CLI
}
```

- `GetRouteTable`：批量返回某张表所有Region的`[startKey, endKey)`与主/副本地址
- `GetTableSchema`：返回列定义与主键名，供SQL层编解码

### 4.2 依赖的 RegionServer 侧 gRPC

```protobuf
service RegionServerService {
    rpc Put(PutRequest)       returns (PutResponse);
    rpc Get(GetRequest)       returns (GetResponse);
    rpc Delete(DeleteRequest) returns (DeleteResponse);
    rpc Exists(ExistsRequest) returns (ExistsResponse);
    rpc Scan(ScanRequest)     returns (stream ScanResponse);  // 服务端流
}
```

- 每个请求都带`region_id`，RegionServer据此路由到本地Region，不匹配则返回`ERROR_STALE_ROUTE`
- `Scan`采用服务端流式，按行边扫边推，客户端按`limit`裁剪

### 4.3 Client 对外暴露的 gRPC（Gateway）

```protobuf
service GatewayService {
    rpc Execute(ExecuteRequest)  returns (ExecuteResponse);
    rpc Ping(PingRequest)        returns (PingResponse);
}
```

### 4.4 Java Public API

| 类 | 关键方法 | 使用场景 |
| --- | --- | --- |
| `MiniSQLClient` | `put / get / delete / exists / scan` | 低层KV，需要极致性能时使用 |
| `SqlExecutor` | `execute(String sql)` | 嵌入式SQL |
| `GatewayServer` | `start() / awaitTermination()` | 多语言接入 |
| `MiniSQLClientException` | `getErrorCode()` | 统一错误识别 |

---

## 5. 数据模型

### 5.1 路由与Region元数据

**RouteEntry（共享proto，Client侧只消费）**

```
regionId       : string
tableName      : string
startKey       : bytes    # 空串表示 -∞
endKey         : bytes    # 空串表示 +∞
primaryAddress : string   # "host:port"
replicaAddresses : repeated string
version        : int64
```

**SortedRouteTable（RouteCache内部）**

```java
class SortedRouteTable {
    RegionRouteTable raw;        // 原始响应，含 version
    List<RouteEntry> entries;    // 按 startKey 无符号升序
}
```

### 5.2 SQL 结果统一载体

**SqlResult**

```java
class SqlResult {
    enum Kind { ROWS, UPDATE }
    Kind kind;
    List<String> columns;                 // ROWS: 列序
    List<Map<String, Object>> rows;       // ROWS: 值按列名索引
    int updateCount;                      // UPDATE: 受影响行数
}
```

**DecodedRow**

```java
class DecodedRow {
    TableSchema schema;
    ByteString key;                       // 主键原始编码
    Map<String, ByteString> rawColumns;   // 未解码值，JOIN 键比较用
    Map<String, Object> columns;          // 已解码值，谓词 & 投影用
}
```

### 5.3 KV 结果

- `PutResult(success, sequenceId, errorCode, errorMessage)`
- `GetResult(found, columns, timestamp, errorCode, errorMessage)`
- `DeleteResult(success, existed, sequenceId, errorCode, errorMessage)`
- `ScanRow(key, columns, timestamp)`

---

## 6. 并发控制

### 6.1 线程安全策略

**数据结构选择：**

- `ConcurrentHashMap`：`ConnectionManager.channels`、`RouteCache.cache`、`TableSchemaCache.cache` —— 读多写少场景下无锁读取
- `computeIfAbsent`：保证同一地址/表只加载一次

**无锁热路径：**

- KV请求链路全程免锁：路由查找、Schema读取、Channel复用都走`ConcurrentHashMap`
- 仅缓存加载本身存在竞争窗口（可能多个线程并发发起同一表的加载），但由于最终结果幂等 + `put`覆盖语义，代价可接受；不做double-checked locking，避免增加复杂度

### 6.2 扫描线程池

**配置：**

- 线程数：`min(CPU核数 * 2, 16)`
- 守护线程：是（避免阻塞JVM退出）
- 命名：`minisql-scan-{id}`，便于排查

**使用方：**

- `ParallelScanner`：跨Region分片
- `JoinExecutor`：左右两表并行扫描（共用同一线程池）

**隔离策略：**

- Join的两表扫描和外部`scan`共享同一个池：教学项目规模下不会互相阻塞；生产场景可通过`MiniSQLClient(masterChannel, connectionManager, executor)`构造器注入专用池。

### 6.3 关闭顺序

```
MiniSQLClient.close():
  1. ConnectionManager.close()   # 关闭所有 RegionServer Channel
  2. 若 owns scanExecutor：shutdown → 5s 等待 → shutdownNow
  3. 若 owns masterChannel：shutdown
```

**原则：** 先关下游（RegionServer连接），再停线程池，最后关Master连接。避免扫描任务在Channel已关时还尝试RPC。

---

## 7. 容错与一致性

### 7.1 路由漂移（Region迁移 / Split）

**场景：** Master触发Region迁移或Split后，客户端缓存的路由信息过期，请求打到错误的RegionServer。

**处理机制：**

```
RegionServer 识别 region_id 不匹配 → 返回 ERROR_STALE_ROUTE
   ↓
MiniSQLClient.callWithRouteRetry 捕获 → RouteCache.invalidate(table)
   ↓
重新 lookup（触发 GetRouteTable RPC）→ 用新路由重试一次
   ↓
仍失败 → 抛给调用方（避免无限循环）
```

**边界：**

- 只重试一次，避免Region连续迁移场景下陷入死循环
- 仅对`ERROR_STALE_ROUTE`触发，其他错误码原样返回，保留业务语义

### 7.2 RegionServer不可达

**场景：** 单台RegionServer崩溃，gRPC调用抛`StatusRuntimeException`。

**处理机制：**

- `callWithRouteRetry`捕获`RuntimeException` → `RouteCache.invalidate(table)` → 抛`ERROR_UNAVAILABLE`
- 下一次请求会重新拉路由，Master此时已完成故障转移并更新副本表，客户端自动切到新主

**不做本地failover的原因：**

- 副本主从切换由Master+ReplicationManager裁决，客户端盲目重试副本可能读到未同步数据，破坏一致性

### 7.3 Master不可达

**场景：** Master单点短暂不可用（选举切换窗口）。

**处理机制：**

- **已缓存路由的表：**读写完全不受影响，沿用旧路由直连RegionServer
- **未缓存或需要refresh的表：**`RouteCache.load`抛`ERROR_UNAVAILABLE`，由应用侧决定是否重试

**一致性取舍：**

客户端不做Master高可用封装（如多Master并发探活）—— 交给gRPC层的重试策略和应用侧超时机制，避免和Master选举机制产生竞态。

### 7.4 扫描结果一致性

- **快照语义：** 客户端scan不保证跨Region快照，每个Region内部由RegionServer决定一致性（默认读主）
- **重复与丢失：** 迁移窗口内若发生Split，同一条记录不会被重复读取（Region归属唯一）；若发生Region在扫描中途下线，会返回`ERROR_STALE_ROUTE`触发整次失败，不做续扫
- **JOIN的外部一致性：** 左右两表扫描可能处于不同瞬时，JOIN结果为"准一致性"，教学场景下可接受

---

## 8. 测试设计

### 8.1 单元测试

**现有测试文件：**

| 测试类 | 覆盖点 |
| --- | --- |
| `ConnectionManagerTest` | Channel复用、关闭后拒绝、注入工厂 |
| `RouteCacheTest` | 点查/范围查找、无符号比较、失效与重拉、空边界 |
| `MiniSQLClientTest` | put/get/delete/scan基本路径、STALE_ROUTE一次重试 |
| `ValueCodecTest` | INT/BIGINT/STRING/DOUBLE编解码、主键编码 |
| `SqlExecutorTest` | INSERT/SELECT/DELETE、WHERE组合、ORDER BY/LIMIT/OFFSET |
| `JoinExecutorTest` | INNER JOIN成功 & 各类不支持场景的错误 |
| `NonPkWhereTest` | 非主键WHERE条件、下推+兜底双重过滤 |
| `GatewayServiceImplTest` | Execute成功路径、ErrorCode透传、异常兜底 |

**Mock策略：**

- Master/RegionServer gRPC桩用Mockito mock，或用`InProcessServerBuilder`建In-process server
- `ConnectionManager`在测试中注入in-process`ChannelFactory`，绕过真实TCP

**覆盖率目标：** 与项目整体要求一致（>70%，关键路径>85%）

### 8.2 集成测试（规划）

**Fast层（无外部依赖）：**

- `SqlEndToEndFastTest`：In-process Master + 2个Fake RegionServer，跑完整INSERT/SELECT/DELETE
- `JoinEndToEndFastTest`：两表Join全链路
- `StaleRouteRecoveryTest`：模拟Region迁移后客户端自动切换

**E2E层（Testcontainers）：**

- `ParallelScanE2ETest`：真实gRPC通信，多Region并行扫描
- `GatewayE2ETest`：Python客户端通过Gateway调用Execute

**Stress层：**

- `ConcurrentKvStressTest`：多线程put/get压测，验证Channel池与RouteCache无竞争问题
- `RouteChurnStressTest`：持续触发路由失效，验证客户端稳定性

### 8.3 测试基础设施

- `FakeRegionServer`：可编程响应的RegionServer桩，支持注入`ERROR_STALE_ROUTE`等错误
- `FakeMasterService`：提供预置的路由表和Schema
- 共享`minisql-common/src/test`下的fixtures（与Master集成测试复用）

---

## 9. 性能优化

### 9.1 网络层优化

- **Channel复用：** 单客户端全局共享`ManagedChannel`，HTTP/2多路复用天然摊薄开销
- **keepAlive：** 30s心跳保活，防止NAT/防火墙断连时首次请求付出重连成本
- **服务端流式Scan：** 避免单次响应承载大结果集，内存占用稳定

### 9.2 查询路径优化

- **点查捷径：** `WHERE pk = ?`绕过scan直接走`get`，单次RTT
- **主键范围萃取：** `PkRangeExtractor`从WHERE中提取`[start, end)`，缩小scan区间
- **谓词下推：** `FilterSerializer`把谓词翻译为RegionServer可识别的filter字符串
- **并行扫描：** `ParallelScanner`让跨Region的scan延迟接近单Region最慢的那一片

### 9.3 Join优化

- **谓词按表切分：** `JoinPredicateSplitter`把WHERE拆成`leftOnly / rightOnly / postJoin`，各自下推
- **Hash Join：** O(n+m)复杂度，左表建Hash、右表探测
- **左右并行扫描：** 两表的全扫描在`scanExecutor`中并发，延迟接近单表最慢的那一侧
- **未来方向：**
  - 基于行数统计选择build side
  - Sort-Merge Join（当两表都已按JOIN列有序时）
  - 谓词下推到RegionServer层的计算下推（下推ON列）

### 9.4 内存优化

- `DecodedRow`同时持有raw与decoded两份，权衡：raw用于JOIN键等值比较（零解码开销），decoded用于谓词求值与投影
- 大结果集场景下考虑换成迭代器式SqlResult，避免一次性materialize——MVP暂不做

---

## 10. 部署架构

### 10.1 部署拓扑

**嵌入式（Java应用直连）：**

```
┌────────────────┐
│   应用 JVM     │
│  ┌──────────┐  │
│  │MiniSQL   │  │───► Master (gRPC)
│  │Client    │  │
│  └──────────┘  │───► RegionServer-1..N (gRPC)
└────────────────┘
```

- 最低延迟，无中间跳
- 适用于：Java应用、内部服务

**Gateway 模式（多语言接入）：**

```
┌──────────────┐    ┌──────────────────┐
│ Python / C++ │───►│  Gateway Server  │───► Master + RegionServer
│ Client       │    │  (Java, gRPC)    │
└──────────────┘    └──────────────────┘
```

- 适用于：非Java语言、Web服务、BI工具
- Gateway可水平扩展，前置负载均衡器即可

### 10.2 启动配置

**嵌入式：**

```java
try (MiniSQLClient client = MiniSQLClient.connect("master-1:8000")) {
    SqlExecutor sql = new SqlExecutor(client, new TableSchemaCache(...));
    SqlResult result = sql.execute("SELECT id, name FROM users WHERE id < 100");
    // ...
}
```

**Gateway：**

```bash
java -cp minisql-client.jar com.minisql.client.gateway.GatewayServer \
  --master=master-1:8000,master-2:8001,master-3:8002 \
  --port=9090
```

### 10.3 配置项（规划）

```properties
# 连接
client.master.addresses=master-1:8000,master-2:8001
client.connection.keepalive=30s

# 扫描
client.scan.executor.threads=16
client.scan.per-region.limit.multiplier=1

# 路由
client.route.cache.refresh-on-stale=true
client.route.cache.background-refresh=false

# Gateway
gateway.port=9090
gateway.max.inbound.message=4194304
```

---

## 11. 监控和运维

### 11.1 关键指标

**请求指标：**

- `client.kv.{put|get|delete|scan}.count / latency`
- `client.sql.execute.count / latency / error_by_code`
- `client.gateway.execute.{qps, p99}`

**缓存指标：**

- `client.route.cache.{size, hit, miss, invalidate}`
- `client.schema.cache.{size, hit, miss}`

**连接指标：**

- `client.connection.channel.count`
- `client.connection.rpc.failure_by_type`

**故障指标：**

- `client.route.stale.retry.count` —— 路由漂移频率
- `client.scan.region.failure.count` —— 并行扫描分片失败

### 11.2 日志规范

与项目统一：`[timestamp] [level] [component] [thread] message`

**关键日志点：**

- `DEBUG`：路由加载、谓词下推结果、Hash Join构建和输出大小
- `INFO`：Gateway启停、Master连接建立
- `WARN`：路由失效重试、意外异常
- `ERROR`：Gateway级未捕获异常

### 11.3 故障排查

| 现象 | 可能原因 | 排查 |
| --- | --- | --- |
| `ERROR_REGION_NOT_FOUND` | 表未创建 / Region未上线 | 检查Master`GetRouteTable`返回、确认`CreateTable`已成功 |
| `ERROR_STALE_ROUTE`频繁重试 | Region正在迁移 / Split | 看Master迁移日志，确认是否处于不稳定期 |
| `ERROR_UNAVAILABLE` | RegionServer崩溃 / 网络分区 | 看RegionServer心跳状态、Channel池日志 |
| Join结果偏少 | ON列类型不兼容被归一化错误 | 检查两表列Schema、`ValueCodec.normalize`类别 |
| Scan慢 | Region数量少导致并行度不足 / 未下推filter | 看`FilterSerializer`是否生成有效filter |

---

## 12. 总结

### 12.1 完成情况

**已完成模块：**

- ✅ `ConnectionManager` —— gRPC Channel池
- ✅ `RouteCache` —— 路由缓存 + 范围查找
- ✅ `TableSchemaCache` —— 表结构缓存
- ✅ `MiniSQLClient` —— KV门面 + 路由重试
- ✅ `ParallelScanner` —— 多Region并行扫描
- ✅ `SqlExecutor` —— INSERT/SELECT/DELETE 解析与执行
- ✅ `JoinExecutor` —— 两表INNER JOIN（Hash Join）
- ✅ `GatewayServer` —— 多语言接入gRPC服务
- ✅ 值编解码、谓词下推、ORDER BY/LIMIT/OFFSET

**测试覆盖：**

- ✅ 单元测试覆盖所有核心类
- ✅ SQL 执行、JOIN、非主键WHERE等关键场景已有用例
- ⏳ 集成测试基础设施与Master侧复用中

### 12.2 技术亮点

1. **路由自愈：** `ERROR_STALE_ROUTE`单次重试，对Region迁移无感
2. **双重保险过滤：** 下推到RegionServer + 客户端兜底，既优化性能又保证语义正确
3. **并行扫描：** 跨Region任务用`CompletableFuture.allOf`编排，延迟接近最慢分片
4. **Hash Join + 谓词切分：** 左右表各自下推谓词，减少网络传输
5. **Gateway抽象：** 一套gRPC接口屏蔽Java内部复杂度，多语言零门槛接入

### 12.3 后续工作

**功能增强：**

- UPDATE语句支持
- 多表JOIN / LEFT / RIGHT OUTER JOIN
- 子查询与聚合（GROUP BY、COUNT/SUM/AVG）
- Prepared Statement和参数化查询

**性能优化：**

- 路由表背景刷新（不等待失败才重拉）
- Join代价估算决定build side
- scan结果流式化，避免全量materialize
- Gateway连接池 & 会话管理

**多语言客户端：**

- Python SDK（基于Gateway proto）
- C/C++ SDK（基于Gateway proto）
- 文档和示例

**可观测性：**

- 接入Micrometer / Prometheus指标
- OpenTelemetry Trace贯穿Client → Master → RegionServer

---

## 附录

### A. 代码仓库

- GitHub: https://github.com/oi35/Distributed_MiniSQL
- Client模块: `minisql-client/`
- 测试代码: `minisql-client/src/test/`

### B. 联系方式

- 模块负责人：冯俊翰（客户端与分布式查询）
- GitHub Issues: https://github.com/oi35/Distributed_MiniSQL/issues



