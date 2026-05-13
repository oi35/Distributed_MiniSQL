  # 分布式MiniSQL系统 - 团队分工方案

**项目名称：** 分布式MiniSQL系统  
**团队规模：** 5人  
**分工日期：** 2026-04-15  
**最后更新：** 2026-05-12

## 团队成员与职责分配

### 成员1：架构负责人 + Master模块开发

**主要职责：**
- 整体架构设计和技术选型
- Master节点核心功能开发
- 团队技术协调和代码审查

**具体任务：**
1. **Master服务开发**
   - ClusterManager（集群管理器）
   - RegionManager（Region管理器）
   - LoadBalancer（负载均衡器）
   - MetadataManager（元数据管理器）

2. **Zookeeper集成**
   - Master选举机制
   - 元数据持久化
   - 配置管理

3. **接口定义**
   - 定义Master与RegionServer的RPC接口
   - 定义Master与Client的RPC接口

**技能要求：**
- 熟悉分布式系统架构
- 掌握Java开发
- 了解Zookeeper使用


---

### 成员2：RegionServer模块开发

**主要职责：**
- RegionServer核心功能开发
- Region管理和数据存储

**具体任务：**
1. **RegionServer服务开发**
   - RegionContainer（Region容器）
   - QueryExecutor（查询执行器）
   - 数据CRUD操作实现

2. **MySQL集成**
   - MySQL连接池管理
   - SQL执行和结果处理
   - Region数据存储设计

3. **Region管理**
   - Region加载和卸载
   - Region分裂逻辑
   - Region状态维护

**技能要求：**
- 熟悉Java开发
- 掌握MySQL数据库
- 了解JDBC使用


---

### 成员3：副本管理与一致性协议开发

**主要职责：**
- 副本同步机制开发
- Paxos一致性协议实现
- WAL日志系统开发

**具体任务：**
1. **副本管理**
   - ReplicationManager（副本管理器）
   - 主从副本同步逻辑
   - 副本故障检测和恢复

2. **Paxos协议实现**
   - Prepare-Accept-Commit流程
   - 提案编号生成和管理
   - 多数派确认机制
   - 简化版两阶段提交（教学版本）

3. **WAL日志系统**
   - WALManager（WAL管理器）
   - 日志写入和读取
   - 日志滚动和清理
   - 故障恢复重放

**技能要求：**
- 理解分布式一致性算法
- 熟悉Java开发
- 了解日志系统设计


---

### 成员4：客户端与分布式查询开发

**主要职责：**
- 客户端SDK开发
- 分布式查询和Join实现
- 多语言客户端支持

**具体任务：**
1. **客户端核心功能**
   - ConnectionManager（连接管理器）
   - RouteCache（路由缓存）
   - QueryParser（查询解析器）
   - JoinExecutor（Join执行器）

2. **分布式查询**
   - SQL解析和路由
   - 多Region并行查询
   - 结果合并和排序
   - 查询下推优化

3. **Join实现**
   - Hash Join算法
   - 两阶段Join执行
   - 数据收集和计算

4. **多语言客户端**
   - Java客户端（主要）
   - C/C++客户端接口（`clients/cpp/` — C++17 gRPC 客户端） — ✅ 已完成
   - Python客户端接口（`clients/python/` — Python 3.8+ gRPC 客户端） — ✅ 已完成

**技能要求：**
- 熟悉Java开发
- 了解SQL解析
- 掌握多语言编程


---

### 成员5：测试、工具与文档

**主要职责：**
- 测试框架搭建和测试用例编写
- CLI管理工具开发
- 系统部署和配置
- 文档编写和维护

**具体任务与交付物：**
1. **测试开发**
   - 单元测试框架搭建（JUnit 4.13.2 + Mockito 5.7.0/5.14.2）
   - 集成测试用例编写（17个E2E集成测试，含内嵌Zookeeper）
   - 系统测试场景设计 ✅（docs/system-test-scenarios.md）
   - 性能测试 ✅（基准测试：PUT ~15,890 ops/sec, GET ~86,237 ops/sec）
   - 压力测试 ✅（10线程并发混合读写，~168K ops/sec，0错误）
   - 容错测试
   - **覆盖率工具**：JaCoCo 0.8.13

2. **CLI管理工具 (minisql-admin)**
   - `cluster status` — 查看集群健康状态
   - `cluster stats` — 查看集群统计信息
   - `cluster nodes` — 列出所有RegionServer节点
   - `cluster balance` — 触发手动负载均衡
   - `table list` — 列出所有表
   - `table describe <tableName>` — 查看表结构
   - `table route <tableName>` — 查看表路由信息

3. **部署与配置**
   - 集群部署脚本（bootstrap/ 目录）
   - 配置文件模板（regionserver.conf）
   - 启动和停止脚本
   - 环境搭建文档

4. **文档编写**
   - 用户手册（docs/user-manual.md）
   - API文档（docs/api-documentation.md）
   - 部署指南（docs/deployment-guide.md）

**技能要求：**
- 熟悉测试框架（JUnit 4、Mockito）
- 掌握Shell脚本
- 良好的文档编写能力


## 协作与接口约定

### 模块间接口

**Master ↔ RegionServer**
- 成员1和成员2需要协商定义RPC接口
- 心跳协议、Region分配协议
- 负责人：成员1

**RegionServer ↔ 副本管理**
- 成员2和成员3需要协商副本同步接口
- WAL日志格式定义
- 负责人：成员3

**Client ↔ Master/RegionServer**
- 成员4需要与成员1、成员2协商客户端接口
- 查询协议、数据传输格式
- 负责人：成员4

### 公共组件

**gRPC协议定义**
- 由成员1统一定义.proto文件
- 所有成员共同遵守

**配置文件格式**
- 由成员1定义配置文件结构
- 成员5负责配置管理工具

**日志格式**
- 统一日志格式：`[timestamp] [level] [component] [thread] message`
- 所有成员遵守

## 开发里程碑

### 基础框架搭建 ✅ 已完成

**成员1：**
- 搭建项目框架
- 定义RPC接口
- 实现Master基本服务

**成员2：**
- 实现RegionServer基本服务
- 集成MySQL连接

**成员3：**
- 设计WAL日志格式
- 实现基础日志功能

**成员4：**
- 实现客户端连接管理
- 实现基本的SQL解析

**成员5：**
- 搭建测试框架
- 编写部署脚本
- CLI管理工具基础框架

**里程碑：** 基础服务可以启动，Master和RegionServer可以通信 — ✅ 达成

---

### 功能开发阶段 ✅ 已完成

**成员1：**
- 实现Region分配逻辑
- 实现集群管理功能
- 集成Zookeeper
- 实现负载均衡 + Region迁移（多阶段状态机）

**成员2：**
- 实现CRUD操作（单行、批量、范围扫描）
- 实现Region管理（打开、关闭、迁移）
- 实现高级查询（Count、Aggregate、Filter）

**成员3：**
- 实现副本同步（ReplicationLogService）
- 实现Paxos协议（Acceptor/Proposer/ConsensusResult）
- 实现WAL日志系统（写入、读取、归档、恢复）

**成员4：**
- 实现查询路由（RouteCache + 自动刷新）
- 实现多Region并行查询（ParallelScanner）
- 实现Hash Join（JoinExecutor）
- 实现SQL解析引擎（jsqlparser）

**成员5：**
- 编写单元测试覆盖所有模块
- 编写集成测试（含内嵌Zookeeper的E2E测试）
- 完善CLI工具（cluster + table全命令）
- 构建覆盖率报告（JaCoCo）
- 编写全部文档

**里程碑：** 系统可以进行基本的CRUD操作，支持副本同步 — ✅ 达成

---

### 测试完善与优化阶段 ✅ 已完成

**成员1：**
- 优化负载均衡策略
- 完善Region迁移稳定性

**成员2：**
- 优化查询性能
- 实现缓存机制

**成员3：**
- 优化副本同步性能
- 完善故障恢复

**成员4：**
- 优化Join性能
- 实现查询优化
- 多语言客户端 — *待实现*

**成员5：**
- 系统集成测试 ✅（313测试全部通过）
- 性能测试 ✅（PUT 15,890 ops/sec, GET 86,237 ops/sec）
- 压力测试 ✅（10线程并发168K ops/sec，0错误）
- 文档维护 ✅

**里程碑：** 系统功能完整，通过所有测试 — 🔄 进行中

---

### 交付阶段 ✅ 进行中

**全员：**
- Bug修复
- 性能优化
- 文档完善
- 准备演示

**里程碑：** 项目完成，准备交付

## 沟通与协作机制

### 每日站会（15分钟）
- 每天早上10:00
- 每人汇报：昨天完成、今天计划、遇到的问题
- 协调接口对接和依赖关系

### 每周评审会（1小时）
- 每周五下午3:00
- 演示本周完成的功能
- 讨论下周计划
- 技术难点讨论

### 代码审查
- 所有代码提交前需要至少一人审查
- 关键模块（Master、副本管理）需要架构负责人审查
- 使用Pull Request流程

### 技术文档
- 每个模块需要编写接口文档
- 重要设计决策需要记录ADR（Architecture Decision Record）
- 由成员5统一整理和维护

## 技术栈与工具

### 开发环境
- **语言**：Java 11
- **构建工具**：Maven 3.6+
- **IDE**：IntelliJ IDEA（推荐）
- **代码覆盖率**：JaCoCo 0.8.13

### 核心依赖
- **RPC框架**：gRPC 1.58.0（netty-shaded）
- **序列化**：Protobuf 3.24.0
- **数据库**：MySQL 8.0+
- **协调服务**：Apache Zookeeper 3.9.1
- **日志框架**：SLF4J 2.0.9 + Logback/Simple
- **测试框架**：JUnit 4.13.2 + Mockito 5.7.0/5.14.2 + Awaitility 4.2.0
- **SQL解析**：JSQLParser 4.6
- **连接池**：HikariCP 5.0.1
- **Guava**: 32.1.3-jre

### 开发工具
- **版本控制**：Git
- **代码仓库**：GitHub

## 质量保证

### 代码规范
- 遵循Google Java Style Guide
- 统一的项目结构约定

### 测试要求
- 单元测试覆盖率 > 70%（关键模块 > 85%）
- 所有公共接口必须有测试
- 集成测试使用内嵌Zookeeper（Curator TestingServer），不依赖外部服务
- 测试报告位置：`<module>/target/site/jacoco/index.html`

### 当前测试状态（截至2026-05-12）

| 模块 | 测试数 | 指令覆盖率 | 关键子模块覆盖率 |
|------|--------|-----------|-----------------|
| minisql-master | 189 | 67% | balance 93%, cluster 85% |
| minisql-regionserver | 53 | 8%(整体) | WAL 70%, replication 49% |
| minisql-client | 55 | 37%(整体) | route 91%, core 83%, conn 77% |
| minisql-admin | 16 | 77% | (新增, 原为0) |
| **总计** | **313** | — | 全部通过, 0失败 |

> 注：regionserver和client的覆盖率因proto自动生成代码（低覆盖率）拉低了整体数据。实际业务代码覆盖率高于指标。
> 注2：新增7个基准测试和压力测试（CrudBenchmarkTest: 4, ConcurrencyStressTest: 3）位于minisql-client模块。

## 风险与应对

### 技术风险

**风险1：Paxos协议实现复杂**
- **应对**：先实现简化版两阶段提交，后期优化 — ✅ 已完成简化实现
- **负责人**：成员3
- **备选方案**：使用Raft协议替代

**风险2：Region迁移数据一致性**
- **应对**：采用双写机制，充分测试
- **负责人**：成员1、成员2
- **备选方案**：暂停写入，完成迁移后恢复

**风险3：分布式Join性能**
- **应对**：先实现功能，后期优化
- **负责人**：成员4
- **备选方案**：限制Join的数据量

### 进度风险

**风险1：某个成员进度落后**
- **应对**：每周评审及时发现，其他成员协助
- **负责人**：成员1（架构负责人）

**风险2：模块间接口不匹配**
- **应对**：提前定义接口，定期对接测试
- **负责人**：成员1

## 附录：技术参考资料

### 分布式系统
- 《Designing Data-Intensive Applications》
- HBase官方文档
- Google Bigtable论文

### 一致性协议
- Paxos Made Simple论文
- Raft协议论文
- 《分布式系统原理与范型》

### 数据库
- MySQL官方文档
- 《高性能MySQL》

### 开发工具
- gRPC官方文档
- Zookeeper官方文档

---

**文档结束**
