package com.minisql.master.balance;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.metadata.MetadataManager;
import com.minisql.master.zk.MasterElection;
import org.junit.Before;
import org.junit.Test;

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
}
