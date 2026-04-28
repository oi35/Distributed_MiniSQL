package com.minisql.master.balance;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.metadata.MetadataManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Practical usage examples for RegionMigrationManager.
 */
public class RegionMigrationManagerUsageExample {

    private RegionMigrationManager manager;
    private ClusterManager clusterManager;
    private MetadataManager metadataManager;
    private MigrationExecutor executor;
    private MigrationConfig config;

    @Before
    public void setup() {
        clusterManager = mock(ClusterManager.class);
        metadataManager = mock(MetadataManager.class);
        executor = new MigrationExecutor();
        config = MigrationConfig.builder()
            .checkPeriodMs(100)
            .maxRetries(2)
            .build();

        manager = new RegionMigrationManager(clusterManager, metadataManager, config, executor);
    }

    @After
    public void teardown() {
        if (manager.isRunning()) {
            manager.stop();
        }
    }

    @Test
    public void demonstrateBasicUsage() throws InterruptedException {
        // Start the manager
        manager.start();
        assertTrue(manager.isRunning());

        // Submit a migration task
        String migrationId = manager.submitMigration("region-1", "server-1", "server-2");
        assertNotNull(migrationId);

        // Monitor progress
        MigrationTask task = manager.getTask(migrationId);
        assertEquals(MigrationState.PENDING, task.getState());

        // Wait for completion
        for (int i = 0; i < 50 && task.getState() != MigrationState.COMPLETED; i++) {
            Thread.sleep(200);
        }

        // Verify completion
        assertEquals(MigrationState.COMPLETED, task.getState());
        assertTrue(task.getDuration() > 0);

        // Stop the manager
        manager.stop();
        assertFalse(manager.isRunning());
    }

    @Test
    public void demonstrateQueryAndControl() throws InterruptedException {
        manager.start();

        // Submit multiple migrations
        String id1 = manager.submitMigration("region-1", "server-1", "server-2");
        String id2 = manager.submitMigration("region-2", "server-1", "server-3");
        String id3 = manager.submitMigration("region-3", "server-2", "server-3");

        Thread.sleep(100);

        // Query by state
        List<MigrationTask> pending = manager.getTasksByState(MigrationState.PENDING);
        assertTrue(pending.size() <= 3);

        // Query by server
        List<MigrationTask> server1Tasks = manager.getTasksByServer("server-1");
        assertEquals(2, server1Tasks.size());

        // Cancel a migration
        boolean cancelled = manager.cancelMigration(id3);
        assertTrue(cancelled);
        assertEquals(MigrationState.CANCELLED, manager.getTask(id3).getState());

        // Wait for one to fail or complete
        Thread.sleep(2000);

        // Retry if failed
        MigrationTask task1 = manager.getTask(id1);
        if (task1.getState() == MigrationState.FAILED) {
            manager.retryMigration(id1);
            assertEquals(MigrationState.PENDING, task1.getState());
        }
    }

    @Test
    public void demonstrateStatistics() throws InterruptedException {
        manager.start();

        // Submit migrations
        manager.submitMigration("region-1", "server-1", "server-2");
        manager.submitMigration("region-2", "server-1", "server-3");
        manager.submitMigration("region-3", "server-2", "server-3");

        // Cancel one
        String id = manager.submitMigration("region-4", "server-3", "server-1");
        Thread.sleep(50);
        manager.cancelMigration(id);

        // Wait for some to complete
        Thread.sleep(3000);

        // Get statistics
        MigrationStatistics stats = manager.getStatistics();
        assertEquals(4, stats.getTotalSubmitted());
        assertTrue(stats.getCompleted() >= 0);
        assertTrue(stats.getCancelled() >= 1);
        assertTrue(stats.getActive() >= 0);

        if (stats.getCompleted() > 0) {
            assertTrue(stats.getAvgDurationMs() > 0);
            assertTrue(stats.getSuccessRate() >= 0.0 && stats.getSuccessRate() <= 1.0);
        }
    }
}
