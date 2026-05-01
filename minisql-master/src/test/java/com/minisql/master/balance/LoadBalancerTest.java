package com.minisql.master.balance;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.cluster.ServerInfo;
import com.minisql.master.metadata.MetadataManager;
import com.minisql.master.metadata.RegionMetadata;
import com.minisql.master.zk.MasterElection;
import com.minisql.common.proto.ServerState;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LoadBalancerTest {

    private ClusterManager clusterManager;
    private MetadataManager metadataManager;
    private RegionMigrationManager migrationManager;
    private MasterElection masterElection;
    private LoadBalancerConfig config;
    private LoadBalancer loadBalancer;

    @Before
    public void setUp() {
        // Use null for dependencies in constructor test
        // They will be properly mocked in future integration tests
        clusterManager = null;
        metadataManager = null;
        migrationManager = null;
        masterElection = null;
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

    @Test
    public void testStartAsLeader() {
        masterElection = mock(MasterElection.class);
        when(masterElection.isLeader()).thenReturn(true);

        loadBalancer = new LoadBalancer(
            clusterManager, metadataManager, migrationManager, masterElection, config
        );

        loadBalancer.start();
        assertTrue(loadBalancer.isRunning());
    }

    @Test
    public void testStartAsNonLeader() {
        masterElection = mock(MasterElection.class);
        when(masterElection.isLeader()).thenReturn(false);

        loadBalancer = new LoadBalancer(
            clusterManager, metadataManager, migrationManager, masterElection, config
        );

        loadBalancer.start();
        assertFalse(loadBalancer.isRunning());
    }

    @Test
    public void testStop() {
        masterElection = mock(MasterElection.class);
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
        masterElection = mock(MasterElection.class);
        when(masterElection.isLeader()).thenReturn(true);

        loadBalancer = new LoadBalancer(
            clusterManager, metadataManager, migrationManager, masterElection, config
        );

        loadBalancer.start();
        loadBalancer.start(); // Second call should have no effect
        assertTrue(loadBalancer.isRunning());
    }

    @Test
    public void testStopWithoutStart() {
        loadBalancer = new LoadBalancer(
            clusterManager, metadataManager, migrationManager, masterElection, config
        );

        loadBalancer.stop(); // Should log warning but not fail
        assertFalse(loadBalancer.isRunning());
    }

    @Test
    public void testNeedsBalanceWithLessThanTwoServers() {
        clusterManager = mock(ClusterManager.class);
        masterElection = mock(MasterElection.class);
        when(masterElection.isLeader()).thenReturn(true);
        when(clusterManager.getOnlineServers()).thenReturn(Collections.emptyList());

        loadBalancer = new LoadBalancer(
            clusterManager, metadataManager, migrationManager, masterElection, config
        );

        assertFalse(loadBalancer.needsBalance());
    }

    @Test
    public void testNeedsBalanceWithBalancedLoad() {
        clusterManager = mock(ClusterManager.class);
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
        clusterManager = mock(ClusterManager.class);
        ServerInfo server1 = createServerWithLoad(3.5, 3); // 过载
        ServerInfo server2 = createServerWithLoad(1.0, 2);

        when(clusterManager.getOnlineServers()).thenReturn(Arrays.asList(server1, server2));

        loadBalancer = new LoadBalancer(
            clusterManager, metadataManager, migrationManager, masterElection, config
        );

        assertTrue(loadBalancer.needsBalance());
    }

    @Test
    public void testNeedsBalanceWithInsufficientRegions() {
        clusterManager = mock(ClusterManager.class);
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

    @Test
    public void testCanStartNewMigrationWithNoActiveMigrations() {
        migrationManager = mock(RegionMigrationManager.class);
        when(migrationManager.getActiveMigrations()).thenReturn(Collections.emptyList());

        loadBalancer = new LoadBalancer(
            clusterManager, metadataManager, migrationManager, masterElection, config
        );

        assertTrue(loadBalancer.canStartNewMigration("rs-001"));
    }

    @Test
    public void testCanStartNewMigrationWithGlobalLimitReached() {
        migrationManager = mock(RegionMigrationManager.class);
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
        migrationManager = mock(RegionMigrationManager.class);
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
}
