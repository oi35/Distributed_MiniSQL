package com.minisql.master.balance;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MigrationExecutorTest {

    private MigrationExecutor executor;

    @Before
    public void setUp() {
        executor = new MigrationExecutor();
    }

    @Test
    public void testPrepareSource() throws MigrationException {
        boolean result = executor.prepareSource("region-1", "server-1");
        assertTrue(result);
    }

    @Test
    public void testPrepareTarget() throws MigrationException {
        boolean result = executor.prepareTarget("region-1", "server-2");
        assertTrue(result);
    }

    @Test
    public void testStartSync() throws MigrationException {
        boolean result = executor.startSync("region-1", "server-1", "server-2");
        assertTrue(result);
    }

    @Test
    public void testGetSyncProgress() throws MigrationException {
        executor.startSync("region-1", "server-1", "server-2");
        SyncProgress progress = executor.getSyncProgress("region-1");
        assertNotNull(progress);
        assertTrue(progress.getProgress() >= 0.0 && progress.getProgress() <= 1.0);
    }

    @Test
    public void testActivateRegion() throws MigrationException {
        boolean result = executor.activateRegion("region-1", "server-2");
        assertTrue(result);
    }

    @Test
    public void testDeactivateRegion() throws MigrationException {
        boolean result = executor.deactivateRegion("region-1", "server-1");
        assertTrue(result);
    }

    @Test
    public void testCleanupTarget() throws MigrationException {
        boolean result = executor.cleanupTarget("region-1", "server-2");
        assertTrue(result);
    }

    @Test
    public void testRestoreSource() throws MigrationException {
        boolean result = executor.restoreSource("region-1", "server-1");
        assertTrue(result);
    }

    @Test
    public void testSyncCompletion() throws Exception {
        executor.startSync("region-1", "server-1", "server-2");
        Thread.sleep(600);
        SyncProgress progress = executor.getSyncProgress("region-1");
        assertTrue(progress.isCompleted());
    }

    @Test(expected = MigrationException.class)
    public void testFailureInjection() throws MigrationException {
        MigrationExecutor failingExecutor = new MigrationExecutor(1.0);
        failingExecutor.prepareSource("region-1", "server-1");
    }
}
