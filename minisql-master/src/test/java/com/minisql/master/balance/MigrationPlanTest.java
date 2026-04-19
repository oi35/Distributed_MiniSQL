package com.minisql.master.balance;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for MigrationPlan
 */
public class MigrationPlanTest {

    @Test
    public void testConstructorAndGetters() {
        MigrationPlan plan = new MigrationPlan(
            "region-001",
            "rs-001",
            "rs-002",
            15.5,
            "Load balancing"
        );

        assertEquals("region-001", plan.getRegionId());
        assertEquals("rs-001", plan.getSourceServerId());
        assertEquals("rs-002", plan.getTargetServerId());
        assertEquals(15.5, plan.getBenefit(), 0.001);
        assertEquals("Load balancing", plan.getReason());
    }

    @Test
    public void testToStringFormat() {
        MigrationPlan plan = new MigrationPlan(
            "region-001",
            "rs-001",
            "rs-002",
            15.5,
            "Load balancing"
        );

        String result = plan.toString();
        assertTrue(result.contains("region-001"));
        assertTrue(result.contains("rs-001"));
        assertTrue(result.contains("rs-002"));
        assertTrue(result.contains("15.50"));
        assertTrue(result.contains("Load balancing"));
    }

    @Test
    public void testPositiveBenefit() {
        MigrationPlan plan = new MigrationPlan(
            "region-001",
            "rs-001",
            "rs-002",
            100.0,
            "High load on source"
        );

        assertEquals(100.0, plan.getBenefit(), 0.001);
    }

    @Test
    public void testNegativeBenefit() {
        MigrationPlan plan = new MigrationPlan(
            "region-001",
            "rs-001",
            "rs-002",
            -5.0,
            "Test negative benefit"
        );

        assertEquals(-5.0, plan.getBenefit(), 0.001);
    }

    @Test
    public void testZeroBenefit() {
        MigrationPlan plan = new MigrationPlan(
            "region-001",
            "rs-001",
            "rs-002",
            0.0,
            "No benefit"
        );

        assertEquals(0.0, plan.getBenefit(), 0.001);
    }
}
