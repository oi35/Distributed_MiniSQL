package com.minisql.master.balance;

import org.junit.Test;
import static org.junit.Assert.*;

public class MigrationExceptionTest {

    @Test
    public void testConstructorWithMessage() {
        MigrationException ex = new MigrationException("test error");
        assertEquals("test error", ex.getMessage());
        assertNull(ex.getCause());
        assertFalse(ex.isRecoverable());
    }

    @Test
    public void testConstructorWithMessageAndCause() {
        Exception cause = new RuntimeException("root cause");
        MigrationException ex = new MigrationException("test error", cause);
        assertEquals("test error", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    public void testRecoverableException() {
        MigrationException ex = new MigrationException("network timeout", true);
        assertTrue(ex.isRecoverable());
    }

    @Test
    public void testFatalException() {
        MigrationException ex = new MigrationException("disk full", false);
        assertFalse(ex.isRecoverable());
    }
}
