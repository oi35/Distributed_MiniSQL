# RegionMigrationManager 完整实现 - 详细设计

**设计日期：** 2026-04-26  
**设计版本：** v1.0  
**负责人：** 成员1 - 架构负责人 + Master模块开发

---

## 1. 概述

### 1.1 目标

实现 RegionMigrationManager 组件，负责：
- 接收和管理 Region 迁移任务
- 协调迁移过程的状态转换
- 处理迁移失败和自动重试
- 提供监控和查询接口
- 与 LoadBalancer 配合实现负载均衡

### 1.2 设计原则

- **状态机驱动**：使用定时调度器推进任务状态，避免复杂的线程管理
- **职责分离**：每个状态处理器专注一个迁移阶段
- **混合实现**：完整的协调逻辑 + mock 的 RegionServer 调用
- **自动容错**：失败后自动重试最多 3 次，使用指数退避
- **内存存储**：任务不持久化，Master 重启后由 LoadBalancer 重新生成

### 1.3 核心特性

1. **完整的状态机**：8 个状态，清晰的转换规则
2. **自动重试**：最多 3 次，指数退避（1分钟、2分钟、4分钟）
3. **超时控制**：每个阶段有独立的超时时间
4. **丰富的监控**：统计信息、按状态/服务器过滤查询
5. **线程安全**：使用 ConcurrentHashMap 和同步机制

---

## 2. 整体架构

### 2.1 类结构

```
RegionMigrationManager (协调器)
├── MigrationTask (已存在 - 任务数据模型)
├── MigrationState (已存在 - 状态枚举)
├── MigrationConfig (配置类)
├── MigrationStateHandler (接口 - 状态处理器)
│   ├── PrepareHandler (准备阶段处理器)
│   ├── SyncHandler (同步阶段处理器)
│   ├── SwitchHandler (切换阶段处理器)
│   └── RollbackHandler (回滚处理器)
├── MigrationExecutor (执行器 - 封装 RegionServer 调用)
└── MigrationStatistics (统计信息)
```

### 2.2 依赖关系

```
RegionMigrationManager
    ├── ClusterManager (获取 RegionServer 信息)
    ├── MetadataManager (更新 Region 路由信息)
    └── MigrationExecutor (调用 RegionServer - 当前为 mock)
```

### 2.3 生命周期

```
start() → 启动定时调度器（每 5 秒）
    ↓
processTasks() → 扫描所有活跃任务
    ↓
advanceTask() → 调用对应的状态处理器
    ↓
stop() → 优雅关闭，等待当前任务完成
```

---

## 3. 状态机设计

### 3.1 状态转换流程

```
PENDING (等待执行)
    ↓ submitMigration()
MIGRATING_PREPARE (准备阶段)
    ↓ PrepareHandler: 通知源和目标服务器准备迁移
MIGRATING_SYNC (数据同步阶段)
    ↓ SyncHandler: 触发数据传输（mock）
MIGRATING_SWITCH (切换阶段)
    ↓ SwitchHandler: 更新路由表，切换流量
COMPLETED (完成)

失败路径：
任何阶段失败 → ROLLING_BACK → FAILED
    ↓ 自动重试（最多3次）
PENDING (重新开始)
```

### 3.2 状态处理器接口

```java
public interface MigrationStateHandler {
    /**
     * 处理当前状态的任务
     * @param task 迁移任务
     * @param executor 执行器
     * @return 下一个状态，如果保持当前状态返回 null
     * @throws MigrationException 处理失败
     */
    MigrationState handle(MigrationTask task, MigrationExecutor executor) 
        throws MigrationException;
    
    /**
     * 该处理器支持的状态
     */
    MigrationState supportedState();
}
```

### 3.3 重试策略

- **最大重试次数**: 3 次
- **退避延迟**: 
  - 第 1 次重试: 1 分钟
  - 第 2 次重试: 2 分钟
  - 第 3 次重试: 4 分钟
- **重试条件**: 失败后自动进入 ROLLING_BACK → FAILED，然后重置为 PENDING
- **放弃条件**: 超过 3 次重试后，任务保持 FAILED 状态

### 3.4 超时机制

| 阶段 | 超时时间 | 说明 |
|------|---------|------|
| PREPARE | 30 秒 | 准备阶段应该很快 |
| SYNC | 5 分钟 | 取决于数据大小，预留足够时间 |
| SWITCH | 30 秒 | 切换操作应该很快 |
| ROLLBACK | 1 分钟 | 清理操作 |

---

## 4. 核心接口

### 4.1 公共接口

```java
public class RegionMigrationManager {
    
    // ========== 任务提交 ==========
    
    /**
     * 提交新的迁移任务
     * @return 迁移任务 ID
     */
    public String submitMigration(String regionId, 
                                  String sourceServerId, 
                                  String targetServerId);
    
    // ========== 任务查询 ==========
    
    /**
     * 获取指定任务
     */
    public MigrationTask getTask(String migrationId);
    
    /**
     * 获取所有任务
     */
    public List<MigrationTask> getAllTasks();
    
    /**
     * 获取所有活跃任务（非终态）
     */
    public List<MigrationTask> getActiveMigrations();
    
    /**
     * 按状态过滤任务
     */
    public List<MigrationTask> getTasksByState(MigrationState state);
    
    /**
     * 按服务器过滤任务（源或目标）
     */
    public List<MigrationTask> getTasksByServer(String serverId);
    
    // ========== 任务控制 ==========
    
    /**
     * 取消迁移任务
     * @return 是否成功取消
     */
    public boolean cancelMigration(String migrationId);
    
    /**
     * 手动重试失败的任务
     * @return 是否成功重试
     */
    public boolean retryMigration(String migrationId);
    
    // ========== 统计信息 ==========
    
    /**
     * 获取统计信息
     */
    public MigrationStatistics getStatistics();
    
    // ========== 生命周期 ==========
    
    /**
     * 启动迁移管理器
     */
    public synchronized void start();
    
    /**
     * 停止迁移管理器
     */
    public synchronized void stop();
    
    /**
     * 是否正在运行
     */
    public boolean isRunning();
}
```

### 4.2 MigrationStatistics 统计类

```java
public class MigrationStatistics {
    private final int totalSubmitted;      // 总提交数
    private final int completed;           // 成功完成数
    private final int failed;              // 失败数
    private final int cancelled;           // 取消数
    private final int active;              // 当前活跃数
    private final double successRate;      // 成功率
    private final long avgDurationMs;      // 平均耗时（毫秒）
    
    // 构造函数和 getter 方法
}
```

### 4.3 MigrationConfig 配置类

```java
public class MigrationConfig {
    private final long checkPeriodMs;           // 调度周期
    private final int maxRetries;               // 最大重试次数
    private final long prepareTimeoutMs;        // 准备超时
    private final long syncTimeoutMs;           // 同步超时
    private final long switchTimeoutMs;         // 切换超时
    private final long rollbackTimeoutMs;       // 回滚超时
    
    // 默认值
    public static final long DEFAULT_CHECK_PERIOD_MS = 5000;        // 5秒
    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final long DEFAULT_PREPARE_TIMEOUT_MS = 30000;    // 30秒
    public static final long DEFAULT_SYNC_TIMEOUT_MS = 300000;      // 5分钟
    public static final long DEFAULT_SWITCH_TIMEOUT_MS = 30000;     // 30秒
    public static final long DEFAULT_ROLLBACK_TIMEOUT_MS = 60000;   // 1分钟
    
    // 构造函数、Builder、getter 方法
}
```

---

## 5. 状态处理器实现

### 5.1 PrepareHandler（准备阶段）

**职责：**
- 通知源 RegionServer 准备迁出 Region
- 通知目标 RegionServer 准备接收 Region
- 验证两端都准备就绪

**处理逻辑：**
```java
public MigrationState handle(MigrationTask task, MigrationExecutor executor) {
    try {
        // 1. 通知源服务器准备迁出
        boolean sourceReady = executor.prepareSource(
            task.getSourceServerId(), 
            task.getRegionId()
        );
        
        // 2. 通知目标服务器准备接收
        boolean targetReady = executor.prepareTarget(
            task.getTargetServerId(), 
            task.getRegionId()
        );
        
        // 3. 检查超时
        if (isTimeout(task, config.getPrepareTimeoutMs())) {
            throw new MigrationException("Prepare timeout");
        }
        
        // 4. 两端都准备好，进入同步阶段
        if (sourceReady && targetReady) {
            return MigrationState.MIGRATING_SYNC;
        }
        
        // 5. 保持当前状态，等待下次检查
        return null;
        
    } catch (Exception e) {
        // 失败，进入回滚
        task.setErrorMessage(e.getMessage());
        return MigrationState.ROLLING_BACK;
    }
}
```

### 5.2 SyncHandler（同步阶段）

**职责：**
- 触发数据从源到目标的传输
- 监控同步进度（当前为 mock）
- 等待同步完成

**处理逻辑：**
```java
public MigrationState handle(MigrationTask task, MigrationExecutor executor) {
    try {
        // 1. 首次进入，启动同步
        if (task.getMetadata("syncStarted") == null) {
            boolean started = executor.startSync(
                task.getSourceServerId(),
                task.getTargetServerId(),
                task.getRegionId()
            );
            if (started) {
                task.setMetadata("syncStarted", true);
            }
        }
        
        // 2. 检查同步进度
        SyncProgress progress = executor.getSyncProgress(
            task.getSourceServerId(),
            task.getTargetServerId(),
            task.getRegionId()
        );
        
        // 3. 检查超时
        if (isTimeout(task, config.getSyncTimeoutMs())) {
            throw new MigrationException("Sync timeout");
        }
        
        // 4. 同步完成，进入切换阶段
        if (progress.isCompleted()) {
            return MigrationState.MIGRATING_SWITCH;
        }
        
        // 5. 继续同步
        return null;
        
    } catch (Exception e) {
        task.setErrorMessage(e.getMessage());
        return MigrationState.ROLLING_BACK;
    }
}
```

### 5.3 SwitchHandler（切换阶段）

**职责：**
- 更新 MetadataManager 中的路由信息
- 通知源服务器卸载 Region
- 通知目标服务器激活 Region

**处理逻辑：**
```java
public MigrationState handle(MigrationTask task, MigrationExecutor executor) {
    try {
        // 1. 更新路由表（Region 指向目标服务器）
        metadataManager.updateRegionLocation(
            task.getRegionId(),
            task.getTargetServerId()
        );
        task.setMetadata("routeUpdated", true);
        
        // 2. 激活目标服务器上的 Region
        boolean activated = executor.activateRegion(
            task.getTargetServerId(),
            task.getRegionId()
        );
        
        // 3. 卸载源服务器上的 Region
        boolean deactivated = executor.deactivateRegion(
            task.getSourceServerId(),
            task.getRegionId()
        );
        
        // 4. 检查超时
        if (isTimeout(task, config.getSwitchTimeoutMs())) {
            throw new MigrationException("Switch timeout");
        }
        
        // 5. 切换完成
        if (activated && deactivated) {
            return MigrationState.COMPLETED;
        }
        
        return null;
        
    } catch (Exception e) {
        task.setErrorMessage(e.getMessage());
        return MigrationState.ROLLING_BACK;
    }
}
```

### 5.4 RollbackHandler（回滚阶段）

**职责：**
- 清理目标服务器上的部分数据
- 恢复源服务器的 Region 状态
- 如果路由表已更新，回滚路由表

**处理逻辑：**
```java
public MigrationState handle(MigrationTask task, MigrationExecutor executor) {
    try {
        // 1. 如果路由表已更新，回滚到源服务器
        if (Boolean.TRUE.equals(task.getMetadata("routeUpdated"))) {
            metadataManager.updateRegionLocation(
                task.getRegionId(),
                task.getSourceServerId()
            );
        }
        
        // 2. 清理目标服务器
        executor.cleanupTarget(
            task.getTargetServerId(),
            task.getRegionId()
        );
        
        // 3. 恢复源服务器
        executor.restoreSource(
            task.getSourceServerId(),
            task.getRegionId()
        );
        
        // 4. 检查超时
        if (isTimeout(task, config.getRollbackTimeoutMs())) {
            throw new MigrationException("Rollback timeout");
        }
        
        // 5. 回滚完成，进入失败状态
        return MigrationState.FAILED;
        
    } catch (Exception e) {
        // 回滚失败，仍然标记为 FAILED
        task.setErrorMessage("Rollback failed: " + e.getMessage());
        return MigrationState.FAILED;
    }
}
```

---

## 6. MigrationExecutor 设计

### 6.1 接口定义

```java
public class MigrationExecutor {
    
    // ========== 准备阶段 ==========
    
    /**
     * 通知源服务器准备迁出 Region
     */
    public boolean prepareSource(String serverId, String regionId);
    
    /**
     * 通知目标服务器准备接收 Region
     */
    public boolean prepareTarget(String serverId, String regionId);
    
    // ========== 同步阶段 ==========
    
    /**
     * 启动数据同步
     */
    public boolean startSync(String sourceId, String targetId, String regionId);
    
    /**
     * 获取同步进度
     */
    public SyncProgress getSyncProgress(String sourceId, String targetId, String regionId);
    
    // ========== 切换阶段 ==========
    
    /**
     * 激活目标服务器上的 Region
     */
    public boolean activateRegion(String serverId, String regionId);
    
    /**
     * 卸载源服务器上的 Region
     */
    public boolean deactivateRegion(String serverId, String regionId);
    
    // ========== 回滚阶段 ==========
    
    /**
     * 清理目标服务器上的数据
     */
    public boolean cleanupTarget(String serverId, String regionId);
    
    /**
     * 恢复源服务器的 Region
     */
    public boolean restoreSource(String serverId, String regionId);
}
```

### 6.2 SyncProgress 类

```java
public class SyncProgress {
    private final long totalBytes;      // 总字节数
    private final long syncedBytes;     // 已同步字节数
    private final boolean completed;    // 是否完成
    
    public double getProgress() {
        if (totalBytes == 0) return 0.0;
        return (double) syncedBytes / totalBytes;
    }
    
    // 构造函数和 getter 方法
}
```

### 6.3 Mock 实现策略

**当前阶段（Phase 1）：**
- 所有方法返回成功（true）
- 添加可配置的失败率用于测试（默认 0%，可设置为 10%）
- 记录详细日志，便于追踪调用流程
- 模拟延迟（100-500ms）使行为更真实
- `getSyncProgress()` 模拟渐进式进度（0% → 100%）

**未来集成（Phase 2）：**
- 实现真实的 gRPC 调用
- 调用 RegionServer 的迁移接口
- 处理网络异常和超时

---

## 7. 错误处理

### 7.1 异常类型

使用已存在的 `MigrationException`：
```java
public class MigrationException extends Exception {
    private final String errorCode;
    private final boolean retryable;
    
    // 构造函数
}
```

### 7.2 错误场景

| 错误类型 | 是否可重试 | 处理方式 |
|---------|-----------|---------|
| 网络错误 | 是 | 进入 ROLLING_BACK，自动重试 |
| 超时错误 | 是 | 进入 ROLLING_BACK，自动重试 |
| 服务器不可用 | 是 | 进入 ROLLING_BACK，自动重试 |
| 数据错误 | 否 | 直接失败（当前阶段较少） |
| 配置错误 | 否 | 直接失败 |

### 7.3 日志记录

```java
// INFO 级别
logger.info("Migration task submitted: {}", task);
logger.info("Migration task state changed: {} -> {}", oldState, newState);
logger.info("Migration task completed: {}", task);

// WARN 级别
logger.warn("Migration task retry: {} (attempt {})", task, retryCount);
logger.warn("Migration task timeout: {} in state {}", task, state);

// ERROR 级别
logger.error("Migration task failed: {}", task, exception);
logger.error("Migration task rollback failed: {}", task, exception);
```

---

## 8. 线程安全

### 8.1 并发控制

- **任务存储**: 使用 `ConcurrentHashMap<String, MigrationTask>` 存储任务
- **状态更新**: 在 `advanceTask()` 方法中使用 synchronized 块
- **调度器**: 使用单线程 `ScheduledExecutorService`，避免并发修改
- **统计信息**: 使用 `AtomicInteger` 和 `AtomicLong` 计数

### 8.2 同步机制

```java
private synchronized void advanceTask(MigrationTask task) {
    MigrationState currentState = task.getState();
    
    // 获取对应的处理器
    MigrationStateHandler handler = handlers.get(currentState);
    
    // 调用处理器
    MigrationState nextState = handler.handle(task, executor);
    
    // 更新状态
    if (nextState != null && currentState.canTransitionTo(nextState)) {
        task.setState(nextState);
        logger.info("Task {} state changed: {} -> {}", 
            task.getMigrationId(), currentState, nextState);
    }
}
```

---

## 9. 与其他组件的交互

### 9.1 与 LoadBalancer 的交互

```
LoadBalancer.executeMigrationPlans()
    ↓
RegionMigrationManager.submitMigration()
    ↓
创建 MigrationTask，状态为 PENDING
    ↓
定时调度器推进任务状态
    ↓
LoadBalancer.canStartNewMigration() 查询活跃任务数
```

### 9.2 与 MetadataManager 的交互

```
SwitchHandler
    ↓
MetadataManager.updateRegionLocation()
    ↓
更新路由表，Region 指向新的 RegionServer
    ↓
客户端下次查询时获取新路由
```

### 9.3 与 ClusterManager 的交互

```
RegionMigrationManager
    ↓
ClusterManager.getServerInfo()
    ↓
验证源和目标服务器是否存在且在线
```

---

## 10. 测试策略

### 10.1 单元测试

- **MigrationTask**: 测试状态转换、重试计数、元数据管理
- **MigrationState**: 测试状态转换规则
- **MigrationConfig**: 测试配置验证
- **各个 Handler**: 独立测试每个处理器的逻辑
- **MigrationExecutor**: 测试 mock 实现

### 10.2 集成测试

- **完整迁移流程**: PENDING → PREPARE → SYNC → SWITCH → COMPLETED
- **失败和重试**: 模拟失败，验证自动重试
- **回滚流程**: 验证回滚逻辑
- **并发迁移**: 提交多个任务，验证并发控制
- **超时处理**: 模拟超时，验证超时机制

### 10.3 测试覆盖率目标

- **指令覆盖率**: > 85%
- **分支覆盖率**: > 80%
- **关键路径**: 100% 覆盖

---

## 11. 性能考虑

### 11.1 调度周期

- **默认**: 5 秒
- **权衡**: 
  - 太短: CPU 占用高，日志过多
  - 太长: 状态推进慢，迁移耗时长
- **可配置**: 通过 MigrationConfig 调整

### 11.2 内存占用

- **每个任务**: 约 1KB（包含元数据）
- **100 个任务**: 约 100KB
- **可接受**: 教学项目规模下内存占用很小

### 11.3 日志量

- **正常迁移**: 每个任务约 10 条日志
- **失败重试**: 每次重试增加 5-10 条日志
- **控制**: 使用合适的日志级别（DEBUG/INFO/WARN/ERROR）

---

## 12. 未来扩展

### 12.1 Phase 2: 真实集成

- 实现真实的 gRPC 调用
- 与 RegionServer 的迁移接口集成
- 处理真实的网络异常和数据传输

### 12.2 Phase 3: 高级特性

- 迁移任务持久化到 Zookeeper
- 支持迁移优先级
- 支持迁移限流（带宽控制）
- 支持迁移暂停/恢复

### 12.3 Phase 4: 监控增强

- 集成 Prometheus Metrics
- 提供 REST API 查询接口
- 实时迁移进度推送（WebSocket）

---

## 13. 总结

RegionMigrationManager 是 Master 模块负载均衡功能的核心组件，负责协调 Region 在 RegionServer 之间的迁移。本设计采用状态机驱动模式，通过独立的状态处理器实现清晰的职责分离，支持自动重试和完整的错误处理。

**关键设计决策：**
1. **状态处理器模式** - 易于测试和扩展
2. **混合实现** - 完整逻辑 + mock 调用，为未来集成做好准备
3. **自动重试** - 提高容错能力
4. **内存存储** - 简化实现，适合当前阶段
5. **丰富监控** - 便于运维和调试

**实现优先级：**
1. 核心框架和状态机（高）
2. 状态处理器实现（高）
3. Mock 执行器（高）
4. 监控和统计（中）
5. 高级查询接口（中）
