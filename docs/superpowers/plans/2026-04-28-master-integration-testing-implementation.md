# Master模块集成测试 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现Master模块的完整集成测试套件，包括Fast层、E2E层和Stress层测试

**Architecture:** 分层测试架构 - Fast层使用嵌入式ZK和in-process gRPC，E2E层使用Testcontainers和真实gRPC，Stress层进行压力和故障测试

**Tech Stack:** JUnit 4, Mockito, Curator Test, gRPC Testing, Testcontainers, Awaitility

---

## 实施阶段

**阶段1：基础设施（1天）** - Tasks 1-5
**阶段2：Fast层测试（1天）** - Tasks 6-7
**阶段3：FakeRegionServer（0.5天）** - Task 8
**阶段4：E2E层测试（1天）** - Tasks 9-10
**阶段5：Stress层测试（1.5天）** - Tasks 11-12

---

## Phase 1: 依赖和基础设施

### Task 1: 添加Maven依赖和Profiles

**Files:**
- Modify: `minisql-master/pom.xml`

- [ ] **Step 1: 在pom.xml的`<dependencies>`部分添加测试依赖**

在现有测试依赖后添加:

```xml
<!-- Curator Test -->
<dependency>
    <groupId>org.apache.curator</groupId>
    <artifactId>curator-test</artifactId>
    <version>5.5.0</version>
    <scope>test</scope>
</dependency>

<!-- gRPC Testing -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-testing</artifactId>
    <version>${grpc.version}</version>
    <scope>test</scope>
</dependency>

<!-- Testcontainers -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>

<!-- Awaitility -->
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <version>4.2.0</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 在`</dependencies>`后添加Maven profiles**

```xml
<profiles>
    <profile>
        <id>integration-fast</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.0.0</version>
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
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.0.0</version>
                    <configuration>
                        <includes>
                            <include>**/integration/e2e/**/*Test.java</include>
                        </includes>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
    <profile>
        <id>integration-stress</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.0.0</version>
                    <configuration>
                        <includes>
                            <include>**/integration/stress/**/*Test.java</include>
                        </includes>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
    <profile>
        <id>integration-all</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.0.0</version>
                    <configuration>
                        <includes>
                            <include>**/integration/**/*Test.java</include>
                        </includes>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

- [ ] **Step 3: 验证依赖**

Run: `cd minisql-master && mvn dependency:resolve -U`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add minisql-master/pom.xml
git commit -m "build(test): add integration test dependencies and profiles"
```

### Task 2: EmbeddedZookeeperServer

**Files:**
- Create: `minisql-master/src/test/java/com/minisql/master/integration/fixtures/EmbeddedZookeeperServer.java`

- [ ] **Step 1: 创建类**

```java
package com.minisql.master.integration.fixtures;

import org.apache.curator.test.TestingServer;

public class EmbeddedZookeeperServer {
    private TestingServer testingServer;
    
    public void start() throws Exception {
        testingServer = new TestingServer(true);
    }
    
    public void stop() throws Exception {
        if (testingServer != null) {
            testingServer.stop();
            testingServer.close();
        }
    }
    
    public String getConnectString() {
        return testingServer != null ? testingServer.getConnectString() : null;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add minisql-master/src/test/java/com/minisql/master/integration/fixtures/EmbeddedZookeeperServer.java
git commit -m "test(fixtures): add EmbeddedZookeeperServer"
```

### Task 3: InProcessGrpcServer

**Files:**
- Create: `minisql-master/src/test/java/com/minisql/master/integration/fixtures/InProcessGrpcServer.java`

- [ ] **Step 1: 创建类**

```java
package com.minisql.master.integration.fixtures;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;

public class InProcessGrpcServer {
    private Server server;
    private String serverName;
    
    public void start(String serverName) throws Exception {
        this.serverName = serverName;
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .build()
                .start();
    }
    
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }
    
    public void addService(BindableService service) {
        // Services must be added before start in InProcess mode
    }
    
    public ManagedChannel createChannel() {
        return InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add minisql-master/src/test/java/com/minisql/master/integration/fixtures/InProcessGrpcServer.java
git commit -m "test(fixtures): add InProcessGrpcServer"
```

### Task 4: TestCluster和TestClusterBuilder

**Files:**
- Create: `minisql-master/src/test/java/com/minisql/master/integration/fixtures/TestCluster.java`
- Create: `minisql-master/src/test/java/com/minisql/master/integration/fixtures/TestClusterBuilder.java`

- [ ] **Step 1: 创建TestCluster**

```java
package com.minisql.master.integration.fixtures;

import com.minisql.master.MasterServer;
import java.util.List;

public class TestCluster {
    private final EmbeddedZookeeperServer zkServer;
    private final MasterServer masterServer;
    
    public TestCluster(EmbeddedZookeeperServer zkServer, MasterServer masterServer) {
        this.zkServer = zkServer;
        this.masterServer = masterServer;
    }
    
    public EmbeddedZookeeperServer getZkServer() {
        return zkServer;
    }
    
    public MasterServer getMasterServer() {
        return masterServer;
    }
    
    public void shutdown() throws Exception {
        if (zkServer != null) {
            zkServer.stop();
        }
    }
}
```

- [ ] **Step 2: 创建TestClusterBuilder**

```java
package com.minisql.master.integration.fixtures;

import com.minisql.master.MasterServer;

public class TestClusterBuilder {
    private EmbeddedZookeeperServer zkServer;
    private int masterPort = 8000;
    private String masterId = "test-master";
    
    public static TestClusterBuilder create() {
        return new TestClusterBuilder();
    }
    
    public TestClusterBuilder withZookeeper(EmbeddedZookeeperServer zk) {
        this.zkServer = zk;
        return this;
    }
    
    public TestClusterBuilder withMaster(int port, String serverId) {
        this.masterPort = port;
        this.masterId = serverId;
        return this;
    }
    
    public TestCluster build() throws Exception {
        if (zkServer == null) {
            throw new IllegalStateException("Zookeeper not configured");
        }
        MasterServer master = new MasterServer(masterPort, masterId, zkServer.getConnectString());
        return new TestCluster(zkServer, master);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add minisql-master/src/test/java/com/minisql/master/integration/fixtures/
git commit -m "test(fixtures): add TestCluster and TestClusterBuilder"
```

---

## Phase 2: Fast层测试

### Task 5: MasterRegionServerIntegrationTest (4个测试)

**Files:**
- Create: `minisql-master/src/test/java/com/minisql/master/integration/fast/MasterRegionServerIntegrationTest.java`

- [ ] **Step 1: 创建测试类框架和testRegionServerRegistration**

```java
package com.minisql.master.integration.fast;

import com.minisql.master.integration.fixtures.*;
import org.junit.*;
import static org.junit.Assert.*;

public class MasterRegionServerIntegrationTest {
    private EmbeddedZookeeperServer zkServer;
    private TestCluster cluster;
    
    @Before
    public void setUp() throws Exception {
        zkServer = new EmbeddedZookeeperServer();
        zkServer.start();
    }
    
    @After
    public void tearDown() throws Exception {
        if (cluster != null) {
            cluster.shutdown();
        }
        if (zkServer != null) {
            zkServer.stop();
        }
    }
    
    @Test
    public void testRegionServerRegistration() throws Exception {
        cluster = TestClusterBuilder.create()
                .withZookeeper(zkServer)
                .withMaster(8000, "master-1")
                .build();
        
        // Test will verify registration when RegionServer implementation is ready
        assertNotNull(cluster.getMasterServer());
    }
}
```

- [ ] **Step 2: 运行测试验证框架**

Run: `cd minisql-master && mvn test -Pintegration-fast -Dtest=MasterRegionServerIntegrationTest`
Expected: 1 test passes

- [ ] **Step 3: Commit**

```bash
git add minisql-master/src/test/java/com/minisql/master/integration/fast/MasterRegionServerIntegrationTest.java
git commit -m "test(fast): add MasterRegionServerIntegrationTest framework"
```

### Task 6: LoadBalancerEndToEndTest (5个测试)

**Files:**
- Create: `minisql-master/src/test/java/com/minisql/master/integration/fast/LoadBalancerEndToEndTest.java`

- [ ] **Step 1: 创建测试类和testLoadDetectionAndPlanGeneration**

```java
package com.minisql.master.integration.fast;

import com.minisql.master.balance.*;
import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.cluster.ServerInfo;
import com.minisql.master.metadata.MetadataManager;
import com.minisql.master.zk.MasterElection;
import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LoadBalancerEndToEndTest {
    private ClusterManager clusterManager;
    private MetadataManager metadataManager;
    private RegionMigrationManager migrationManager;
    private LoadBalancer loadBalancer;
    
    @Before
    public void setUp() {
        clusterManager = mock(ClusterManager.class);
        metadataManager = new MetadataManager();
        MigrationConfig config = MigrationConfig.builder().build();
        MigrationExecutor executor = new MigrationExecutor();
        migrationManager = new RegionMigrationManager(clusterManager, metadataManager, config, executor);
        
        MasterElection election = mock(MasterElection.class);
        when(election.isLeader()).thenReturn(true);
        
        loadBalancer = new LoadBalancer(clusterManager, metadataManager, migrationManager, election, new LoadBalancerConfig());
    }
    
    @After
    public void tearDown() {
        if (loadBalancer.isRunning()) {
            loadBalancer.stop();
        }
        if (migrationManager.isRunning()) {
            migrationManager.stop();
        }
    }
    
    @Test
    public void testLoadDetectionAndPlanGeneration() {
        ServerInfo overloaded = mock(ServerInfo.class);
        when(overloaded.getServerId()).thenReturn("rs-001");
        when(overloaded.getLoadScore()).thenReturn(5.0);
        when(overloaded.getRegionCount()).thenReturn(5);
        
        ServerInfo underloaded = mock(ServerInfo.class);
        when(underloaded.getServerId()).thenReturn("rs-002");
        when(underloaded.getLoadScore()).thenReturn(1.0);
        when(underloaded.getRegionCount()).thenReturn(1);
        
        when(clusterManager.getOnlineServers()).thenReturn(java.util.Arrays.asList(overloaded, underloaded));
        
        assertTrue(loadBalancer.needsBalance());
    }
}
```

- [ ] **Step 2: 运行测试**

Run: `cd minisql-master && mvn test -Pintegration-fast -Dtest=LoadBalancerEndToEndTest`
Expected: 1 test passes

- [ ] **Step 3: Commit**

```bash
git add minisql-master/src/test/java/com/minisql/master/integration/fast/LoadBalancerEndToEndTest.java
git commit -m "test(fast): add LoadBalancerEndToEndTest"
```

---

## Phase 3: FakeRegionServer增强

### Task 7: 完整FakeRegionServer实现

**Files:**
- Modify: `minisql-master/src/test/java/com/minisql/master/integration/fixtures/FakeRegionServer.java`

- [ ] **Step 1: 实现完整的FakeRegionServer（支持gRPC和故障注入）**

创建完整实现，包含heartbeat、prepareRegionMigration、syncRegionData、switchRegion等方法，以及FailureMode枚举。

- [ ] **Step 2: Commit**

```bash
git add minisql-master/src/test/java/com/minisql/master/integration/fixtures/FakeRegionServer.java
git commit -m "test(fixtures): implement complete FakeRegionServer with failure injection"
```

---

## Phase 4: E2E层测试

### Task 8: EndToEndMigrationTest (4个测试)

**Files:**
- Create: `minisql-master/src/test/java/com/minisql/master/integration/e2e/EndToEndMigrationTest.java`

- [ ] **Step 1: 创建测试类框架**

使用Testcontainers启动真实Zookeeper，使用真实gRPC服务器和FakeRegionServer。

- [ ] **Step 2: 实现testCompleteMigrationWithRealGrpc**

验证真实gRPC调用的完整迁移流程。

- [ ] **Step 3: Commit**

```bash
git add minisql-master/src/test/java/com/minisql/master/integration/e2e/EndToEndMigrationTest.java
git commit -m "test(e2e): add EndToEndMigrationTest"
```

### Task 9: EndToEndFailoverTest (4个测试)

**Files:**
- Create: `minisql-master/src/test/java/com/minisql/master/integration/e2e/EndToEndFailoverTest.java`

- [ ] **Step 1: 创建测试类框架**

启动多个Master实例进行选举测试。

- [ ] **Step 2: 实现testMasterElectionWithMultipleInstances**

验证多Master选举机制。

- [ ] **Step 3: Commit**

```bash
git add minisql-master/src/test/java/com/minisql/master/integration/e2e/EndToEndFailoverTest.java
git commit -m "test(e2e): add EndToEndFailoverTest"
```

---

## Phase 5: Stress层测试

### Task 10: MasterElectionStressTest (5个测试)

**Files:**
- Create: `minisql-master/src/test/java/com/minisql/master/integration/stress/MasterElectionStressTest.java`

- [ ] **Step 1: 创建测试类框架**

使用Testcontainers和多Master实例。

- [ ] **Step 2: 实现testRapidMasterFailover**

连续杀死Leader 5次，验证选举。

- [ ] **Step 3: Commit**

```bash
git add minisql-master/src/test/java/com/minisql/master/integration/stress/MasterElectionStressTest.java
git commit -m "test(stress): add MasterElectionStressTest"
```

### Task 11: FailureRecoveryIntegrationTest (6个测试)

**Files:**
- Create: `minisql-master/src/test/java/com/minisql/master/integration/stress/FailureRecoveryIntegrationTest.java`

- [ ] **Step 1: 创建测试类框架**

使用FakeRegionServer的故障注入功能。

- [ ] **Step 2: 实现testRegionServerCrashDuringMigration**

在迁移过程中杀死RegionServer，验证回滚。

- [ ] **Step 3: Commit**

```bash
git add minisql-master/src/test/java/com/minisql/master/integration/stress/FailureRecoveryIntegrationTest.java
git commit -m "test(stress): add FailureRecoveryIntegrationTest"
```

---

## Phase 6: 验证和文档

### Task 12: 运行所有测试并验证

- [ ] **Step 1: 运行Fast层测试**

Run: `cd minisql-master && mvn test -Pintegration-fast`
Expected: 所有Fast层测试通过，执行时间 < 30秒

- [ ] **Step 2: 运行E2E层测试**

Run: `cd minisql-master && mvn test -Pintegration-e2e`
Expected: 所有E2E层测试通过，执行时间 < 2分钟

- [ ] **Step 3: 运行Stress层测试**

Run: `cd minisql-master && mvn test -Pintegration-stress`
Expected: 所有Stress层测试通过，执行时间 < 5分钟

- [ ] **Step 4: 运行所有集成测试**

Run: `cd minisql-master && mvn test -Pintegration-all`
Expected: 所有测试通过

- [ ] **Step 5: 最终commit**

```bash
git add .
git commit -m "test(integration): complete Master module integration test suite"
```

---

## 注意事项

1. **测试隔离**: 每个测试类使用独立的ZK实例和端口
2. **资源清理**: 所有测试必须在@After中清理资源
3. **异步验证**: 使用Awaitility替代Thread.sleep()
4. **故障注入**: FakeRegionServer支持多种故障模式
5. **并发控制**: Stress测试注意线程安全

## 验收标准

- ✅ 所有测试用例实现
- ✅ Fast层 < 30秒
- ✅ E2E层 < 2分钟
- ✅ Stress层 < 5分钟
- ✅ 无flaky tests
- ✅ 代码覆盖率提升

