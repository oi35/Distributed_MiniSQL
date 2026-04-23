# LoadBalancer核心实现 - 详细设计

**设计日期：** 2026-04-20  
**设计版本：** v1.0  
**负责人：** 成员1 - 架构负责人 + Master模块开发

---

## 1. 概述

### 1.1 目标

实现LoadBalancer组件，负责：
- 定期监控集群负载状态
- 识别负载不均衡
- 生成智能迁移计划
- 协调迁移任务执行
- 支持自动/手动/干运行模式

### 1.2 设计原则

- **单一职责**：LoadBalancer专注于负载检测和计划生成，迁移执行由RegionMigrationManager负责
- **Master选举集成**：只有Active Master运行LoadBalancer，避免多Master冲突
- **综合评分策略**：考虑Region大小、负载方差、迁移成本
- **并发控制**：全局最多2个并发迁移 + 每服务器最多参与1个迁移
- **防抖动**：冷却期、最小不均衡度等机制避免频繁迁移

### 1.3 核心特性

1. **智能触发条件**：
   - 负载超过平均值150%
   - 过载服务器至少有2个Region
   - 负载差 > 平均值的30%
   - 冷却期10分钟

2. **综合评分Region选择**：
   - 计算负载方差减少量
   - 考虑迁移成本（数据大小）
   - 选择收益最高的Region

3. **严格并发控制**：
   - 全局最多2个并发迁移
   - 每个RegionServer最多参与1个迁移

---

## 2. 架构设计

### 2.1 类结构

```java
public class LoadBalancer {
    // 依赖组件
    private final ClusterManager clusterManager;
    private final MetadataManager metadataManager;
    private final RegionMigrationManager migrationManager;
    private final MasterElection masterElection;
    
    // 配置参数
    private final double loadThreshold;        // 1.5 (150%)
    private final long checkPeriodMs;          // 300000 (5分钟)
    private final int minRegionCount;          // 2
    private final double minLoadDiff;          // 0.3 (30%)
    private final long cooldownPeriodMs;       // 600000 (10分钟)
    private final int maxConcurrentMigrations; // 2
    
    // 运行状态
    private volatile boolean running;
    private volatile long lastBalanceTime;
    private ScheduledExecutorService scheduler;
}
```

### 2.2 依赖关系

```
LoadBalancer
    ├── ClusterManager (获取服务器信息和负载)
    ├── MetadataManager (获取Region元数据)
    ├── RegionMigrationManager (提交迁移任务)
    └── MasterElection (监听Master选举状态)
```

### 2.3 与其他组件的交互

**启动流程：**
```
MasterServer选举成功 
    → MasterElection.onBecomeLeader()
    → LoadBalancer.start()
    → 启动定期检查任务
```

**检查流程：**
```
定期触发 (每5分钟)
    → checkAndBalance()
    → needsBalance() 检查是否需要均衡
    → generateMigrationPlans() 生成迁移计划
    → executeMigrationPlans() 提交给RegionMigrationManager
```

---

## 3. 核心功能设计

### 3.1 生命周期管理

**启动逻辑：**
```java
public void start() {
    if (running) {
        return;
    }
    
    // 只有Active Master才启动
    if (!masterElection.isLeader()) {
        logger.warn("Not the leader, LoadBalancer will not start");
        return;
    }
    
    running = true;
    scheduler = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder()
            .setNameFormat("load-balancer-%d")
            .setDaemon(true)
            .build()
    );
    
    // 定期执行负载检查
    scheduler.scheduleAtFixedRate(
        this::checkAndBalance,
        checkPeriodMs,  // 初始延迟
        checkPeriodMs,  // 执行周期
        TimeUnit.MILLISECONDS
    );
    
    logger.info("LoadBalancer started, check period: {}ms", checkPeriodMs);
}
```

**停止逻辑：**
```java
public void stop() {
    if (!running) {
        return;
    }
    
    running = false;
    if (scheduler != null) {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    logger.info("LoadBalancer stopped");
}
```

**与Master选举集成：**
- MasterServer在`onBecomeLeader()`回调中调用`loadBalancer.start()`
- MasterServer在`onLoseLeadership()`回调中调用`loadBalancer.stop()`

---

### 3.2 负载检测和触发条件

**主检查流程：**
```java
private void checkAndBalance() {
    try {
        // 1. 检查是否在冷却期
        if (System.currentTimeMillis() - lastBalanceTime < cooldownPeriodMs) {
            logger.debug("In cooldown period, skip balance check");
            return;
        }
        
        // 2. 检查是否需要均衡
        if (!needsBalance()) {
            logger.debug("Cluster is balanced, no action needed");
            return;
        }
        
        // 3. 生成迁移计划
        List<MigrationPlan> plans = generateMigrationPlans();
        if (plans.isEmpty()) {
            logger.info("No migration plans generated");
            return;
        }
        
        // 4. 执行迁移
        executeMigrationPlans(plans);
        lastBalanceTime = System.currentTimeMillis();
        
    } catch (Exception e) {
        logger.error("Error during balance check", e);
    }
}
```

**触发条件判断：**
```java
public boolean needsBalance() {
    // 1. 获取所有在线服务器
    List<ServerInfo> onlineServers = clusterManager.getOnlineServers();
    if (onlineServers.size() < 2) {
        return false; // 少于2台服务器，无需均衡
    }
    
    // 2. 计算平均负载
    double totalLoad = onlineServers.stream()
        .mapToDouble(ServerInfo::getLoadScore)
        .sum();
    double avgLoad = totalLoad / onlineServers.size();
    
    // 3. 找出过载服务器（负载 > 平均值 * 1.5 且 Region数 >= 2）
    List<ServerInfo> overloaded = onlineServers.stream()
        .filter(s -> s.getLoadScore() > avgLoad * loadThreshold)
        .filter(s -> s.getRegionCount() >= minRegionCount)
        .collect(Collectors.toList());
    
    if (overloaded.isEmpty()) {
        return false;
    }
    
    // 4. 找出最轻服务器
    ServerInfo lightest = onlineServers.stream()
        .min(Comparator.comparingDouble(ServerInfo::getLoadScore))
        .orElse(null);
    
    if (lightest == null) {
        return false;
    }
    
    // 5. 检查负载差是否足够大（> 平均值 * 0.3）
    double maxLoad = overloaded.stream()
        .mapToDouble(ServerInfo::getLoadScore)
        .max()
        .orElse(0);
    
    double loadDiff = maxLoad - lightest.getLoadScore();
    return loadDiff > avgLoad * minLoadDiff;
}
```

**触发条件总结：**
1. ✅ 不在冷却期（10分钟）
2. ✅ 至少2台在线服务器
3. ✅ 存在过载服务器（负载 > 平均值 * 1.5）
4. ✅ 过载服务器至少有2个Region
5. ✅ 最大负载 - 最小负载 > 平均值 * 0.3

---

### 3.3 Region选择和迁移计划生成

**计划生成主流程：**
```java
private List<MigrationPlan> generateMigrationPlans() {
    List<ServerInfo> onlineServers = clusterManager.getOnlineServers();
    double avgLoad = onlineServers.stream()
        .mapToDouble(ServerInfo::getLoadScore)
        .average()
        .orElse(0);
    
    // 识别过载和轻载服务器
    List<ServerInfo> overloaded = onlineServers.stream()
        .filter(s -> s.getLoadScore() > avgLoad * loadThreshold)
        .sorted(Comparator.comparingDouble(ServerInfo::getLoadScore).reversed())
        .collect(Collectors.toList());
    
    List<ServerInfo> underloaded = onlineServers.stream()
        .filter(s -> s.getLoadScore() < avgLoad)
        .sorted(Comparator.comparingDouble(ServerInfo::getLoadScore))
        .collect(Collectors.toList());
    
    if (overloaded.isEmpty() || underloaded.isEmpty()) {
        return Collections.emptyList();
    }
    
    List<MigrationPlan> plans = new ArrayList<>();
    
    // 为每个过载服务器生成迁移计划
    for (ServerInfo source : overloaded) {
        // 检查并发限制
        if (!canStartNewMigration(source.getServerId())) {
            continue;
        }
        
        // 选择最佳Region
        RegionCandidate candidate = selectBestRegion(source, underloaded, avgLoad);
        if (candidate == null) {
            continue;
        }
        
        // 创建迁移计划
        MigrationPlan plan = new MigrationPlan(
            candidate.regionId,
            source.getServerId(),
            candidate.targetServerId,
            candidate.benefit,
            String.format("Load balance: %.2f -> %.2f", 
                source.getLoadScore(), candidate.targetLoad)
        );
        plans.add(plan);
        
        // 达到全局并发限制则停止
        if (plans.size() >= maxConcurrentMigrations) {
            break;
        }
    }
    
    return plans;
}
```

**Region选择算法（综合评分）：**
```java
private RegionCandidate selectBestRegion(ServerInfo source, 
                                         List<ServerInfo> targets,
                                         double avgLoad) {
    List<String> regionIds = source.getRegionIds();
    RegionCandidate best = null;
    
    for (String regionId : regionIds) {
        RegionMetadata region = metadataManager.getRegion(regionId);
        if (region == null || region.getState() != RegionState.REGION_ONLINE) {
            continue;
        }
        
        // 为每个目标服务器计算收益
        for (ServerInfo target : targets) {
            if (!canStartNewMigration(target.getServerId())) {
                continue;
            }
            
            double benefit = calculateBenefit(source, target, region, avgLoad);
            double targetLoadAfter = target.getLoadScore() + estimateRegionLoad(region);
            
            if (best == null || benefit > best.benefit) {
                best = new RegionCandidate(
                    regionId,
                    target.getServerId(),
                    benefit,
                    targetLoadAfter
                );
            }
        }
    }
    
    return best;
}
```

**收益计算公式：**
```java
private double calculateBenefit(ServerInfo source, ServerInfo target, 
                                RegionMetadata region, double avgLoad) {
    // 估算Region负载
    double regionLoad = estimateRegionLoad(region);
    
    // 迁移后的负载
    double sourceAfter = source.getLoadScore() - regionLoad;
    double targetAfter = target.getLoadScore() + regionLoad;
    
    // 计算负载方差减少量
    double beforeVariance = Math.pow(source.getLoadScore() - avgLoad, 2) + 
                           Math.pow(target.getLoadScore() - avgLoad, 2);
    double afterVariance = Math.pow(sourceAfter - avgLoad, 2) + 
                          Math.pow(targetAfter - avgLoad, 2);
    
    double varianceReduction = beforeVariance - afterVariance;
    
    // 计算迁移成本（数据大小，单位GB）
    double migrationCost = region.getSizeBytes() / (1024.0 * 1024 * 1024);
    
    // 综合评分 = 方差减少量 - 迁移成本权重
    return varianceReduction - (migrationCost * 0.1);
}

private double estimateRegionLoad(RegionMetadata region) {
    // Region数量基础分数
    double countScore = 0.5;
    
    // Region大小分数（GB * 0.3）
    double sizeScore = (region.getSizeBytes() / (1024.0 * 1024 * 1024)) * 0.3;
    
    return countScore + sizeScore;
}
```

**选择策略总结：**
- 遍历过载服务器的所有Region
- 为每个Region计算迁移到每个轻载服务器的收益
- 收益 = 负载方差减少量 - 迁移成本（数据大小 * 0.1）
- 选择收益最高的Region-Target组合

---

### 3.4 并发控制

**并发检查逻辑：**
```java
private boolean canStartNewMigration(String serverId) {
    // 1. 检查全局并发限制
    List<MigrationTask> activeMigrations = migrationManager.getActiveMigrations();
    if (activeMigrations.size() >= maxConcurrentMigrations) {
        return false;
    }
    
    // 2. 检查该服务器是否已参与迁移
    for (MigrationTask task : activeMigrations) {
        if (task.getSourceServerId().equals(serverId) || 
            task.getTargetServerId().equals(serverId)) {
            return false; // 该服务器已参与一个迁移
        }
    }
    
    return true;
}
```

**并发控制规则：**
1. **全局限制**：整个集群同时最多2个迁移任务
2. **服务器限制**：每个RegionServer最多参与1个迁移（作为源或目标）
3. **检查时机**：生成计划时检查，提交任务前再次检查

---

### 3.5 迁移执行

**执行迁移计划：**
```java
private void executeMigrationPlans(List<MigrationPlan> plans) {
    logger.info("Executing {} migration plans", plans.size());
    
    for (MigrationPlan plan : plans) {
        try {
            // 提交给RegionMigrationManager执行
            String migrationId = migrationManager.submitMigration(
                plan.getRegionId(),
                plan.getTargetServerId()
            );
            
            logger.info("Migration submitted: {} - {}", migrationId, plan);
            
        } catch (Exception e) {
            logger.error("Failed to submit migration: {}", plan, e);
            // 继续提交其他计划
        }
    }
}
```

---

### 3.6 手动操作支持

**手动触发均衡：**
```java
public void manualBalance() {
    logger.info("Manual balance triggered");
    checkAndBalance();
}
```

**干运行模式：**
```java
public List<MigrationPlan> dryRun() {
    logger.info("Dry run: generating migration plans without execution");
    
    if (!needsBalance()) {
        logger.info("Cluster is balanced");
        return Collections.emptyList();
    }
    
    List<MigrationPlan> plans = generateMigrationPlans();
    logger.info("Generated {} migration plans", plans.size());
    
    for (MigrationPlan plan : plans) {
        logger.info("  Plan: {}", plan);
    }
    
    return plans;
}
```

---

## 4. 配置参数

### 4.1 配置项说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| loadThreshold | 1.5 | 负载阈值倍数（150%） |
| checkPeriodMs | 300000 | 检查周期（5分钟） |
| minRegionCount | 2 | 最小Region数量 |
| minLoadDiff | 0.3 | 最小负载差（30%） |
| cooldownPeriodMs | 600000 | 冷却期（10分钟） |
| maxConcurrentMigrations | 2 | 最大并发迁移数 |

### 4.2 配置文件

扩展 `minisql-master/src/main/resources/balance.properties`：

```properties
# LoadBalancer配置
load.balance.threshold=1.5
load.balance.check.period=300000
load.balance.min.region.count=2
load.balance.min.load.diff=0.3
load.balance.cooldown.period=600000
load.balance.max.concurrent.migrations=2
```

---

## 5. 公共API

```java
public class LoadBalancer {
    /**
     * 构造函数
     */
    public LoadBalancer(ClusterManager clusterManager,
                       MetadataManager metadataManager,
                       RegionMigrationManager migrationManager,
                       MasterElection masterElection,
                       LoadBalancerConfig config);
    
    /**
     * 启动LoadBalancer（仅Active Master）
     */
    public void start();
    
    /**
     * 停止LoadBalancer
     */
    public void stop();
    
    /**
     * 是否正在运行
     */
    public boolean isRunning();
    
    /**
     * 检查是否需要负载均衡
     */
    public boolean needsBalance();
    
    /**
     * 手动触发负载均衡
     */
    public void manualBalance();
    
    /**
     * 干运行模式（仅生成计划不执行）
     */
    public List<MigrationPlan> dryRun();
}
```

---

## 6. 错误处理

### 6.1 异常场景

1. **Master选举失败** - 不启动LoadBalancer
2. **检查过程异常** - 记录日志，下次继续
3. **计划生成失败** - 记录日志，不影响下次检查
4. **提交迁移失败** - 记录日志，继续提交其他计划

### 6.2 日志级别

- **INFO**：启动/停止、生成计划、提交迁移
- **DEBUG**：冷却期跳过、集群已均衡
- **WARN**：非Leader启动、并发限制达到
- **ERROR**：检查异常、提交失败

---

## 7. 测试策略

### 7.1 单元测试

- 触发条件判断（needsBalance）
- Region选择算法（selectBestRegion）
- 收益计算（calculateBenefit）
- 并发控制（canStartNewMigration）

### 7.2 集成测试

- 完整的检查和生成流程
- 与ClusterManager/MetadataManager集成
- 与RegionMigrationManager集成

### 7.3 场景测试

- 单服务器过载场景
- 多服务器过载场景
- 并发限制场景
- 冷却期场景

---

## 8. 性能考虑

### 8.1 时间复杂度

- needsBalance: O(n) - n为服务器数量
- generateMigrationPlans: O(n * m * k) - n为过载服务器数，m为Region数，k为轻载服务器数
- 实际场景：n < 10, m < 100, k < 10，性能足够

### 8.2 内存占用

- 临时对象：服务器列表、Region列表、计划列表
- 峰值内存：< 10MB（假设100个Region）

### 8.3 优化建议

- 缓存Region元数据（避免重复查询）
- 限制每次生成的计划数量（已实现：最多2个）
- 异步执行检查（已实现：独立线程）

---

## 9. 未来扩展

### 9.1 可插拔策略

如果需要支持多种负载均衡策略：
- 定义LoadBalancePolicy接口
- 实现不同的策略类
- 运行时切换策略

### 9.2 动态参数调整

支持运行时修改配置参数：
- 通过JMX暴露配置
- 支持热更新

### 9.3 更复杂的评分算法

- 考虑Region访问热度（QPS）
- 考虑网络拓扑（跨机架成本）
- 考虑历史迁移成功率

---

## 10. 总结

LoadBalancer采用单体设计，集成了负载检测、Region选择、计划生成等功能。通过综合评分策略和严格的并发控制，实现了智能、安全的负载均衡。设计简洁清晰，适合教学项目，同时保留了未来扩展的空间。
