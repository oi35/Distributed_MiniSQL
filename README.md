# Distributed MiniSQL

一个用于教学目的的分布式关系型数据库系统，实现了数据分片、副本管理、负载均衡等核心功能。

## 项目概述

Distributed MiniSQL 是一个简化的分布式数据库系统，采用 Master-RegionServer 架构，支持：

- **数据分片**：基于范围的Region分片
- **副本管理**：使用Paxos协议保证一致性
- **负载均衡**：自动检测负载并触发Region迁移
- **高可用**：Master选举和故障转移
- **分布式查询**：支持跨Region的Join查询

## 架构设计

### 系统架构

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │
       ↓
┌─────────────┐      ┌──────────────┐
│   Master    │←────→│  Zookeeper   │
│  (Leader)   │      │  (Consensus) │
└──────┬──────┘      └──────────────┘
       │
       ↓
┌─────────────────────────────┐
│      RegionServers          │
│  ┌────────┐  ┌────────┐    │
│  │Region 1│  │Region 2│... │
│  └────────┘  └────────┘    │
└─────────────────────────────┘
```

### 核心组件

**Master节点：**
- ClusterManager：集群管理、心跳监控、故障恢复
- MetadataManager：表/Region元数据、路由表管理
- LoadBalancer：负载检测、迁移计划生成
- RegionMigrationManager：Region迁移状态机、自动重试
- Zookeeper集成：Master选举、元数据持久化

**RegionServer节点：**
- Region存储和查询执行
- MySQL作为底层存储引擎
- Paxos副本同步

**Client SDK：**
- 路由缓存
- 分布式查询执行
- Hash Join实现

### 模块结构
```
Distributed_MiniSQL/
├── minisql-common/          # 公共模块（protobuf定义）✅
├── minisql-master/          # Master服务（100%完成）✅
│   ├── cluster/            # 集群管理 ✅
│   ├── metadata/           # 元数据管理 ✅
│   ├── balance/            # 负载均衡 + 迁移管理 ✅
│   ├── service/            # gRPC服务实现 ✅
│   ├── zk/                 # Zookeeper集成 ✅
│   └── integration/        # 集成测试（200个测试）✅
│       ├── fixtures/       # 测试工具类（5个）
│       ├── fast/           # Fast层测试（9个）
│       ├── e2e/            # E2E层测试（8个）
│       └── stress/         # Stress层测试（11个）
├── minisql-regionserver/    # RegionServer服务 ⏳
└── minisql-client/          # 客户端SDK ⏳
```

## 快速开始

### 环境要求

- Java 17+
- Maven 3.8+
- Zookeeper 3.9+
- Docker（用于运行集成测试）

### 构建项目

```bash
# 克隆仓库
git clone https://github.com/oi35/Distributed_MiniSQL.git
cd Distributed_MiniSQL

# 构建所有模块
mvn clean install
```

### 启动服务

**1. 启动Zookeeper**

```bash
docker run -d --name zookeeper -p 2181:2181 zookeeper:3.9.1
```

**2. 启动Master**

```bash
cd minisql-master
mvn exec:java -Dexec.mainClass="com.minisql.master.MasterServer"
```

**3. 测试Master选举（可选）**

```bash
# 终端1 - Master-1
export MASTER_PORT=8000
export MASTER_ID=master-1
mvn exec:java -Dexec.mainClass="com.minisql.master.MasterServer"

# 终端2 - Master-2
export MASTER_PORT=8001
export MASTER_ID=master-2
mvn exec:java -Dexec.mainClass="com.minisql.master.MasterServer"
```

### 运行测试

```bash
# 运行所有单元测试
cd minisql-master
mvn test

# 运行Fast层集成测试（无需Docker）
mvn test -Pintegration-fast

# 运行E2E层集成测试（需要Docker）
mvn test -Dtest=EndToEndMigrationTest,EndToEndFailoverTest

# 运行Stress层集成测试（需要Docker）
mvn test -Dtest=MasterElectionStressTest,FailureRecoveryIntegrationTest

# 查看测试覆盖率
mvn clean test jacoco:report
# 报告位置：target/site/jacoco/index.html
```

## 项目状态

### Master模块：100%完成 ✅

**核心功能：**
- ✅ ClusterManager - 集群管理、心跳监控、故障恢复
- ✅ MetadataManager - 表/Region元数据、路由表管理
- ✅ LoadBalancer - 负载检测、迁移计划生成、自动均衡
- ✅ RegionMigrationManager - 迁移状态机、自动重试、统计信息
- ✅ Zookeeper集成 - Master选举、元数据持久化
- ✅ 完整的集成测试套件

**测试覆盖（200个测试，100%通过）：**
- 单元测试：172/172 通过 ✅
- Fast层：9/9 通过 ✅
- E2E层：8/8 通过 ✅
- Stress层：11/11 通过 ✅
- **总计：200/200 通过（100%）** ✅

**代码质量：**
- Balance包覆盖率：94%（指令），89%（分支）
- 完整的Javadoc文档
- 线程安全设计
- 生产级代码质量

**技术亮点：**
- 可配置超时系统（测试/生产环境分离）
- 自动心跳功能（测试工具）
- 快速故障检测（12秒 vs 40秒）
- 完整的三层测试架构（Fast/E2E/Stress）

### 其他模块：待开发 ⏳

- ⏳ RegionServer模块
- ⏳ 副本管理和Paxos
- ⏳ Client SDK

## 文档

### 设计文档

- [整体架构设计](docs/superpowers/specs/2026-04-15-distributed-minisql-design.md)
- [LoadBalancer设计](docs/superpowers/specs/2026-04-20-loadbalancer-design.md)
- [RegionMigrationManager设计](docs/superpowers/specs/2026-04-26-regionmigrationmanager-design.md)
- [Zookeeper集成设计](docs/superpowers/specs/2026-04-18-zookeeper-integration-design.md)
- [集成测试设计](docs/superpowers/specs/2026-04-28-master-integration-testing-design.md)
- [Master总体详细设计](docs/superpowers/specs/2026-04-29-master-module-complete-design.md)

### 使用指南

- [RegionMigrationManager快速指南](docs/RegionMigrationManager-QuickStart.md)
- [使用示例代码](minisql-master/src/test/java/com/minisql/master/balance/RegionMigrationManagerUsageExample.java)

### 开发指南

- [团队分工](docs/team-division.md)
- [Claude Code配置](CLAUDE.md)

## 技术栈

- **语言**：Java 17
- **构建工具**：Maven
- **RPC框架**：gRPC + Protobuf
- **协调服务**：Apache Zookeeper
- **存储引擎**：MySQL（待集成）
- **测试框架**：JUnit 4, Mockito, Testcontainers, Awaitility
- **日志**：SLF4J + Logback
- **代码覆盖**：JaCoCo

## 测试架构

### 三层测试架构

**Fast层（无Docker）：**
- 使用嵌入式Zookeeper
- 快速验证核心功能
- 执行时间：~2分钟
- 适合日常开发

**E2E层（需Docker）：**
- 使用真实Zookeeper容器
- 端到端流程测试
- 执行时间：~3分钟
- 验证完整功能

**Stress层（需Docker）：**
- 高负载场景测试
- 故障恢复测试
- 执行时间：~3分钟
- 验证系统稳定性

### 测试配置

**生产环境：**
- 心跳超时：30秒
- 监控间隔：10秒

**测试环境：**
- 心跳超时：10秒
- 监控间隔：2秒

## 性能目标

- 单表点查询延迟：< 10ms
- 单表范围查询QPS：> 1000
- 两表Join查询延迟：< 100ms
- 系统可用性：> 99%

## 贡献指南

### 代码规范

- 遵循 Google Java Style Guide
- 使用 SLF4J 进行日志记录
- 完整的 Javadoc 文档
- 测试覆盖率 > 70%（关键模块 > 85%）

### Git提交格式

```
<type>(<scope>): <description>

[optional body]
```

**类型：**
- `feat`: 新功能
- `fix`: Bug修复
- `refactor`: 代码重构
- `test`: 添加测试
- `docs`: 文档更新
- `build`: 构建系统变更

**示例：**
```
feat(master): implement region assignment logic
fix(regionserver): handle region split edge case
test(paxos): add concurrent proposal tests
```

### 开发流程

1. Fork 项目
2. 创建 feature 分支
3. 遵循 TDD 开发
4. 提交 Pull Request
5. 代码审查
6. 合并到 develop 分支

## 团队

- **成员1**：架构负责人 + Master模块（100%完成）
- **成员2**：RegionServer模块
- **成员3**：副本管理和Paxos
- **成员4**：Client SDK
- **成员5**：测试和工具

## 许可证

本项目仅用于教学目的。

## 联系方式

- GitHub Issues: https://github.com/oi35/Distributed_MiniSQL/issues
- 项目主页: https://github.com/oi35/Distributed_MiniSQL

---

**最后更新：** 2026-05-06  
**Master模块状态：** ✅ 100%完成，200/200测试通过
