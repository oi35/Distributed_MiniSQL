package com.minisql.master.balance;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.metadata.MetadataManager;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

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
        task.setStateUnchecked(MigrationState.FAILED);
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

    @Test
    public void testGetAllTasks() {
        String id1 = manager.submitMigration("region-1", "rs-1", "rs-2");
        String id2 = manager.submitMigration("region-2", "rs-2", "rs-3");
        String id3 = manager.submitMigration("region-3", "rs-3", "rs-4");

        List<MigrationTask> tasks = manager.getAllTasks();
        assertEquals(3, tasks.size());
    }

    @Test
    public void testGetTasksByState() {
        String id1 = manager.submitMigration("region-1", "rs-1", "rs-2");
        String id2 = manager.submitMigration("region-2", "rs-2", "rs-3");
        String id3 = manager.submitMigration("region-3", "rs-3", "rs-4");

        manager.getTask(id1).setStateUnchecked(MigrationState.COMPLETED);
        manager.getTask(id2).setStateUnchecked(MigrationState.FAILED);

        List<MigrationTask> pending = manager.getTasksByState(MigrationState.PENDING);
        assertEquals(1, pending.size());
        assertEquals(id3, pending.get(0).getMigrationId());

        List<MigrationTask> completed = manager.getTasksByState(MigrationState.COMPLETED);
        assertEquals(1, completed.size());
        assertEquals(id1, completed.get(0).getMigrationId());

        List<MigrationTask> failed = manager.getTasksByState(MigrationState.FAILED);
        assertEquals(1, failed.size());
        assertEquals(id2, failed.get(0).getMigrationId());
    }

    @Test
    public void testGetTasksByServer() {
        String id1 = manager.submitMigration("region-1", "rs-1", "rs-2");
        String id2 = manager.submitMigration("region-2", "rs-2", "rs-3");
        String id3 = manager.submitMigration("region-3", "rs-3", "rs-4");

        List<MigrationTask> rs2Tasks = manager.getTasksByServer("rs-2");
        assertEquals(2, rs2Tasks.size());

        List<MigrationTask> rs1Tasks = manager.getTasksByServer("rs-1");
        assertEquals(1, rs1Tasks.size());
        assertEquals(id1, rs1Tasks.get(0).getMigrationId());

        List<MigrationTask> rs4Tasks = manager.getTasksByServer("rs-4");
        assertEquals(1, rs4Tasks.size());
        assertEquals(id3, rs4Tasks.get(0).getMigrationId());
    }

    @Test
    public void testCancelMigration() {
        String id = manager.submitMigration("region-1", "rs-1", "rs-2");
        MigrationTask task = manager.getTask(id);

        assertTrue(manager.cancelMigration(id));
        assertEquals(MigrationState.CANCELLED, task.getState());
        assertTrue(task.getEndTime() > 0);
    }

    @Test
    public void testCancelMigrationAlreadyCompleted() {
        String id = manager.submitMigration("region-1", "rs-1", "rs-2");
        MigrationTask task = manager.getTask(id);
        task.setStateUnchecked(MigrationState.COMPLETED);

        assertFalse(manager.cancelMigration(id));
        assertEquals(MigrationState.COMPLETED, task.getState());
    }

    @Test
    public void testCancelMigrationNotFound() {
        assertFalse(manager.cancelMigration("non-existent"));
    }

    @Test
    public void testRetryMigration() {
        String id = manager.submitMigration("region-1", "rs-1", "rs-2");
        MigrationTask task = manager.getTask(id);
        task.setStateUnchecked(MigrationState.FAILED);
        task.setErrorMessage("Test error");
        task.setMetadata("retryTime", 12345L);

        assertTrue(manager.retryMigration(id));
        assertEquals(MigrationState.PENDING, task.getState());
        assertNull(task.getErrorMessage());
        assertNull(task.getMetadata("retryTime"));
    }

    @Test
    public void testRetryMigrationNotFailed() {
        String id = manager.submitMigration("region-1", "rs-1", "rs-2");
        assertFalse(manager.retryMigration(id));
    }

    @Test
    public void testRetryMigrationNotFound() {
        assertFalse(manager.retryMigration("non-existent"));
    }

    @Test
    public void testGetStatistics() {
        String id1 = manager.submitMigration("region-1", "rs-1", "rs-2");
        String id2 = manager.submitMigration("region-2", "rs-2", "rs-3");
        String id3 = manager.submitMigration("region-3", "rs-3", "rs-4");
        String id4 = manager.submitMigration("region-4", "rs-4", "rs-5");
        String id5 = manager.submitMigration("region-5", "rs-5", "rs-6");

        MigrationTask task1 = manager.getTask(id1);
        task1.setStateUnchecked(MigrationState.COMPLETED);
        task1.setStartTime(1000);
        task1.setEndTime(3000);

        MigrationTask task2 = manager.getTask(id2);
        task2.setStateUnchecked(MigrationState.COMPLETED);
        task2.setStartTime(2000);
        task2.setEndTime(6000);

        MigrationTask task3 = manager.getTask(id3);
        task3.setStateUnchecked(MigrationState.FAILED);

        MigrationTask task4 = manager.getTask(id4);
        task4.setStateUnchecked(MigrationState.CANCELLED);

        MigrationStatistics stats = manager.getStatistics();
        assertEquals(5, stats.getTotalSubmitted());
        assertEquals(2, stats.getCompleted());
        assertEquals(1, stats.getFailed());
        assertEquals(1, stats.getCancelled());
        assertEquals(1, stats.getActive());
        assertEquals(3000, stats.getAvgDurationMs());
    }
}
