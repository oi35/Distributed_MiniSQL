package com.minisql.master.balance;

import org.junit.Test;
import static org.junit.Assert.*;

public class MigrationStatisticsTest {

    @Test
    public void testStatisticsCalculation() {
        MigrationStatistics stats = new MigrationStatistics(
            10,  // totalSubmitted
            7,   // completed
            3,   // failed
            0,   // cancelled
            0,   // active
            5000 // avgDurationMs
        );

        assertEquals(10, stats.getTotalSubmitted());
        assertEquals(7, stats.getCompleted());
        assertEquals(3, stats.getFailed());
        assertEquals(0, stats.getCancelled());
        assertEquals(0, stats.getActive());
        assertEquals(5000, stats.getAvgDurationMs());
        assertEquals(0.7, stats.getSuccessRate(), 0.01);
    }

    @Test
    public void testSuccessRateWithNoCompletedTasks() {
        MigrationStatistics stats = new MigrationStatistics(0, 0, 0, 0, 0, 0);
        assertEquals(0.0, stats.getSuccessRate(), 0.01);
    }

    @Test
    public void testSuccessRateWithOnlyCompletedTasks() {
        MigrationStatistics stats = new MigrationStatistics(5, 5, 0, 0, 0, 3000);
        assertEquals(1.0, stats.getSuccessRate(), 0.01);
    }

    @Test
    public void testSuccessRateWithOnlyFailedTasks() {
        MigrationStatistics stats = new MigrationStatistics(5, 0, 5, 0, 0, 0);
        assertEquals(0.0, stats.getSuccessRate(), 0.01);
    }

    @Test
    public void testEqualsAndHashCode() {
        MigrationStatistics stats1 = new MigrationStatistics(10, 7, 2, 1, 0, 5000);
        MigrationStatistics stats2 = new MigrationStatistics(10, 7, 2, 1, 0, 5000);
        MigrationStatistics stats3 = new MigrationStatistics(10, 6, 2, 1, 1, 5000);

        assertEquals(stats1, stats2);
        assertEquals(stats1.hashCode(), stats2.hashCode());
        assertNotEquals(stats1, stats3);
        assertNotEquals(stats1, null);
        assertNotEquals(stats1, new Object());
    }

    @Test
    public void testToString() {
        MigrationStatistics stats = new MigrationStatistics(10, 7, 2, 1, 0, 5000);
        String str = stats.toString();

        assertTrue(str.contains("totalSubmitted=10"));
        assertTrue(str.contains("completed=7"));
        assertTrue(str.contains("failed=2"));
        assertTrue(str.contains("cancelled=1"));
        assertTrue(str.contains("active=0"));
        assertTrue(str.contains("avgDurationMs=5000"));
    }

    @Test
    public void testWithActiveTasks() {
        MigrationStatistics stats = new MigrationStatistics(10, 5, 2, 1, 2, 4500);

        assertEquals(10, stats.getTotalSubmitted());
        assertEquals(2, stats.getActive());
        assertEquals(5.0 / 7.0, stats.getSuccessRate(), 0.01);
    }
}
