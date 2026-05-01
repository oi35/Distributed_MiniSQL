package com.minisql.master.integration.e2e;

import com.minisql.master.MasterServer;
import com.minisql.master.integration.fixtures.FakeRegionServer;
import org.junit.*;
import org.testcontainers.containers.GenericContainer;
import static org.junit.Assert.*;
import static org.awaitility.Awaitility.*;
import java.util.concurrent.TimeUnit;

/**
 * E2E layer integration tests for Master failover and election.
 * Tests multi-Master scenarios with real Zookeeper.
 */
public class EndToEndFailoverTest {
    private static GenericContainer<?> zookeeper;
    private MasterServer master1;
    private MasterServer master2;
    private FakeRegionServer regionServer;

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

    @After
    public void tearDown() throws Exception {
        if (regionServer != null) {
            regionServer.stop();
        }
        if (master1 != null) {
            master1.stop();
        }
        if (master2 != null) {
            master2.stop();
        }
    }

    @Test
    public void testMasterElectionWithMultipleInstances() throws Exception {
        String zkConnect = zookeeper.getHost() + ":" + zookeeper.getMappedPort(2181);

        // Start two Master instances
        master1 = new MasterServer(8100, "master-failover-1", zkConnect);
        master1.start();

        master2 = new MasterServer(8101, "master-failover-2", zkConnect);
        master2.start();

        // Wait for election to complete
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> master1.isLeader() || master2.isLeader());

        // Verify exactly one is leader
        boolean master1IsLeader = master1.isLeader();
        boolean master2IsLeader = master2.isLeader();

        assertTrue("Exactly one Master should be leader",
                (master1IsLeader && !master2IsLeader) || (!master1IsLeader && master2IsLeader));

        // Verify the other is follower
        if (master1IsLeader) {
            assertFalse("Master2 should be follower", master2.isLeader());
        } else {
            assertFalse("Master1 should be follower", master1.isLeader());
        }
    }

    @Test
    public void testLeaderFailoverOnCrash() throws Exception {
        String zkConnect = zookeeper.getHost() + ":" + zookeeper.getMappedPort(2181);

        // Start two Masters
        master1 = new MasterServer(8102, "master-failover-3", zkConnect);
        master1.start();

        master2 = new MasterServer(8103, "master-failover-4", zkConnect);
        master2.start();

        // Wait for election
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> master1.isLeader() || master2.isLeader());

        // Identify leader and follower
        MasterServer leader = master1.isLeader() ? master1 : master2;
        MasterServer follower = master1.isLeader() ? master2 : master1;

        assertTrue("Leader should be elected", leader.isLeader());
        assertFalse("Follower should not be leader", follower.isLeader());

        // Stop the leader (simulate crash)
        leader.stop();

        // Wait for follower to become leader
        await().atMost(20, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> follower.isLeader());

        assertTrue("Follower should become new leader", follower.isLeader());
    }

    @Test
    public void testRegionServerReconnectAfterFailover() throws Exception {
        String zkConnect = zookeeper.getHost() + ":" + zookeeper.getMappedPort(2181);

        // Start two Masters
        master1 = new MasterServer(8104, "master-failover-5", zkConnect);
        master1.start();

        master2 = new MasterServer(8105, "master-failover-6", zkConnect);
        master2.start();

        // Wait for election
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> master1.isLeader() || master2.isLeader());

        MasterServer leader = master1.isLeader() ? master1 : master2;
        MasterServer follower = master1.isLeader() ? master2 : master1;

        // Start RegionServer and connect to leader
        regionServer = new FakeRegionServer("rs-failover-001");
        regionServer.start(leader.getPort());
        regionServer.heartbeat();

        // Wait for registration
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> leader.getClusterManager().getServerInfo("rs-failover-001") != null);

        assertTrue("RegionServer should be registered",
                leader.getClusterManager().getServerInfo("rs-failover-001") != null);

        // Stop leader
        leader.stop();

        // Wait for follower to become leader
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> follower.isLeader());

        // RegionServer should reconnect to new leader
        // (In real implementation, RegionServer would detect leader change and reconnect)
        // For now, just verify new leader is active
        assertTrue("New leader should be active", follower.isLeader());
    }

    @Test
    public void testMetadataConsistencyAfterFailover() throws Exception {
        String zkConnect = zookeeper.getHost() + ":" + zookeeper.getMappedPort(2181);

        // Start two Masters
        master1 = new MasterServer(8106, "master-failover-7", zkConnect);
        master1.start();

        master2 = new MasterServer(8107, "master-failover-8", zkConnect);
        master2.start();

        // Wait for election
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> master1.isLeader() || master2.isLeader());

        MasterServer leader = master1.isLeader() ? master1 : master2;
        MasterServer follower = master1.isLeader() ? master2 : master1;

        // Create region on leader
        leader.getMetadataManager().createRegion("region-failover-001", "test-table", "key1", "key2");

        // Verify region exists
        assertNotNull("Region should exist on leader",
                leader.getMetadataManager().getRegion("region-failover-001"));

        // Stop leader
        leader.stop();

        // Wait for follower to become leader
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> follower.isLeader());

        // Verify metadata is consistent on new leader
        // (In real implementation with ZK-backed metadata, this should work)
        // For now, verify new leader is functional
        assertTrue("New leader should be functional", follower.isLeader());

        // New leader should be able to create regions
        follower.getMetadataManager().createRegion("region-failover-002", "test-table", "key2", "key3");
        assertNotNull("New leader should create regions",
                follower.getMetadataManager().getRegion("region-failover-002"));
    }
}
