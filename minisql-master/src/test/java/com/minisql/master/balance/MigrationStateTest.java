package com.minisql.master.balance;

import org.junit.Test;
import static org.junit.Assert.*;

public class MigrationStateTest {

    @Test
    public void testAllStatesExist() {
        MigrationState[] states = MigrationState.values();
        assertEquals(8, states.length);

        assertNotNull(MigrationState.valueOf("PENDING"));
        assertNotNull(MigrationState.valueOf("MIGRATING_PREPARE"));
        assertNotNull(MigrationState.valueOf("MIGRATING_SYNC"));
        assertNotNull(MigrationState.valueOf("MIGRATING_SWITCH"));
        assertNotNull(MigrationState.valueOf("COMPLETED"));
        assertNotNull(MigrationState.valueOf("FAILED"));
        assertNotNull(MigrationState.valueOf("CANCELLED"));
        assertNotNull(MigrationState.valueOf("ROLLING_BACK"));
    }

    @Test
    public void testIsTerminalState() {
        assertTrue(MigrationState.COMPLETED.isTerminal());
        assertTrue(MigrationState.FAILED.isTerminal());
        assertTrue(MigrationState.CANCELLED.isTerminal());

        assertFalse(MigrationState.PENDING.isTerminal());
        assertFalse(MigrationState.MIGRATING_PREPARE.isTerminal());
        assertFalse(MigrationState.MIGRATING_SYNC.isTerminal());
        assertFalse(MigrationState.MIGRATING_SWITCH.isTerminal());
        assertFalse(MigrationState.ROLLING_BACK.isTerminal());
    }

    @Test
    public void testCanTransitionTo() {
        // PENDING can transition to MIGRATING_PREPARE or CANCELLED
        assertTrue(MigrationState.PENDING.canTransitionTo(MigrationState.MIGRATING_PREPARE));
        assertTrue(MigrationState.PENDING.canTransitionTo(MigrationState.CANCELLED));
        assertFalse(MigrationState.PENDING.canTransitionTo(MigrationState.COMPLETED));

        // MIGRATING_PREPARE can transition to MIGRATING_SYNC or ROLLING_BACK
        assertTrue(MigrationState.MIGRATING_PREPARE.canTransitionTo(MigrationState.MIGRATING_SYNC));
        assertTrue(MigrationState.MIGRATING_PREPARE.canTransitionTo(MigrationState.ROLLING_BACK));

        // Terminal states cannot transition
        assertFalse(MigrationState.COMPLETED.canTransitionTo(MigrationState.PENDING));
        assertFalse(MigrationState.FAILED.canTransitionTo(MigrationState.PENDING));
    }

    @Test
    public void testAllValidTransitions() {
        // MIGRATING_SYNC transitions
        assertTrue(MigrationState.MIGRATING_SYNC.canTransitionTo(MigrationState.MIGRATING_SWITCH));
        assertTrue(MigrationState.MIGRATING_SYNC.canTransitionTo(MigrationState.ROLLING_BACK));
        assertTrue(MigrationState.MIGRATING_SYNC.canTransitionTo(MigrationState.CANCELLED));

        // MIGRATING_SWITCH transitions
        assertTrue(MigrationState.MIGRATING_SWITCH.canTransitionTo(MigrationState.COMPLETED));
        assertTrue(MigrationState.MIGRATING_SWITCH.canTransitionTo(MigrationState.ROLLING_BACK));

        // ROLLING_BACK transitions
        assertTrue(MigrationState.ROLLING_BACK.canTransitionTo(MigrationState.FAILED));

        // MIGRATING_PREPARE to CANCELLED
        assertTrue(MigrationState.MIGRATING_PREPARE.canTransitionTo(MigrationState.CANCELLED));
    }

    @Test
    public void testSelfTransitionNotAllowed() {
        assertFalse(MigrationState.PENDING.canTransitionTo(MigrationState.PENDING));
        assertFalse(MigrationState.MIGRATING_PREPARE.canTransitionTo(MigrationState.MIGRATING_PREPARE));
    }

    @Test
    public void testNullTransitionHandling() {
        assertFalse(MigrationState.PENDING.canTransitionTo(null));
        assertFalse(MigrationState.COMPLETED.canTransitionTo(null));
    }
}
