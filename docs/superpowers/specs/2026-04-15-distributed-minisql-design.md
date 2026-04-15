# 分布式MiniSQL系统架构设计

**项目名称：** 分布式MiniSQL系统  
**设计日期：** 2026-04-15  
**设计版本：** v1.0  
**项目类型：** 分布式数据库系统

## 1. 项目概述

### 1.1 项目目标

本项目旨在实现一个小型的分布式数据库系统，重点在于实现分布式特性。系统使用MySQL作为底层存储引擎，在其之上构建分布式层，提供数据分片、副本管理、分布式查询等核心功能。

### 1.2 核心功能

- **数据分布**：基于范围分片的数据分布策略
- **集群管理**：Master-RegionServer架构的集群管理
- **分布式查询**：支持跨Region的查询和基础Join操作
- **副本管理**：基于Paxos协议的强一致性副本管理
- **容错容灾**：节点故障自动检测和恢复
- **负载均衡**：动态Region分配和迁移
- **缓存机制**：客户端路由缓存和查询缓存
- **日志机制**：WAL日志和操作日志
- **跨语言客户端**：支持Java、C、C++、Python等多语言

### 1.3 技术栈

- **服务端语言**：Java
- **底层数据库**：MySQL
- **协调服务**：Apache Zookeeper
- **RPC框架**：gRPC
- **客户端语言**：Java、C、C++、Python

### 1.4 集群规模

- **预期节点数**：5个节点
- **Master节点**：1个（支持HA扩展）
- **RegionServer节点**：4个
- **Zookeeper集群**：3个节点

## 2. 系统整体架构

### 2.1 架构层次

系统采用三层架构：

**客户端层（Client Layer）**
- 支持Java、C、C++、Python等多语言客户端
- 通过统一的接口协议与系统通信
- 客户端缓存Table的Region定位信息
- 直接与RegionServer通信进行数据读写

**协调层（Coordination Layer）**
- Master节点：负责集群管理、Region分配、负载均衡
- Zookeeper集群：提供分布式协调、配置管理、Master选举

**存储层（Storage Layer）**
- RegionServer节点：管理Region，处理数据读写请求
- MySQL实例：每个RegionServer对应一个MySQL实例，存储实际数据

### 2.2 核心组件

**Master节点**
- 管理RegionServer的注册和心跳
- 维护Table到Region的映射关系
- 负责Region的分配和迁移
- 监控集群状态，执行负载均衡
- 处理DDL操作（CREATE TABLE、DROP TABLE等）

**RegionServer节点**
- 管理多个Region
- 处理客户端的读写请求
- 执行本地查询和Join操作
- 维护Region的副本同步
- 向Master报告心跳和状态

**Zookeeper集群**
- 存储Master的位置信息
- 存储Region的位置信息（可选，客户端可从Master或Zookeeper获取）
- 提供Master选举功能（支持Master HA）
- 存储集群配置信息

### 2.3 数据流

**写操作流程：**
1. 客户端根据主键计算目标Region
2. 从缓存或Master获取Region位置
3. 向主副本RegionServer发送写请求
4. 主副本使用Paxos协议同步到从副本
5. 所有副本写入成功后返回客户端

**读操作流程：**
1. 客户端根据主键计算目标Region
2. 从缓存或Master获取Region位置
3. 直接向RegionServer发送读请求
4. RegionServer查询本地MySQL返回结果

## 3. 数据分片与分布策略

### 3.1 分片策略（推荐：范围分片 + 动态分裂）

**推荐理由：**

1. **符合HBase设计理念** - Region按主键范围划分，概念清晰
2. **支持范围查询** - 相邻的数据存储在同一Region，范围查询效率高
3. **动态负载均衡** - Region可以根据数据量动态分裂和合并
4. **易于实现** - 相比一致性哈希，实现复杂度更低
5. **教学价值** - 能够很好地展示分布式数据分片的核心思想

**分片机制：**
- 每个Table初始创建时分配1个Region
- Region按主键范围划分：[startKey, endKey)
- 当Region大小超过阈值（如256MB）时自动分裂
- 分裂点选择中位数，将Region一分为二
- Master负责将新Region分配到负载较低的RegionServer

### 3.2 Region结构

**Region元数据：**
```java
Region {
    regionId: String          // Region唯一标识
    tableName: String         // 所属表名
    startKey: byte[]          // 起始主键（包含）
    endKey: byte[]            // 结束主键（不包含）
    regionServer: String      // 当前所在RegionServer
    replicas: List<String>    // 副本所在RegionServer列表
    state: RegionState        // ONLINE/SPLITTING/OFFLINE
    size: long                // 当前数据量大小
}
```

**Region在MySQL中的存储：**
- 每个Region对应MySQL中的一个或多个表
- 表名格式：`{tableName}_{regionId}`
- 保留原始表结构，添加内部元数据列

### 3.3 数据定位

**客户端定位流程：**
1. 客户端维护Table的Region路由表缓存
2. 根据主键二分查找目标Region
3. 如果缓存未命中或失效，从Master获取最新路由表
4. 可选：从Zookeeper获取Region位置（减轻Master压力）

**路由表结构：**
```java
RegionRouteTable {
    tableName: String
    regions: List<RegionInfo>  // 按startKey排序
    version: long               // 版本号，用于缓存失效
}
```

## 4. 副本管理与一致性保证

### 4.1 副本策略

**副本配置：**
- 每个Region配置3个副本（1主2从）
- 主副本负责处理写请求和协调副本同步
- 从副本可以处理读请求（可配置读一致性级别）
- 副本分布在不同的RegionServer上，避免单点故障

**副本分配原则：**
- 同一Region的副本不能在同一RegionServer
- 尽量均匀分布副本，避免某些节点负载过高
- Master在分配Region时考虑副本分布

### 4.2 Paxos一致性协议

**写操作的Paxos流程：**

1. **Prepare阶段**
   - 主副本作为Proposer，生成提案编号（proposal number）
   - 向所有副本发送Prepare请求
   - 副本（Acceptor）承诺不再接受编号更小的提案

2. **Accept阶段**
   - 主副本收到多数派（2/3）的Promise响应
   - 向所有副本发送Accept请求，包含写入数据
   - 副本接受提案并写入本地MySQL

3. **Commit阶段**
   - 主副本收到多数派的Accept响应
   - 向客户端返回成功
   - 异步通知所有副本提交事务

**简化实现（适合教学）：**
- 采用简化的两阶段提交
- 主副本向所有从副本发送写请求
- 等待所有副本确认后返回成功
- 如果某个副本失败，回滚所有副本的写入

### 4.3 副本同步与故障恢复

**正常同步：**
- 主副本接收写请求后，同步写入所有副本
- 使用WAL（Write-Ahead Log）记录操作日志
- 从副本应用WAL日志保持数据一致

**副本故障恢复：**
- 从副本宕机后重启，从主副本拉取增量WAL
- 应用WAL日志恢复到最新状态
- 恢复完成后重新加入副本组

**主副本切换：**
- Master检测到主副本RegionServer宕机
- 从剩余副本中选举新的主副本
- 更新Region元数据，通知客户端
- 新主副本继续提供服务

## 5. 负载均衡与容错容灾

### 5.1 负载均衡策略

**新建表时的负载均衡：**
- Master统计各RegionServer的Region数量和数据量
- 选择负载最低的RegionServer分配新Region
- 考虑因素：Region数量、数据总量、CPU/内存使用率

**动态负载均衡：**
- Master定期（如每5分钟）检查集群负载
- 识别负载不均衡的情况：
  - 某个RegionServer的Region数量超过平均值的150%
  - 某个RegionServer的数据量超过平均值的150%
- 触发Region迁移，将热点Region迁移到负载较低的节点

**Region分裂触发的负载均衡：**
- Region达到大小阈值（256MB）时自动分裂
- 分裂后的两个子Region，一个留在原RegionServer
- 另一个分配到负载较低的RegionServer
- Master更新元数据和路由表

### 5.2 Region迁移流程

**迁移步骤：**
1. Master选择源RegionServer和目标RegionServer
2. 在目标RegionServer上创建Region副本
3. 从源RegionServer同步数据到目标RegionServer
4. 数据同步完成后，切换Region的主副本
5. 更新元数据，通知客户端刷新缓存
6. 删除源RegionServer上的旧副本

**迁移期间的数据一致性：**
- 迁移过程中Region仍然可读写
- 使用双写机制：同时写入源和目标
- 切换完成后，旧副本标记为只读，逐步删除

### 5.3 容错容灾机制

**RegionServer故障检测：**
- RegionServer定期（如每3秒）向Master发送心跳
- Master超过30秒未收到心跳，标记RegionServer为DEAD
- Master触发故障恢复流程

**RegionServer故障恢复：**
1. Master识别故障RegionServer上的所有Region
2. 对每个Region，从剩余副本中选举新的主副本
3. 将Region重新分配到其他RegionServer
4. 在新的RegionServer上创建缺失的副本
5. 更新元数据和路由表，通知客户端

**Master故障恢复（Master HA）：**
- 部署多个Master节点（1主多备）
- 使用Zookeeper进行Master选举
- 备用Master监听Zookeeper的Master节点
- 主Master故障时，备用Master自动接管
- 新Master从Zookeeper加载集群元数据

**数据持久化与恢复：**
- 所有元数据持久化到Zookeeper
- RegionServer的WAL日志持久化到磁盘
- 故障恢复时，从WAL日志重放未提交的操作

## 6. 分布式查询与Join实现

### 6.1 查询处理流程

**单表查询：**
1. 客户端解析SQL，提取WHERE条件
2. 根据主键条件定位目标Region
3. 如果是点查询（主键精确匹配），直接路由到单个Region
4. 如果是范围查询，可能涉及多个Region
5. 并行向多个RegionServer发送查询请求
6. 客户端合并结果并返回

**查询下推优化：**
- 将过滤条件（WHERE）下推到RegionServer执行
- 将投影操作（SELECT字段）下推到RegionServer
- 减少网络传输的数据量

### 6.2 基础Join实现

**支持的Join类型：**
- INNER JOIN（内连接）
- LEFT JOIN（左外连接）
- RIGHT JOIN（右外连接）

**Join执行策略（两阶段）：**

**阶段1：数据收集**
1. 客户端解析Join SQL，识别左表和右表
2. 确定Join条件（ON子句）
3. 分别查询左表和右表的相关Region
4. 将数据拉取到客户端或协调节点

**阶段2：Join计算**
- 采用Hash Join算法：
  1. 对较小的表（构建表）建立哈希表
  2. 遍历较大的表（探测表），在哈希表中查找匹配
  3. 输出Join结果

**分布式Join优化（可选扩展）：**
- 如果两个表的Join键都是主键，且分片策略相同
- 可以将Join下推到RegionServer执行（Co-located Join）
- 减少网络传输开销

### 6.3 查询示例

**示例1：单表点查询**
```sql
SELECT * FROM users WHERE user_id = 12345;
```
- 根据user_id定位到Region_3
- 直接向Region_3所在的RegionServer查询
- 返回结果

**示例2：单表范围查询**
```sql
SELECT * FROM users WHERE user_id BETWEEN 10000 AND 20000;
```
- 定位到Region_2和Region_3
- 并行查询两个RegionServer
- 合并结果并排序返回

**示例3：两表Join**
```sql
SELECT u.name, o.order_id 
FROM users u 
INNER JOIN orders o ON u.user_id = o.user_id 
WHERE u.user_id > 10000;
```
- 查询users表的相关Region，获取user_id > 10000的记录
- 根据user_id列表查询orders表的相关Region
- 在客户端执行Hash Join
- 返回结果

## 7. 缓存机制与日志系统

### 7.1 客户端缓存

**Region路由表缓存：**
- 客户端缓存Table的Region分布信息
- 缓存结构：`Map<TableName, RegionRouteTable>`
- 缓存失效策略：
  - 版本号机制：Master更新路由表时递增版本号
  - 客户端请求失败时（Region已迁移），主动刷新缓存
  - 定期刷新（如每10分钟）

**查询结果缓存（可选）：**
- 缓存热点查询的结果
- 使用LRU策略淘汰
- 写操作时失效相关缓存

### 7.2 RegionServer缓存

**MySQL查询缓存：**
- 利用MySQL自身的查询缓存机制
- 缓存频繁查询的结果集

**元数据缓存：**
- 缓存Region的元数据信息
- 缓存表结构信息
- 减少对Master的请求

### 7.3 日志系统

**WAL（Write-Ahead Log）：**
- 每个RegionServer维护WAL日志
- 写操作先写WAL，再写MySQL
- WAL格式：
  ```java
  LogEntry {
      sequenceId: long        // 日志序列号
      timestamp: long         // 时间戳
      regionId: String        // Region ID
      operation: OpType       // INSERT/UPDATE/DELETE
      data: byte[]            // 操作数据
      checksum: long          // 校验和
  }
  ```

**WAL的作用：**
- 故障恢复：RegionServer重启后重放WAL
- 副本同步：从副本通过WAL保持与主副本一致
- 数据持久化：确保数据不丢失

**WAL管理：**
- WAL文件滚动：达到大小阈值（如64MB）时创建新文件
- WAL清理：数据已持久化到MySQL后，删除旧的WAL文件
- WAL归档：可选地将WAL归档用于审计或备份

### 7.4 操作日志

**Master操作日志：**
- 记录所有集群管理操作：
  - Region分配和迁移
  - RegionServer上下线
  - 负载均衡操作
  - DDL操作
- 日志级别：INFO、WARN、ERROR
- 日志输出：文件 + 控制台

**RegionServer操作日志：**
- 记录数据读写操作
- 记录Region状态变化
- 记录副本同步状态
- 性能指标：QPS、延迟、错误率

**日志格式（统一）：**
```
[timestamp] [level] [component] [thread] message
```

**日志存储：**
- 本地文件系统
- 日志滚动：按天或按大小（如100MB）
- 日志保留：默认保留7天

## 8. 模块设计与接口定义

### 8.1 Master模块

**核心子模块：**

**ClusterManager（集群管理器）**
- 管理RegionServer的注册和注销
- 监控RegionServer心跳
- 维护集群拓扑信息

**RegionManager（Region管理器）**
- 管理Region的创建、分配、迁移
- 维护Region元数据
- 处理Region分裂和合并

**LoadBalancer（负载均衡器）**
- 监控集群负载状态
- 计算负载均衡方案
- 执行Region迁移

**MetadataManager（元数据管理器）**
- 管理Table元数据
- 管理Region路由表
- 与Zookeeper同步元数据

**主要接口：**
```java
interface MasterService {
    // Region管理
    RegionInfo assignRegion(String tableName);
    void reportRegionSplit(String regionId, RegionInfo[] newRegions);
    RegionRouteTable getRegionRouteTable(String tableName);
    
    // RegionServer管理
    void registerRegionServer(RegionServerInfo info);
    void reportHeartbeat(String regionServerId, RegionServerStatus status);
    
    // 表管理
    void createTable(TableSchema schema);
    void dropTable(String tableName);
    TableSchema getTableSchema(String tableName);
}
```

### 8.2 RegionServer模块

**核心子模块：**

**RegionContainer（Region容器）**
- 管理本地的所有Region
- 处理Region的加载和卸载
- 维护Region状态

**QueryExecutor（查询执行器）**
- 解析和执行SQL查询
- 与MySQL交互
- 实现查询优化

**ReplicationManager（副本管理器）**
- 管理Region的副本同步
- 实现Paxos协议
- 处理副本故障恢复

**WALManager（WAL管理器）**
- 管理WAL日志的写入和读取
- 实现WAL滚动和清理
- 支持故障恢复

**主要接口：**
```java
interface RegionServerService {
    // 数据操作
    Result get(String tableName, byte[] key);
    void put(String tableName, byte[] key, byte[] value);
    void delete(String tableName, byte[] key);
    List<Result> scan(String tableName, byte[] startKey, byte[] endKey);
    
    // Region管理
    void openRegion(RegionInfo region);
    void closeRegion(String regionId);
    RegionInfo splitRegion(String regionId);
    
    // 副本同步
    void replicateLog(String regionId, List<LogEntry> logs);
    void syncRegion(String regionId, long fromSequence);
}
```

### 8.3 Client模块

**核心子模块：**

**ConnectionManager（连接管理器）**
- 管理与Master和RegionServer的连接
- 连接池管理
- 自动重连机制

**RouteCache（路由缓存）**
- 缓存Region路由表
- 处理缓存失效和刷新
- 提供快速路由查找

**QueryParser（查询解析器）**
- 解析SQL语句
- 生成查询计划
- 识别涉及的Region

**JoinExecutor（Join执行器）**
- 实现分布式Join逻辑
- 协调多Region查询
- 合并查询结果

**主要接口：**
```java
interface MiniSQLClient {
    // 连接管理
    void connect(String masterAddress);
    void close();
    
    // DDL操作
    void createTable(String sql);
    void dropTable(String tableName);
    
    // DML操作
    ResultSet executeQuery(String sql);
    int executeUpdate(String sql);
    
    // 事务支持（可选）
    void beginTransaction();
    void commit();
    void rollback();
}
```

### 8.4 通信协议

**RPC框架选择：**
- 推荐使用gRPC或Apache Thrift
- 支持跨语言客户端
- 提供高性能的序列化和传输

**协议定义（gRPC示例）：**
```protobuf
service MasterService {
    rpc GetRegionRouteTable(GetRouteTableRequest) returns (RouteTableResponse);
    rpc RegisterRegionServer(RegisterRequest) returns (RegisterResponse);
    rpc ReportHeartbeat(HeartbeatRequest) returns (HeartbeatResponse);
}

service RegionServerService {
    rpc Get(GetRequest) returns (GetResponse);
    rpc Put(PutRequest) returns (PutResponse);
    rpc Scan(ScanRequest) returns (stream ScanResponse);
}
```

## 9. 部署架构与管理工具

### 9.1 部署架构

**节点配置（5节点示例）：**

**节点1：Master + Zookeeper**
- Master服务（端口：8000）
- Zookeeper（端口：2181）
- 配置：2核CPU，4GB内存

**节点2-5：RegionServer + MySQL + Zookeeper**
- RegionServer服务（端口：8001-8004）
- MySQL实例（端口：3306）
- Zookeeper（节点2-4，端口：2181）
- 配置：4核CPU，8GB内存，100GB存储

**Zookeeper集群：**
- 3节点部署（节点1、2、3）
- 提供高可用性
- 存储路径：
  - `/minisql/master` - Master信息
  - `/minisql/regions` - Region路由表
  - `/minisql/regionservers` - RegionServer列表

### 9.2 配置管理

**Master配置（master.conf）：**
```properties
# 集群配置
cluster.name=minisql-cluster
master.port=8000
master.web.port=8080

# Zookeeper配置
zookeeper.quorum=node1:2181,node2:2181,node3:2181
zookeeper.session.timeout=30000

# Region配置
region.split.size=268435456  # 256MB
region.replica.count=3

# 负载均衡配置
balancer.period=300000  # 5分钟
balancer.threshold=1.5  # 150%

# 心跳配置
heartbeat.interval=3000  # 3秒
heartbeat.timeout=30000  # 30秒
```

**RegionServer配置（regionserver.conf）：**
```properties
# 服务配置
regionserver.port=8001
regionserver.id=rs-001

# Master配置
master.address=node1:8000

# MySQL配置
mysql.host=localhost
mysql.port=3306
mysql.database=minisql_rs001
mysql.username=root
mysql.password=password

# WAL配置
wal.dir=/data/minisql/wal
wal.max.size=67108864  # 64MB
wal.retention.hours=24

# 缓存配置
cache.size=1073741824  # 1GB
```

### 9.3 CLI管理工具

**命令行工具（minisql-admin）：**

**集群管理命令：**
```bash
# 启动集群
minisql-admin cluster start

# 停止集群
minisql-admin cluster stop

# 查看集群状态
minisql-admin cluster status

# 查看RegionServer列表
minisql-admin regionserver list

# 查看Region分布
minisql-admin region list [table-name]
```

**表管理命令：**
```bash
# 创建表
minisql-admin table create -f schema.sql

# 删除表
minisql-admin table drop <table-name>

# 查看表信息
minisql-admin table describe <table-name>

# 查看表的Region分布
minisql-admin table regions <table-name>
```

**负载均衡命令：**
```bash
# 手动触发负载均衡
minisql-admin balance run

# 查看负载均衡状态
minisql-admin balance status

# 手动迁移Region
minisql-admin region move <region-id> <target-regionserver>
```

**监控命令：**
```bash
# 查看集群指标
minisql-admin metrics cluster

# 查看RegionServer指标
minisql-admin metrics regionserver <rs-id>

# 查看Region指标
minisql-admin metrics region <region-id>
```

### 9.4 Web管理界面（后续实现）

**功能模块：**

**Dashboard（仪表盘）**
- 集群概览：节点数、Region数、表数
- 实时指标：QPS、延迟、错误率
- 告警信息

**集群管理**
- RegionServer列表和状态
- Region分布可视化
- 节点资源使用情况

**表管理**
- 表列表和详情
- Region分布图
- 表操作（创建、删除、查看）

**监控与日志**
- 实时监控图表
- 日志查看和搜索
- 性能分析

**技术栈：**
- 后端：Spring Boot + RESTful API
- 前端：Vue.js + Element UI
- 图表：ECharts

### 9.5 测试与验证

**单元测试：**
- 各模块的核心逻辑测试
- 使用JUnit + Mockito

**集成测试：**
- Master与RegionServer交互测试
- 客户端与服务端交互测试
- Paxos协议测试

**系统测试：**
- 功能测试：CRUD操作、Join查询
- 性能测试：并发读写、大数据量查询
- 容错测试：节点故障、网络分区
- 负载均衡测试：Region迁移、动态扩容

**测试场景：**
1. 正常读写操作
2. Region分裂和迁移
3. RegionServer故障恢复
4. Master故障切换
5. 并发写入冲突
6. 分布式Join查询

## 10. 实施计划

### 10.1 开发阶段

**阶段1：基础架构（2-3周）**
- 搭建项目框架
- 实现Master基本功能
- 实现RegionServer基本功能
- 实现简单的客户端

**阶段2：数据分片与存储（2-3周）**
- 实现Region管理
- 实现数据分片逻辑
- 集成MySQL存储
- 实现基本的CRUD操作

**阶段3：副本与一致性（2-3周）**
- 实现副本管理
- 实现Paxos协议（或简化版本）
- 实现WAL日志
- 实现故障恢复

**阶段4：负载均衡与容错（2周）**
- 实现负载均衡算法
- 实现Region迁移
- 实现故障检测和恢复
- 实现Master HA

**阶段5：分布式查询（2周）**
- 实现查询路由
- 实现基础Join
- 实现查询优化

**阶段6：管理工具与测试（1-2周）**
- 实现CLI管理工具
- 编写测试用例
- 性能测试和优化

### 10.2 技术难点

1. **Paxos协议实现** - 可以先实现简化版本
2. **Region分裂和迁移** - 需要保证数据一致性
3. **分布式Join** - 性能优化较复杂
4. **故障恢复** - 需要考虑各种边界情况

---

**文档结束**

