package com.minisql.master.balance;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.cluster.ServerInfo;
import com.minisql.master.metadata.MetadataManager;
import com.minisql.master.zk.MasterElection;
import com.minisql.common.proto.ServerState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LoadBalancerIntegrationTest {

    private ClusterManager clusterManager;
    private MetadataManager metadataManager;
    private MigrationConfig migrationConfig;
    private MigrationExecutor executor;
    private RegionMigrationManager migrationManager;
    private MasterElection masterElection;
    private LoadBalancerConfig loadBalancerConfig;
    private LoadBalancer loadBalancer;

    @Before
    public void setUp() {
        clusterManager = mock(ClusterManager.class);
        metadataManager = new MetadataManager();
        migrationConfig = MigrationConfig.builder()
            .checkPeriodMs(100)
            .build();
        executor = new MigrationExecutor();
        migrationManager = new RegionMigrationManager(clusterManager, metadataManager, migrationConfig, executor);
        masterElection = mock(MasterElection.class);
        when(masterElection.isLeader()).thenReturn(true);
        loadBalancerConfig = new LoadBalancerConfig();
        loadBalancer = new LoadBalancer(clusterManager, metadataManager, migrationManager, masterElection, loadBalancerConfig);
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
    public void testIntegrationWithOverloadedServers() throws InterruptedException {
        ServerInfo overloaded = createServer("rs-001", 5.0, 5);
        ServerInfo underloaded = createServer("rs-002", 1.0, 2);
        when(clusterManager.getOnlineServers()).thenReturn(Arrays.asList(overloaded, underloaded));

        migrationManager.start();
        loadBalancer.start();

        assertTrue(loadBalancer.needsBalance());

        String migrationId = migrationManager.submitMigration("region-1", "rs-001", "rs-002");
        assertNotNull(migrationId);

        Thread.sleep(2000);

        List<MigrationTask> activeMigrations = migrationManager.getActiveMigrations();
        assertTrue(activeMigrations.size() > 0);
        assertFalse(loadBalancer.canStartNewMigration("rs-001"));
    }

    @Test
    public void testNoMigrationsWhenBalanced() throws InterruptedException {
        ServerInfo server1 = createServer("rs-001", 2.0, 3);
        ServerInfo server2 = createServer("rs-002", 2.1, 3);
        when(clusterManager.getOnlineServers()).thenReturn(Arrays.asList(server1, server2));

        migrationManager.start();
        loadBalancer.start();

        assertFalse(loadBalancer.needsBalance());

        Thread.sleep(2000);

        List<MigrationTask> activeMigrations = migrationManager.getActiveMigrations();
        assertEquals(0, activeMigrations.size());
        assertTrue(loadBalancer.canStartNewMigration("rs-001"));
    }

    private ServerInfo createServer(String serverId, double loadScore, int regionCount) {
        ServerInfo server = mock(ServerInfo.class);
        when(server.getServerId()).thenReturn(serverId);
        when(server.getLoadScore()).thenReturn(loadScore);
        when(server.getRegionCount()).thenReturn(regionCount);
        when(server.getState()).thenReturn(ServerState.SERVER_ONLINE);
        return server;
    }
}
