package com.minisql.master.balance;

import org.junit.Test;
import static org.junit.Assert.*;

public class MigrationTaskTest {

    @Test
    public void testCreateMigrationTask() {
        MigrationTask task = new MigrationTask(
            "mig-001", "region-123", "rs-001", "rs-002");

        assertEquals("mig-001", task.getMigrationId());
        assertEquals("region-123", task.getRegionId());
        assertEquals("rs-001", task.getSourceServerId());
        assertEquals("rs-002", task.getTargetServerId());
        assertEquals(MigrationState.PENDING, task.getState());
        assertEquals(0, task.getRetryCount());
        assertNull(task.getErrorMessage());
        assertTrue(task.getCreateTime() > 0);
    }

    @Test
    public void testStateTransition() {
        MigrationTask task = new MigrationTask(
            "mig-001", "region-123", "rs-001", "rs-002");

        task.setState(MigrationState.MIGRATING_PREPARE);
        assertEquals(MigrationState.MIGRATING_PREPARE, task.getState());

        task.setState(MigrationState.MIGRATING_SYNC);
        assertEquals(MigrationState.MIGRATING_SYNC, task.getState());
    }

    @Test
    public void testRetryCount() {
        MigrationTask task = new MigrationTask(
            "mig-001", "region-123", "rs-001", "rs-002");

        assertEquals(0, task.getRetryCount());

        task.incrementRetry();
        assertEquals(1, task.getRetryCount());

        task.incrementRetry();
        assertEquals(2, task.getRetryCount());
    }

    @Test
    public void testMetadata() {
        MigrationTask task = new MigrationTask(
            "mig-001", "region-123", "rs-001", "rs-002");

        task.setMetadata("snapshotSeq", 12345L);
        assertEquals(12345L, task.getMetadata("snapshotSeq"));

        task.setMetadata("progress", 0.5);
        assertEquals(0.5, (Double) task.getMetadata("progress"), 0.001);
    }

    @Test
    public void testDuration() {
        MigrationTask task = new MigrationTask(
            "mig-001", "region-123", "rs-001", "rs-002");

        task.setStartTime(1000L);
        task.setEndTime(5000L);

        assertEquals(4000L, task.getDuration());
    }

    @Test
    public void testDurationNotStarted() {
        MigrationTask task = new MigrationTask(
            "mig-001", "region-123", "rs-001", "rs-002");

        assertEquals(0L, task.getDuration());
    }
}
