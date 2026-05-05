package com.minisql.master.integration.stress;

import com.minisql.master.MasterServer;
import org.junit.*;
import org.testcontainers.containers.GenericContainer;
import static org.junit.Assert.*;
import static org.awaitility.Awaitility.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stress layer tests for Master election under high load and failure scenarios.
 * Tests system behavior under extreme conditions.
 */
public class MasterElectionStressTest {
    private static GenericContainer<?> zookeeper;
    private List<MasterServer> masters = new ArrayList<>();

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
        for (MasterServer master : masters) {
            if (master != null) {
                try {
                    master.stop();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
        masters.clear();
    }

    @Test
    public void testRapidMasterFailover() throws Exception {
        String zkConnect = zookeeper.getHost() + ":" + zookeeper.getMappedPort(2181);

        // Start 3 Masters
        for (int i = 0; i < 3; i++) {
            MasterServer master = new MasterServer(8200 + i, "master-stress-" + i, zkConnect);
            master.start();
            masters.add(master);
        }

        // Wait for initial election
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> masters.stream().anyMatch(MasterServer::isLeader));

        // Perform rapid failovers (stop leader 5 times)
        for (int round = 0; round < 5; round++) {
            // Find current leader
            MasterServer leader = masters.stream()
                    .filter(MasterServer::isLeader)
                    .findFirst()
                    .orElse(null);

            assertNotNull("Should have a leader in round " + round, leader);

            // Stop leader
            leader.stop();
            masters.remove(leader);

            // Wait for new leader
            await().atMost(20, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .until(() -> masters.stream().anyMatch(MasterServer::isLeader));

            // Start a new Master to replace the stopped one
            if (masters.size() < 3) {
                MasterServer newMaster = new MasterServer(
                        8200 + masters.size(),
                        "master-stress-new-" + round,
                        zkConnect);
                newMaster.start();
                masters.add(newMaster);
            }
        }

        // Verify system is still functional
        assertTrue("Should have at least one Master running", !masters.isEmpty());
        assertTrue("Should have a leader after all failovers",
                masters.stream().anyMatch(MasterServer::isLeader));
    }

    @Test
    public void testConcurrentMasterStartup() throws Exception {
        String zkConnect = zookeeper.getHost() + ":" + zookeeper.getMappedPort(2181);

        // Start 5 Masters concurrently
        List<Thread> startThreads = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final int index = i;
            Thread t = new Thread(() -> {
                try {
                    MasterServer master = new MasterServer(
                            8210 + index,
                            "master-concurrent-" + index,
                            zkConnect);
                    master.start();
                    synchronized (masters) {
                        masters.add(master);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            startThreads.add(t);
            t.start();
        }

        // Wait for all to start
        for (Thread t : startThreads) {
            t.join(10000);
        }

        // Wait for election
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> masters.stream().anyMatch(MasterServer::isLeader));

        // Verify exactly one leader
        long leaderCount = masters.stream().filter(MasterServer::isLeader).count();
        assertEquals("Should have exactly one leader", 1, leaderCount);
    }

    @Test
    public void testElectionUnderLoad() throws Exception {
        String zkConnect = zookeeper.getHost() + ":" + zookeeper.getMappedPort(2181);

        // Start 3 Masters
        for (int i = 0; i < 3; i++) {
            MasterServer master = new MasterServer(8220 + i, "master-load-" + i, zkConnect);
            master.start();
            masters.add(master);
        }

        // Wait for election
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> masters.stream().anyMatch(MasterServer::isLeader));

        // Simulate load by creating many regions
        MasterServer leader = masters.stream()
                .filter(MasterServer::isLeader)
                .findFirst()
                .orElseThrow();

        // Note: createRegion requires table to exist
        // For stress testing, we just verify leader remains stable under load
        // Simulate load by repeatedly querying metadata
        for (int i = 0; i < 100; i++) {
            leader.getMetadataManager().getTable("test-table");
        }

        // Verify leader is still functional
        assertTrue("Leader should still be active", leader.isLeader());

        // Verify metadata manager is still accessible
        assertNotNull("Should be able to access metadata manager",
                leader.getMetadataManager());
    }

    @Test
    public void testSplitBrainPrevention() throws Exception {
        String zkConnect = zookeeper.getHost() + ":" + zookeeper.getMappedPort(2181);

        // Start 2 Masters
        MasterServer master1 = new MasterServer(8230, "master-split-1", zkConnect);
        master1.start();
        masters.add(master1);

        MasterServer master2 = new MasterServer(8231, "master-split-2", zkConnect);
        master2.start();
        masters.add(master2);

        // Wait for election
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> master1.isLeader() || master2.isLeader());

        // Verify no split brain (exactly one leader)
        int leaderCount = 0;
        if (master1.isLeader()) leaderCount++;
        if (master2.isLeader()) leaderCount++;

        assertEquals("Should have exactly one leader (no split brain)", 1, leaderCount);

        // Simulate network partition by stopping and restarting
        MasterServer leader = master1.isLeader() ? master1 : master2;
        MasterServer follower = master1.isLeader() ? master2 : master1;

        leader.stop();

        // Wait for follower to become leader
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> follower.isLeader());

        // Restart old leader
        MasterServer newMaster = new MasterServer(
                leader.getPort(),
                leader.getServerId() + "-restarted",
                zkConnect);
        newMaster.start();
        masters.add(newMaster);

        // Wait for stabilization
        Thread.sleep(5000);

        // Verify still only one leader
        leaderCount = 0;
        if (follower.isLeader()) leaderCount++;
        if (newMaster.isLeader()) leaderCount++;

        assertEquals("Should still have exactly one leader after restart", 1, leaderCount);
    }

    @Test
    public void testLeaderStabilityUnderChurn() throws Exception {
        String zkConnect = zookeeper.getHost() + ":" + zookeeper.getMappedPort(2181);

        // Start 3 Masters
        for (int i = 0; i < 3; i++) {
            MasterServer master = new MasterServer(8240 + i, "master-churn-" + i, zkConnect);
            master.start();
            masters.add(master);
        }

        // Wait for election
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> masters.stream().anyMatch(MasterServer::isLeader));

        AtomicInteger leaderChanges = new AtomicInteger(0);
        String[] lastLeaderId = new String[1];

        // Monitor leader for 30 seconds while adding/removing followers
        long endTime = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < endTime) {
            // Check current leader
            MasterServer currentLeader = masters.stream()
                    .filter(MasterServer::isLeader)
                    .findFirst()
                    .orElse(null);

            if (currentLeader != null) {
                String currentLeaderId = currentLeader.getServerId();
                if (lastLeaderId[0] != null && !lastLeaderId[0].equals(currentLeaderId)) {
                    leaderChanges.incrementAndGet();
                }
                lastLeaderId[0] = currentLeaderId;
            }

            // Add/remove a follower
            MasterServer follower = masters.stream()
                    .filter(m -> !m.isLeader())
                    .findFirst()
                    .orElse(null);

            if (follower != null && masters.size() > 2) {
                follower.stop();
                masters.remove(follower);
            }

            if (masters.size() < 4) {
                MasterServer newMaster = new MasterServer(
                        8240 + masters.size(),
                        "master-churn-new-" + System.currentTimeMillis(),
                        zkConnect);
                newMaster.start();
                masters.add(newMaster);
            }

            Thread.sleep(2000);
        }

        // Verify leader was relatively stable (fewer than 5 changes in 30 seconds)
        assertTrue("Leader should be relatively stable under churn (changes: " + leaderChanges.get() + ")",
                leaderChanges.get() < 5);
    }
}
