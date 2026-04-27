package com.minisql.master.balance;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PrepareHandlerTest {

    private MigrationConfig config;
    private PrepareHandler handler;
    private MigrationExecutor executor;

    @Before
    public void setUp() {
        config = MigrationConfig.getDefault();
        handler = new PrepareHandler(config);
        executor = mock(MigrationExecutor.class);
    }

    @Test
    public void testSupportedState() {
        assertEquals(MigrationState.MIGRATING_PREPARE, handler.supportedState());
    }

    @Test
    public void testSuccessfulPrepare() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-001", "region-001", "rs-001", "rs-002");
        task.setState(MigrationState.MIGRATING_PREPARE);
        task.setStartTime(System.currentTimeMillis());

        when(executor.prepareSource("region-001", "rs-001")).thenReturn(true);
        when(executor.prepareTarget("region-001", "rs-002")).thenReturn(true);

        MigrationState nextState = handler.handle(task, executor);

        assertEquals(MigrationState.MIGRATING_SYNC, nextState);
        verify(executor).prepareSource("region-001", "rs-001");
        verify(executor).prepareTarget("region-001", "rs-002");
    }

    @Test
    public void testPrepareTimeout() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-001", "region-001", "rs-001", "rs-002");
        task.setState(MigrationState.MIGRATING_PREPARE);
        task.setStartTime(System.currentTimeMillis() - 31000);

        try {
            handler.handle(task, executor);
            fail("Expected MigrationException");
        } catch (MigrationException e) {
            assertTrue(e.getMessage().contains("timeout"));
            assertNotNull(task.getErrorMessage());
        }
    }

    @Test
    public void testPrepareSourceFailure() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-001", "region-001", "rs-001", "rs-002");
        task.setState(MigrationState.MIGRATING_PREPARE);
        task.setStartTime(System.currentTimeMillis());

        when(executor.prepareSource("region-001", "rs-001"))
            .thenThrow(new MigrationException("Source prepare failed"));

        try {
            handler.handle(task, executor);
            fail("Expected MigrationException");
        } catch (MigrationException e) {
            assertTrue(e.getMessage().contains("Source prepare failed"));
            assertNotNull(task.getErrorMessage());
        }
    }

    @Test
    public void testPrepareTargetFailure() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-001", "region-001", "rs-001", "rs-002");
        task.setState(MigrationState.MIGRATING_PREPARE);
        task.setStartTime(System.currentTimeMillis());

        when(executor.prepareSource("region-001", "rs-001")).thenReturn(true);
        when(executor.prepareTarget("region-001", "rs-002"))
            .thenThrow(new MigrationException("Target prepare failed"));

        try {
            handler.handle(task, executor);
            fail("Expected MigrationException");
        } catch (MigrationException e) {
            assertTrue(e.getMessage().contains("Target prepare failed"));
            assertNotNull(task.getErrorMessage());
        }
    }
}
