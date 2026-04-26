package com.minisql.master.balance;

import org.junit.Test;
import static org.junit.Assert.*;

public class MigrationConfigTest {

    @Test
    public void testDefaultConfiguration() {
        MigrationConfig config = MigrationConfig.getDefault();

        assertEquals(5000L, config.getCheckPeriodMs());
        assertEquals(3, config.getMaxRetries());
        assertEquals(30000L, config.getPrepareTimeoutMs());
        assertEquals(300000L, config.getSyncTimeoutMs());
        assertEquals(30000L, config.getSwitchTimeoutMs());
        assertEquals(60000L, config.getRollbackTimeoutMs());
    }

    @Test
    public void testBuilderWithAllCustomValues() {
        MigrationConfig config = MigrationConfig.builder()
            .checkPeriodMs(10000L)
            .maxRetries(5)
            .prepareTimeoutMs(60000L)
            .syncTimeoutMs(600000L)
            .switchTimeoutMs(45000L)
            .rollbackTimeoutMs(90000L)
            .build();

        assertEquals(10000L, config.getCheckPeriodMs());
        assertEquals(5, config.getMaxRetries());
        assertEquals(60000L, config.getPrepareTimeoutMs());
        assertEquals(600000L, config.getSyncTimeoutMs());
        assertEquals(45000L, config.getSwitchTimeoutMs());
        assertEquals(90000L, config.getRollbackTimeoutMs());
    }

    @Test
    public void testBuilderWithPartialCustomValues() {
        MigrationConfig config = MigrationConfig.builder()
            .maxRetries(10)
            .syncTimeoutMs(900000L)
            .build();

        assertEquals(5000L, config.getCheckPeriodMs());  // default
        assertEquals(10, config.getMaxRetries());         // custom
        assertEquals(30000L, config.getPrepareTimeoutMs()); // default
        assertEquals(900000L, config.getSyncTimeoutMs());   // custom
        assertEquals(30000L, config.getSwitchTimeoutMs());  // default
        assertEquals(60000L, config.getRollbackTimeoutMs()); // default
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderRejectsNegativeCheckPeriod() {
        MigrationConfig.builder()
            .checkPeriodMs(-1000L)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderRejectsZeroCheckPeriod() {
        MigrationConfig.builder()
            .checkPeriodMs(0L)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderRejectsNegativeMaxRetries() {
        MigrationConfig.builder()
            .maxRetries(-1)
            .build();
    }

    @Test
    public void testBuilderAcceptsZeroMaxRetries() {
        MigrationConfig config = MigrationConfig.builder()
            .maxRetries(0)
            .build();

        assertEquals(0, config.getMaxRetries());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderRejectsNegativePrepareTimeout() {
        MigrationConfig.builder()
            .prepareTimeoutMs(-5000L)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderRejectsZeroPrepareTimeout() {
        MigrationConfig.builder()
            .prepareTimeoutMs(0L)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderRejectsNegativeSyncTimeout() {
        MigrationConfig.builder()
            .syncTimeoutMs(-10000L)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderRejectsZeroSyncTimeout() {
        MigrationConfig.builder()
            .syncTimeoutMs(0L)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderRejectsNegativeSwitchTimeout() {
        MigrationConfig.builder()
            .switchTimeoutMs(-3000L)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderRejectsZeroSwitchTimeout() {
        MigrationConfig.builder()
            .switchTimeoutMs(0L)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderRejectsNegativeRollbackTimeout() {
        MigrationConfig.builder()
            .rollbackTimeoutMs(-8000L)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderRejectsZeroRollbackTimeout() {
        MigrationConfig.builder()
            .rollbackTimeoutMs(0L)
            .build();
    }
}
