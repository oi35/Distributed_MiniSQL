package com.minisql.master.balance;

import com.minisql.master.metadata.MetadataManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RollbackHandlerTest {

    private MigrationConfig config;
    private MetadataManager metadataManager;
    private MigrationExecutor executor;
    private RollbackHandler handler;

    @Before
    public void setUp() {
        config = MigrationConfig.getDefault();
        metadataManager = mock(MetadataManager.class);
        executor = mock(MigrationExecutor.class);
        handler = new RollbackHandler(config, metadataManager);
    }

    @Test
    public void testSupportedState() {
        assertEquals(MigrationState.ROLLING_BACK, handler.supportedState());
    }

    @Test
    public void testRollbackWithoutRouteUpdate() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-1", "region-1", "server-1", "server-2");
        task.setState(MigrationState.ROLLING_BACK);

        MigrationState nextState = handler.handle(task, executor);

        assertEquals(MigrationState.FAILED, nextState);
        verify(metadataManager, never()).updateRegionLocation(anyString(), anyString());
        verify(executor).cleanupTarget("region-1", "server-2");
        verify(executor).restoreSource("region-1", "server-1");
    }

    @Test
    public void testRollbackWithRouteUpdate() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-1", "region-1", "server-1", "server-2");
        task.setState(MigrationState.ROLLING_BACK);
        task.setMetadata("routeUpdated", true);

        MigrationState nextState = handler.handle(task, executor);

        assertEquals(MigrationState.FAILED, nextState);
        InOrder inOrder = inOrder(metadataManager, executor);
        inOrder.verify(metadataManager).updateRegionLocation("region-1", "server-1");
        inOrder.verify(executor).cleanupTarget("region-1", "server-2");
        inOrder.verify(executor).restoreSource("region-1", "server-1");
    }

    @Test
    public void testRollbackWithRouteUpdateFalse() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-1", "region-1", "server-1", "server-2");
        task.setState(MigrationState.ROLLING_BACK);
        task.setMetadata("routeUpdated", false);

        MigrationState nextState = handler.handle(task, executor);

        assertEquals(MigrationState.FAILED, nextState);
        verify(metadataManager, never()).updateRegionLocation(anyString(), anyString());
        verify(executor).cleanupTarget("region-1", "server-2");
        verify(executor).restoreSource("region-1", "server-1");
    }

    @Test
    public void testRollbackWithCleanupFailure() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-1", "region-1", "server-1", "server-2");
        task.setState(MigrationState.ROLLING_BACK);
        task.setMetadata("routeUpdated", true);

        doThrow(new MigrationException("Cleanup failed"))
            .when(executor).cleanupTarget("region-1", "server-2");

        MigrationState nextState = handler.handle(task, executor);

        assertEquals(MigrationState.FAILED, nextState);
        assertNotNull(task.getErrorMessage());
        assertTrue(task.getErrorMessage().contains("Cleanup failed"));
    }

    @Test
    public void testRollbackWithRestoreFailure() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-1", "region-1", "server-1", "server-2");
        task.setState(MigrationState.ROLLING_BACK);

        doThrow(new MigrationException("Restore failed"))
            .when(executor).restoreSource("region-1", "server-1");

        MigrationState nextState = handler.handle(task, executor);

        assertEquals(MigrationState.FAILED, nextState);
        assertNotNull(task.getErrorMessage());
        assertTrue(task.getErrorMessage().contains("Restore failed"));
    }

    @Test
    public void testRollbackAlwaysReturnsFailed() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-1", "region-1", "server-1", "server-2");
        task.setState(MigrationState.ROLLING_BACK);

        MigrationState nextState = handler.handle(task, executor);

        assertEquals(MigrationState.FAILED, nextState);
    }
}
