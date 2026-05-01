# RegionMigrationManager 快速使用指南

## 概述

RegionMigrationManager 是 Master 模块中负责协调 Region 迁移的核心组件。本指南展示如何使用它进行 Region 迁移。

## 基本使用

### 1. 初始化

```java
// 创建配置（使用默认值）
MigrationConfig config = MigrationConfig.getDefault();

// 或自定义配置
MigrationConfig config = MigrationConfig.builder()
    .checkPeriodMs(5000)      // 检查周期：5秒
    .maxRetries(3)            // 最大重试次数：3次
    .prepareTimeoutMs(30000)  // 准备超时：30秒
    .syncTimeoutMs(300000)    // 同步超时：5分钟
    .build();

// 创建 RegionMigrationManager
RegionMigrationManager manager = new RegionMigrationManager(
    clusterManager,    // 集群管理器
    metadataManager,   // 元数据管理器
    config            // 配置
);

// 启动管理器
manager.start();
```

### 2. 提交迁移任务

```java
// 提交一个 Region 迁移任务
String migrationId = manager.submitMigration(
    "region-001",      // Region ID
    "server-1",        // 源服务器 ID
    "server-2"         // 目标服务器 ID
);

System.out.println("Migration submitted: " + migrationId);
```

### 3. 监控迁移进度

```java
// 获取任务详情
MigrationTask task = manager.getTask(migrationId);

// 检查状态
System.out.println("Current state: " + task.getState());
System.out.println("Progress: " + task.getProgress());

// 状态转换流程：
// PENDING → MIGRATING_PREPARE → MIGRATING_SYNC → MIGRATING_SWITCH → COMPLETED
```

### 4. 查询任务

```java
// 获取所有任务
List<MigrationTask> allTasks = manager.getAllTasks();

// 按状态查询
List<MigrationTask> activeTasks = manager.getTasksByState(MigrationState.MIGRATING_SYNC);

// 按服务器查询（源或目标）
List<MigrationTask> serverTasks = manager.getTasksByServer("server-1");

// 获取活跃任务（非终态）
List<MigrationTask> active = manager.getActiveMigrations();
```

### 5. 控制任务

```java
// 取消迁移
boolean cancelled = manager.cancelMigration(migrationId);

// 重试失败的迁移
boolean retried = manager.retryMigration(migrationId);
```

### 6. 获取统计信息

```java
MigrationStatistics stats = manager.getStatistics();

System.out.println("Total submitted: " + stats.getTotalSubmitted());
System.out.println("Completed: " + stats.getCompleted());
System.out.println("Failed: " + stats.getFailed());
System.out.println("Success rate: " + stats.getSuccessRate());
System.out.println("Average duration: " + stats.getAvgDurationMs() + "ms");
```

### 7. 停止管理器

```java
// 优雅停止（等待当前任务完成）
manager.stop();
```

## 完整示例

参考测试文件：
```
minisql-master/src/test/java/com/minisql/master/balance/RegionMigrationManagerUsageExample.java
```

运行示例：
```bash
cd minisql-master
mvn test -Dtest=RegionMigrationManagerUsageExample
```

## 状态说明

| 状态 | 说明 |
|------|------|
| PENDING | 等待执行 |
| MIGRATING_PREPARE | 准备阶段（通知源和目标服务器） |
| MIGRATING_SYNC | 数据同步阶段 |
| MIGRATING_SWITCH | 切换阶段（更新路由表） |
| COMPLETED | 成功完成 |
| ROLLING_BACK | 回滚中 |
| FAILED | 失败（可重试） |
| CANCELLED | 已取消 |

## 自动重试

失败的任务会自动重试，使用指数退避策略：
- 第1次重试：1分钟后
- 第2次重试：2分钟后
- 第3次重试：4分钟后
- 超过3次后保持FAILED状态

## 与 LoadBalancer 集成

LoadBalancer 会自动调用 RegionMigrationManager 提交迁移任务：

```java
LoadBalancer loadBalancer = new LoadBalancer(
    clusterManager,
    metadataManager,
    migrationManager,  // 传入 RegionMigrationManager
    masterElection,
    LoadBalancerConfig.getDefault()
);

loadBalancer.start();
// LoadBalancer 会自动检测负载不均衡并提交迁移任务
```

## 注意事项

1. **线程安全**：RegionMigrationManager 是线程安全的，可以并发提交任务
2. **内存存储**：任务不持久化，Master 重启后会丢失
3. **Mock 实现**：当前 RegionServer 调用是 mock 的，实际部署需要实现真实的 gRPC 调用
4. **超时控制**：每个阶段有独立超时，超时会触发回滚
5. **资源清理**：停止管理器前确保重要任务已完成

## 监控建议

定期检查统计信息：
```java
// 每分钟检查一次
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(() -> {
    MigrationStatistics stats = manager.getStatistics();
    logger.info("Migration stats - Total: {}, Completed: {}, Failed: {}, Active: {}",
        stats.getTotalSubmitted(), stats.getCompleted(), 
        stats.getFailed(), stats.getActive());
}, 0, 60, TimeUnit.SECONDS);
```

## 故障排查

### 任务卡在某个状态
- 检查超时配置是否合理
- 查看日志中的错误信息
- 使用 `task.getErrorMessage()` 获取错误详情

### 任务频繁失败
- 检查 RegionServer 是否正常运行
- 验证网络连接
- 查看是否超过重试次数限制

### 性能问题
- 调整 `checkPeriodMs` 检查周期
- 限制并发迁移任务数量
- 监控系统资源使用情况

## 更多信息

- 设计文档：`docs/superpowers/specs/2026-04-26-regionmigrationmanager-design.md`
- 实现计划：`docs/superpowers/plans/2026-04-26-regionmigrationmanager-implementation.md`
- 测试用例：`minisql-master/src/test/java/com/minisql/master/balance/`
