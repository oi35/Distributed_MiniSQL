package com.minisql.master.balance;

import org.junit.Test;
import static org.junit.Assert.*;

public class SyncProgressTest {

    @Test
    public void testProgressCalculation() {
        SyncProgress progress = new SyncProgress(1000, 500, false);
        assertEquals(0.5, progress.getProgress(), 0.001);
        assertEquals(1000, progress.getTotalBytes());
        assertEquals(500, progress.getSyncedBytes());
        assertFalse(progress.isCompleted());
    }

    @Test
    public void testCompletedProgress() {
        SyncProgress progress = new SyncProgress(1000, 1000, true);
        assertEquals(1.0, progress.getProgress(), 0.001);
        assertTrue(progress.isCompleted());
    }

    @Test
    public void testZeroTotalBytes() {
        SyncProgress progress = new SyncProgress(0, 0, false);
        assertEquals(0.0, progress.getProgress(), 0.001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeTotalBytes() {
        new SyncProgress(-1, 0, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeSyncedBytes() {
        new SyncProgress(1000, -1, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSyncedBytesExceedsTotalBytes() {
        new SyncProgress(1000, 1001, false);
    }

    @Test
    public void testEqualsAndHashCode() {
        SyncProgress p1 = new SyncProgress(1000, 500, false);
        SyncProgress p2 = new SyncProgress(1000, 500, false);
        SyncProgress p3 = new SyncProgress(1000, 600, false);

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
        assertNotEquals(p1, p3);
    }

    @Test
    public void testToString() {
        SyncProgress progress = new SyncProgress(1000, 500, false);
        String str = progress.toString();
        assertTrue(str.contains("1000"));
        assertTrue(str.contains("500"));
        assertTrue(str.contains("false"));
    }
}
