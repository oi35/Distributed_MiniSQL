package com.minisql.master.balance;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SyncHandlerTest {

    private MigrationConfig config;
    private SyncHandler handler;
    private MigrationExecutor executor;

    @Before
    public void setUp() {
        config = MigrationConfig.getDefault();
        handler = new SyncHandler(config);
        executor = mock(MigrationExecutor.class);
    }

    @Test
    public void testSupportedState() {
        assertEquals(MigrationState.MIGRATING_SYNC, handler.supportedState());
    }

    @Test
    public void testStartSync() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-001", "region-001", "rs-001", "rs-002");
        task.setStateUnchecked(MigrationState.MIGRATING_SYNC);
        task.setStartTime(System.currentTimeMillis());

        when(executor.startSync("region-001", "rs-001", "rs-002")).thenReturn(true);

        MigrationState nextState = handler.handle(task, executor);

        assertNull(nextState);
        verify(executor).startSync("region-001", "rs-001", "rs-002");
        assertEquals(true, task.getMetadata("syncStarted"));
    }

    @Test
    public void testSyncInProgress() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-001", "region-001", "rs-001", "rs-002");
        task.setStateUnchecked(MigrationState.MIGRATING_SYNC);
        task.setStartTime(System.currentTimeMillis());
        task.setMetadata("syncStarted", true);

        SyncProgress progress = new SyncProgress(1000, 500, false);
        when(executor.getSyncProgress("region-001")).thenReturn(progress);

        MigrationState nextState = handler.handle(task, executor);

        assertNull(nextState);
        verify(executor).getSyncProgress("region-001");
        verify(executor, never()).startSync(anyString(), anyString(), anyString());
    }

    @Test
    public void testSyncCompleted() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-001", "region-001", "rs-001", "rs-002");
        task.setStateUnchecked(MigrationState.MIGRATING_SYNC);
        task.setStartTime(System.currentTimeMillis());
        task.setMetadata("syncStarted", true);

        SyncProgress progress = new SyncProgress(1000, 1000, true);
        when(executor.getSyncProgress("region-001")).thenReturn(progress);

        MigrationState nextState = handler.handle(task, executor);

        assertEquals(MigrationState.MIGRATING_SWITCH, nextState);
        verify(executor).getSyncProgress("region-001");
    }

    @Test
    public void testSyncTimeout() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-001", "region-001", "rs-001", "rs-002");
        task.setStateUnchecked(MigrationState.MIGRATING_SYNC);
        task.setStartTime(System.currentTimeMillis() - 301000);

        try {
            handler.handle(task, executor);
            fail("Expected MigrationException");
        } catch (MigrationException e) {
            assertTrue(e.getMessage().contains("timeout"));
            assertNotNull(task.getErrorMessage());
        }
    }

    @Test
    public void testStartSyncFailure() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-001", "region-001", "rs-001", "rs-002");
        task.setStateUnchecked(MigrationState.MIGRATING_SYNC);
        task.setStartTime(System.currentTimeMillis());

        when(executor.startSync("region-001", "rs-001", "rs-002"))
            .thenThrow(new MigrationException("Sync start failed"));

        try {
            handler.handle(task, executor);
            fail("Expected MigrationException");
        } catch (MigrationException e) {
            assertTrue(e.getMessage().contains("Sync start failed"));
        }
    }

    @Test
    public void testGetProgressFailure() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-001", "region-001", "rs-001", "rs-002");
        task.setStateUnchecked(MigrationState.MIGRATING_SYNC);
        task.setStartTime(System.currentTimeMillis());
        task.setMetadata("syncStarted", true);

        when(executor.getSyncProgress("region-001"))
            .thenThrow(new MigrationException("Progress check failed"));

        try {
            handler.handle(task, executor);
            fail("Expected MigrationException");
        } catch (MigrationException e) {
            assertTrue(e.getMessage().contains("Progress check failed"));
        }
    }
}
