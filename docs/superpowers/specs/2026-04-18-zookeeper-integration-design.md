# Zookeeper集成设计文档

**文档版本：** 1.0  
**创建日期：** 2026-04-18  
**作者：** 成员1 - 架构负责人  
**状态：** 已实现

---

## 1. 概述

### 1.1 目标

为Distributed MiniSQL的Master节点实现基于Zookeeper的高可用和元数据持久化功能，确保：
- Master节点的高可用性（主备切换）
- 元数据的持久化存储
- 分布式协调和配置管理

### 1.2 实现范围

本次实现包括：
1. Zookeeper客户端封装（ZookeeperClient）
2. Master选举机制（MasterElection）
3. 元数据持久化（MetadataPersistence）
4. MasterServer集成

### 1.3 技术栈

- **Zookeeper版本：** 3.9.1
- **序列化：** Gson 2.10.1
- **语言：** Java 11
- **构建工具：** Maven

---

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Master Instances                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Master-1    │  │  Master-2    │  │  Master-3    │      │
│  │  (Active)    │  │  (Standby)   │  │  (Standby)   │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                  │                  │              │
│         └──────────────────┼──────────────────┘              │
│                            │                                 │
└────────────────────────────┼─────────────────────────────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │   Zookeeper Cluster  │
                  │                      │
                  │  /minisql/           │
                  │    ├─ master/        │
                  │    │   └─ election/  │
                  │    └─ metadata/      │
                  │        ├─ tables/    │
                  │        └─ regions/   │
                  └──────────────────────┘
```

### 2.2 组件关系

```
MasterServer
    ├── ZookeeperClient (连接管理)
    ├── MasterElection (选举协调)
    │   └── uses ZookeeperClient
    ├── MetadataPersistence (数据持久化)
    │   └── uses ZookeeperClient
    ├── ClusterManager (集群管理)
    └── MetadataManager (元数据管理)
```

---

## 3. 核心组件设计

### 3.1 ZookeeperClient

**职责：** Zookeeper客户端封装，提供基础操作

**核心方法：**

```java
// 连接管理
void connect() throws IOException, InterruptedException
void close() throws InterruptedException
boolean isConnected()

// 节点操作
String createNode(String path, String data, CreateMode mode)
String createPersistentNode(String path, String data)
String createEphemeralNode(String path, String data)
String createEphemeralSequentialNode(String path, String data)
void deleteNode(String path)

// 数据操作
boolean exists(String path)
Stat exists(String path, Watcher watcher)
String getData(String path)
String getData(String path, Watcher watcher)
void setData(String path, String data)

// 子节点操作
List<String> getChildren(String path)
List<String> getChildren(String path, Watcher watcher)

// 工具方法
void ensurePath(String path) // 递归创建路径
```

**设计要点：**
- 连接状态监控（SyncConnected/Disconnected/Expired）
- 自动重连机制（通过Watcher）
- 超时控制（sessionTimeout）
- 线程安全（Zookeeper客户端本身线程安全）

**配置参数：**
- `connectString`: Zookeeper连接字符串（默认：localhost:2181）
- `sessionTimeout`: 会话超时时间（默认：30秒）

---

### 3.2 MasterElection

**职责：** 实现Master选举和故障转移

**选举算法：**
1. 每个Master实例创建临时顺序节点：`/minisql/master/election/master-XXXXXXXXXX`
2. 获取所有选举节点，按序号排序
3. 序号最小的节点成为活跃Master
4. 其他节点成为备用Master，监听前驱节点
5. 前驱节点删除时，重新检查是否成为Master

**状态转换：**
```
启动 → 创建选举节点 → 检查是否最小
                        ├─ 是 → 成为Master → 启动服务
                        └─ 否 → 成为Standby → 监听前驱
                                                  ↓
                                            前驱删除
                                                  ↓
                                            重新检查
```

**核心方法：**
```java
void startElection() throws KeeperException, InterruptedException
void stopElection()
boolean isMaster()
void setListener(MasterStateListener listener)

interface MasterStateListener {
    void onBecomeMaster();
    void onLoseMaster();
}
```

**设计要点：**
- 使用临时顺序节点（EPHEMERAL_SEQUENTIAL）
- 前驱节点监听（避免羊群效应）
- 状态监听器回调
- 优雅退出（删除选举节点）

**选举路径：**
- `/minisql/master/election/master-0000000001`
- `/minisql/master/election/master-0000000002`
- `/minisql/master/election/master-0000000003`

---

### 3.3 MetadataPersistence

**职责：** 元数据持久化到Zookeeper

**数据结构：**
```
/minisql/metadata/
    ├── tables/
    │   ├── users
    │   ├── orders
    │   └── products
    └── regions/
        ├── region-1
        ├── region-2
        └── region-3
```

**表元数据格式（JSON）：**
```json
{
  "tableName": "users",
  "partitionKey": "id",
  "regionIds": ["region-1", "region-2"],
  "createTime": 1713456789000,
  "updateTime": 1713456789000,
  "version": 1
}
```

**Region元数据格式（JSON）：**
```json
{
  "regionId": "region-1",
  "tableName": "users",
  "startKey": "",
  "endKey": "m",
  "primaryServer": "rs-001",
  "replicas": ["rs-001", "rs-002", "rs-003"],
  "state": "REGION_ONLINE",
  "createTime": 1713456789000,
  "updateTime": 1713456789000,
  "version": 1
}
```

**核心方法：**
```java
// 初始化
void initialize() throws KeeperException, InterruptedException

// 表操作
void saveTable(TableMetadata table)
Map<String, Object> loadTable(String tableName)
void deleteTable(String tableName)
List<String> loadAllTableNames()

// Region操作
void saveRegion(RegionMetadata region)
Map<String, Object> loadRegion(String regionId)
void deleteRegion(String regionId)
List<String> loadAllRegionIds()
```

**设计要点：**
- 使用持久节点（PERSISTENT）
- JSON序列化（Gson）
- 简化的元数据对象（避免序列化protobuf）
- 原子更新（setData）

---

## 4. MasterServer集成

### 4.1 启动流程

```
MasterServer.start()
    ↓
连接Zookeeper
    ↓
初始化元数据路径
    ↓
设置选举监听器
    ↓
开始Master选举
    ↓
等待选举结果
    ├─ 成为Master → startServices()
    │                  ├─ 启动gRPC服务器
    │                  ├─ 启动心跳监控器
    │                  └─ 注册关闭钩子
    └─ 成为Standby → 等待（监听前驱节点）
```

### 4.2 关闭流程

```
MasterServer.stop()
    ↓
停止Master选举（删除选举节点）
    ↓
stopServices()
    ├─ 停止心跳监控器
    └─ 停止gRPC服务器
    ↓
关闭Zookeeper连接
```

### 4.3 配置参数

**命令行参数：**
```bash
java -jar minisql-master.jar <port> <serverId> <zkConnect>
```

**环境变量：**
- `MASTER_PORT`: Master服务端口（默认：8000）
- `MASTER_ID`: Master服务器ID（默认：master-<timestamp>）
- `ZK_CONNECT`: Zookeeper连接字符串（默认：localhost:2181）

**示例：**
```bash
# 使用默认配置
java -jar minisql-master.jar

# 使用命令行参数
java -jar minisql-master.jar 8000 master-1 localhost:2181

# 使用环境变量
export MASTER_PORT=8000
export MASTER_ID=master-1
export ZK_CONNECT=zk1:2181,zk2:2181,zk3:2181
java -jar minisql-master.jar
```

---

## 5. 故障场景处理

### 5.1 Master故障

**场景：** 活跃Master进程崩溃或网络分区

**处理流程：**
1. Master的临时节点自动删除（会话过期）
2. 下一个备用Master检测到前驱节点删除
3. 备用Master重新检查选举状态
4. 成为新的活跃Master
5. 调用`onBecomeMaster()`启动服务

**恢复时间：** < 会话超时时间（30秒）

### 5.2 Zookeeper故障

**场景：** Zookeeper集群不可用

**处理流程：**
1. Master检测到连接断开（Disconnected事件）
2. 自动尝试重连
3. 如果会话过期（Expired事件），需要重新选举
4. 重新创建选举节点

**注意：** 生产环境应使用Zookeeper集群（3或5个节点）

### 5.3 网络分区

**场景：** Master与Zookeeper网络分区

**处理流程：**
1. Master会话超时，临时节点删除
2. 新Master被选举
3. 旧Master检测到会话过期，停止服务
4. 避免脑裂（两个活跃Master）

---

## 6. 测试策略

### 6.1 单元测试

**ZookeeperClientTest（6个测试）：**
- testConnection: 验证连接
- testCreatePersistentNode: 创建持久节点
- testGetAndSetData: 获取/设置数据
- testDeleteNode: 删除节点
- testEnsurePath: 递归路径创建
- testCreateEphemeralNode: 创建临时节点

**测试策略：**
- 自动跳过（如果Zookeeper未运行）
- 使用本地Zookeeper（localhost:2181）
- 测试后清理节点

### 6.2 集成测试

**Master选举测试：**
1. 启动3个Master实例
2. 验证只有1个成为活跃Master
3. 杀死活跃Master
4. 验证新Master被选举
5. 验证服务正常运行

**元数据持久化测试：**
1. 创建表和Region
2. 验证数据写入Zookeeper
3. 重启Master
4. 验证数据可恢复

### 6.3 故障测试

**测试场景：**
- Master进程崩溃
- Zookeeper连接断开
- 网络延迟
- 会话超时

---

## 7. 性能考虑

### 7.1 选举性能

- **选举时间：** < 5秒（取决于网络延迟）
- **故障转移时间：** < 30秒（会话超时）
- **监听开销：** 最小（仅监听前驱节点）

### 7.2 元数据性能

- **读取延迟：** < 10ms（本地网络）
- **写入延迟：** < 50ms（需要持久化）
- **批量操作：** 支持（多个节点操作）

### 7.3 优化建议

1. **元数据缓存：** 在内存中缓存，定期同步到Zookeeper
2. **批量更新：** 合并多个元数据更新
3. **异步写入：** 非关键元数据异步持久化
4. **压缩：** 大数据使用压缩（Gzip）

---

## 8. 运维指南

### 8.1 部署

**Zookeeper部署：**
```bash
# 使用Docker
docker run -d --name zookeeper -p 2181:2181 zookeeper:3.9.1

# 使用Docker Compose（集群）
version: '3'
services:
  zk1:
    image: zookeeper:3.9.1
    ports: ["2181:2181"]
  zk2:
    image: zookeeper:3.9.1
    ports: ["2182:2181"]
  zk3:
    image: zookeeper:3.9.1
    ports: ["2183:2181"]
```

**Master部署：**
```bash
# Master-1
export MASTER_PORT=8000
export MASTER_ID=master-1
export ZK_CONNECT=zk1:2181,zk2:2181,zk3:2181
java -jar minisql-master.jar

# Master-2
export MASTER_PORT=8001
export MASTER_ID=master-2
export ZK_CONNECT=zk1:2181,zk2:2181,zk3:2181
java -jar minisql-master.jar
```

### 8.2 监控

**关键指标：**
- Master选举状态（活跃/备用）
- Zookeeper连接状态
- 会话超时次数
- 元数据更新频率
- 故障转移次数

**日志监控：**
```
[INFO] Connected to Zookeeper: localhost:2181
[INFO] Created election node: /minisql/master/election/master-0000000001
[INFO] Became Master: master-1
[INFO] Master server started, listening on port 8000
```

### 8.3 故障排查

**问题1：Master无法启动**
- 检查Zookeeper是否运行
- 检查网络连接
- 检查端口是否被占用

**问题2：频繁选举**
- 检查网络稳定性
- 增加会话超时时间
- 检查Zookeeper负载

**问题3：元数据丢失**
- 检查Zookeeper数据目录
- 检查持久化日志
- 恢复Zookeeper快照

---

## 9. 未来优化

### 9.1 短期优化

1. **元数据恢复：** Master启动时从Zookeeper恢复元数据
2. **配置管理：** 将配置存储到Zookeeper
3. **监控集成：** 暴露Prometheus指标

### 9.2 长期优化

1. **分布式锁：** 使用Zookeeper实现分布式锁
2. **配置热更新：** 监听配置变更，动态更新
3. **元数据版本控制：** 支持元数据回滚
4. **多数据中心：** 跨数据中心的Master选举

---

## 10. 总结

### 10.1 实现成果

- ✅ Zookeeper客户端封装（ZookeeperClient）
- ✅ Master选举机制（MasterElection）
- ✅ 元数据持久化（MetadataPersistence）
- ✅ MasterServer集成
- ✅ 单元测试（6个测试，全部通过）

### 10.2 技术亮点

1. **高可用：** 自动故障转移，无单点故障
2. **持久化：** 元数据持久化到Zookeeper
3. **可扩展：** 支持多Master实例
4. **易运维：** 简单的配置和部署

### 10.3 经验教训

1. **临时节点：** 使用临时顺序节点实现选举
2. **前驱监听：** 避免羊群效应
3. **会话管理：** 正确处理会话超时
4. **测试策略：** 自动跳过Zookeeper测试

---

**文档结束**

