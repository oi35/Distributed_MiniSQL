# 分布式 MiniSQL 系统总体设计报告

**文档版本：** v1.0
**创建日期：** 2026-05-13
**负责人：** 刘一鸣（成员5）— 总体设计报告汇总

---

## 目录

1. [概述](#1-概述)
2. [团队分工](#2-团队分工)
3. [系统总体架构](#3-系统总体架构)
4. [Master 模块设计](#4-master-模块设计)
5. [RegionServer 模块设计](#5-regionserver-模块设计)
6. [副本管理与一致性协议模块设计](#6-副本管理与一致性协议模块设计)
7. [Client 模块设计](#7-client-模块设计)
8. [测试体系与工具](#8-测试体系与工具)
9. [部署架构](#9-部署架构)
10. [系统总览与总结](#10-系统总览与总结)

---

## 1. 概述

### 1.1 项目背景

Distributed MiniSQL 是一个教学用途的分布式关系型数据库系统，采用 **Master-RegionServer** 两层架构，支持数据分片、副本管理、负载均衡、分布式查询等核心功能。系统使用 MySQL 作为底层存储引擎，在其之上构建分布式层，通过 gRPC + Protobuf 实现各节点间的通信。

### 1.2 系统设计目标

**功能目标：**
- 支持数据分片（Range-Based Partitioning）和分布式存储
- 支持多副本复制和强一致性保证（Paxos 共识协议）
- 支持 Master 高可用和故障自动转移
- 支持负载均衡和 Region 在线迁移
- 支持分布式查询（并行扫描、Hash Join）
- 支持多语言接入（Gateway gRPC 服务）

**性能目标：**
- 单表点查询端到端延迟 < 10ms
- Master 心跳处理延迟 < 50ms
- Master 选举时间 < 5s

**可靠性目标：**
- Master 可用性 > 99.9%
- 数据多副本强一致性
- 故障自动恢复

### 1.3 技术栈

| 技术 | 用途 |
|------|------|
| Java 11/17 | 开发语言 |
| gRPC + Protobuf 3 | RPC 通信与数据序列化 |
| Apache ZooKeeper 3.9 | 分布式协调、Master 选举、元数据存储 |
| Maven | 构建工具 |
| JUnit 4 + Mockito | 单元测试与 Mock |
| MySQL (JDBC/HikariCP) | 持久化存储引擎 |
| SLF4J + Logback | 日志框架 |
| JSqlParser 4.x | SQL 解析 |

---

## 2. 团队分工

本系统由 5 人团队协作完成，各成员分工如下：

| 成员 | 角色 | 负责模块 | 核心职责 |
|------|------|---------|---------|
| **成员1** 李明睿 | Master 模块开发 | `minisql-master` | 集群管理、元数据管理、负载均衡、Region 迁移、Master 选举、ZooKeeper 集成 |
| **成员2** 李锋然 | RegionServer 模块开发 | `minisql-regionserver`（服务层+存储层） | 数据存储引擎、CRUD 操作、Region 生命周期管理、gRPC 服务实现 |
| **成员3** 周赫 | 副本管理与一致性协议 | `minisql-regionserver`（副本层） | WAL 日志系统、Paxos 共识协议、副本管理、日志复制服务 |
| **成员4** 冯俊翰 | 客户端与分布式查询 | `minisql-client` | 路由缓存、KV 门面、SQL 解析与执行、Hash Join、Gateway 多语言接入 |
| **成员5** 刘一鸣 | 总体设计负责人 / 测试与工具 | `minisql-admin` + 测试 + 文档 | 总体设计报告汇总、CLI 管理工具、测试框架、部署脚本、文档体系 |

系统代码模块依赖关系：

```
minisql-common (proto 定义，所有模块依赖)
    ├── minisql-master (成员1)
    ├── minisql-regionserver (成员2 + 成员3)
    └── minisql-client (成员4)
        └── minisql-admin (成员5)
```

---

## 3. 系统总体架构

### 3.1 系统架构总览

```
┌─────────────────────────────────────────────────────────────────────┐
│                       应用 / CLI / 多语言客户端                       │
│             Java API 直连          gRPC（Gateway）                   │
└────────────────────────┬──────────────────────────┬──────────────────┘
                         │                          │
┌────────────────────────▼──────────────────────────▼──────────────────┐
│                          Client 模块 (成员4)                          │
│   ┌──────────────┐   ┌──────────────┐   ┌────────────────────────┐  │
│   │ RouteCache   │   │  SqlExecutor │   │    Gateway Server      │  │
│   │ 路由缓存+重试│   │ SQL解析+执行 │   │  多语言接入 gRPC 服务  │  │
│   └──────┬───────┘   └──────┬───────┘   └──────────┬─────────────┘  │
│          │                  │                       │                │
│   ┌──────▼──────────────────▼───────────────────────▼──────────┐    │
│   │                 MiniSQLClient (KV 门面)                     │    │
│   │       put/get/delete/scan/exists + ParallelScanner         │    │
│   └────────────────────────────────┬───────────────────────────┘    │
└────────────────────────────────────┼─────────────────────────────────┘
                                     │
            ┌────────────────────────┼────────────────────────┐
            │                        │                        │
      ┌─────▼──────┐         ┌───────▼────────┐      ┌───────▼───────┐
      │  Master    │         │  RegionServer  │      │  RegionServer │
      │  (成员1)   │◄───────►│  (成员2+成员3) │......│  (成员2+成员3)│
      │ gRPC :8000 │ 心跳    │  gRPC :8001    │      │  gRPC :8002   │
      └─────┬──────┘  注册   └────────────────┘      └───────────────┘
            │
      ┌─────▼──────┐
      │ ZooKeeper  │
      │ :2181      │
      └────────────┘
```

### 3.2 模块依赖关系

```
LoadBalancer (成员1)
    ↓ 依赖
RegionMigrationManager (成员1)
    ↓ 依赖
ClusterManager (成员1) ←── HeartbeatMonitor (成员1)
    ↓ 依赖
ZookeeperClient (成员1) ←── MasterElection (成员1)

RegionServerServiceImpl (成员2)
    ├── RegionManager → Region → StorageEngine (MySQL/Memory)
    ├── WalService (成员3)
    ├── PaxosProposer / PaxosAcceptor (成员3)
    ├── ReplicationManager (成员3)
    └── ReplicationLogService (成员3)

MiniSQLClient (成员4)
    ├── RouteCache ←── ClientMasterService (Master gRPC)
    ├── ConnectionManager → RegionServerService (RS gRPC)
    ├── SqlExecutor → JSqlParser
    └── JoinExecutor (Hash Join)
```

### 3.3 核心数据流

以下时序图展示了一次写操作从客户端到存储的完整数据流：

```mermaid
sequenceDiagram
    participant Client as Client (成员4)
    participant Master as Master (成员1)
    participant RS as RegionServer Primary (成员2)
    participant WAL as WalService (成员3)
    participant Paxos as PaxosProposer (成员3)
    participant Replica as 远程副本 RS (成员3)
    participant Store as 存储引擎 (成员2)

    Client->>Master: GetRouteTable(table, key)
    Master-->>Client: RouteEntry(regionId, primaryAddr)

    Client->>RS: Put(regionId, key, columns)

    Note over RS,Store: Step 1: WAL-First
    RS->>WAL: append(regionId, PUT, key, columns)
    WAL-->>RS: WalRecord(seq, checksum)

    Note over RS,Paxos: Step 2: Paxos 共识
    RS->>Paxos: proposeWithRetry(regionId, record, replicas)
    Paxos->>Paxos: Prepare → Promise → Accept → Accepted → Commit
    Paxos-->>RS: COMMITTED

    Note over RS,Store: Step 3: 写入本地存储
    RS->>Store: insertRow(key, columns)
    Store-->>RS: ok

    Note over RS,Replica: Step 4: 异步副本复制
    RS->>Replica: applyReplicationLog(record)
    Replica-->>RS: applied

    RS-->>Client: PutResponse(success)
```

---

## 4. Master 模块设计

> 本节内容引用自 [member1-design.md](./member1-design.md)，由成员1（李明睿）完成。

### 4.1 模块定位

Master 模块是分布式 MiniSQL 的核心控制节点，负责集群管理、元数据管理、负载均衡和 Region 迁移、高可用和故障转移。

### 4.2 核心组件类图

```mermaid
classDiagram
    class MasterServer {
        +main(args: String[]): void
        -grpcServer: Server
        -clusterManager: ClusterManager
        -metadataManager: MetadataManager
        -loadBalancer: LoadBalancer
        -regionMigrationManager: RegionMigrationManager
        -zkClient: ZookeeperClient
        -masterElection: MasterElection
        -heartbeatMonitor: HeartbeatMonitor
        -failureRecoveryManager: FailureRecoveryManager
        +start(): void
        +stop(): void
    }

    class ClusterManager {
        -servers: ConcurrentHashMap~String, ServerInfo~
        +registerServer(serverId, host, port): void
        +updateHeartbeat(serverId, metrics): void
        +getOnlineServers(): List~ServerInfo~
        +selectLeastLoadedServer(): ServerInfo
    }

    class ServerInfo {
        -serverId: String
        -host: String
        -port: int
        -cpuUsage: double
        -memoryUsage: double
        -regionCount: int
        +getLoadScore(): double
    }

    class HeartbeatMonitor {
        -timeout: long
        +start(): void
        +stop(): void
    }

    class FailureRecoveryManager {
        +handleServerFailure(serverId): void
        +redistributeRegions(failedServer): void
    }

    class MetadataManager {
        -tables: ConcurrentHashMap~String, TableMetadata~
        -regions: ConcurrentHashMap~String, RegionMetadata~
        -routeTable: RouteTable
        +createTable(table): void
        +createRegion(region): void
        +findRegionForKey(table, key): String
    }

    class TableMetadata {
        -tableName: String
        -columns: List~String~
        -partitionColumn: String
        -replicationFactor: int
        -regionIds: List~String~
    }

    class RegionMetadata {
        -regionId: String
        -tableName: String
        -startKey: String
        -endKey: String
        -primaryServer: String
        -replicaServers: List~String~
        -state: RegionState
    }

    class LoadBalancer {
        -config: LoadBalancerConfig
        +needsBalance(): boolean
        +generateMigrationPlans(): List~MigrationPlan~
        +checkAndBalance(): void
    }

    class RegionMigrationManager {
        -tasks: ConcurrentHashMap~String, MigrationTask~
        +submitMigration(regionId, src, dst): String
        +getTask(migrationId): MigrationTask
        +cancelMigration(migrationId): boolean
    }

    class ZookeeperClient {
        +createNode(path, data, mode): void
        +getData(path): byte[]
        +exists(path): boolean
    }

    class MasterElection {
        +startElection(): void
        +isLeader(): boolean
    }

    MasterServer --> ClusterManager
    MasterServer --> MetadataManager
    MasterServer --> LoadBalancer
    MasterServer --> RegionMigrationManager
    MasterServer --> ZookeeperClient
    MasterServer --> MasterElection
    MasterServer --> HeartbeatMonitor
    MasterServer --> FailureRecoveryManager

    ClusterManager --> ServerInfo
    ClusterManager --> HeartbeatMonitor
    ClusterManager --> FailureRecoveryManager
    LoadBalancer --> RegionMigrationManager
    MasterElection --> ZookeeperClient
    MetadataManager --> ZookeeperClient
```

### 4.3 Master 启动与选举流程

```mermaid
sequenceDiagram
    participant M1 as Master-1
    participant M2 as Master-2
    participant ZK as ZooKeeper

    Note over M1,ZK: Step 1: 连接 ZooKeeper
    M1->>ZK: connect("localhost:2181")
    M2->>ZK: connect("localhost:2181")

    Note over M1,ZK: Step 2: 创建选举节点（临时顺序节点）
    M1->>ZK: create("/minisql/election/master-0000000001")
    ZK-->>M1: created
    M2->>ZK: create("/minisql/election/master-0000000002")
    ZK-->>M2: created

    Note over M1,ZK: Step 3: 获取所有节点，比较序号
    M1->>ZK: getChildren("/minisql/election")
    ZK-->>M1: [master-0000000001, master-0000000002]
    M1->>M1: 我是最小序号 → 成为 Leader
    M2->>ZK: getChildren("/minisql/election")
    ZK-->>M2: [master-0000000001, master-0000000002]
    M2->>M2: 我不是最小序号 → 监听前驱

    Note over M1: Leader 就绪
    M1->>M1: 从 ZK 恢复元数据
    M1->>M1: 启动 gRPC 服务 (port 8000)
    M1->>M1: 启动心跳监控

    Note over M2: 等待故障转移
    M2->>ZK: watch("/minisql/election/master-0000000001")
```

### 4.4 负载均衡与 Region 迁移

Master 的负载均衡器使用加权负载评分算法，当检测到节点间负载差异超过阈值时自动触发 Region 迁移。

**迁移状态机：**

```
PENDING → PREPARING → SYNCING → SWITCHING → COMPLETED
                ↓         ↓          ↓
              FAILED ← FAILED ← FAILED
                ↓
            ROLLING_BACK → ROLLED_BACK
```

**负载评分公式：**

```
loadScore = cpuUsage × 0.3 + memoryUsage × 0.3 + diskUsage × 0.4
```

详细设计请参见 [member1-design.md §3.3](./member1-design.md) 和 [member1-design.md §3.4](./member1-design.md)。

### 4.5 gRPC 接口

Master 提供两组 gRPC 服务：

- **MasterService**（面向 RegionServer）：注册、心跳、迁移控制
- **ClientMasterService**（面向 Client）：建表、路由查询、表信息

详细接口定义请参见 [member1-design.md §4](./member1-design.md)。

---

## 5. RegionServer 模块设计

> 本节内容引用自 [member2-design.md](./member2-design.md)，由成员2（李锋然）完成。

### 5.1 模块定位

RegionServer 是分布式 MiniSQL 系统的核心数据节点，负责实际的数据存储、查询执行以及 Region 的生命周期管理。

### 5.2 核心组件类图

```mermaid
classDiagram
    class RegionServerMain {
        +main(args: String[]): void
        -grpcServer: Server
        -service: RegionServerServiceImpl
        -regionServerId: String
        +start(): void
    }

    class RegionServerServiceImpl {
        -regionManager: RegionManager
        -walService: WalService
        -replicationLogService: ReplicationLogService
        -paxosProposer: PaxosProposer
        -replicationManager: ReplicationManager
        +put(request, observer): void
        +get(request, observer): void
        +delete(request, observer): void
        +scan(request, observer): void
        +openRegion(request, observer): void
        +closeRegion(request, observer): void
        +applyReplicationLog(request, observer): void
    }

    class RegionManager {
        -regions: Map
        +openRegion(info): Region
        +closeRegion(regionId): void
        +getRegion(regionId): Region
    }

    class Region {
        -regionId: String
        -tableName: String
        -startKey: String
        -endKey: String
        -state: RegionState
        -storage: StorageEngine
        +put(key, columns): void
        +get(key): Map
        +scan(startKey, endKey, limit): List
    }

    class StorageEngine {
        +put(key, columns): void
        +get(key): Map
        +delete(key): void
        +scan(startKey, endKey, limit): List
    }

    class MySqlStorage {
        -dataSource: HikariDataSource
        +put(key, columns): void
        +get(key): Map
        +delete(key): void
        +scan(startKey, endKey, limit): List
    }

    class MemoryStorage {
        -data: ConcurrentSkipListMap
        +put(key, columns): void
        +get(key): Map
        +delete(key): void
        +scan(startKey, endKey, limit): List
    }

    class HeartbeatScheduler {
        -masterStub: MasterServiceBlockingStub
        -intervalMs: long
        +start(): void
        +stop(): void
        +sendHeartbeat(): void
    }

    RegionServerMain --> RegionServerServiceImpl
    RegionServerServiceImpl --> RegionManager
    RegionServerServiceImpl --> HeartbeatScheduler

    RegionManager --> Region
    Region --> StorageEngine
    StorageEngine <|.. MySqlStorage
    StorageEngine <|.. MemoryStorage

    RegionServerServiceImpl --> WalService : 成员3
    RegionServerServiceImpl --> PaxosProposer : 成员3
    RegionServerServiceImpl --> ReplicationManager : 成员3
    RegionServerServiceImpl --> ReplicationLogService : 成员3
```

### 5.3 Region 生命周期状态机

```mermaid
stateDiagram-v2
    [*] --> CLOSED: 创建 Region 元数据
    CLOSED --> OPENING: Master 下发 OpenRegion
    OPENING --> ONLINE: 初始化存储 + WAL 恢复
    ONLINE --> CLOSING: Master 下发 CloseRegion/MigrateRegion
    CLOSING --> CLOSED: Flush 数据 + 关闭资源
    ONLINE --> SPLITTING: Region 大小超过阈值
    SPLITTING --> ONLINE: 分裂完成
    ONLINE --> OFFLINE: Master 检测心跳超时
    OFFLINE --> ONLINE: RegionServer 恢复
```

### 5.4 存储引擎设计

RegionServer 采用策略模式，支持两种存储后端：

- **MySQL 存储** (`MySqlStorage`)：使用 HikariCP 连接池，每个 Region 对应一张物理表
- **内存存储** (`MemoryStorage`)：使用 `ConcurrentSkipListMap`，适用于测试和快速原型

详细设计请参见 [member2-design.md §3.3](./member2-design.md)。

### 5.5 gRPC 接口

RegionServerService 提供的数据操作接口：

| 方法 | 描述 |
|------|------|
| `Put` | 插入或更新单行数据 |
| `Get` | 查询单行数据 |
| `Delete` | 删除单行数据 |
| `Scan` | 范围扫描（服务端流式） |
| `OpenRegion` | 加载 Region |
| `CloseRegion` | 卸载 Region |
| `ApplyReplicationLog` | 副本间同步 WAL |

详细接口定义请参见 [member2-design.md §4](./member2-design.md)。

---

## 6. 副本管理与一致性协议模块设计

> 本节内容引用自 [member3-design.md](./member3-design.md)，由成员3（周赫）完成。

### 6.1 模块定位

本模块位于 RegionServer 节点内部，承担副本管理、Paxos 共识协议和 WAL 日志系统的核心职责。

### 6.2 模块在 RegionServer 中的位置

```
┌─────────────────────────────────────────────────────────────┐
│                       RegionServer                           │
│  ┌──────────────────────────────────────────────────────┐  │
│  │               RegionServerServiceImpl                │  │
│  │         (gRPC 入口，协调所有子模块)                     │  │
│  └────┬──────────┬────────────┬────────────┬───────────┘  │
│       │          │            │            │                │
│  ┌────▼───┐ ┌───▼────┐ ┌────▼────┐ ┌─────▼──────┐         │
│  │ Store  │ │Replicat│ │  Paxos  │ │    WAL     │         │
│  │(MySQL/ │ │ Manager│ │Protocol │ │   System   │         │
│  │Memory) │ │        │ │         │ │            │         │
│  └────────┘ └───▲────┘ └────▲────┘ └─────▲──────┘         │
│                 │            │            │                │
│                 └────────────┴────────────┘                │
│                     成员3 负责范围                           │
└─────────────────────────────────────────────────────────────┘
```

### 6.3 核心组件类图

```mermaid
classDiagram
    class WalService {
        -sequenceGenerator: AtomicLong
        -memoryIndex: TreeMap~Long, WalRecord~
        -regionIndex: Map~String, TreeSet~Long~~
        -currentWalManager: WalManager
        +append(regionId, op, key, columns): WalRecord
        +getRecords(regionId, startSeq, endSeq): List~WalRecord~
        +loadAll(): List~WalRecord~
        +getLatestSequenceId(regionId): long
    }

    class WalManager {
        -walFile: Path
        +append(record): void
        +loadAll(): List~WalRecord~
    }

    class WalRecord {
        -sequenceId: long
        -regionId: String
        -operation: String
        -key: byte[]
        -columns: Map~String, byte[]~
        -checksum: byte[]
        +deserialize(data): WalRecord
    }

    class PaxosProposer {
        -localAcceptor: PaxosAcceptor
        -replicaChannels: Map~String, ManagedChannel~
        -proposalSequence: AtomicLong
        +propose(regionId, record, replicas): ConsensusResult
        +proposeWithRetry(regionId, record, replicas): ConsensusResult
        -broadcastPrepare(regionId, pn, replicas): Map
        -broadcastAccept(regionId, pn, value, replicas): Map
        -broadcastCommit(regionId, pn, replicas): void
    }

    class PaxosAcceptor {
        -highestPromised: Map~String, ProposalNumber~
        -lastAcceptedProposal: Map~String, ProposalNumber~
        -lastAcceptedValue: Map~String, byte[]~
        -lastCommittedProposal: Map~String, ProposalNumber~
        +handlePrepare(request): PromiseResponse
        +handleAccept(request): AcceptedResponse
        +handleCommit(request): boolean
        +resetRegion(regionId): void
    }

    class ProposalNumber {
        -sequenceNum: long
        -serverId: String
        +compareTo(other): int
    }

    class ReplicationManager {
        -regionReplicas: Map~String, List~String~~
        -watermarkMap: Map~String, Map~String, Long~~
        -failureCountMap: Map~String, Map~String, AtomicLong~~
        +setReplicasFromRegionInfo(regionId, primary, replicas): void
        +replicate(regionId, record): void
        +getMinWatermark(regionId): long
        +recoverReplica(regionId, addr, walService): boolean
    }

    class ReplicationLogService {
        -walService: WalService
        -regionStores: Map~String, RegionDataStore~
        -paxosAcceptor: PaxosAcceptor
        +handleGetReplicationLog(request, observer): void
        +handleApplyReplicationLog(request, observer): void
        -handlePaxosPrepare(request, observer): void
        -handlePaxosAccept(request, observer): void
        -handlePaxosCommit(request, observer): void
    }

    WalService --> WalManager
    WalService --> WalRecord
    PaxosProposer --> PaxosAcceptor
    ReplicationManager --> WalService
    ReplicationLogService --> PaxosAcceptor
    ReplicationLogService --> WalService
```

### 6.4 Paxos 三阶段共识流程

```mermaid
sequenceDiagram
    participant Proposer as PaxosProposer (Primary)
    participant Acceptor1 as PaxosAcceptor (自身)
    participant Acceptor2 as PaxosAcceptor (副本1)
    participant Acceptor3 as PaxosAcceptor (副本2)

    Note over Proposer,Acceptor3: Phase 1: Prepare
    Proposer->>Proposer: 生成提案编号 N = (seq++, serverId)
    Proposer->>Acceptor1: Prepare(N)
    Proposer->>Acceptor2: Prepare(N)
    Proposer->>Acceptor3: Prepare(N)

    alt N > highestPromised
        Acceptor1-->>Proposer: Promise(N, lastAcceptedValue)
        Acceptor2-->>Proposer: Promise(N, lastAcceptedValue)
    else N <= highestPromised
        Acceptor2-->>Proposer: Reject
    end

    Note over Proposer,Acceptor3: 收集多数派 Promise → 进入 Phase 2

    Note over Proposer,Acceptor3: Phase 2: Accept
    Proposer->>Acceptor1: Accept(N, value)
    Proposer->>Acceptor2: Accept(N, value)
    Proposer->>Acceptor3: Accept(N, value)

    alt N >= highestPromised
        Acceptor1-->>Proposer: Accepted(N)
        Acceptor2-->>Proposer: Accepted(N)
    end

    Note over Proposer,Acceptor3: 收集多数派 Accepted → 进入 Phase 3

    Note over Proposer,Acceptor3: Phase 3: Commit
    Proposer->>Acceptor1: Commit(N)
    Proposer->>Acceptor2: Commit(N)
    Proposer->>Acceptor3: Commit(N)
    Note over Acceptor1: 应用数据到存储引擎
    Note over Acceptor2: 应用数据到存储引擎
```

### 6.5 WAL-First 写操作完整流程

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant RS as RegionServer (主)
    participant WS as WalService
    participant PP as PaxosProposer
    participant PA as PaxosAcceptor (本地)
    participant RM as ReplicationManager
    participant Replica as 远程副本 RS

    Client->>RS: Put RPC

    Note over RS,WS: Step 1: WAL-First
    RS->>WS: append(regionId, PUT, key, columns)
    WS->>WS: 生成序列号 + MD5 校验和
    WS->>WS: 写入磁盘 WAL 文件
    WS-->>RS: WalRecord(seq, checksum)

    Note over RS,PA: Step 2: Paxos 共识
    RS->>PP: proposeWithRetry(regionId, record, replicas)

    rect rgb(220, 240, 255)
        Note over PP,Replica: Phase 1: Prepare
        PP->>PA: handlePrepare(N, regionId)
        PP->>Replica: Prepare(N) via gRPC
        PA-->>PP: Promise
        Replica-->>PP: Promise

        Note over PP,Replica: Phase 2: Accept
        PP->>PA: handleAccept(N, value)
        PP->>Replica: Accept(N, value) via gRPC
        PA-->>PP: Accepted
        Replica-->>PP: Accepted

        Note over PP,Replica: Phase 3: Commit
        PP->>PA: handleCommit(N)
        PP->>Replica: Commit(N) via gRPC
    end

    PP-->>RS: COMMITTED

    Note over RS: Step 3: 写入本地存储

    Note over RS,RM: Step 4: 异步副本复制
    RS->>RM: replicate(regionId, record)
    RM->>Replica: applyReplicationLog via gRPC
    RM->>RM: 更新水印

    RS-->>Client: PutResponse(success, seq)
```

详细设计请参见 [member3-design.md §3.2](./member3-design.md)（Paxos 共识协议）和 [member3-design.md §3.1](./member3-design.md)（WAL 日志系统）。

---

## 7. Client 模块设计

> 本节内容引用自 [member4-design.md](./member4-design.md)，由成员4（冯俊翰）完成。

### 7.1 模块定位

Client 模块是 Distributed MiniSQL 面向上层应用的接入层，负责屏蔽集群拓扑、提供两层 API（低层 KV 与高层 SQL）、分布式查询执行和多语言支持。

### 7.2 核心组件类图

```mermaid
classDiagram
    class MiniSQLClient {
        -routeCache: RouteCache
        -connectionManager: ConnectionManager
        -schemaCache: TableSchemaCache
        -scanExecutor: ExecutorService
        +connect(masterAddress): MiniSQLClient
        +put(table, key, columns): PutResult
        +get(table, key, columns): GetResult
        +delete(table, key): DeleteResult
        +scan(table, start, end, limit, columns): List~ScanRow~
    }

    class ConnectionManager {
        -channels: Map~String, ManagedChannel~
        +getChannel(address): ManagedChannel
        +close(): void
    }

    class RouteCache {
        -cache: Map~String, SortedRouteTable~
        +lookup(table, key): RouteEntry
        +lookupRange(table, start, end): List~RouteEntry~
        +invalidate(table): void
        +refresh(table): RegionRouteTable
    }

    class TableSchemaCache {
        -cache: Map~String, TableSchema~
        +get(tableName): TableSchema
        +invalidate(tableName): void
    }

    class ParallelScanner {
        +scan(routes, start, end, limit, columns): List~ScanRow~
    }

    class SqlExecutor {
        -client: MiniSQLClient
        -schemaCache: TableSchemaCache
        +execute(sql): SqlResult
        -executeInsert(stmt): SqlResult
        -executeSelect(stmt): SqlResult
        -executeDelete(stmt): SqlResult
    }

    class JoinExecutor {
        -client: MiniSQLClient
        -schemaCache: TableSchemaCache
        +executeJoin(select, leftSchema, rightSchema): SqlResult
        -buildHashTable(rows, keyColumn): Map
        -probeHashTable(hashTable, rows, keyColumn): List
    }

    class GatewayServer {
        -grpcServer: Server
        -gatewayService: GatewayServiceImpl
        +start(port): void
    }

    class GatewayServiceImpl {
        -sqlExecutor: SqlExecutor
        +execute(request, observer): void
        +ping(request, observer): void
    }

    MiniSQLClient --> RouteCache
    MiniSQLClient --> ConnectionManager
    MiniSQLClient --> TableSchemaCache
    MiniSQLClient --> ParallelScanner

    SqlExecutor --> MiniSQLClient
    SqlExecutor --> JoinExecutor

    GatewayServer --> GatewayServiceImpl
    GatewayServiceImpl --> SqlExecutor
```

### 7.3 SQL 执行流程

```mermaid
sequenceDiagram
    participant App as 应用程序
    participant Exec as SqlExecutor
    participant Parser as JSqlParser
    participant Client as MiniSQLClient
    participant Cache as RouteCache
    participant RS as RegionServer

    App->>Exec: execute("SELECT id, name FROM users WHERE age > 18 LIMIT 10")

    Exec->>Parser: parse(sql)
    Parser-->>Exec: PlainSelect
    Exec->>Exec: 提取投影列 + WHERE → Predicate

    alt 点查捷径 (WHERE pk = ?)
        Exec->>Client: get("users", key)
        Client->>Cache: lookup("users", key)
        Cache-->>Client: RouteEntry(region-1, rs-001)
        Client->>RS: Get(region-1, key)
        RS-->>Client: GetResponse
        Client-->>Exec: GetResult
    else 范围扫描
        Exec->>Client: scan("users", startKey, endKey, 10, [id,name], filter)
        Client->>Cache: lookupRange("users", start, end)
        Cache-->>Client: [RouteEntry(region-1), RouteEntry(region-2)]
        Client->>Client: ParallelScanner 并行扫描多个 Region
        Client->>RS: Scan(region-1, filter)
        Client->>RS: Scan(region-2, filter)
        RS-->>Client: ScanResponse 流
        RS-->>Client: ScanResponse 流
        Client-->>Exec: List~ScanRow~
    end

    Note over Exec: 客户端兜底过滤 (predicate.test)
    Exec->>Exec: OrderBy + Limit + Offset
    Exec-->>App: SqlResult(rows)
```

### 7.4 Hash Join 流程

```mermaid
sequenceDiagram
    participant App as 应用程序
    participant Join as JoinExecutor
    participant Client as MiniSQLClient
    participant RS as RegionServer

    App->>Join: SELECT * FROM orders o JOIN customers c ON o.cid = c.id WHERE o.amount > 100

    Note over Join,Client: 谓词按表切分 + 并行扫描两表
    Join->>Client: scanAsync("orders", filter="o.amount>100")
    Join->>Client: scanAsync("customers", filter=null)
    Client-->>Join: ordersRows (build side)
    Client-->>Join: customersRows (probe side)

    Note over Join: Hash Join 算法
    Join->>Join: hashTable = groupBy(ordersRows, "cid")

    loop for each customerRow in customersRows
        Join->>Join: matches = hashTable[customerRow.id]
        alt matches != null
            loop for each match in matches
                Join->>Join: emit joinedRow
            end
        end
    end

    Join-->>App: SqlResult(joinedRows)
```

### 7.5 路由失效自动恢复机制

Client 在检测到 `ERROR_STALE_ROUTE` 时自动执行一次路由刷新和重试：

```
1. RouteCache.lookup(table, key) → RouteEntry
2. 向 RegionServer 发起 RPC
3. 若响应 errorCode == ERROR_STALE_ROUTE：
   a. RouteCache.invalidate(table)
   b. 重新 lookup（触发 GetRouteTable RPC）
   c. 用新路由重试一次（只重试一次，避免死循环）
4. 返回最终响应
```

详细设计请参见 [member4-design.md §3.2](./member4-design.md)（路由缓存）和 [member4-design.md §3.7](./member4-design.md)（Hash Join）。

---

## 8. 测试体系与工具

> 本节内容引用自 [member5-design.md](./member5-design.md)，由成员5（刘一鸣）完成。

### 8.1 测试分层架构

| 层级 | 工具 | 范围 | 数量 |
|------|------|------|------|
| 单元测试 | JUnit 4 + Mockito | 单个类/方法 | ~240 |
| 集成测试 | 内嵌 ZK + in-process gRPC | 模块间交互 | ~50 |
| 端到端测试 | 完整集群模拟 | 全流程 | ~10 |
| 性能测试 | 基准测试框架 | 吞吐量/延迟 | 7 |
| 压力测试 | 并发客户端 | 高负载稳定性 | 3 |
| **总计** | | | **~310** |

### 8.2 测试基础设施类图

```mermaid
classDiagram
    class EmbeddedZookeeperServer {
        +start(): void
        +stop(): void
        +getConnectString(): String
    }

    class InProcessGrpcServer {
        +start(service): void
        +stop(): void
        +getChannel(): ManagedChannel
    }

    class FakeRegionServer {
        -serverId: String
        -regions: Map~String, RegionInfo~
        +start(): void
        +registerToMaster(): void
        +sendHeartbeat(load): void
        +simulateCrash(): void
    }

    class TestCluster {
        -zkServer: EmbeddedZookeeperServer
        -master: MasterServer
        -regionServers: List~FakeRegionServer~
        +start(): void
        +stop(): void
        +awaitHealthy(timeout): boolean
        +getMasterStub(): ClientMasterServiceBlockingStub
    }

    class TestClusterBuilder {
        +withRegionServers(count): TestClusterBuilder
        +withMasterConfig(config): TestClusterBuilder
        +build(): TestCluster
    }

    TestClusterBuilder --> TestCluster
    TestCluster --> EmbeddedZookeeperServer
    TestCluster --> FakeRegionServer
    FakeRegionServer --> InProcessGrpcServer
```

### 8.3 CLI 管理工具

`minisql-admin` 命令行工具提供 7 个管理命令：

| 命令 | 功能 |
|------|------|
| `cluster status` | 集群健康状态 |
| `cluster stats` | 集群统计信息 |
| `cluster nodes` | 节点列表+详情 |
| `cluster balance` | 手动触发负载均衡 |
| `table list` | 表列表 |
| `table describe <name>` | 表结构详情 |
| `table route <name>` | 表路由信息 |

### 8.4 性能测试结果

| 操作 | 吞吐量 |
|------|--------|
| PUT | ~15,890 ops/sec |
| GET | ~86,237 ops/sec |
| DELETE | ~103,176 ops/sec |
| 10 线程并发混合读写 | ~168,000 ops/sec（0 错误） |

详细设计请参见 [member5-design.md §3](./member5-design.md)（Admin 工具）和 [member5-design.md §4](./member5-design.md)（测试体系）。

---

## 9. 部署架构

### 9.1 部署拓扑

```
┌────────────────────────────────────────────┐
│         ZooKeeper Cluster (3节点)          │
│   zk-1:2181  zk-2:2181  zk-3:2181         │
└─────────────────┬──────────────────────────┘
                  │
    ┌─────────────┼─────────────┐
    │             │             │
┌───▼────┐   ┌───▼────┐   ┌───▼────┐
│Master-1│   │Master-2│   │Master-3│
│(Leader)│   │(Backup)│   │(Backup)│
│ :8000  │   │ :8001  │   │ :8002  │
└────────┘   └────────┘   └────────┘
    │             │             │
    └─────────────┼─────────────┘
                  │
    ┌─────────────┼─────────────┐
    │             │             │
┌───▼────┐   ┌───▼────┐   ┌───▼────┐
│   RS   │   │   RS   │   │   RS   │
│ :8001  │   │ :8002  │   │ :8003  │
└────────┘   └────────┘   └────────┘
```

### 9.2 启动顺序

1. 启动 ZooKeeper 集群
2. 启动 Master 节点（自动选举 Leader）
3. 启动 RegionServer 节点（向 Master 注册）
4. 启动 Client / Gateway 服务

详细部署步骤请参见 [member5-design.md §5](./member5-design.md) 和 [member1-design.md §10](./member1-design.md)。

---

## 10. 系统总览与总结

### 10.1 各模块完成情况

| 模块 | 负责人 | 完成度 | 关键实现 |
|------|--------|--------|---------|
| **Master** | 成员1 李明睿 | ✅ 已完成 | 集群管理、元数据管理、负载均衡、Region 迁移、Master 选举 |
| **RegionServer** | 成员2 李锋然 | ✅ 已完成 | 存储引擎（MySQL/Memory）、CRUD 操作、Region 生命周期 |
| **副本与一致性** | 成员3 周赫 | ✅ 已完成 | WAL 日志、Paxos 共识、副本管理、日志复制 |
| **Client** | 成员4 冯俊翰 | ✅ 已完成 | 路由缓存、KV 门面、SQL 执行、Hash Join、Gateway |
| **测试与工具** | 成员5 刘一鸣 | ✅ 已完成 | CLI 管理工具、测试框架（~310 测试）、部署脚本、文档 |

### 10.2 模块间交互关系汇总

```
Client (成员4)
  │ ClientMasterService (gRPC)
  ▼
Master (成员1) ──ZooKeeper── MetadataPersistence
  │ MasterService (gRPC: 注册/心跳/迁移)
  ▼
RegionServer Service (成员2)
  ├── StorageEngine (成员2: MySQL/Memory)
  ├── WalService (成员3: WAL 日志)
  ├── PaxosProposer/Acceptor (成员3: 共识)
  ├── ReplicationManager (成员3: 副本管理)
  └── ReplicationLogService (成员3: 日志复制)
       │ ApplyReplicationLog (gRPC)
       ▼
  远程副本 RegionServer
```

### 10.3 总体测试统计

| 模块 | 测试数 | 关键覆盖率 |
|------|--------|-----------|
| minisql-master | 189 | balance 94%, cluster 57% |
| minisql-regionserver | 53 | WAL 70%, replication 49% |
| minisql-client | 55 | route 91%, core 83% |
| minisql-admin | 16 | 77% |
| **总计** | **~313** | **全部通过** |

### 10.4 技术亮点

1. **完整 Master 选举机制**：基于 ZooKeeper 临时顺序节点，选举时间 < 5s，支持故障自动转移
2. **负载均衡与 Region 在线迁移**：加权负载评分 + 完整状态机（Prepare → Sync → Switch → Complete），支持自动重试和回滚
3. **Paxos 强一致性共识**：三阶段 Prepare-Accept-Commit 协议，指数退避防活锁，per-Region 隔离
4. **WAL-First 持久化保证**：所有写操作先写日志，配合 Paxos 共识，故障恢复时可重放未提交操作
5. **分布式查询能力**：并行扫描跨 Region 并发 + Hash Join 两表关联 + 谓词下推 + 路由失效自动重试
6. **Gateway 多语言接入**：非 Java 语言通过 gRPC Gateway 使用全部功能，无需复刻路由/重试/Join 逻辑

---

## 附录

### A. 各成员模块设计报告

| 报告 | 作者 | 内容 |
|------|------|------|
| [member1-design.md](./member1-design.md) | 成员1 李明睿 | Master 模块完整设计 |
| [member2-design.md](./member2-design.md) | 成员2 李锋然 | RegionServer 模块详细设计 |
| [member3-design.md](./member3-design.md) | 成员3 周赫 | 副本管理与一致性协议模块设计 |
| [member4-design.md](./member4-design.md) | 成员4 冯俊翰 | Client 模块完整设计 |
| [member5-design.md](./member5-design.md) | 成员5 刘一鸣 | 测试、工具与文档设计 |

### B. 代码仓库

- GitHub: https://github.com/oi35/Distributed_MiniSQL
- 模块目录：`minisql-master/`, `minisql-regionserver/`, `minisql-client/`, `minisql-admin/`, `minisql-common/`
