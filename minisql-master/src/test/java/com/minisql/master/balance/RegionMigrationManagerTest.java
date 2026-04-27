package com.minisql.master.balance;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.metadata.MetadataManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class RegionMigrationManagerTest {

    private ClusterManager clusterManager;
    private MetadataManager metadataManager;
    private MigrationConfig config;
    private MigrationExecutor executor;
    private RegionMigrationManager manager;

    @Before
    public void setUp() {
        clusterManager = new ClusterManager(30000, 5000);
        metadataManager = new MetadataManager();
        config = MigrationConfig.builder()
            .checkPeriodMs(100)
            .build();
        executor = new MigrationExecutor();
        manager = new RegionMigrationManager(clusterManager, metadataManager, config, executor);
    }

    @Test
    public void testStartAndStop() {
        assertFalse(manager.isRunning());

        manager.start();
        assertTrue(manager.isRunning());

        manager.stop();
        assertFalse(manager.isRunning());
    }
}
