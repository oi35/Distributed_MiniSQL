# Master模块集成测试 - 详细设计

**设计日期：** 2026-04-28  
**设计版本：** v1.0  
**负责人：** 成员1 - 架构负责人 + Master模块开发

---

## 1. 概述

### 1.1 目标

为Master模块实现完整的集成测试套件，验证：
1. Master-RegionServer端到端集成
2. LoadBalancer端到端功能
3. Master选举压力测试
4. 故障恢复流程

### 1.2 设计原则

- **分层测试**：快速测试（CI）+ 深度测试（PR）+ 压力测试（夜间）
- **混合环境**：半真实环境（大部分）+ 完全真实环境（关键场景）
- **渐进模拟**：Mock RegionServer（快速）+ Fake RegionServer（真实RPC）
- **完整覆盖**：正常流程 + 边界场景 + 故障场景

### 1.3 测试分层

**Fast层（~2秒/测试）：**
- 嵌入式Zookeeper
- In-process gRPC
- Mock RegionServer
- CI每次提交运行

**E2E层（~10秒/测试）：**
- Testcontainers Zookeeper
- 真实gRPC服务器
- Fake RegionServer
- PR合并前运行

**Stress层（~30秒-2分钟/测试）：**
- 完全真实环境
- 多实例并发
- 故障注入
- 夜间构建运行

---

## 2. 整体架构

### 2.1 目录结构

```
minisql-master/src/test/java/com/minisql/master/
├── integration/                    # 新增：集成测试包
│   ├── fast/                      # 快速集成测试
│   │   ├── MasterRegionServerIntegrationTest.java
│   │   └── LoadBalancerEndToEndTest.java
│   ├── e2e/                       # 端到端测试
│   │   ├── EndToEndMigrationTest.java
│   │   └── EndToEndFailoverTest.java
│   ├── stress/                    # 压力和故障测试
│   │   ├── MasterElectionStressTest.java
│   │   └── FailureRecoveryIntegrationTest.java
│   └── fixtures/                  # 测试基础设施
│       ├── EmbeddedZookeeperServer.java
│       ├── FakeRegionServer.java
│       ├── InProcessGrpcServer.java
│       └── TestClusterBuilder.java
├── balance/                       # 现有单元测试
├── cluster/
└── metadata/
```

### 2.2 依赖管理

**新增Maven依赖：**
- `curator-test` - 嵌入式Zookeeper
- `grpc-testing` - In-process gRPC
- `testcontainers` - 容器化测试环境
- `awaitility` - 异步验证

**Maven Profile配置：**
- `integration-fast` - 运行Fast层
- `integration-e2e` - 运行E2E层
- `integration-stress` - 运行Stress层
- `integration-all` - 运行全部集成测试

---

## 3. 测试基础设施（Fixtures）

### 3.1 EmbeddedZookeeperServer

**职责：** 提供嵌入式Zookeeper用于快速测试

**核心方法：**
```java
public class EmbeddedZookeeperServer {
    void start()                    // 启动嵌入式ZK
    void stop()                     // 停止并清理
    String getConnectString()       // 返回连接字符串
}
```

**实现：** 使用Curator TestingServer

### 3.2 FakeRegionServer

**职责：** 模拟RegionServer的gRPC服务

**核心功能：**
- 实现MasterService中RegionServer相关的RPC方法
- 模拟Region数据（内存Map存储）
- 支持故障注入（延迟、失败、断连）

**关键方法：**
```java
public class FakeRegionServer {
    void start(int port)                           // 启动gRPC服务
    void stop()                                    // 停止服务
    void addRegion(String regionId, long sizeBytes) // 添加Region
    void setFailureMode(FailureMode mode)          // 注入故障
    List<String> getRegions()                      // 获取Region列表
}
```

**故障模式：**
- `NONE` - 正常运行
- `SLOW` - 模拟慢响应
- `FAIL_PREPARE` - 准备阶段失败
- `FAIL_SYNC` - 同步阶段失败
- `DISCONNECT` - 模拟断连

### 3.3 InProcessGrpcServer

**职责：** 提供in-process gRPC服务器用于快速测试

**特点：**
- 无网络开销
- 真实序列化/反序列化
- 支持多个服务注册

**核心方法：**
```java
public class InProcessGrpcServer {
    void start(String serverName)              // 启动in-process服务器
    void stop()                                // 停止服务器
    void addService(BindableService service)   // 注册服务
    ManagedChannel createChannel()             // 创建客户端Channel
}
```

### 3.4 TestClusterBuilder

**职责：** 构建测试集群的Builder模式工具

**示例用法：**
```java
TestCluster cluster = TestClusterBuilder.create()
    .withZookeeper(embedded)
    .withMaster(port, serverId)
    .withFakeRegionServer("rs-001", 3)  // 3个Region
    .withFakeRegionServer("rs-002", 2)
    .build();
```

**核心方法：**
```java
public class TestClusterBuilder {
    TestClusterBuilder withZookeeper(EmbeddedZookeeperServer zk)
    TestClusterBuilder withMaster(int port, String serverId)
    TestClusterBuilder withFakeRegionServer(String serverId, int regionCount)
    TestCluster build()
}
```

---

## 4. Fast层测试详细设计

### 4.1 MasterRegionServerIntegrationTest

**测试场景：** Master与RegionServer的基础交互

**环境配置：**
- 嵌入式Zookeeper
- In-process gRPC
- Mock RegionServer（MigrationExecutor）

**测试用例：**

**4.1.1 testRegionServerRegistration**
- 启动Master
- RegionServer注册
- 验证ClusterManager中有该服务器
- 验证服务器状态为ONLINE

**4.1.2 testHeartbeatMechanism**
- RegionServer定期发送心跳
- 验证心跳时间戳更新
- 验证服务器保持ONLINE状态

**4.1.3 testHeartbeatTimeout**
- RegionServer停止发送心跳
- 等待超时时间（30秒）
- 验证服务器状态变为OFFLINE
- 验证HeartbeatMonitor检测到故障

**4.1.4 testFailureRecovery**
- RegionServer故障
- 验证FailureRecoveryManager触发
- 验证Region重新分配逻辑

**预期执行时间：** ~8秒（4个测试）

### 4.2 LoadBalancerEndToEndTest

**测试场景：** LoadBalancer完整功能验证

**环境配置：**
- 嵌入式Zookeeper
- In-process gRPC
- Mock RegionServer
- 真实LoadBalancer和RegionMigrationManager

**测试用例：**

**4.2.1 testLoadDetectionAndPlanGeneration**
- 创建负载不均衡场景（rs-001: 5个Region, rs-002: 1个Region）
- 调用LoadBalancer.needsBalance()
- 验证返回true
- 调用LoadBalancer.generateMigrationPlans()
- 验证生成迁移计划

**4.2.2 testMigrationLifecycle**
- 提交迁移任务
- 验证状态转换：PENDING → PREPARING → SYNCING → SWITCHING → COMPLETED
- 验证每个阶段的超时控制
- 验证最终元数据更新

**4.2.3 testConcurrentMigrationLimit**
- 提交多个迁移任务
- 验证并发迁移数量限制（默认2个）
- 验证canStartNewMigration()正确返回

**4.2.4 testMigrationRetry**
- 注入迁移失败
- 验证自动重试（最多3次）
- 验证指数退避（1min, 2min, 4min）
- 验证最终成功或进入FAILED状态

**4.2.5 testMigrationRollback**
- 在SYNCING阶段注入失败
- 验证进入ROLLBACK状态
- 验证回滚操作执行
- 验证最终状态为FAILED

**预期执行时间：** ~15秒（5个测试）

---

## 5. E2E层测试详细设计

### 5.1 EndToEndMigrationTest

**测试场景：** 使用真实gRPC和FakeRegionServer验证完整迁移流程

**环境配置：**
- Testcontainers Zookeeper
- 真实gRPC服务器（独立端口）
- FakeRegionServer（实现gRPC接口）

**测试用例：**

**5.1.1 testCompleteMigrationWithRealGrpc**
- 启动Master和2个FakeRegionServer
- 提交迁移任务
- 验证真实gRPC调用序列
- 验证消息序列化/反序列化
- 验证迁移完成

**5.1.2 testMigrationWithDataSync**
- FakeRegionServer包含模拟数据
- 执行迁移
- 验证数据同步过程
- 验证同步进度更新
- 验证数据完整性

**5.1.3 testMultiRegionMigration**
- 同时迁移3个Region
- 验证并发控制
- 验证所有迁移完成
- 验证无资源竞争

**5.1.4 testMigrationWithSlowNetwork**
- FakeRegionServer设置延迟（500ms）
- 验证超时配置生效
- 验证迁移仍能完成

**预期执行时间：** ~40秒（4个测试）

### 5.2 EndToEndFailoverTest

**测试场景：** Master选举和故障转移的端到端验证

**环境配置：**
- Testcontainers Zookeeper
- 多个真实Master实例
- FakeRegionServer

**测试用例：**

**5.2.1 testMasterElectionWithMultipleInstances**
- 启动3个Master实例
- 验证只有1个成为Leader
- 验证其他2个为Standby
- 验证Leader可以处理请求

**5.2.2 testFailoverWhenActiveMasterDies**
- 启动3个Master
- 杀死Leader
- 验证新Leader在10秒内选出
- 验证新Leader接管服务

**5.2.3 testServiceContinuityAfterFailover**
- 故障转移前提交迁移任务
- 触发故障转移
- 验证任务继续执行
- 验证客户端请求不中断

**5.2.4 testMetadataConsistencyAfterFailover**
- 故障转移前创建表和Region
- 触发故障转移
- 验证新Leader读取到相同元数据
- 验证元数据无丢失

**预期执行时间：** ~50秒（4个测试）

---

## 6. Stress层测试详细设计

### 6.1 MasterElectionStressTest

**测试场景：** Master选举的压力和边界场景

**环境配置：**
- Testcontainers Zookeeper
- 多Master实例（最多10个）

**测试用例：**

**6.1.1 testRapidMasterFailover**
- 启动5个Master
- 连续杀死Leader 5次
- 验证每次都能选出新Leader
- 验证选举时间 < 10秒
- 验证无数据丢失

**6.1.2 testConcurrentMasterStartup**
- 同时启动10个Master
- 验证只有1个成为Leader
- 验证选举过程无死锁
- 验证所有Standby正确监听

**6.1.3 testNetworkPartition**
- 启动3个Master（M1, M2, M3）
- M1为Leader
- 模拟网络分区（M1与ZK断开）
- 验证M2或M3成为新Leader
- 恢复网络后验证M1变为Standby

**6.1.4 testSplitBrainPrevention**
- 模拟网络分区导致两个Master都认为自己是Leader
- 验证Zookeeper的临时节点机制防止脑裂
- 验证只有一个Master能写入元数据

**6.1.5 testZookeeperSessionExpiry**
- Leader的ZK会话过期
- 验证Leader检测到并退出
- 验证新Leader选出
- 验证服务恢复

**预期执行时间：** ~2分钟（5个测试）

### 6.2 FailureRecoveryIntegrationTest

**测试场景：** 完整故障场景的恢复验证

**环境配置：**
- Testcontainers Zookeeper
- 真实gRPC
- FakeRegionServer（支持故障注入）

**测试用例：**

**6.2.1 testRegionServerCrashDuringMigration**
- 启动迁移任务
- 在SYNCING阶段杀死源RegionServer
- 验证迁移进入ROLLBACK
- 验证回滚完成
- 验证Region状态恢复

**6.2.2 testSourceServerFailureDuringSync**
- 迁移进行中
- 源服务器在同步阶段失败
- 验证检测到故障（心跳超时）
- 验证迁移自动重试或回滚
- 验证数据一致性

**6.2.3 testTargetServerFailureDuringSwitch**
- 迁移到SWITCHING阶段
- 目标服务器失败
- 验证迁移回滚
- 验证Region仍在源服务器可用

**6.2.4 testMultipleServersCrashSimultaneously**
- 4个RegionServer运行
- 同时杀死2个
- 验证故障检测
- 验证Region重新分配
- 验证系统仍可用

**6.2.5 testZookeeperConnectionLoss**
- Master运行中
- 断开Zookeeper连接
- 验证Master检测到连接丢失
- 验证Master停止服务
- 恢复连接后验证Master重新选举

**6.2.6 testDataInconsistencyDetection**
- 模拟元数据不一致（Region在多个服务器）
- 验证Master检测到不一致
- 验证触发修复流程
- 验证最终一致性

**预期执行时间：** ~3分钟（6个测试）

---

## 7. 测试执行策略

### 7.1 CI/CD集成

**CI流程：**
- **每次提交：** 运行Fast层（~30秒）
- **PR合并前：** 运行Fast + E2E层（~2分钟）
- **每日构建：** 运行全部测试（~5分钟）

### 7.2 Maven Profile配置

**pom.xml配置：**
```xml
<profiles>
    <profile>
        <id>integration-fast</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <includes>
                            <include>**/integration/fast/**/*Test.java</include>
                        </includes>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
    
    <profile>
        <id>integration-e2e</id>
        <!-- 类似配置，包含e2e目录 -->
    </profile>
    
    <profile>
        <id>integration-stress</id>
        <!-- 类似配置，包含stress目录 -->
    </profile>
    
    <profile>
        <id>integration-all</id>
        <!-- 包含所有integration目录 -->
    </profile>
</profiles>
```

**执行命令：**
```bash
# 运行Fast层
mvn test -Pintegration-fast

# 运行E2E层
mvn test -Pintegration-e2e

# 运行Stress层
mvn test -Pintegration-stress

# 运行全部集成测试
mvn test -Pintegration-all
```

---

## 8. 依赖和工具

### 8.1 新增Maven依赖

```xml
<!-- Curator Test - 嵌入式Zookeeper -->
<dependency>
    <groupId>org.apache.curator</groupId>
    <artifactId>curator-test</artifactId>
    <version>5.5.0</version>
    <scope>test</scope>
</dependency>

<!-- gRPC Testing - In-process gRPC -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-testing</artifactId>
    <version>${grpc.version}</version>
    <scope>test</scope>
</dependency>

<!-- Testcontainers - 容器化测试 -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>

<!-- Awaitility - 异步验证 -->
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <version>4.2.0</version>
    <scope>test</scope>
</dependency>
```

### 8.2 测试工具选择

**Curator TestingServer：**
- 嵌入式Zookeeper
- 快速启动（~1秒）
- 适合Fast层

**Testcontainers：**
- 真实Zookeeper容器
- 完全隔离
- 适合E2E和Stress层

**gRPC In-Process：**
- 无网络开销
- 真实序列化
- 适合Fast层

**Awaitility：**
- 异步条件等待
- 替代Thread.sleep()
- 提高测试稳定性

---

## 9. 实施计划

### 9.1 阶段划分

**阶段1：基础设施（1天）**
- EmbeddedZookeeperServer
- InProcessGrpcServer
- TestClusterBuilder
- Maven Profile配置

**阶段2：Fast层测试（1天）**
- MasterRegionServerIntegrationTest
- LoadBalancerEndToEndTest

**阶段3：FakeRegionServer（0.5天）**
- 实现gRPC接口
- 故障注入机制

**阶段4：E2E层测试（1天）**
- EndToEndMigrationTest
- EndToEndFailoverTest

**阶段5：Stress层测试（1.5天）**
- MasterElectionStressTest
- FailureRecoveryIntegrationTest

**总计：** 5天

### 9.2 验收标准

**功能完整性：**
- ✅ 所有测试用例实现
- ✅ 覆盖4类集成测试
- ✅ 覆盖完整故障场景

**质量标准：**
- ✅ 所有测试通过
- ✅ Fast层执行时间 < 30秒
- ✅ E2E层执行时间 < 2分钟
- ✅ Stress层执行时间 < 5分钟
- ✅ 无测试不稳定性（flaky tests）

**文档完整性：**
- ✅ 测试用例文档
- ✅ 执行指南
- ✅ 故障排查指南

---

## 10. 风险和缓解

### 10.1 风险识别

**风险1：测试不稳定**
- 原因：异步操作、时间依赖
- 缓解：使用Awaitility、增加超时容忍度

**风险2：环境依赖**
- 原因：Docker、端口占用
- 缓解：动态端口分配、环境检查

**风险3：执行时间过长**
- 原因：容器启动、网络延迟
- 缓解：分层执行、并行测试

**风险4：FakeRegionServer不够真实**
- 原因：简化实现
- 缓解：逐步完善、参考真实实现

---

## 11. 后续扩展

### 11.1 短期扩展

- 添加性能基准测试
- 添加内存泄漏检测
- 添加并发压力测试

### 11.2 长期扩展

- 集成真实RegionServer（模块完成后）
- 添加混沌工程测试
- 添加分布式追踪验证

