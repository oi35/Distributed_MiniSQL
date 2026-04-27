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

    @Test
    public void testSubmitMigration() {
        String migrationId = manager.submitMigration("region-1", "rs-1", "rs-2");
        assertNotNull(migrationId);

        MigrationTask task = manager.getTask(migrationId);
        assertNotNull(task);
        assertEquals("region-1", task.getRegionId());
        assertEquals("rs-1", task.getSourceServerId());
        assertEquals("rs-2", task.getTargetServerId());
        assertEquals(MigrationState.PENDING, task.getState());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSubmitMigrationWithNullRegionId() {
        manager.submitMigration(null, "rs-1", "rs-2");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSubmitMigrationWithNullSourceId() {
        manager.submitMigration("region-1", null, "rs-2");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSubmitMigrationWithNullTargetId() {
        manager.submitMigration("region-1", "rs-1", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSubmitMigrationWithSameSourceAndTarget() {
        manager.submitMigration("region-1", "rs-1", "rs-1");
    }

    @Test
    public void testStateAdvancement() throws InterruptedException {
        manager.start();
        String migrationId = manager.submitMigration("region-1", "rs-1", "rs-2");
        MigrationTask task = manager.getTask(migrationId);
        assertEquals(MigrationState.PENDING, task.getState());

        Thread.sleep(200);

        assertEquals(MigrationState.MIGRATING_PREPARE, task.getState());
        assertTrue(task.getStartTime() > 0);
        manager.stop();
    }

    @Test
    public void testAutoRetry() throws InterruptedException {
        config = MigrationConfig.builder()
            .checkPeriodMs(50)
            .maxRetries(3)
            .build();
        manager = new RegionMigrationManager(clusterManager, metadataManager, config, executor);
        manager.start();

        String migrationId = manager.submitMigration("region-1", "rs-1", "rs-2");
        MigrationTask task = manager.getTask(migrationId);

        task.incrementRetry();
        task.setState(MigrationState.FAILED);
        task.setMetadata("retryTime", System.currentTimeMillis() - 100);

        Thread.sleep(250);

        assertNotEquals(MigrationState.FAILED, task.getState());
        assertTrue(task.getRetryCount() > 0);
        manager.stop();
    }

    @Test
    public void testRetryDelay() {
        config = MigrationConfig.builder()
            .checkPeriodMs(100)
            .maxRetries(3)
            .build();
        manager = new RegionMigrationManager(clusterManager, metadataManager, config, executor);

        String migrationId = manager.submitMigration("region-1", "rs-1", "rs-2");
        MigrationTask task = manager.getTask(migrationId);

        task.setState(MigrationState.MIGRATING_PREPARE);

        long beforeRetry = System.currentTimeMillis();

        try {
            java.lang.reflect.Method method = RegionMigrationManager.class.getDeclaredMethod(
                "handleFailure", MigrationTask.class, String.class);
            method.setAccessible(true);
            method.invoke(manager, task, "Test error");

            Long retryTime = (Long) task.getMetadata("retryTime");
            assertNotNull(retryTime);
            long delay = retryTime - beforeRetry;
            assertTrue("Delay should be >= 60000, got: " + delay, delay >= 60000);
            assertTrue("Delay should be <= 125000, got: " + delay, delay <= 125000);
        } catch (Exception e) {
            fail("Failed to test retry delay: " + e.getMessage());
        }
    }
}
