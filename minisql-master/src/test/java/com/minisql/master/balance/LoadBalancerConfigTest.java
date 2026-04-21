package com.minisql.master.balance;

import org.junit.Test;
import static org.junit.Assert.*;

public class LoadBalancerConfigTest {

    @Test
    public void testDefaultConfig() {
        LoadBalancerConfig config = new LoadBalancerConfig();

        assertEquals(1.5, config.getLoadThreshold(), 0.001);
        assertEquals(300000L, config.getCheckPeriodMs());
        assertEquals(2, config.getMinRegionCount());
        assertEquals(0.3, config.getMinLoadDiff(), 0.001);
        assertEquals(600000L, config.getCooldownPeriodMs());
        assertEquals(2, config.getMaxConcurrentMigrations());
    }

    @Test
    public void testCustomConfig() {
        LoadBalancerConfig config = new LoadBalancerConfig(
            2.0, 60000L, 3, 0.5, 120000L, 5
        );

        assertEquals(2.0, config.getLoadThreshold(), 0.001);
        assertEquals(60000L, config.getCheckPeriodMs());
        assertEquals(3, config.getMinRegionCount());
        assertEquals(0.5, config.getMinLoadDiff(), 0.001);
        assertEquals(120000L, config.getCooldownPeriodMs());
        assertEquals(5, config.getMaxConcurrentMigrations());
    }
}
