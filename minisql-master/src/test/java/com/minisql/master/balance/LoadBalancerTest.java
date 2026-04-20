package com.minisql.master.balance;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.metadata.MetadataManager;
import com.minisql.master.zk.MasterElection;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

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
}
