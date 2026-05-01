# 阶段5：负载均衡与Region迁移 - 详细设计

**设计日期：** 2026-04-19  
**设计版本：** v1.0  
**负责人：** 成员1 - 架构负责人 + Master模块开发

---

## 1. 概述

### 1.1 目标

实现Master模块的负载均衡和Region迁移功能，包括：
- 动态负载均衡监控和自动触发
- 智能Region分配策略
- 完整的Region迁移流程（双写机制）
- 混合触发模式（自动+手动）
- 完善的故障处理和恢复

### 1.2 设计原则

- **控制与数据分离**：Master控制流程，RegionServer直接传输数据
- **状态机驱动**：明确的状态转换，便于监控和调试
- **故障容错**：支持重试、回滚和人工介入
- **教学友好**：清晰的日志和可观测性

### 1.3 核心特性

1. **综合负载评分**：基于Region数量、数据大小、CPU、内存的综合评分
2. **智能Region选择**：考虑迁移效果、热点、Region状态
3. **双写机制**：迁移期间保持服务可用
4. **混合故障处理**：自动重试+自动回滚+人工介入
5. **完整测试覆盖**：单元、集成、系统、故障注入测试

---

## 2. 整体架构

### 2.1 组件结构

**Master端组件：**

```
com.minisql.master.balance/
├── LoadBalancer.java                    # 负载均衡器
├── LoadBalancePolicy.java               # 负载均衡策略接口
├── DefaultLoadBalancePolicy.java        # 默认策略实现
├── RegionMigrationManager.java          # 迁移管理器
├── MigrationTask.java                   # 迁移任务
├── MigrationExecutor.java               # 迁移执行器
├── MigrationState.java                  # 迁移状态枚举
└── MigrationException.java              # 迁移异常
```

**RegionServer端组件（扩展）：**

```
com.minisql.regionserver.migration/
├── DoubleWriteProxy.java                # 双写代理
├── RegionTransferService.java           # Region传输服务
├── RegionExporter.java                  # Region导出器
└── RegionImporter.java                  # Region导入器
```

### 2.2 核心交互流程

**负载均衡触发流程：**
```
1. LoadBalancer定期检查（每5分钟）
2. 计算各RegionServer的负载分数
3. 识别负载不均衡（超过平均值150%）
4. 生成迁移计划（选择Region和目标）
5. 提交给RegionMigrationManager执行
```

**Region迁移流程：**
```
1. PREPARE阶段：
   - Master创建MigrationTask
   - 通知目标RegionServer准备接收
   - 状态：ONLINE → MIGRATING_PREPARE

2. SYNC阶段：
   - 目标创建空Region
   - 源RegionServer启用双写
   - 源→目标传输全量数据
   - 状态：MIGRATING_PREPARE → MIGRATING_SYNC

3. CATCH_UP阶段：
   - 持续同步增量数据
   - 检查数据是否追平
   - 状态：保持MIGRATING_SYNC

4. SWITCH阶段：
   - 更新Master元数据
   - 切换主副本到目标
   - 通知客户端刷新缓存
   - 状态：MIGRATING_SYNC → MIGRATING_SWITCH

5. CLEANUP阶段：
   - 停止双写
   - 删除源Region
   - 状态：MIGRATING_SWITCH → ONLINE
```

### 2.3 数据流与控制流

**控制流（Master ↔ RegionServer）：**
- Master通过gRPC发送迁移指令
- RegionServer上报迁移进度和状态
- Master协调状态转换

**数据流（源RegionServer → 目标RegionServer）：**
- 直接点对点传输，减轻Master压力
- 使用gRPC流式传输大数据
- 支持断点续传

**状态持久化（Master → Zookeeper）：**
- 迁移任务状态持久化
- 支持Master故障后恢复
- 路径：`/minisql/migrations/{migrationId}`

---

## 3. 核心组件设计

### 3.1 LoadBalancer（负载均衡器）

**职责：**
- 定期检查集群负载状态
- 计算负载不均衡度
- 生成迁移计划
- 支持手动触发和干运行模式

**核心方法：**
```java
public class LoadBalancer {
    // 启动后台监控
    void start();
    
    // 停止监控
    void stop();
    
    // 检查是否需要负载均衡
    boolean needsBalance();
    
    // 生成迁移计划
    List<MigrationPlan> generateMigrationPlans();
    
    // 执行负载均衡（自动模式）
    void balance();
    
    // 手动触发负载均衡
    void manualBalance();
    
    // 干运行模式（仅生成计划不执行）
    List<MigrationPlan> dryRun();
}
```

**负载计算算法：**
```java
// 使用ServerInfo.getLoadScore()
double avgLoad = totalLoad / onlineServerCount;
double threshold = avgLoad * 1.5; // 150%阈值

// 识别过载服务器
List<ServerInfo> overloadedServers = servers.stream()
    .filter(s -> s.getLoadScore() > threshold)
    .collect(Collectors.toList());
```

### 3.2 RegionMigrationManager（迁移管理器）

**职责：**
- 管理所有迁移任务的生命周期
- 维护迁移状态机
- 协调MigrationExecutor执行
- 处理并发迁移控制

**核心方法：**
```java
public class RegionMigrationManager {
    // 提交迁移任务
    String submitMigration(String regionId, String targetServerId);
    
    // 取消迁移
    boolean cancelMigration(String migrationId);
    
    // 查询迁移状态
    MigrationTask getMigrationTask(String migrationId);
    
    // 获取所有进行中的迁移
    List<MigrationTask> getActiveMigrations();
    
    // 处理迁移状态转换
    void handleStateTransition(String migrationId, MigrationState newState);
    
    // 处理迁移失败
    void handleMigrationFailure(String migrationId, Exception error);
}
```

**并发控制：**
- 同一Region同时只能有一个迁移任务
- 同一RegionServer同时最多N个迁移任务（可配置，默认2）
- 使用ConcurrentHashMap管理任务状态

### 3.3 MigrationTask（迁移任务）

**数据结构：**
```java
public class MigrationTask {
    private String migrationId;           // 迁移任务ID
    private String regionId;              // Region ID
    private String sourceServerId;        // 源服务器
    private String targetServerId;        // 目标服务器
    private MigrationState state;         // 当前状态
    private long createTime;              // 创建时间
    private long startTime;               // 开始时间
    private long endTime;                 // 结束时间
    private int retryCount;               // 重试次数
    private String errorMessage;          // 错误信息
    private Map<String, Object> metadata; // 元数据（进度等）
}
```

**状态枚举：**
```java
public enum MigrationState {
    PENDING,              // 等待执行
    MIGRATING_PREPARE,    // 准备阶段
    MIGRATING_SYNC,       // 数据同步阶段
    MIGRATING_SWITCH,     // 切换阶段
    COMPLETED,            // 完成
    FAILED,               // 失败
    CANCELLED,            // 已取消
    ROLLING_BACK          // 回滚中
}
```

### 3.4 MigrationExecutor（迁移执行器）

**职责：**
- 执行具体的迁移步骤
- 调用RegionServer的RPC接口
- 处理每个阶段的失败和重试
- 记录详细的迁移日志

**核心方法：**
```java
public class MigrationExecutor {
    // 执行迁移（异步）
    CompletableFuture<Void> executeMigration(MigrationTask task);
    
    // 准备阶段
    void executePrepare(MigrationTask task);
    
    // 同步阶段
    void executeSync(MigrationTask task);
    
    // 切换阶段
    void executeSwitch(MigrationTask task);
    
    // 清理阶段
    void executeCleanup(MigrationTask task);
    
    // 回滚
    void rollback(MigrationTask task);
}
```

**执行流程：**
```java
// 伪代码
CompletableFuture<Void> executeMigration(MigrationTask task) {
    return CompletableFuture.runAsync(() -> {
        try {
            executePrepare(task);
            executeSync(task);
            executeSwitch(task);
            executeCleanup(task);
            task.setState(COMPLETED);
        } catch (RecoverableException e) {
            // 可恢复错误：重试
            retry(task);
        } catch (FatalException e) {
            // 严重错误：回滚
            rollback(task);
            task.setState(FAILED);
        }
    });
}
```

### 3.5 DoubleWriteProxy（双写代理）

**职责：**
- 拦截Region的写操作
- 同时写入源和目标Region
- 处理双写失败（源成功但目标失败）
- 记录双写日志用于数据追平

**核心方法：**
```java
public class DoubleWriteProxy {
    // 启用双写
    void enableDoubleWrite(String regionId, String targetServer);
    
    // 停用双写
    void disableDoubleWrite(String regionId);
    
    // 双写操作
    void doubleWrite(String regionId, WriteOperation op);
    
    // 获取双写失败的操作（用于重试）
    List<WriteOperation> getFailedWrites(String regionId);
}
```

**双写策略：**
- 源写入成功是必须的（失败则整个操作失败）
- 目标写入失败记录到队列，后续重试
- 保证最终一致性

### 3.6 RegionTransferService（Region传输服务）

**职责：**
- 导出Region数据
- 接收并导入Region数据
- 支持增量同步
- 支持断点续传

**核心方法：**
```java
public class RegionTransferService {
    // 导出Region（流式）
    Iterator<DataChunk> exportRegion(String regionId);
    
    // 导入Region（流式）
    void importRegion(String regionId, Iterator<DataChunk> chunks);
    
    // 获取Region的最新序列号（用于增量同步）
    long getRegionSequence(String regionId);
    
    // 导出增量数据
    Iterator<DataChunk> exportIncremental(String regionId, long fromSeq);
}
```

**数据传输格式：**
```java
public class DataChunk {
    private long sequenceId;      // 序列号
    private byte[] data;          // 数据内容
    private String checksum;      // 校验和
    private boolean isLast;       // 是否最后一块
}
```

---

## 4. 详细设计

### 4.1 智能Region选择算法

**目标：** 选择最优的Region进行迁移，使负载均衡效果最好

**算法步骤：**
```java
Region selectRegionToMigrate(ServerInfo overloadedServer, 
                             ServerInfo targetServer) {
    List<Region> candidates = overloadedServer.getRegions();
    
    // 1. 过滤不可迁移的Region
    candidates = candidates.stream()
        .filter(r -> r.getState() == ONLINE)
        .filter(r -> !r.isSplitting())
        .filter(r -> !r.isMigrating())
        .collect(Collectors.toList());
    
    // 2. 计算每个Region的迁移收益
    Map<Region, Double> benefits = new HashMap<>();
    for (Region region : candidates) {
        double benefit = calculateMigrationBenefit(
            region, overloadedServer, targetServer);
        benefits.put(region, benefit);
    }
    
    // 3. 选择收益最大的Region
    return benefits.entrySet().stream()
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElse(null);
}

double calculateMigrationBenefit(Region region, 
                                 ServerInfo source, 
                                 ServerInfo target) {
    // 迁移后的负载分数
    double sourceLoadAfter = source.getLoadScore() - region.getLoadScore();
    double targetLoadAfter = target.getLoadScore() + region.getLoadScore();
    
    // 负载方差减少量（越大越好）
    double varianceBefore = calculateVariance(allServers);
    double varianceAfter = calculateVarianceAfter(region, source, target);
    double varianceReduction = varianceBefore - varianceAfter;
    
    // 迁移成本（越小越好）
    double migrationCost = region.getSize() / 1024.0 / 1024; // MB
    double qpsCost = region.getQPS() * 0.1; // QPS越高成本越高
    
    // 综合收益 = 负载改善 - 迁移成本
    return varianceReduction * 100 - migrationCost - qpsCost;
}
```

### 4.2 双写机制详细设计

**启用双写流程：**
```
1. Master通知源RegionServer启用双写
2. 源RegionServer创建DoubleWriteProxy
3. 拦截所有写操作（PUT/DELETE）
4. 同时写入本地和目标RegionServer
5. 记录双写日志
```

**双写实现：**
```java
public void doubleWrite(String regionId, WriteOperation op) {
    // 1. 先写本地（源）
    boolean localSuccess = false;
    try {
        writeLocal(regionId, op);
        localSuccess = true;
    } catch (Exception e) {
        // 本地写失败，整个操作失败
        throw new WriteException("Local write failed", e);
    }
    
    // 2. 再写远程（目标）
    try {
        writeRemote(targetServer, regionId, op);
    } catch (Exception e) {
        // 远程写失败，记录到失败队列
        logger.warn("Remote write failed, will retry: {}", op);
        failedWriteQueue.add(op);
    }
}
```

**失败重试机制：**
```java
// 后台线程定期重试失败的写操作
ScheduledExecutorService retryExecutor = ...;
retryExecutor.scheduleAtFixedRate(() -> {
    List<WriteOperation> failed = failedWriteQueue.poll(100);
    for (WriteOperation op : failed) {
        try {
            writeRemote(targetServer, regionId, op);
            logger.info("Retry succeeded: {}", op);
        } catch (Exception e) {
            if (op.getRetryCount() < MAX_RETRY) {
                op.incrementRetry();
                failedWriteQueue.add(op);
            } else {
                logger.error("Retry exhausted: {}", op);
                deadLetterQueue.add(op);
            }
        }
    }
}, 1, 1, TimeUnit.SECONDS);
```

### 4.3 数据同步与追平

**全量同步：**
```java
void syncFullData(MigrationTask task) {
    String regionId = task.getRegionId();
    
    // 1. 获取源Region的快照序列号
    long snapshotSeq = sourceRS.getRegionSequence(regionId);
    task.setMetadata("snapshotSeq", snapshotSeq);
    
    // 2. 流式传输数据
    Iterator<DataChunk> chunks = sourceRS.exportRegion(regionId);
    targetRS.importRegion(regionId, chunks);
    
    logger.info("Full sync completed: region={}, seq={}", 
                regionId, snapshotSeq);
}
```

**增量同步：**
```java
void syncIncrementalData(MigrationTask task) {
    String regionId = task.getRegionId();
    long fromSeq = task.getMetadata("snapshotSeq");
    
    while (true) {
        // 1. 获取源Region当前序列号
        long currentSeq = sourceRS.getRegionSequence(regionId);
        
        // 2. 如果已追平，退出
        if (currentSeq - fromSeq < CATCH_UP_THRESHOLD) {
            logger.info("Data caught up: region={}, lag={}", 
                        regionId, currentSeq - fromSeq);
            break;
        }
        
        // 3. 同步增量数据
        Iterator<DataChunk> chunks = 
            sourceRS.exportIncremental(regionId, fromSeq);
        targetRS.importRegion(regionId, chunks);
        
        fromSeq = currentSeq;
        Thread.sleep(100); // 短暂休眠
    }
}
```

### 4.4 故障处理策略

**故障分类：**

**1. 可恢复错误（自动重试3次）：**
- 网络临时故障（连接超时、读写超时）
- 目标RegionServer临时繁忙
- 数据传输校验失败（重传）

**2. 严重错误（自动回滚）：**
- 目标RegionServer宕机
- 磁盘空间不足
- 数据损坏无法恢复

**3. 不确定错误（暂停等待人工）：**
- 未知异常
- 状态不一致
- 超过最大重试次数

**故障处理实现：**
```java
void handleMigrationFailure(MigrationTask task, Exception error) {
    ErrorType type = classifyError(error);
    
    switch (type) {
        case RECOVERABLE:
            if (task.getRetryCount() < MAX_RETRY) {
                task.incrementRetry();
                logger.warn("Retrying migration: {}, attempt {}", 
                           task.getMigrationId(), task.getRetryCount());
                retryMigration(task);
            } else {
                // 重试次数用尽，转为人工介入
                pauseForManualIntervention(task, error);
            }
            break;
            
        case FATAL:
            logger.error("Fatal error, rolling back: {}", 
                        task.getMigrationId(), error);
            rollback(task);
            task.setState(FAILED);
            task.setErrorMessage(error.getMessage());
            break;
            
        case UNKNOWN:
            logger.error("Unknown error, pausing for manual intervention: {}", 
                        task.getMigrationId(), error);
            pauseForManualIntervention(task, error);
            break;
    }
}
```

**回滚流程：**
```java
void rollback(MigrationTask task) {
    try {
        // 1. 停止双写
        sourceRS.disableDoubleWrite(task.getRegionId());
        
        // 2. 删除目标Region
        targetRS.deleteRegion(task.getRegionId());
        
        // 3. 恢复Region状态为ONLINE
        metadataManager.updateRegionState(task.getRegionId(), ONLINE);
        
        // 4. 清理Zookeeper中的迁移任务
        zkClient.delete("/minisql/migrations/" + task.getMigrationId());
        
        logger.info("Rollback completed: {}", task.getMigrationId());
        
    } catch (Exception e) {
        logger.error("Rollback failed: {}", task.getMigrationId(), e);
        // 回滚失败需要人工介入
        alertAdmin(task, e);
    }
}
```

### 4.5 元数据切换

**切换流程：**
```java
void executeSwitch(MigrationTask task) {
    String regionId = task.getRegionId();
    String targetServerId = task.getTargetServerId();
    
    // 1. 更新Region元数据
    RegionMetadata region = metadataManager.getRegion(regionId);
    String oldServer = region.getRegionServer();
    region.setRegionServer(targetServerId);
    
    // 2. 更新路由表
    metadataManager.updateRegion(region);
    
    // 3. 持久化到Zookeeper
    metadataPersistence.saveRegion(region);
    
    // 4. 更新ClusterManager
    clusterManager.removeRegionFromServer(oldServer, regionId);
    clusterManager.addRegionToServer(targetServerId, regionId, region.getSize());
    
    // 5. 通知客户端刷新缓存（通过版本号机制）
    metadataManager.incrementRouteTableVersion(region.getTableName());
    
    logger.info("Metadata switched: region={}, {} -> {}", 
                regionId, oldServer, targetServerId);
}
```

---

## 5. gRPC接口定义

### 5.1 Master → RegionServer

**迁移控制接口：**
```protobuf
service RegionServerService {
    // 准备接收Region
    rpc PrepareReceiveRegion(PrepareReceiveRequest) 
        returns (PrepareReceiveResponse);
    
    // 启用双写
    rpc EnableDoubleWrite(EnableDoubleWriteRequest) 
        returns (EnableDoubleWriteResponse);
    
    // 停用双写
    rpc DisableDoubleWrite(DisableDoubleWriteRequest) 
        returns (DisableDoubleWriteResponse);
    
    // 删除Region
    rpc DeleteRegion(DeleteRegionRequest) 
        returns (DeleteRegionResponse);
    
    // 获取Region序列号
    rpc GetRegionSequence(GetRegionSequenceRequest) 
        returns (GetRegionSequenceResponse);
}

message PrepareReceiveRequest {
    string region_id = 1;
    string table_name = 2;
    bytes start_key = 3;
    bytes end_key = 4;
}

message EnableDoubleWriteRequest {
    string region_id = 1;
    string target_server = 2;
}
```

### 5.2 RegionServer → RegionServer

**数据传输接口：**
```protobuf
service RegionTransferService {
    // 导出Region（流式）
    rpc ExportRegion(ExportRegionRequest) 
        returns (stream DataChunk);
    
    // 导入Region（流式）
    rpc ImportRegion(stream DataChunk) 
        returns (ImportRegionResponse);
    
    // 导出增量数据
    rpc ExportIncremental(ExportIncrementalRequest) 
        returns (stream DataChunk);
}

message DataChunk {
    int64 sequence_id = 1;
    bytes data = 2;
    string checksum = 3;
    bool is_last = 4;
}
```

---

## 6. 配置参数

### 6.1 负载均衡配置

```properties
# 负载均衡周期（毫秒）
load.balance.period=300000  # 5分钟

# 负载不均衡阈值（倍数）
load.balance.threshold=1.5  # 150%

# 是否启用自动负载均衡
load.balance.auto.enabled=true

# 同时进行的最大迁移数
load.balance.max.concurrent.migrations=2

# 数据追平阈值（条数）
migration.catch.up.threshold=100
```

### 6.2 迁移配置

```properties
# 迁移超时时间（毫秒）
migration.timeout=3600000  # 1小时

# 最大重试次数
migration.max.retry=3

# 数据块大小（字节）
migration.chunk.size=1048576  # 1MB

# 双写失败队列大小
migration.double.write.queue.size=10000
```

---

## 7. 监控与日志

### 7.1 监控指标

**负载均衡指标：**
- `load_balance_check_count`：负载检查次数
- `load_balance_trigger_count`：触发次数
- `load_imbalance_degree`：负载不均衡度

**迁移指标：**
- `migration_total_count`：总迁移次数
- `migration_success_count`：成功次数
- `migration_failed_count`：失败次数
- `migration_duration_ms`：迁移耗时
- `migration_data_size_bytes`：迁移数据量
- `migration_active_count`：进行中的迁移数

**双写指标：**
- `double_write_total_count`：双写总次数
- `double_write_failed_count`：双写失败次数
- `double_write_retry_count`：重试次数

### 7.2 日志规范

**负载均衡日志：**
```
[2026-04-19 10:00:00] [INFO] [LoadBalancer] [balance-thread] 
  Load balance check: avgLoad=45.2, threshold=67.8, overloaded=[rs-001, rs-003]

[2026-04-19 10:00:01] [INFO] [LoadBalancer] [balance-thread] 
  Migration plan generated: region=region-123, rs-001 -> rs-004, benefit=23.5
```

**迁移日志：**
```
[2026-04-19 10:00:05] [INFO] [MigrationExecutor] [migration-worker-1] 
  Migration started: id=mig-001, region=region-123, rs-001 -> rs-004

[2026-04-19 10:00:06] [INFO] [MigrationExecutor] [migration-worker-1] 
  State transition: mig-001, PENDING -> MIGRATING_PREPARE

[2026-04-19 10:05:30] [INFO] [MigrationExecutor] [migration-worker-1] 
  Full sync completed: mig-001, size=256MB, duration=5m25s

[2026-04-19 10:06:00] [INFO] [MigrationExecutor] [migration-worker-1] 
  Data caught up: mig-001, lag=50 records

[2026-04-19 10:06:05] [INFO] [MigrationExecutor] [migration-worker-1] 
  Migration completed: mig-001, total_duration=6m0s
```

---

## 8. 测试策略

### 8.1 单元测试

**LoadBalancer测试：**
- 负载计算正确性
- 不均衡检测准确性
- Region选择算法验证
- 干运行模式测试

**RegionMigrationManager测试：**
- 任务提交和状态管理
- 并发控制（同一Region/同一Server）
- 状态转换正确性
- 任务取消功能

**MigrationExecutor测试：**
- 各阶段执行逻辑
- 重试机制
- 回滚逻辑
- 异常处理

**DoubleWriteProxy测试：**
- 双写正确性
- 失败队列管理
- 重试机制

### 8.2 集成测试

**Master-RegionServer交互：**
- 迁移指令下发和响应
- 数据传输流程
- 状态同步

**端到端迁移流程：**
- 完整迁移流程（PREPARE → CLEANUP）
- 迁移期间数据读写
- 元数据一致性

### 8.3 系统测试

**功能测试场景：**
1. 正常迁移流程
2. 手动触发迁移
3. 自动负载均衡触发
4. 干运行模式
5. 迁移取消

**性能测试场景：**
1. 迁移期间QPS影响
2. 迁移耗时（不同数据量）
3. 双写性能开销
4. 并发迁移性能

### 8.4 故障注入测试

**网络故障：**
- 数据传输中断
- RPC调用超时
- 网络抖动

**节点故障：**
- 目标RegionServer宕机（PREPARE阶段）
- 目标RegionServer宕机（SYNC阶段）
- 源RegionServer宕机（SYNC阶段）
- Master故障（迁移进行中）

**数据故障：**
- 数据校验失败
- 磁盘空间不足
- 数据损坏

**并发冲突：**
- 迁移期间Region分裂
- 多个迁移任务冲突
- 迁移期间负载均衡再次触发

### 8.5 测试覆盖率目标

- 单元测试覆盖率：> 85%
- 集成测试覆盖率：> 70%
- 关键路径覆盖：100%

---

## 9. 实施计划

### 9.1 开发阶段

**阶段1：基础框架（3天）**
- LoadBalancer基础结构
- RegionMigrationManager基础结构
- MigrationTask和状态枚举
- 配置和日志框架

**阶段2：负载均衡（4天）**
- 负载检测和计算
- 智能Region选择算法
- 迁移计划生成
- 手动触发和干运行模式
- 单元测试

**阶段3：迁移执行器（5天）**
- MigrationExecutor实现
- PREPARE阶段实现
- SYNC阶段实现
- SWITCH阶段实现
- CLEANUP阶段实现
- 单元测试

**阶段4：双写机制（4天）**
- DoubleWriteProxy实现
- 失败队列和重试
- RegionServer端集成
- 单元测试

**阶段5：数据传输（5天）**
- RegionTransferService实现
- 全量数据导出/导入
- 增量数据同步
- 流式传输和校验
- 单元测试

**阶段6：故障处理（3天）**
- 错误分类和处理
- 重试机制
- 回滚逻辑
- 人工介入接口
- 单元测试

**阶段7：集成测试（4天）**
- Master-RegionServer集成
- 端到端迁移流程
- 并发场景测试

**阶段8：故障注入测试（3天）**
- 网络故障测试
- 节点故障测试
- 数据故障测试
- 并发冲突测试

**总计：31天（约6周）**

### 9.2 里程碑

- **M1（1周）**：基础框架和负载均衡完成
- **M2（3周）**：迁移执行器和双写机制完成
- **M3（5周）**：数据传输和故障处理完成
- **M4（6周）**：所有测试完成，功能验收

### 9.3 风险与应对

**风险1：双写机制复杂度高**
- 应对：先实现简化版本，逐步完善
- 备选：如果时间不足，可以短暂停写

**风险2：数据传输性能问题**
- 应对：使用流式传输，支持断点续传
- 备选：优化数据块大小和并发度

**风险3：故障场景覆盖不全**
- 应对：优先测试高频故障场景
- 备选：后续迭代补充测试

---

## 10. 验收标准

### 10.1 功能验收

- [ ] 自动负载均衡正常工作
- [ ] 手动触发迁移成功
- [ ] 干运行模式正确生成计划
- [ ] 迁移期间服务可用（读写正常）
- [ ] 迁移完成后数据一致
- [ ] 故障自动重试和回滚
- [ ] 人工介入接口可用

### 10.2 性能验收

- [ ] 迁移期间QPS下降 < 10%
- [ ] 256MB Region迁移耗时 < 10分钟
- [ ] 双写性能开销 < 5%
- [ ] 负载均衡检查耗时 < 1秒

### 10.3 测试验收

- [ ] 单元测试覆盖率 > 85%
- [ ] 所有集成测试通过
- [ ] 所有故障注入测试通过
- [ ] 无严重Bug

---

## 11. 后续优化方向

### 11.1 性能优化

- 并行迁移多个Region
- 优化数据传输协议（压缩、批量）
- 智能限流（避免影响业务）

### 11.2 功能增强

- 支持Region合并
- 支持跨数据中心迁移
- 支持迁移优先级
- 支持迁移暂停和恢复

### 11.3 可观测性

- 迁移进度可视化
- 实时监控Dashboard
- 告警和通知

---

**文档结束**

