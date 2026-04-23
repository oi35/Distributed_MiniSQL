# LoadBalancer核心实现 - 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现LoadBalancer组件，负责定期监控集群负载、识别不均衡、生成智能迁移计划并协调执行

**Architecture:** 单体设计，集成负载检测、Region选择、计划生成功能。基于Master选举控制启停，使用ScheduledExecutorService定期执行。综合评分策略选择最优Region，严格并发控制（全局2个+每服务器1个）。

**Tech Stack:** Java 11, JUnit 4, Mockito, Maven, ScheduledExecutorService

---

## 文件结构规划

### 新增文件

**主实现：**
- `minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java` - 负载均衡器主类
- `minisql-master/src/main/java/com/minisql/master/balance/LoadBalancerConfig.java` - 配置类

**测试文件：**
- `minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java` - 单元测试

### 修改文件

**扩展现有类：**
- `minisql-master/src/main/java/com/minisql/master/cluster/ServerInfo.java` - 添加getRegionIds()方法

**配置文件：**
- `minisql-master/src/main/resources/balance.properties` - 添加LoadBalancer配置

---

## 前置条件检查

本计划假设以下组件已实现：
- ✅ MigrationState, MigrationException, MigrationTask, MigrationPlan (已完成)
- ⚠️ RegionMigrationManager - 需要部分API（getActiveMigrations, submitMigration）
- ✅ ClusterManager.getOnlineServers()
- ✅ MetadataManager.getRegion()
- ✅ MasterElection.isLeader()

**注意：** RegionMigrationManager的完整实现是下一个任务，本计划中会使用Mock对象。

---

## 阶段1：准备工作

### Task 1.1: 扩展ServerInfo添加getRegionIds()方法

**Files:**
- Modify: `minisql-master/src/main/java/com/minisql/master/cluster/ServerInfo.java:121-123`
- Test: `minisql-master/src/test/java/com/minisql/master/cluster/ServerInfoTest.java`

- [ ] **Step 1: 编写测试**

在ServerInfoTest中添加：

```java
@Test
public void testGetRegionIds() {
    ServerInfo server = new ServerInfo("rs-001", "localhost", 8001);
    
    // 初始为空
    List<String> regionIds = server.getRegionIds();
    assertTrue(regionIds.isEmpty());
    
    // 添加Region
    server.addRegion("region-1", 1024L);
    server.addRegion("region-2", 2048L);
    
    regionIds = server.getRegionIds();
    assertEquals(2, regionIds.size());
    assertTrue(regionIds.contains("region-1"));
    assertTrue(regionIds.contains("region-2"));
    
    // 返回的是副本，修改不影响原数据
    regionIds.add("region-3");
    assertEquals(2, server.getRegionIds().size());
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
cd minisql-master
mvn test -Dtest=ServerInfoTest#testGetRegionIds
```

Expected: 编译失败，getRegionIds()方法不存在

- [ ] **Step 3: 实现getRegionIds()方法**

在ServerInfo.java的getRegions()方法后添加：

```java
public List<String> getRegionIds() {
    return new ArrayList<>(regions.keySet());
}
```

需要在文件顶部添加import：
```java
import java.util.ArrayList;
import java.util.List;
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -Dtest=ServerInfoTest#testGetRegionIds
```

Expected: 测试通过

- [ ] **Step 5: 提交**

```bash
git add minisql-master/src/main/java/com/minisql/master/cluster/ServerInfo.java
git add minisql-master/src/test/java/com/minisql/master/cluster/ServerInfoTest.java
git commit -m "feat(cluster): add getRegionIds method to ServerInfo"
```

---

### Task 1.2: 创建LoadBalancerConfig配置类

**Files:**
- Create: `minisql-master/src/main/java/com/minisql/master/balance/LoadBalancerConfig.java`
- Test: `minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerConfigTest.java`

- [ ] **Step 1: 编写配置类测试**

```java
package com.minisql.master.balance;

import org.junit.Test;
import static org.junit.Assert.*;

public class LoadBalancerConfigTest {

    @Test
    public void testDefaultConfig() {
        LoadBalancerConfig config = new LoadBalancerConfig();
        
        assertEquals(1.5, config.getLoadThreshold(), 0.001);
        assertEquals(300000L, config.getCheckPeriodMs());
        assertEquals(2, config.getMinRegionCount());
        assertEquals(0.3, config.getMinLoadDiff(), 0.001);
        assertEquals(600000L, config.getCooldownPeriodMs());
        assertEquals(2, config.getMaxConcurrentMigrations());
    }

    @Test
    public void testCustomConfig() {
        LoadBalancerConfig config = new LoadBalancerConfig(
            2.0, 60000L, 3, 0.5, 120000L, 5
        );
        
        assertEquals(2.0, config.getLoadThreshold(), 0.001);
        assertEquals(60000L, config.getCheckPeriodMs());
        assertEquals(3, config.getMinRegionCount());
        assertEquals(0.5, config.getMinLoadDiff(), 0.001);
        assertEquals(120000L, config.getCooldownPeriodMs());
        assertEquals(5, config.getMaxConcurrentMigrations());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -Dtest=LoadBalancerConfigTest
```

Expected: 编译失败

- [ ] **Step 3: 实现LoadBalancerConfig类**

```java
package com.minisql.master.balance;

/**
 * LoadBalancer配置
 */
public class LoadBalancerConfig {
    
    private final double loadThreshold;
    private final long checkPeriodMs;
    private final int minRegionCount;
    private final double minLoadDiff;
    private final long cooldownPeriodMs;
    private final int maxConcurrentMigrations;

    /**
     * 默认配置
     */
    public LoadBalancerConfig() {
        this(1.5, 300000L, 2, 0.3, 600000L, 2);
    }

    /**
     * 自定义配置
     */
    public LoadBalancerConfig(double loadThreshold,
                             long checkPeriodMs,
                             int minRegionCount,
                             double minLoadDiff,
                             long cooldownPeriodMs,
                             int maxConcurrentMigrations) {
        this.loadThreshold = loadThreshold;
        this.checkPeriodMs = checkPeriodMs;
        this.minRegionCount = minRegionCount;
        this.minLoadDiff = minLoadDiff;
        this.cooldownPeriodMs = cooldownPeriodMs;
        this.maxConcurrentMigrations = maxConcurrentMigrations;
    }

    public double getLoadThreshold() {
        return loadThreshold;
    }

    public long getCheckPeriodMs() {
        return checkPeriodMs;
    }

    public int getMinRegionCount() {
        return minRegionCount;
    }

    public double getMinLoadDiff() {
        return minLoadDiff;
    }

    public long getCooldownPeriodMs() {
        return cooldownPeriodMs;
    }

    public int getMaxConcurrentMigrations() {
        return maxConcurrentMigrations;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -Dtest=LoadBalancerConfigTest
```

Expected: 所有测试通过

- [ ] **Step 5: 提交**

```bash
git add minisql-master/src/main/java/com/minisql/master/balance/LoadBalancerConfig.java
git add minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerConfigTest.java
git commit -m "feat(balance): add LoadBalancerConfig class"
```

---

## 阶段2：LoadBalancer核心实现

### Task 2.1: LoadBalancer基础框架和构造函数

**Files:**
- Create: `minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java`
- Test: `minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java`

- [ ] **Step 1: 编写构造函数测试**

```java
package com.minisql.master.balance;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.metadata.MetadataManager;
import com.minisql.master.zk.MasterElection;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LoadBalancerTest {

    @Mock
    private ClusterManager clusterManager;
    
    @Mock
    private MetadataManager metadataManager;
    
    @Mock
    private RegionMigrationManager migrationManager;
    
    @Mock
    private MasterElection masterElection;
    
    private LoadBalancerConfig config;
    private LoadBalancer loadBalancer;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        config = new LoadBalancerConfig();
    }

    @Test
    public void testConstructor() {
        loadBalancer = new LoadBalancer(
            clusterManager,
            metadataManager,
            migrationManager,
            masterElection,
            config
        );
        
        assertNotNull(loadBalancer);
        assertFalse(loadBalancer.isRunning());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -Dtest=LoadBalancerTest#testConstructor
```

Expected: 编译失败，LoadBalancer类不存在

- [ ] **Step 3: 实现LoadBalancer基础框架**

```java
package com.minisql.master.balance;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.metadata.MetadataManager;
import com.minisql.master.zk.MasterElection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;

/**
 * 负载均衡器
 */
public class LoadBalancer {
    
    private static final Logger logger = LoggerFactory.getLogger(LoadBalancer.class);
    
    private final ClusterManager clusterManager;
    private final MetadataManager metadataManager;
    private final RegionMigrationManager migrationManager;
    private final MasterElection masterElection;
    private final LoadBalancerConfig config;
    
    private volatile boolean running;
    private volatile long lastBalanceTime;
    private ScheduledExecutorService scheduler;

    public LoadBalancer(ClusterManager clusterManager,
                       MetadataManager metadataManager,
                       RegionMigrationManager migrationManager,
                       MasterElection masterElection,
                       LoadBalancerConfig config) {
        this.clusterManager = clusterManager;
        this.metadataManager = metadataManager;
        this.migrationManager = migrationManager;
        this.masterElection = masterElection;
        this.config = config;
        this.running = false;
        this.lastBalanceTime = 0;
    }

    public boolean isRunning() {
        return running;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -Dtest=LoadBalancerTest#testConstructor
```

Expected: 测试通过

- [ ] **Step 5: 提交**

```bash
git add minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java
git add minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java
git commit -m "feat(balance): add LoadBalancer basic framework"
```

---

### Task 2.2: 实现start()和stop()生命周期方法

**Files:**
- Modify: `minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java`
- Test: `minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java`

- [ ] **Step 1: 编写start/stop测试**

在LoadBalancerTest中添加：

```java
@Test
public void testStartAsLeader() {
    when(masterElection.isLeader()).thenReturn(true);
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    loadBalancer.start();
    assertTrue(loadBalancer.isRunning());
}

@Test
public void testStartAsNonLeader() {
    when(masterElection.isLeader()).thenReturn(false);
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    loadBalancer.start();
    assertFalse(loadBalancer.isRunning());
}

@Test
public void testStop() {
    when(masterElection.isLeader()).thenReturn(true);
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    loadBalancer.start();
    assertTrue(loadBalancer.isRunning());
    
    loadBalancer.stop();
    assertFalse(loadBalancer.isRunning());
}

@Test
public void testStartTwice() {
    when(masterElection.isLeader()).thenReturn(true);
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    loadBalancer.start();
    loadBalancer.start(); // 第二次调用应该无效
    assertTrue(loadBalancer.isRunning());
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -Dtest=LoadBalancerTest
```

Expected: start()和stop()方法不存在

- [ ] **Step 3: 实现start()和stop()方法**

在LoadBalancer类中添加import：

```java
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
```

在LoadBalancer类中添加方法：

```java
public void start() {
    if (running) {
        logger.warn("LoadBalancer already running");
        return;
    }
    
    if (!masterElection.isLeader()) {
        logger.warn("Not the leader, LoadBalancer will not start");
        return;
    }
    
    running = true;
    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "load-balancer");
        t.setDaemon(true);
        return t;
    });
    
    scheduler.scheduleAtFixedRate(
        this::checkAndBalance,
        config.getCheckPeriodMs(),
        config.getCheckPeriodMs(),
        TimeUnit.MILLISECONDS
    );
    
    logger.info("LoadBalancer started, check period: {}ms", config.getCheckPeriodMs());
}

public void stop() {
    if (!running) {
        logger.warn("LoadBalancer not running");
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

private void checkAndBalance() {
    // Placeholder - will implement in next task
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -Dtest=LoadBalancerTest
```

Expected: 所有测试通过

- [ ] **Step 5: 提交**

```bash
git add minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java
git add minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java
git commit -m "feat(balance): implement LoadBalancer start/stop lifecycle"
```

---

### Task 2.3: 实现needsBalance()负载检测逻辑

**Files:**
- Modify: `minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java`
- Test: `minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java`

- [ ] **Step 1: 编写needsBalance测试**

在LoadBalancerTest中添加：

```java
import com.minisql.master.cluster.ServerInfo;
import com.minisql.common.proto.ServerState;
import java.util.Arrays;
import java.util.Collections;

@Test
public void testNeedsBalanceWithLessThanTwoServers() {
    when(masterElection.isLeader()).thenReturn(true);
    when(clusterManager.getOnlineServers()).thenReturn(Collections.emptyList());
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    assertFalse(loadBalancer.needsBalance());
}

@Test
public void testNeedsBalanceWithBalancedLoad() {
    ServerInfo server1 = createServerWithLoad(1.0, 2);
    ServerInfo server2 = createServerWithLoad(1.1, 2);
    
    when(clusterManager.getOnlineServers()).thenReturn(Arrays.asList(server1, server2));
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    assertFalse(loadBalancer.needsBalance());
}

@Test
public void testNeedsBalanceWithOverloadedServer() {
    ServerInfo server1 = createServerWithLoad(3.0, 3); // 过载
    ServerInfo server2 = createServerWithLoad(1.0, 2);
    
    when(clusterManager.getOnlineServers()).thenReturn(Arrays.asList(server1, server2));
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    assertTrue(loadBalancer.needsBalance());
}

@Test
public void testNeedsBalanceWithInsufficientRegions() {
    ServerInfo server1 = createServerWithLoad(3.0, 1); // 过载但Region数不足
    ServerInfo server2 = createServerWithLoad(1.0, 2);
    
    when(clusterManager.getOnlineServers()).thenReturn(Arrays.asList(server1, server2));
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    assertFalse(loadBalancer.needsBalance());
}

private ServerInfo createServerWithLoad(double loadScore, int regionCount) {
    ServerInfo server = mock(ServerInfo.class);
    when(server.getLoadScore()).thenReturn(loadScore);
    when(server.getRegionCount()).thenReturn(regionCount);
    when(server.getState()).thenReturn(ServerState.SERVER_ONLINE);
    return server;
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -Dtest=LoadBalancerTest
```

Expected: needsBalance()方法不存在

- [ ] **Step 3: 实现needsBalance()方法**

在LoadBalancer类中添加import：

```java
import com.minisql.master.cluster.ServerInfo;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
```

在LoadBalancer类中添加方法：

```java
public boolean needsBalance() {
    List<ServerInfo> onlineServers = clusterManager.getOnlineServers();
    if (onlineServers.size() < 2) {
        return false;
    }
    
    double totalLoad = onlineServers.stream()
        .mapToDouble(ServerInfo::getLoadScore)
        .sum();
    double avgLoad = totalLoad / onlineServers.size();
    
    List<ServerInfo> overloaded = onlineServers.stream()
        .filter(s -> s.getLoadScore() > avgLoad * config.getLoadThreshold())
        .filter(s -> s.getRegionCount() >= config.getMinRegionCount())
        .collect(Collectors.toList());
    
    if (overloaded.isEmpty()) {
        return false;
    }
    
    ServerInfo lightest = onlineServers.stream()
        .min(Comparator.comparingDouble(ServerInfo::getLoadScore))
        .orElse(null);
    
    if (lightest == null) {
        return false;
    }
    
    double maxLoad = overloaded.stream()
        .mapToDouble(ServerInfo::getLoadScore)
        .max()
        .orElse(0);
    
    double loadDiff = maxLoad - lightest.getLoadScore();
    return loadDiff > avgLoad * config.getMinLoadDiff();
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -Dtest=LoadBalancerTest
```

Expected: 所有测试通过

- [ ] **Step 5: 提交**

```bash
git add minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java
git add minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java
git commit -m "feat(balance): implement needsBalance load detection logic"
```

---

### Task 2.4: 实现并发控制canStartNewMigration()

**Files:**
- Modify: `minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java`
- Test: `minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java`

- [ ] **Step 1: 编写并发控制测试**

在LoadBalancerTest中添加：

```java
@Test
public void testCanStartNewMigrationWithNoActiveMigrations() {
    when(migrationManager.getActiveMigrations()).thenReturn(Collections.emptyList());
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    assertTrue(loadBalancer.canStartNewMigration("rs-001"));
}

@Test
public void testCanStartNewMigrationWithGlobalLimitReached() {
    MigrationTask task1 = createMigrationTask("mig-1", "rs-001", "rs-002");
    MigrationTask task2 = createMigrationTask("mig-2", "rs-003", "rs-004");
    
    when(migrationManager.getActiveMigrations()).thenReturn(Arrays.asList(task1, task2));
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    assertFalse(loadBalancer.canStartNewMigration("rs-005"));
}

@Test
public void testCanStartNewMigrationWithServerAlreadyInvolved() {
    MigrationTask task = createMigrationTask("mig-1", "rs-001", "rs-002");
    
    when(migrationManager.getActiveMigrations()).thenReturn(Collections.singletonList(task));
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    assertFalse(loadBalancer.canStartNewMigration("rs-001")); // 作为源
    assertFalse(loadBalancer.canStartNewMigration("rs-002")); // 作为目标
    assertTrue(loadBalancer.canStartNewMigration("rs-003"));  // 未参与
}

private MigrationTask createMigrationTask(String migrationId, String sourceId, String targetId) {
    MigrationTask task = mock(MigrationTask.class);
    when(task.getMigrationId()).thenReturn(migrationId);
    when(task.getSourceServerId()).thenReturn(sourceId);
    when(task.getTargetServerId()).thenReturn(targetId);
    return task;
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -Dtest=LoadBalancerTest
```

Expected: canStartNewMigration()方法不存在

- [ ] **Step 3: 实现canStartNewMigration()方法**

在LoadBalancer类中添加方法：

```java
private boolean canStartNewMigration(String serverId) {
    List<MigrationTask> activeMigrations = migrationManager.getActiveMigrations();
    
    if (activeMigrations.size() >= config.getMaxConcurrentMigrations()) {
        return false;
    }
    
    for (MigrationTask task : activeMigrations) {
        if (task.getSourceServerId().equals(serverId) || 
            task.getTargetServerId().equals(serverId)) {
            return false;
        }
    }
    
    return true;
}
```

为了测试，需要将方法改为包可见（去掉private）或添加测试用的getter。这里我们使用包可见：

```java
boolean canStartNewMigration(String serverId) {
    // ... 同上
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -Dtest=LoadBalancerTest
```

Expected: 所有测试通过

- [ ] **Step 5: 提交**

```bash
git add minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java
git add minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java
git commit -m "feat(balance): implement concurrent migration control"
```

---

### Task 2.5: 实现Region选择辅助方法

**Files:**
- Modify: `minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java`
- Test: `minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java`

- [ ] **Step 1: 编写辅助方法测试**

在LoadBalancerTest中添加：

```java
import com.minisql.master.metadata.RegionMetadata;

@Test
public void testEstimateRegionLoad() {
    RegionMetadata region = mock(RegionMetadata.class);
    when(region.getSizeBytes()).thenReturn(2L * 1024 * 1024 * 1024); // 2GB
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    double load = loadBalancer.estimateRegionLoad(region);
    assertEquals(0.5 + 2.0 * 0.3, load, 0.001); // 0.5 + 0.6 = 1.1
}

@Test
public void testCalculateBenefit() {
    ServerInfo source = createServerWithLoad(3.0, 3);
    ServerInfo target = createServerWithLoad(1.0, 2);
    RegionMetadata region = mock(RegionMetadata.class);
    when(region.getSizeBytes()).thenReturn(1L * 1024 * 1024 * 1024); // 1GB
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    double avgLoad = 2.0;
    double benefit = loadBalancer.calculateBenefit(source, target, region, avgLoad);
    
    // 收益应该为正（方差减少）
    assertTrue(benefit > 0);
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -Dtest=LoadBalancerTest
```

Expected: 方法不存在

- [ ] **Step 3: 实现辅助方法**

在LoadBalancer类中添加import：

```java
import com.minisql.master.metadata.RegionMetadata;
```

在LoadBalancer类中添加方法：

```java
double estimateRegionLoad(RegionMetadata region) {
    double countScore = 0.5;
    double sizeScore = (region.getSizeBytes() / (1024.0 * 1024 * 1024)) * 0.3;
    return countScore + sizeScore;
}

double calculateBenefit(ServerInfo source, ServerInfo target, 
                       RegionMetadata region, double avgLoad) {
    double regionLoad = estimateRegionLoad(region);
    
    double sourceAfter = source.getLoadScore() - regionLoad;
    double targetAfter = target.getLoadScore() + regionLoad;
    
    double beforeVariance = Math.pow(source.getLoadScore() - avgLoad, 2) + 
                           Math.pow(target.getLoadScore() - avgLoad, 2);
    double afterVariance = Math.pow(sourceAfter - avgLoad, 2) + 
                          Math.pow(targetAfter - avgLoad, 2);
    
    double varianceReduction = beforeVariance - afterVariance;
    
    double migrationCost = region.getSizeBytes() / (1024.0 * 1024 * 1024);
    
    return varianceReduction - (migrationCost * 0.1);
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -Dtest=LoadBalancerTest
```

Expected: 所有测试通过

- [ ] **Step 5: 提交**

```bash
git add minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java
git add minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java
git commit -m "feat(balance): implement region selection helper methods"
```

---

### Task 2.6: 实现selectBestRegion()选择算法

**Files:**
- Modify: `minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java`
- Test: `minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java`

- [ ] **Step 1: 编写selectBestRegion测试**

在LoadBalancerTest中添加：

```java
import com.minisql.common.proto.RegionState;

@Test
public void testSelectBestRegionWithNoRegions() {
    ServerInfo source = mock(ServerInfo.class);
    when(source.getRegionIds()).thenReturn(Collections.emptyList());
    
    List<ServerInfo> targets = Arrays.asList(createServerWithLoad(1.0, 2));
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    LoadBalancer.RegionCandidate candidate = loadBalancer.selectBestRegion(source, targets, 2.0);
    assertNull(candidate);
}

@Test
public void testSelectBestRegionSuccess() {
    ServerInfo source = mock(ServerInfo.class);
    when(source.getRegionIds()).thenReturn(Arrays.asList("region-1", "region-2"));
    when(source.getLoadScore()).thenReturn(3.0);
    
    RegionMetadata region1 = mock(RegionMetadata.class);
    when(region1.getState()).thenReturn(RegionState.REGION_ONLINE);
    when(region1.getSizeBytes()).thenReturn(1L * 1024 * 1024 * 1024);
    
    RegionMetadata region2 = mock(RegionMetadata.class);
    when(region2.getState()).thenReturn(RegionState.REGION_ONLINE);
    when(region2.getSizeBytes()).thenReturn(2L * 1024 * 1024 * 1024);
    
    when(metadataManager.getRegion("region-1")).thenReturn(region1);
    when(metadataManager.getRegion("region-2")).thenReturn(region2);
    
    ServerInfo target = createServerWithLoad(1.0, 2);
    when(target.getServerId()).thenReturn("rs-002");
    
    when(migrationManager.getActiveMigrations()).thenReturn(Collections.emptyList());
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    LoadBalancer.RegionCandidate candidate = loadBalancer.selectBestRegion(
        source, Arrays.asList(target), 2.0
    );
    
    assertNotNull(candidate);
    assertTrue(Arrays.asList("region-1", "region-2").contains(candidate.regionId));
    assertEquals("rs-002", candidate.targetServerId);
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -Dtest=LoadBalancerTest
```

Expected: selectBestRegion()和RegionCandidate类不存在

- [ ] **Step 3: 实现RegionCandidate内部类**

在LoadBalancer类内部添加：

```java
static class RegionCandidate {
    final String regionId;
    final String targetServerId;
    final double benefit;
    final double targetLoad;
    
    RegionCandidate(String regionId, String targetServerId, double benefit, double targetLoad) {
        this.regionId = regionId;
        this.targetServerId = targetServerId;
        this.benefit = benefit;
        this.targetLoad = targetLoad;
    }
}
```

- [ ] **Step 4: 实现selectBestRegion()方法**

在LoadBalancer类中添加import：

```java
import com.minisql.common.proto.RegionState;
```

在LoadBalancer类中添加方法：

```java
RegionCandidate selectBestRegion(ServerInfo source, List<ServerInfo> targets, double avgLoad) {
    List<String> regionIds = source.getRegionIds();
    RegionCandidate best = null;
    
    for (String regionId : regionIds) {
        RegionMetadata region = metadataManager.getRegion(regionId);
        if (region == null || region.getState() != RegionState.REGION_ONLINE) {
            continue;
        }
        
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

- [ ] **Step 5: 运行测试验证通过**

```bash
mvn test -Dtest=LoadBalancerTest
```

Expected: 所有测试通过

- [ ] **Step 6: 提交**

```bash
git add minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java
git add minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java
git commit -m "feat(balance): implement selectBestRegion algorithm"
```

---

### Task 2.7: 实现generateMigrationPlans()计划生成

**Files:**
- Modify: `minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java`
- Test: `minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java`

- [ ] **Step 1: 编写generateMigrationPlans测试**

在LoadBalancerTest中添加：

```java
import java.util.ArrayList;

@Test
public void testGenerateMigrationPlansWithBalancedCluster() {
    ServerInfo server1 = createServerWithLoad(1.0, 2);
    ServerInfo server2 = createServerWithLoad(1.1, 2);
    
    when(clusterManager.getOnlineServers()).thenReturn(Arrays.asList(server1, server2));
    when(migrationManager.getActiveMigrations()).thenReturn(Collections.emptyList());
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    List<MigrationPlan> plans = loadBalancer.generateMigrationPlans();
    assertTrue(plans.isEmpty());
}

@Test
public void testGenerateMigrationPlansWithOverload() {
    ServerInfo source = mock(ServerInfo.class);
    when(source.getServerId()).thenReturn("rs-001");
    when(source.getLoadScore()).thenReturn(3.0);
    when(source.getRegionCount()).thenReturn(3);
    when(source.getRegionIds()).thenReturn(Arrays.asList("region-1"));
    
    ServerInfo target = mock(ServerInfo.class);
    when(target.getServerId()).thenReturn("rs-002");
    when(target.getLoadScore()).thenReturn(1.0);
    when(target.getRegionCount()).thenReturn(2);
    
    RegionMetadata region = mock(RegionMetadata.class);
    when(region.getState()).thenReturn(RegionState.REGION_ONLINE);
    when(region.getSizeBytes()).thenReturn(1L * 1024 * 1024 * 1024);
    
    when(clusterManager.getOnlineServers()).thenReturn(Arrays.asList(source, target));
    when(metadataManager.getRegion("region-1")).thenReturn(region);
    when(migrationManager.getActiveMigrations()).thenReturn(Collections.emptyList());
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    List<MigrationPlan> plans = loadBalancer.generateMigrationPlans();
    
    assertEquals(1, plans.size());
    assertEquals("region-1", plans.get(0).getRegionId());
    assertEquals("rs-001", plans.get(0).getSourceServerId());
    assertEquals("rs-002", plans.get(0).getTargetServerId());
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -Dtest=LoadBalancerTest
```

Expected: generateMigrationPlans()方法不存在

- [ ] **Step 3: 实现generateMigrationPlans()方法**

在LoadBalancer类中添加import：

```java
import java.util.ArrayList;
import java.util.Collections;
```

在LoadBalancer类中添加方法：

```java
List<MigrationPlan> generateMigrationPlans() {
    List<ServerInfo> onlineServers = clusterManager.getOnlineServers();
    double avgLoad = onlineServers.stream()
        .mapToDouble(ServerInfo::getLoadScore)
        .average()
        .orElse(0);
    
    List<ServerInfo> overloaded = onlineServers.stream()
        .filter(s -> s.getLoadScore() > avgLoad * config.getLoadThreshold())
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
    
    for (ServerInfo source : overloaded) {
        if (!canStartNewMigration(source.getServerId())) {
            continue;
        }
        
        RegionCandidate candidate = selectBestRegion(source, underloaded, avgLoad);
        if (candidate == null) {
            continue;
        }
        
        MigrationPlan plan = new MigrationPlan(
            candidate.regionId,
            source.getServerId(),
            candidate.targetServerId,
            candidate.benefit,
            String.format("Load balance: %.2f -> %.2f", 
                source.getLoadScore(), candidate.targetLoad)
        );
        plans.add(plan);
        
        if (plans.size() >= config.getMaxConcurrentMigrations()) {
            break;
        }
    }
    
    return plans;
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -Dtest=LoadBalancerTest
```

Expected: 所有测试通过

- [ ] **Step 5: 提交**

```bash
git add minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java
git add minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java
git commit -m "feat(balance): implement generateMigrationPlans"
```

---

### Task 2.8: 实现executeMigrationPlans()和checkAndBalance()

**Files:**
- Modify: `minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java`
- Test: `minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java`

- [ ] **Step 1: 编写executeMigrationPlans测试**

在LoadBalancerTest中添加：

```java
@Test
public void testExecuteMigrationPlans() throws Exception {
    MigrationPlan plan = new MigrationPlan(
        "region-1", "rs-001", "rs-002", 1.5, "test"
    );
    
    when(migrationManager.submitMigration("region-1", "rs-002"))
        .thenReturn("mig-001");
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    loadBalancer.executeMigrationPlans(Arrays.asList(plan));
    
    verify(migrationManager).submitMigration("region-1", "rs-002");
}

@Test
public void testCheckAndBalanceInCooldown() {
    when(masterElection.isLeader()).thenReturn(true);
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    // 设置lastBalanceTime为当前时间
    loadBalancer.setLastBalanceTime(System.currentTimeMillis());
    
    loadBalancer.checkAndBalance();
    
    // 在冷却期内，不应该调用needsBalance
    verify(clusterManager, never()).getOnlineServers();
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -Dtest=LoadBalancerTest
```

Expected: 方法不存在

- [ ] **Step 3: 实现executeMigrationPlans()方法**

在LoadBalancer类中添加方法：

```java
void executeMigrationPlans(List<MigrationPlan> plans) {
    logger.info("Executing {} migration plans", plans.size());
    
    for (MigrationPlan plan : plans) {
        try {
            String migrationId = migrationManager.submitMigration(
                plan.getRegionId(),
                plan.getTargetServerId()
            );
            
            logger.info("Migration submitted: {} - {}", migrationId, plan);
            
        } catch (Exception e) {
            logger.error("Failed to submit migration: {}", plan, e);
        }
    }
}
```

- [ ] **Step 4: 更新checkAndBalance()实现**

替换之前的占位符实现：

```java
private void checkAndBalance() {
    try {
        if (System.currentTimeMillis() - lastBalanceTime < config.getCooldownPeriodMs()) {
            logger.debug("In cooldown period, skip balance check");
            return;
        }
        
        if (!needsBalance()) {
            logger.debug("Cluster is balanced, no action needed");
            return;
        }
        
        List<MigrationPlan> plans = generateMigrationPlans();
        if (plans.isEmpty()) {
            logger.info("No migration plans generated");
            return;
        }
        
        executeMigrationPlans(plans);
        lastBalanceTime = System.currentTimeMillis();
        
    } catch (Exception e) {
        logger.error("Error during balance check", e);
    }
}
```

- [ ] **Step 5: 添加测试用的setter**

为了测试，添加包可见的setter：

```java
void setLastBalanceTime(long time) {
    this.lastBalanceTime = time;
}
```

- [ ] **Step 6: 运行测试验证通过**

```bash
mvn test -Dtest=LoadBalancerTest
```

Expected: 所有测试通过

- [ ] **Step 7: 提交**

```bash
git add minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java
git add minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java
git commit -m "feat(balance): implement executeMigrationPlans and checkAndBalance"
```

---

### Task 2.9: 实现手动操作方法（manualBalance和dryRun）

**Files:**
- Modify: `minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java`
- Test: `minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java`

- [ ] **Step 1: 编写手动操作测试**

在LoadBalancerTest中添加：

```java
@Test
public void testManualBalance() {
    ServerInfo source = mock(ServerInfo.class);
    when(source.getServerId()).thenReturn("rs-001");
    when(source.getLoadScore()).thenReturn(3.0);
    when(source.getRegionCount()).thenReturn(3);
    when(source.getRegionIds()).thenReturn(Arrays.asList("region-1"));
    
    ServerInfo target = createServerWithLoad(1.0, 2);
    when(target.getServerId()).thenReturn("rs-002");
    
    RegionMetadata region = mock(RegionMetadata.class);
    when(region.getState()).thenReturn(RegionState.REGION_ONLINE);
    when(region.getSizeBytes()).thenReturn(1L * 1024 * 1024 * 1024);
    
    when(clusterManager.getOnlineServers()).thenReturn(Arrays.asList(source, target));
    when(metadataManager.getRegion("region-1")).thenReturn(region);
    when(migrationManager.getActiveMigrations()).thenReturn(Collections.emptyList());
    when(migrationManager.submitMigration(anyString(), anyString())).thenReturn("mig-001");
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    loadBalancer.manualBalance();
    
    verify(migrationManager).submitMigration("region-1", "rs-002");
}

@Test
public void testDryRunWithBalancedCluster() {
    ServerInfo server1 = createServerWithLoad(1.0, 2);
    ServerInfo server2 = createServerWithLoad(1.1, 2);
    
    when(clusterManager.getOnlineServers()).thenReturn(Arrays.asList(server1, server2));
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    List<MigrationPlan> plans = loadBalancer.dryRun();
    
    assertTrue(plans.isEmpty());
    verify(migrationManager, never()).submitMigration(anyString(), anyString());
}

@Test
public void testDryRunWithOverload() {
    ServerInfo source = mock(ServerInfo.class);
    when(source.getServerId()).thenReturn("rs-001");
    when(source.getLoadScore()).thenReturn(3.0);
    when(source.getRegionCount()).thenReturn(3);
    when(source.getRegionIds()).thenReturn(Arrays.asList("region-1"));
    
    ServerInfo target = createServerWithLoad(1.0, 2);
    when(target.getServerId()).thenReturn("rs-002");
    
    RegionMetadata region = mock(RegionMetadata.class);
    when(region.getState()).thenReturn(RegionState.REGION_ONLINE);
    when(region.getSizeBytes()).thenReturn(1L * 1024 * 1024 * 1024);
    
    when(clusterManager.getOnlineServers()).thenReturn(Arrays.asList(source, target));
    when(metadataManager.getRegion("region-1")).thenReturn(region);
    when(migrationManager.getActiveMigrations()).thenReturn(Collections.emptyList());
    
    loadBalancer = new LoadBalancer(
        clusterManager, metadataManager, migrationManager, masterElection, config
    );
    
    List<MigrationPlan> plans = loadBalancer.dryRun();
    
    assertEquals(1, plans.size());
    verify(migrationManager, never()).submitMigration(anyString(), anyString());
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -Dtest=LoadBalancerTest
```

Expected: manualBalance()和dryRun()方法不存在

- [ ] **Step 3: 实现manualBalance()方法**

在LoadBalancer类中添加方法：

```java
public void manualBalance() {
    logger.info("Manual balance triggered");
    checkAndBalance();
}
```

- [ ] **Step 4: 实现dryRun()方法**

在LoadBalancer类中添加方法：

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

- [ ] **Step 5: 运行测试验证通过**

```bash
mvn test -Dtest=LoadBalancerTest
```

Expected: 所有测试通过

- [ ] **Step 6: 提交**

```bash
git add minisql-master/src/main/java/com/minisql/master/balance/LoadBalancer.java
git add minisql-master/src/test/java/com/minisql/master/balance/LoadBalancerTest.java
git commit -m "feat(balance): implement manualBalance and dryRun methods"
```

---

## 阶段3：集成测试和文档

### Task 3.1: 运行完整测试套件

**Files:**
- Test: All test files

- [ ] **Step 1: 运行所有balance包测试**

```bash
cd minisql-master
mvn test -Dtest=com.minisql.master.balance.*
```

Expected: 所有测试通过

- [ ] **Step 2: 运行完整测试套件**

```bash
mvn test
```

Expected: 所有测试通过（包括之前的ClusterManager、MetadataManager等）

- [ ] **Step 3: 检查测试覆盖率**

如果有覆盖率工具：
```bash
mvn test jacoco:report
```

查看LoadBalancer的覆盖率，目标 > 80%

---

### Task 3.2: 添加配置文件

**Files:**
- Create: `minisql-master/src/main/resources/balance.properties`

- [ ] **Step 1: 创建配置文件**

```properties
# LoadBalancer配置
load.balance.threshold=1.5
load.balance.check.period=300000
load.balance.min.region.count=2
load.balance.min.load.diff=0.3
load.balance.cooldown.period=600000
load.balance.max.concurrent.migrations=2
```

- [ ] **Step 2: 提交配置文件**

```bash
git add minisql-master/src/main/resources/balance.properties
git commit -m "config(balance): add LoadBalancer configuration file"
```

---

### Task 3.3: 更新文档

**Files:**
- Modify: `docs/superpowers/plans/2026-04-20-loadbalancer-implementation.md`

- [ ] **Step 1: 在计划文档末尾添加完成总结**

在本文档末尾添加：

```markdown
## 实施完成总结

### 已完成组件

1. **LoadBalancerConfig** - 配置类，支持默认和自定义配置
2. **LoadBalancer** - 核心负载均衡器，包含：
   - 生命周期管理（start/stop）
   - 负载检测（needsBalance）
   - 并发控制（canStartNewMigration）
   - Region选择算法（selectBestRegion, calculateBenefit）
   - 计划生成（generateMigrationPlans）
   - 计划执行（executeMigrationPlans）
   - 手动操作（manualBalance, dryRun）

3. **ServerInfo扩展** - 添加getRegionIds()方法

### 测试覆盖

- LoadBalancerConfigTest: 2个测试
- LoadBalancerTest: 15+个测试
- ServerInfoTest: 扩展1个测试

### 配置文件

- balance.properties: LoadBalancer配置参数

### 下一步

LoadBalancer已完成，下一个任务是实现RegionMigrationManager。
```

- [ ] **Step 2: 提交文档更新**

```bash
git add docs/superpowers/plans/2026-04-20-loadbalancer-implementation.md
git commit -m "docs(balance): add implementation completion summary"
```

---

## 自查清单

### 规范覆盖检查

对照设计文档 `docs/superpowers/specs/2026-04-20-loadbalancer-design.md`：

- [x] 类结构和依赖 (Section 2.1) - Task 2.1
- [x] 生命周期管理 (Section 3.1) - Task 2.2
- [x] 负载检测和触发条件 (Section 3.2) - Task 2.3
- [x] Region选择和计划生成 (Section 3.3) - Task 2.5, 2.6, 2.7
- [x] 并发控制 (Section 3.4) - Task 2.4
- [x] 迁移执行 (Section 3.5) - Task 2.8
- [x] 手动操作支持 (Section 3.6) - Task 2.9
- [x] 配置参数 (Section 4) - Task 1.2, 3.2
- [x] 公共API (Section 5) - 所有public方法已实现

### 占位符检查

- [x] 无TBD或TODO
- [x] 所有代码块完整
- [x] 所有测试包含具体断言
- [x] 所有命令包含预期输出

### 类型一致性检查

- [x] LoadBalancerConfig字段名称一致
- [x] RegionCandidate字段名称一致
- [x] 方法签名在所有任务中一致
- [x] Mock对象使用一致

---

## 执行建议

**推荐方式：Subagent-Driven Development**

每个Task派遣一个子代理执行，任务间进行两阶段审查：
1. 规范审查：验证是否符合设计文档
2. 代码质量审查：检查代码质量、测试覆盖、潜在问题

**预计时间：**
- 阶段1（准备工作）：30分钟
- 阶段2（核心实现）：2-3小时
- 阶段3（集成测试）：30分钟
- 总计：3-4小时

**注意事项：**
1. RegionMigrationManager尚未实现，测试中使用Mock
2. ServerInfo.getRegionIds()需要先实现
3. 所有测试使用JUnit 4（与项目保持一致）
4. 包可见性用于测试辅助方法

