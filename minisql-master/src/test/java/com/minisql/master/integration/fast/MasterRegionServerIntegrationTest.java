package com.minisql.master.integration.fast;

import com.minisql.master.integration.fixtures.*;
import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.cluster.ServerInfo;
import org.junit.*;
import static org.junit.Assert.*;
import static org.awaitility.Awaitility.*;
import java.util.concurrent.TimeUnit;

/**
 * Fast layer integration tests for Master-RegionServer interaction.
 * Uses embedded Zookeeper and in-process gRPC.
 */
public class MasterRegionServerIntegrationTest {
    private EmbeddedZookeeperServer zkServer;
    private TestCluster cluster;
    private FakeRegionServer regionServer1;
    private FakeRegionServer regionServer2;

    @Before
    public void setUp() throws Exception {
        zkServer = new EmbeddedZookeeperServer();
        zkServer.start();

        cluster = TestClusterBuilder.create()
                .withZookeeper(zkServer)
                .withMaster(8000, "master-1")
                .build();

        // Wait for Master to become leader
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> cluster.getMasterServer().isLeader());
    }

    @After
    public void tearDown() throws Exception {
        if (regionServer1 != null) {
            regionServer1.stop();
        }
        if (regionServer2 != null) {
            regionServer2.stop();
        }
        if (cluster != null) {
            cluster.shutdown();
        }
        if (zkServer != null) {
            zkServer.stop();
        }
    }

    @Test
    public void testRegionServerRegistration() throws Exception {
        // Create and start a fake RegionServer
        regionServer1 = new FakeRegionServer("rs-001");
        regionServer1.start(8000);

        // Send heartbeat to register
        regionServer1.heartbeat();

        // Verify registration in ClusterManager
        ClusterManager clusterManager = cluster.getMasterServer().getClusterManager();
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> clusterManager.getServerInfo("rs-001") != null);

        ServerInfo serverInfo = clusterManager.getServerInfo("rs-001");
        assertNotNull("RegionServer should be registered", serverInfo);
        assertEquals("rs-001", serverInfo.getServerId());
        assertTrue("Server should be online", serverInfo.isOnline());
    }

    @Test
    public void testHeartbeatMechanism() throws Exception {
        // Register RegionServer
        regionServer1 = new FakeRegionServer("rs-001");
        regionServer1.start(8000);
        regionServer1.heartbeat();

        ClusterManager clusterManager = cluster.getMasterServer().getClusterManager();
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> clusterManager.getServerInfo("rs-001") != null);

        // Get initial timestamp
        ServerInfo serverInfo = clusterManager.getServerInfo("rs-001");
        long initialTimestamp = serverInfo.getLastHeartbeatTime();

        // Wait a bit and send another heartbeat
        Thread.sleep(1000);
        regionServer1.heartbeat();

        // Verify timestamp updated
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> {
                    ServerInfo updated = clusterManager.getServerInfo("rs-001");
                    return updated.getLastHeartbeatTime() > initialTimestamp;
                });

        ServerInfo updatedInfo = clusterManager.getServerInfo("rs-001");
        assertTrue("Heartbeat timestamp should be updated",
                updatedInfo.getLastHeartbeatTime() > initialTimestamp);
        assertTrue("Server should remain online", updatedInfo.isOnline());
    }

    @Test
    public void testRegionServerFailureDetection() throws Exception {
        // Register RegionServer
        regionServer1 = new FakeRegionServer("rs-001");
        regionServer1.start(8000);
        regionServer1.heartbeat();

        ClusterManager clusterManager = cluster.getMasterServer().getClusterManager();
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> clusterManager.getServerInfo("rs-001") != null);

        // Verify server is online
        assertTrue("Server should be online initially",
                clusterManager.getServerInfo("rs-001").isOnline());

        // Stop sending heartbeats (simulate failure)
        // Wait for heartbeat timeout (default 30 seconds)
        await().atMost(35, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> {
                    ServerInfo info = clusterManager.getServerInfo("rs-001");
                    return info != null && !info.isOnline();
                });

        ServerInfo failedInfo = clusterManager.getServerInfo("rs-001");
        assertFalse("Server should be detected as offline", failedInfo.isOnline());
    }

    @Test
    public void testRegionAssignment() throws Exception {
        // Register two RegionServers
        regionServer1 = new FakeRegionServer("rs-001");
        regionServer1.start(8000);
        regionServer1.heartbeat();

        regionServer2 = new FakeRegionServer("rs-002");
        regionServer2.start(8000);
        regionServer2.heartbeat();

        ClusterManager clusterManager = cluster.getMasterServer().getClusterManager();

        // Wait for both servers to register
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> clusterManager.getServerInfo("rs-001") != null
                        && clusterManager.getServerInfo("rs-002") != null);

        // Verify both servers are online
        assertTrue("rs-001 should be online",
                clusterManager.getServerInfo("rs-001").isOnline());
        assertTrue("rs-002 should be online",
                clusterManager.getServerInfo("rs-002").isOnline());

        // Test region assignment by adding regions to servers
        regionServer1.addRegion("region-001", 100 * 1024 * 1024);
        regionServer1.heartbeat();

        regionServer2.addRegion("region-002", 150 * 1024 * 1024);
        regionServer2.heartbeat();

        // Verify regions are tracked
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> {
                    ServerInfo info1 = clusterManager.getServerInfo("rs-001");
                    ServerInfo info2 = clusterManager.getServerInfo("rs-002");
                    return info1.getRegionIds().contains("region-001")
                            && info2.getRegionIds().contains("region-002");
                });

        ServerInfo info1 = clusterManager.getServerInfo("rs-001");
        ServerInfo info2 = clusterManager.getServerInfo("rs-002");

        assertTrue("rs-001 should have region-001",
                info1.getRegionIds().contains("region-001"));
        assertTrue("rs-002 should have region-002",
                info2.getRegionIds().contains("region-002"));
    }
}
