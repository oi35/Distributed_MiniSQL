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
        assertFalse(ex.isRecoverable());
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

    @Test
    public void testConstructorWithMessageCauseAndRecoverable() {
        Exception cause = new RuntimeException("root cause");

        // Test with recoverable=true
        MigrationException recoverableEx = new MigrationException("network error", cause, true);
        assertEquals("network error", recoverableEx.getMessage());
        assertEquals(cause, recoverableEx.getCause());
        assertTrue(recoverableEx.isRecoverable());

        // Test with recoverable=false
        MigrationException fatalEx = new MigrationException("disk error", cause, false);
        assertEquals("disk error", fatalEx.getMessage());
        assertEquals(cause, fatalEx.getCause());
        assertFalse(fatalEx.isRecoverable());
    }
}
