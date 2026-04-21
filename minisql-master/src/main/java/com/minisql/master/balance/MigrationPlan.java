package com.minisql.master.balance;

/**
 * 迁移计划
 * Immutable data model representing a region migration plan.
 */
public class MigrationPlan {
    private final String regionId;
    private final String sourceServerId;
    private final String targetServerId;
    private final double benefit;
    private final String reason;

    public MigrationPlan(String regionId, String sourceServerId,
                        String targetServerId, double benefit, String reason) {
        this.regionId = regionId;
        this.sourceServerId = sourceServerId;
        this.targetServerId = targetServerId;
        this.benefit = benefit;
        this.reason = reason;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getSourceServerId() {
        return sourceServerId;
    }

    public String getTargetServerId() {
        return targetServerId;
    }

    public double getBenefit() {
        return benefit;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return String.format("MigrationPlan{region=%s, %s->%s, benefit=%.2f, reason=%s}",
                regionId, sourceServerId, targetServerId, benefit, reason);
    }
}
