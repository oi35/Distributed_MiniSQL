# MiniSQL 验收演示脚本

## 演示重点

聚焦各模块**实际实现的命令行功能**，展示系统的完整能力链路：
**Admin CLI → Master → RegionServer → 数据存储/复制/WAL**

---

## 环境准备（提前完成）

```bash
# 1. 编译全部模块（生成 gRPC 桩代码并安装所有模块）
cd Distributed_MiniSQL
mvn clean install -DskipTests

# 2. 启动 Zookeeper（嵌入式）
# 使用 Maven 自动解析 curator-test 所有依赖（含 zookeeper-jute 等传递依赖），
# 避免硬编码 classpath 路径在不同机器上不兼容的问题
cd bootstrap
# 将 classpath 写入临时文件（注意：PowerShell 中 -D 参数必须用单引号包裹）
mvn -f ../minisql-master/pom.xml dependency:build-classpath '-DincludeScope=test' '-Dmdep.outputFile=zk-cp.txt' -q
# 注：classpath 文件生成在 minisql-master/ 目录下（相对于 POM 路径）
$CP = Get-Content ../minisql-master/zk-cp.txt -Raw

# 编译辅助工具（ZkStart + CreateTableTest，供后续环节使用）
javac -cp "$CP" ZkStart.java CreateTableTest.java

# 启动 ZK（前台运行，阻塞终端）
java -cp ".;$CP" ZkStart

# 3. 启动 Master（新终端）
cd minisql-master
mvn exec:java '-Dexec.mainClass=com.minisql.master.MasterServer'

# 4. 启动 RegionServer rs-001（新终端）
cd minisql-regionserver
mvn exec:java '-Dexec.args=rs-001 8001'

# 5. 启动 RegionServer rs-002（新终端）
cd minisql-regionserver
mvn exec:java '-Dexec.args=rs-002 8002'

# 6. 打包 Admin CLI + 配置 classpath（fat jar 有依赖冲突，用 Maven classpath 代替）
cd minisql-admin
mvn package '-DskipTests'
# 定义 admin 命令 classpath（供后续环节使用，同一终端生效）
$AdminCP = Get-Content ../minisql-master/zk-cp.txt -Raw
```

---

## 演示流程（共7个环节，约20分钟）

---

### 环节一：系统架构说明（2分钟）

**不用敲命令，口头说明 + 展示项目目录结构**

```
Distributed_MiniSQL/
├── minisql-common/        # Proto定义 + gRPC桩代码
├── minisql-master/        # Master节点
├── minisql-regionserver/  # RegionServer节点
├── minisql-client/        # 客户端SDK + Gateway + SQL解析
└── minisql-admin/         # 管理CLI工具
```

**讲解要点：**
- Master-RegionServer 两层架构
- gRPC 通信（展示 proto 文件位置 `minisql-common/src/main/proto/`）
- Zookeeper 做元数据持久化和 Master 选举
- 范围分片 `[start_key, end_key)`
- 副本管理 + WAL + Paxos 一致性

---

### 环节二：集群管理命令演示（3分钟）

用 **Admin CLI** 展示集群管理功能：

```bash
# 2.1 查看集群健康状态 → 展示 HEALTHY、在线服务器数
java -cp "minisql-admin/target/classes;$AdminCP" com.minisql.admin.MiniSqlAdmin cluster status

# 预期的输出：
#   Cluster Health: HEALTHY
#   Total Servers: 2
#   Online Servers: 2
#   Total Regions: 0
#   Online Regions: 0
```

**讲解：** `cluster status` 调用 Master 的 `ClientMasterService.GetClusterHealth` RPC，Master 从 `ClusterManager` 获取所有 RegionServer 的状态，聚合后返回。

```bash
# 2.2 查看集群统计信息 → 展示表/Region/数据量统计
java -cp "minisql-admin/target/classes;$AdminCP" com.minisql.admin.MiniSqlAdmin cluster stats

# 2.3 列出所有 RegionServer 节点 → 展示每个节点的状态、负载、Region数
java -cp "minisql-admin/target/classes;$AdminCP" com.minisql.admin.MiniSqlAdmin cluster nodes

# 预期的输出：
#   RegionServer List (2/2 online):
#
#     Server: rs-001
#       Address: 0.0.0.0:8001
#       State: ONLINE
#       Load Score: 0.00
#       Regions: 0
#       Data Size: 0 B
#       Uptime: ...
#       Last Heartbeat: ...
#
#     Server: rs-002
#       Address: 0.0.0.0:8002
#       State: ONLINE
#       Load Score: 0.00
#       Regions: 0
#       Data Size: 0 B
#       Uptime: ...
#       Last Heartbeat: ...
```

**讲解：** `cluster nodes` 调用 Master 的 `AdminService.ListServers` RPC，返回每个 RegionServer 的 `ServerInfo`（地址、状态、负载评分、Region数、数据大小、运行时间、最后心跳时间）。

---

### 环节三：创建表和路由展示（2分钟）

```bash
# 3.1 列出所有表（初始为空）
java -cp "minisql-admin/target/classes;$AdminCP" com.minisql.admin.MiniSqlAdmin table list
# → No tables found.

# 3.2 创建表 users（通过 RegionServer 交互控制台）
# 在 RegionServer rs-001 的终端输入：
# RegionServer> put users user-1001 name=Alice age=30 email=alice@test.com
# → PUT successful
# （数据写入 RegionServer 本地存储，但表元数据还未注册到 Master）

# 3.3 将表注册到 Master（使用 bootstrap 中的辅助工具，以便查看路由）
# 保持 ZK 运行，在另一个闲置终端执行：
cd bootstrap
java -cp ".;$(Get-Content ../minisql-master/zk-cp.txt -Raw)" CreateTableTest
#
# 预期的输出：
#   CreateTable result:
#     Success: true
#     Regions: 1
#     Route version: 2

# 3.4 查看表列表
java -cp "minisql-admin/target/classes;$AdminCP" com.minisql.admin.MiniSqlAdmin table list
# → Tables (1):
#   - users

# 3.5 查看表路由信息 → 展示 Region 分布
java -cp "minisql-admin/target/classes;$AdminCP" com.minisql.admin.MiniSqlAdmin table route users
# → Route Table: users
#    Version: 2
#    Regions (1):
#      Region: users-region-0
#        Range: [, )
#        Primary: rs-001
#        Replicas: rs-001
```

**讲解：** `put` 将数据写入 RegionServer 本地存储，再用 `CreateTableTest` 调用 Master 的 `CreateTable` RPC 注册表元数据（含列定义和默认 Region）。路由表存储在 Zookeeper 中，`GetRouteTable` RPC 从 `RouteTable` 对象读取，包含 `version` 用于客户端缓存失效检测。

---

### 环节四：RegionServer 数据操作命令演示（4分钟）

使用 **RegionServer 交互式控制台** 演示完整 CRUD：

```bash
# 在 RegionServer rs-001 终端操作：

# 4.1 PUT - 插入数据
RegionServer> put users user-1001 name=Alice age=30
# → PUT successful

RegionServer> put users user-1002 name=Bob age=25
# → PUT successful

RegionServer> put users user-1003 name=Charlie age=35
# → PUT successful

# 4.2 GET - 按主键查询
RegionServer> get users user-1001
# → Data:
#     name = Alice
#     age = 30
#     email = alice@test.com

# 4.3 EXISTS - 检查键是否存在
RegionServer> exists users user-1001
# → Key exists

RegionServer> exists users nonexistent-key
# → Key not found

# 4.4 LIST - 范围扫描
RegionServer> list users
# → user-1001 name=Alice age=30
# → user-1002 name=Bob age=25
# → user-1003 name=Charlie age=35

RegionServer> list users 2
# → user-1001 name=Alice age=30
# → user-1002 name=Bob age=25

# 4.5 DELETE - 删除数据
RegionServer> delete users user-1003
# → DELETE successful (key existed)

RegionServer> get users user-1003
# → Key not found

# 4.6 验证数据仍然存在
RegionServer> get users user-1001
# → Data:
#     name = Alice
#     age = 30
#     email = alice@test.com
```

**讲解每个操作的实现（演示中使用控制台路径）：**

| 命令 | 控制台路径（本次演示） | gRPC 客户端路径（全栈） |
|------|----------------------|------------------------|
| `put` | 本地 `insertRow()` → 内存存储 | `put()` gRPC → Region检查 → WAL写入 → Paxos共识 → 内存存储 → 异步复制 |
| `get` | 本地 `selectRow()` → 内存存储 | `get()` gRPC → Region检查 → 内存查询 → 列过滤 |
| `delete` | 本地 `deleteRow()` → 内存删除 | `delete()` gRPC → Region检查 → WAL记录 → Paxos共识 → 内存删除 |
| `exists` | 本地 `existsRow()` → 内存键检查 | `exists()` gRPC → Region检查 → 内存键检查 |
| `list` | 本地 `listRows()` → 内存列表+截断 | `scan()` gRPC → Region检查 → 范围扫描 → 流式返回 |

---

### 环节五：Master-RegionServer 交互机制讲解（2分钟）

**结合代码展示，口头讲解不敲命令：**

**5.1 RegionServer 注册流程**
- `RegionServerMain` 启动 → 初始化 `RegionServerServiceImpl`
- 调用 Master 的 `RegisterRegionServer` RPC
- Master 的 `MasterServiceImpl.registerRegionServer()` → `ClusterManager.registerServer()`
- 心跳线程启动

**5.2 心跳机制**
- RegionServer 每 5 秒发送 `SendHeartbeat` RPC
- Master 的 `HeartbeatMonitor` 每 10 秒检查，30 秒超时标记 OFFLINE
- 心跳响应中可携带命令（打开/关闭/迁移 Region）

**5.3 负载均衡**
- `LoadBalancer` 定期检查（默认 5 分钟）
- 计算各节点负载评分 → 生成 `MigrationPlan` → 逐个执行 `MigrationTask`
- 迁移流程：`Prepare → Sync → Switch` 三阶段状态机

**5.4 数据复制**
- `ReplicationManager` 管理主从副本同步
- `PaxosProposer` + `PaxosAcceptor` 实现简化版 Paxos 一致性
- `WalService` 记录所有写入操作，支持故障恢复重放

---

### 环节六：故障容错演示（3分钟）

```bash
# 6.1 当前集群状态（2节点在线）
java -cp "minisql-admin/target/classes;$AdminCP" com.minisql.admin.MiniSqlAdmin cluster nodes

# 6.2 停掉 rs-002（在 rs-002 终端按 Ctrl+C）
# 或从另一个终端：
# taskkill /F /PID <rs-002-pid>

# 6.3 查看集群状态 → 变为 DEGRADED
java -cp "minisql-admin/target/classes;$AdminCP" com.minisql.admin.MiniSqlAdmin cluster status
# → Cluster Health: DEGRADED
# → Total Servers: 2
# → Online Servers: 1

# 6.4 数据仍然可用（rs-001 上的数据不受影响）
# RegionServer> get users user-1001
# → Key: user-1001, name=Alice, age=30

# 6.5 重启 rs-002
cd minisql-regionserver
mvn exec:java '-Dexec.args=rs-002 8002'

# 6.6 确认集群恢复健康
java -cp "minisql-admin/target/classes;$AdminCP" com.minisql.admin.MiniSqlAdmin cluster status
# → Cluster Health: HEALTHY
# → Online Servers: 2
```

**讲解：** `HeartbeatMonitor` 检测心跳超时 → `FailureRecoveryManager` 处理节点故障 → Region 重新分配。重新上线后自动注册并恢复心跳。

---

### 环节七（可选）：多语言网关测试（2分钟）

如果时间充裕，展示 GatewayServer 的多语言支持能力：

```bash
# 1. 启动 GatewayServer（新终端）
cd minisql-client
mvn exec:java '-Dexec.mainClass=com.minisql.client.gateway.GatewayServer' \
  '-Dgateway.port=9090' '-Dgateway.master=localhost:8000'

# 2. 用 Java GatewayClient 测试
java -cp "bootstrap/;minisql-client/target/minisql-client-1.0-SNAPSHOT.jar;..." \
  GatewayClient localhost 9090
# → Ping 成功 → Execute SQL → 结果
```

**展示要点：**
- Gateway 将 SQL 转换为分布式查询（解析 → 路由 → 并行执行 → 结果合并）
- protobuf 协议是语言无关的 — Python/C++ 均可用相同协议通信
- Python 客户端：`clients/python/minisql_client/client.py`
- C++ 客户端：`clients/cpp/src/minisql_client.h`
- 代码位置：`clients/` 目录

---

### 环节八：Admin CLI 全部命令汇总（1分钟）

展示所有 Admin CLI 命令一览：

| 命令 | 后端 RPC | 功能描述 |
|------|----------|----------|
| `cluster status` | `ClientMasterService.GetClusterHealth` | 集群健康状态 |
| `cluster stats` | `ClientMasterService.GetClusterStats` | 集群统计信息 |
| `cluster nodes` | `AdminService.ListServers` | 节点列表+详情 |
| `cluster balance` | `AdminService.TriggerBalance` | 手动触发负载均衡 |
| `table list` | `ClientMasterService.ListTables` | 表列表 |
| `table describe <t>` | `ClientMasterService.GetTableSchema` | 表结构详情 |
| `table route <t>` | `ClientMasterService.GetRouteTable` | 表路由信息 |

---

## 助教可能问的问题

| 问题 | 答案要点 |
|------|---------|
| 数据存在哪里？ | RegionServer 内存存储（`RegionDataStore`），可选 MySQL 后端 |
| 怎么分片的？ | 范围分片 `[start_key, end_key)`，每个表可配初始 Region 数 |
| 怎么保证数据不丢？ | WAL 日志顺序写，启动时 WAL 恢复重放 |
| 怎么保证一致性？ | 简化版 Paxos：Proposer 发起 Prepare → Acceptor 承诺 → Accept → Commit |
| Master 挂了怎么办？ | Zookeeper 选举切换到备用 Master（`MasterElection` 监听临时节点） |
| 客户端怎么路由？ | 查询 `GetRouteTable` → 缓存到 `RouteCache` → 直连 RegionServer（stale 时自动刷新） |
| 副本怎么同步？ | 主副本写入完成后异步复制（`ReplicationManager`），从副本拉取 WAL 日志 |
| 负载均衡怎么做的？ | 按 Region 数/负载评分计算 `MigrationPlan`，Prepare→Sync→Switch 三阶段迁移，可回滚 |
| 这个系统能跑多快？ | PUT ~15,890 ops/sec, GET ~86,237 ops/sec, DELETE ~103,176 ops/sec |
| 建表怎么建？ | 通过 `ClientMasterService.CreateTable` RPC（Admin CLI 未直接暴露，可通过 RegionServer put 自动建表） |

---

## 给演示者的提示

1. **启动顺序固定：** ZK → Master → RS-001 → RS-002，每个等待就绪再启动下一个
2. **Admin CLI 连接失败：** 检查 `--host` `--port` 参数是否匹配 Master 地址
3. **RegionServer 无法注册：** 检查 ZK 连通性、Master 是否正常
4. **数据在不同 RegionServer 间隔离：** rs-001 写入的数据不能在 rs-002 查到（需查路由表确认）
5. **备用方案：** 如果某步失败，口头说明预期结果和实现原理即可
