package com.minisql.master.balance;

/**
 * LoadBalancer配置
 */
public class LoadBalancerConfig {

    private final double loadThreshold;
    private final long checkPeriodMs;
    private final int minRegionCount;
    private final double minLoadDiff;
    private final long cooldownPeriodMs;
    private final int maxConcurrentMigrations;

    /**
     * 默认配置
     */
    public LoadBalancerConfig() {
        this(1.5, 300000L, 2, 0.3, 600000L, 2);
    }

    /**
     * 自定义配置
     */
    public LoadBalancerConfig(double loadThreshold,
                             long checkPeriodMs,
                             int minRegionCount,
                             double minLoadDiff,
                             long cooldownPeriodMs,
                             int maxConcurrentMigrations) {
        this.loadThreshold = loadThreshold;
        this.checkPeriodMs = checkPeriodMs;
        this.minRegionCount = minRegionCount;
        this.minLoadDiff = minLoadDiff;
        this.cooldownPeriodMs = cooldownPeriodMs;
        this.maxConcurrentMigrations = maxConcurrentMigrations;
    }

    public double getLoadThreshold() {
        return loadThreshold;
    }

    public long getCheckPeriodMs() {
        return checkPeriodMs;
    }

    public int getMinRegionCount() {
        return minRegionCount;
    }

    public double getMinLoadDiff() {
        return minLoadDiff;
    }

    public long getCooldownPeriodMs() {
        return cooldownPeriodMs;
    }

    public int getMaxConcurrentMigrations() {
        return maxConcurrentMigrations;
    }
}
