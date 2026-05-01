package com.minisql.master.balance;

import com.minisql.master.metadata.MetadataManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SwitchHandlerTest {

    private MigrationConfig config;
    private MetadataManager metadataManager;
    private SwitchHandler handler;
    private MigrationExecutor executor;

    @Before
    public void setUp() {
        config = MigrationConfig.getDefault();
        metadataManager = mock(MetadataManager.class);
        handler = new SwitchHandler(config, metadataManager);
        executor = mock(MigrationExecutor.class);
    }

    @Test
    public void testSupportedState() {
        assertEquals(MigrationState.MIGRATING_SWITCH, handler.supportedState());
    }

    @Test
    public void testSuccessfulSwitch() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-001", "region-001", "rs-001", "rs-002");
        task.setStateUnchecked(MigrationState.MIGRATING_SWITCH);

        when(metadataManager.updateRegionLocation("region-001", "rs-002")).thenReturn(true);
        when(executor.activateRegion("region-001", "rs-002")).thenReturn(true);
        when(executor.deactivateRegion("region-001", "rs-001")).thenReturn(true);

        MigrationState nextState = handler.handle(task, executor);

        assertEquals(MigrationState.COMPLETED, nextState);
        verify(metadataManager).updateRegionLocation("region-001", "rs-002");
        verify(executor).activateRegion("region-001", "rs-002");
        verify(executor).deactivateRegion("region-001", "rs-001");
        assertEquals(true, task.getMetadata("routeUpdated"));
    }

    @Test
    public void testUpdateRouteFailure() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-001", "region-001", "rs-001", "rs-002");
        task.setStateUnchecked(MigrationState.MIGRATING_SWITCH);

        when(metadataManager.updateRegionLocation("region-001", "rs-002")).thenReturn(false);

        MigrationState nextState = handler.handle(task, executor);

        assertEquals(MigrationState.COMPLETED, nextState);
        verify(metadataManager).updateRegionLocation("region-001", "rs-002");
    }

    @Test
    public void testActivateRegionFailure() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-001", "region-001", "rs-001", "rs-002");
        task.setStateUnchecked(MigrationState.MIGRATING_SWITCH);

        when(metadataManager.updateRegionLocation("region-001", "rs-002")).thenReturn(true);
        when(executor.activateRegion("region-001", "rs-002"))
            .thenThrow(new MigrationException("Activate failed"));

        try {
            handler.handle(task, executor);
            fail("Expected MigrationException");
        } catch (MigrationException e) {
            assertTrue(e.getMessage().contains("Activate failed"));
            assertNotNull(task.getErrorMessage());
        }
    }

    @Test
    public void testDeactivateRegionFailure() throws MigrationException {
        MigrationTask task = new MigrationTask("mig-001", "region-001", "rs-001", "rs-002");
        task.setStateUnchecked(MigrationState.MIGRATING_SWITCH);

        when(metadataManager.updateRegionLocation("region-001", "rs-002")).thenReturn(true);
        when(executor.activateRegion("region-001", "rs-002")).thenReturn(true);
        when(executor.deactivateRegion("region-001", "rs-001"))
            .thenThrow(new MigrationException("Deactivate failed"));

        try {
            handler.handle(task, executor);
            fail("Expected MigrationException");
        } catch (MigrationException e) {
            assertTrue(e.getMessage().contains("Deactivate failed"));
            assertNotNull(task.getErrorMessage());
        }
    }
}
