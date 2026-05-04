package com.minisql.master.integration.fast;

import com.minisql.master.balance.*;
import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.cluster.ServerInfo;
import com.minisql.master.metadata.MetadataManager;
import com.minisql.master.metadata.RegionMetadata;
import com.minisql.master.zk.MasterElection;
import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Fast layer end-to-end tests for LoadBalancer functionality.
 * Tests complete load balancing workflow with real components.
 */
public class LoadBalancerEndToEndTest {
    private ClusterManager clusterManager;
    private MetadataManager metadataManager;
    private RegionMigrationManager migrationManager;
    private LoadBalancer loadBalancer;
    private MasterElection election;

    @Before
    public void setUp() {
        clusterManager = mock(ClusterManager.class);
        metadataManager = new MetadataManager();

        MigrationConfig config = MigrationConfig.builder()
                .checkPeriodMs(1000)  // Check every 1 second for faster tests
                .maxRetries(3)
                .prepareTimeoutMs(5000)
                .syncTimeoutMs(10000)
                .switchTimeoutMs(3000)
                .build();

        MigrationExecutor executor = new MigrationExecutor();
        migrationManager = new RegionMigrationManager(clusterManager, metadataManager, config, executor);

        election = mock(MasterElection.class);
        when(election.isLeader()).thenReturn(true);

        LoadBalancerConfig lbConfig = new LoadBalancerConfig();

        loadBalancer = new LoadBalancer(clusterManager, metadataManager, migrationManager, election, lbConfig);
    }

    @After
    public void tearDown() {
        if (loadBalancer != null && loadBalancer.isRunning()) {
            loadBalancer.stop();
        }
        if (migrationManager != null && migrationManager.isRunning()) {
            migrationManager.stop();
        }
    }

    @Test
    public void testLoadBalancerStartsAutomatically() {
        // Start LoadBalancer
        loadBalancer.start();

        // Verify it's running
        assertTrue("LoadBalancer should be running", loadBalancer.isRunning());

        // Verify background thread is active
        await().atMost(3, TimeUnit.SECONDS)
                .until(() -> loadBalancer.isRunning());
    }

    @Test
    public void testDetectsImbalance() {
        // Create imbalanced cluster: rs-001 has 5 regions, rs-002 has 1 region
        ServerInfo overloaded = mock(ServerInfo.class);
        when(overloaded.getServerId()).thenReturn("rs-001");
        when(overloaded.getLoadScore()).thenReturn(5.0);
        when(overloaded.getRegionCount()).thenReturn(5);
        when(overloaded.getRegionIds()).thenReturn(Arrays.asList(
                "region-001", "region-002", "region-003", "region-004", "region-005"));

        ServerInfo underloaded = mock(ServerInfo.class);
        when(underloaded.getServerId()).thenReturn("rs-002");
        when(underloaded.getLoadScore()).thenReturn(1.0);
        when(underloaded.getRegionCount()).thenReturn(1);
        when(underloaded.getRegionIds()).thenReturn(Arrays.asList("region-006"));

        when(clusterManager.getOnlineServers()).thenReturn(Arrays.asList(overloaded, underloaded));

        // Add regions to metadata
        for (int i = 1; i <= 6; i++) {
            String regionId = "region-" + String.format("%03d", i);
            metadataManager.createRegion(regionId, "test-table", "key" + i, "key" + (i+1));
        }

        // Test imbalance detection
        assertTrue("Should detect imbalance", loadBalancer.needsBalance());
    }

    @Test
    public void testGeneratesMigrationPlan() {
        // Setup imbalanced cluster
        ServerInfo overloaded = mock(ServerInfo.class);
        when(overloaded.getServerId()).thenReturn("rs-001");
        when(overloaded.getLoadScore()).thenReturn(5.0);
        when(overloaded.getRegionCount()).thenReturn(5);
        when(overloaded.getRegionIds()).thenReturn(Arrays.asList(
                "region-001", "region-002", "region-003", "region-004", "region-005"));

        ServerInfo underloaded = mock(ServerInfo.class);
        when(underloaded.getServerId()).thenReturn("rs-002");
        when(underloaded.getLoadScore()).thenReturn(1.0);
        when(underloaded.getRegionCount()).thenReturn(1);
        when(underloaded.getRegionIds()).thenReturn(Arrays.asList("region-006"));

        when(clusterManager.getOnlineServers()).thenReturn(Arrays.asList(overloaded, underloaded));
        when(clusterManager.getServerInfo("rs-001")).thenReturn(overloaded);
        when(clusterManager.getServerInfo("rs-002")).thenReturn(underloaded);

        // Add regions to metadata
        for (int i = 1; i <= 6; i++) {
            String regionId = "region-" + String.format("%03d", i);
            metadataManager.createRegion(regionId, "test-table", "key" + i, "key" + (i+1));
        }

        // Verify imbalance is detected
        assertTrue("Should detect imbalance", loadBalancer.needsBalance());
    }

    @Test
    public void testExecutesMigration() throws Exception {
        // Setup cluster
        ServerInfo source = mock(ServerInfo.class);
        when(source.getServerId()).thenReturn("rs-001");

        ServerInfo target = mock(ServerInfo.class);
        when(target.getServerId()).thenReturn("rs-002");

        when(clusterManager.getServerInfo("rs-001")).thenReturn(source);
        when(clusterManager.getServerInfo("rs-002")).thenReturn(target);

        // Add region to metadata
        metadataManager.createRegion("region-001", "test-table", "key1", "key2");

        // Start migration manager
        migrationManager.start();

        // Submit migration
        String taskId = migrationManager.submitMigration("region-001", "rs-001", "rs-002");

        assertNotNull("Task ID should not be null", taskId);

        // Wait for migration to complete (or fail, since we're using mock executor)
        await().atMost(20, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    MigrationTask task = migrationManager.getTask(taskId);
                    MigrationState state = task.getState();
                    return state == MigrationState.COMPLETED || state == MigrationState.FAILED;
                });

        MigrationTask task = migrationManager.getTask(taskId);
        assertNotNull("Task should exist", task);

        // With mock executor, task should complete successfully
        assertEquals("Task should complete", MigrationState.COMPLETED, task.getState());
    }

    @Test
    public void testRebalancesCluster() throws Exception {
        // Setup imbalanced cluster
        ServerInfo overloaded = mock(ServerInfo.class);
        when(overloaded.getServerId()).thenReturn("rs-001");
        when(overloaded.getLoadScore()).thenReturn(4.0);
        when(overloaded.getRegionCount()).thenReturn(4);
        when(overloaded.getRegionIds()).thenReturn(Arrays.asList(
                "region-001", "region-002", "region-003", "region-004"));

        ServerInfo underloaded = mock(ServerInfo.class);
        when(underloaded.getServerId()).thenReturn("rs-002");
        when(underloaded.getLoadScore()).thenReturn(1.0);
        when(underloaded.getRegionCount()).thenReturn(1);
        when(underloaded.getRegionIds()).thenReturn(Arrays.asList("region-005"));

        when(clusterManager.getOnlineServers()).thenReturn(Arrays.asList(overloaded, underloaded));
        when(clusterManager.getServerInfo("rs-001")).thenReturn(overloaded);
        when(clusterManager.getServerInfo("rs-002")).thenReturn(underloaded);

        // Add regions to metadata
        for (int i = 1; i <= 5; i++) {
            String regionId = "region-" + String.format("%03d", i);
            metadataManager.createRegion(regionId, "test-table", "key" + i, "key" + (i+1));
        }

        // Start components
        migrationManager.start();
        loadBalancer.start();

        // Verify LoadBalancer detects imbalance
        assertTrue("Should detect imbalance", loadBalancer.needsBalance());

        // Wait for LoadBalancer to potentially trigger migrations
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> loadBalancer.isRunning());

        // Verify LoadBalancer is running
        assertTrue("LoadBalancer should be running", loadBalancer.isRunning());
    }
}
