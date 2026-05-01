package com.minisql.master.integration.stress;

import com.minisql.master.MasterServer;
import com.minisql.master.balance.MigrationTask;
import com.minisql.master.balance.MigrationState;
import com.minisql.master.integration.fixtures.FakeRegionServer;
import com.minisql.common.proto.ServerState;
import org.junit.*;
import org.testcontainers.containers.GenericContainer;
import static org.junit.Assert.*;
import static org.awaitility.Awaitility.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Stress layer tests for failure recovery scenarios.
 * Tests system resilience under various failure conditions.
 */
public class FailureRecoveryIntegrationTest {
    private static GenericContainer<?> zookeeper;
    private MasterServer masterServer;
    private List<FakeRegionServer> regionServers = new ArrayList<>();

    @BeforeClass
    public static void setUpClass() {
        zookeeper = new GenericContainer<>("zookeeper:3.9.1")
                .withExposedPorts(2181);
        zookeeper.start();
    }

    @AfterClass
    public static void tearDownClass() {
        if (zookeeper != null) {
            zookeeper.stop();
        }
    }

    @Before
    public void setUp() throws Exception {
        String zkConnect = zookeeper.getHost() + ":" + zookeeper.getMappedPort(2181);
        masterServer = new MasterServer(8300, "master-recovery", zkConnect);
        masterServer.start();

        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> masterServer.isLeader());
    }

    @After
    public void tearDown() throws Exception {
        for (FakeRegionServer rs : regionServers) {
            if (rs != null) {
                try {
                    rs.stop();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
        regionServers.clear();

        if (masterServer != null) {
            masterServer.stop();
        }
    }

    @Test
    public void testRegionServerCrashDuringMigration() throws Exception {
        // Start two RegionServers
        FakeRegionServer rs1 = new FakeRegionServer("rs-recovery-001");
        rs1.start(8300);
        rs1.addRegion("region-crash-001", 100 * 1024 * 1024);
        rs1.heartbeat();
        regionServers.add(rs1);

        FakeRegionServer rs2 = new FakeRegionServer("rs-recovery-002");
        rs2.start(8300);
        rs2.heartbeat();
        regionServers.add(rs2);

        // Wait for registration
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> masterServer.getClusterManager().getServerInfo("rs-recovery-001") != null
                        && masterServer.getClusterManager().getServerInfo("rs-recovery-002") != null);

        // Create region
        masterServer.getMetadataManager().createRegion("region-crash-001", "test-table", "key1", "key2");

        // Submit migration
        String taskId = masterServer.getMigrationManager()
                .submitMigration("region-crash-001", "rs-recovery-001", "rs-recovery-002");

        // Wait a bit for migration to start
        Thread.sleep(2000);

        // Crash source RegionServer
        rs1.setFailureMode(FakeRegionServer.FailureMode.DISCONNECT);
        rs1.stop();

        // Wait for migration to fail or complete
        await().atMost(40, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    MigrationTask task = masterServer.getMigrationManager().getTask(taskId);
                    return task.getState() == MigrationState.FAILED
                            || task.getState() == MigrationState.COMPLETED;
                });

        MigrationTask task = masterServer.getMigrationManager().getTask(taskId);

        // Verify migration handled the crash gracefully
        assertNotNull("Task should exist", task);
        assertTrue("Task should fail or complete",
                task.getState() == MigrationState.FAILED || task.getState() == MigrationState.COMPLETED);
    }

    @Test
    public void testMultipleRegionServerFailures() throws Exception {
        // Start 5 RegionServers
        for (int i = 0; i < 5; i++) {
            FakeRegionServer rs = new FakeRegionServer("rs-multi-fail-" + i);
            rs.start(8300);
            rs.addRegion("region-multi-" + i, 100 * 1024 * 1024);
            rs.heartbeat();
            regionServers.add(rs);
        }

        // Wait for all to register
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> masterServer.getClusterManager().getOnlineServers().size() == 5);

        assertEquals("Should have 5 online servers", 5,
                masterServer.getClusterManager().getOnlineServers().size());

        // Crash 3 servers simultaneously
        for (int i = 0; i < 3; i++) {
            regionServers.get(i).setFailureMode(FakeRegionServer.FailureMode.DISCONNECT);
            regionServers.get(i).stop();
        }

        // Wait for Master to detect failures
        await().atMost(40, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> masterServer.getClusterManager().getOnlineServers().size() == 2);

        // Verify only 2 servers remain online
        assertEquals("Should have 2 online servers after failures", 2,
                masterServer.getClusterManager().getOnlineServers().size());

        // Verify Master is still functional
        assertTrue("Master should still be leader", masterServer.isLeader());
    }

    @Test
    public void testRegionServerRecoveryAfterCrash() throws Exception {
        // Start RegionServer
        FakeRegionServer rs = new FakeRegionServer("rs-recovery-003");
        rs.start(8300);
        rs.addRegion("region-recovery-001", 100 * 1024 * 1024);
        rs.heartbeat();
        regionServers.add(rs);

        // Wait for registration
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> masterServer.getClusterManager().getServerInfo("rs-recovery-003") != null);

        assertTrue("Server should be online",
                masterServer.getClusterManager().getServerInfo("rs-recovery-003") != null);

        // Crash server
        rs.stop();

        // Wait for Master to detect failure
        await().atMost(40, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> {
                    var info = masterServer.getClusterManager().getServerInfo("rs-recovery-003");
                    return info == null || info.getState() != ServerState.SERVER_ONLINE;
                });

        // Restart server
        FakeRegionServer newRs = new FakeRegionServer("rs-recovery-003");
        newRs.start(8300);
        newRs.addRegion("region-recovery-001", 100 * 1024 * 1024);
        newRs.heartbeat();
        regionServers.add(newRs);

        // Wait for re-registration
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> {
                    var info = masterServer.getClusterManager().getServerInfo("rs-recovery-003");
                    return info != null && info.getState() == ServerState.SERVER_ONLINE;
                });

        // Verify server is back online
        assertNotNull("Server should be re-registered",
                masterServer.getClusterManager().getServerInfo("rs-recovery-003"));
    }

    @Test
    public void testCascadingFailures() throws Exception {
        // Start 4 RegionServers
        for (int i = 0; i < 4; i++) {
            FakeRegionServer rs = new FakeRegionServer("rs-cascade-" + i);
            rs.start(8300);
            rs.addRegion("region-cascade-" + i, 100 * 1024 * 1024);
            rs.heartbeat();
            regionServers.add(rs);
        }

        // Wait for all to register
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> masterServer.getClusterManager().getOnlineServers().size() == 4);

        // Fail servers one by one with delays
        for (int i = 0; i < 3; i++) {
            FakeRegionServer rs = regionServers.get(i);
            rs.setFailureMode(FakeRegionServer.FailureMode.DISCONNECT);
            rs.stop();

            // Wait a bit between failures
            Thread.sleep(5000);

            // Verify Master is still functional
            assertTrue("Master should remain functional during cascading failures",
                    masterServer.isLeader());
        }

        // Wait for Master to detect all failures
        await().atMost(40, TimeUnit.SECONDS)
                .until(() -> masterServer.getClusterManager().getOnlineServers().size() == 1);

        // Verify system is still operational with 1 server
        assertEquals("Should have 1 server remaining", 1,
                masterServer.getClusterManager().getOnlineServers().size());
        assertTrue("Master should still be functional", masterServer.isLeader());
    }

    @Test
    public void testNetworkPartitionRecovery() throws Exception {
        // Start 3 RegionServers
        for (int i = 0; i < 3; i++) {
            FakeRegionServer rs = new FakeRegionServer("rs-partition-" + i);
            rs.start(8300);
            rs.addRegion("region-partition-" + i, 100 * 1024 * 1024);
            rs.heartbeat();
            regionServers.add(rs);
        }

        // Wait for registration
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> masterServer.getClusterManager().getOnlineServers().size() == 3);

        // Simulate network partition (slow mode)
        for (FakeRegionServer rs : regionServers) {
            rs.setFailureMode(FakeRegionServer.FailureMode.SLOW);
        }

        // Send slow heartbeats
        for (int i = 0; i < 3; i++) {
            for (FakeRegionServer rs : regionServers) {
                try {
                    rs.heartbeat();
                } catch (Exception e) {
                    // Expected - slow mode may timeout
                }
            }
            Thread.sleep(3000);
        }

        // Recover from partition
        for (FakeRegionServer rs : regionServers) {
            rs.setFailureMode(FakeRegionServer.FailureMode.NONE);
        }

        // Send normal heartbeats
        for (FakeRegionServer rs : regionServers) {
            rs.heartbeat();
        }

        // Wait for recovery
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> masterServer.getClusterManager().getOnlineServers().size() >= 2);

        // Verify system recovered
        assertTrue("Should have at least 2 servers online after recovery",
                masterServer.getClusterManager().getOnlineServers().size() >= 2);
        assertTrue("Master should still be functional", masterServer.isLeader());
    }

    @Test
    public void testHighLoadDuringFailure() throws Exception {
        // Start 2 RegionServers
        FakeRegionServer rs1 = new FakeRegionServer("rs-load-001");
        rs1.start(8300);
        rs1.heartbeat();
        regionServers.add(rs1);

        FakeRegionServer rs2 = new FakeRegionServer("rs-load-002");
        rs2.start(8300);
        rs2.heartbeat();
        regionServers.add(rs2);

        // Wait for registration
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> masterServer.getClusterManager().getOnlineServers().size() == 2);

        // Create many regions under load
        for (int i = 0; i < 50; i++) {
            masterServer.getMetadataManager().createRegion(
                    "region-load-" + i,
                    "test-table",
                    "key" + i,
                    "key" + (i + 1));
        }

        // Fail one server during load
        rs1.setFailureMode(FakeRegionServer.FailureMode.DISCONNECT);
        rs1.stop();

        // Continue creating regions
        for (int i = 50; i < 100; i++) {
            masterServer.getMetadataManager().createRegion(
                    "region-load-" + i,
                    "test-table",
                    "key" + i,
                    "key" + (i + 1));
        }

        // Wait for failure detection
        await().atMost(40, TimeUnit.SECONDS)
                .until(() -> masterServer.getClusterManager().getOnlineServers().size() == 1);

        // Verify Master handled load during failure
        assertTrue("Master should remain functional under load during failure",
                masterServer.isLeader());

        // Verify all regions were created
        assertNotNull("Should be able to query regions",
                masterServer.getMetadataManager().getRegion("region-load-75"));
    }
}
