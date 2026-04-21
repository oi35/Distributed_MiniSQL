package com.minisql.master.balance;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.cluster.ServerInfo;
import com.minisql.master.metadata.MetadataManager;
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
}
