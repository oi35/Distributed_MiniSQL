package com.minisql.master.balance;

import java.util.EnumSet;
import java.util.Set;

/**
 * Region迁移状态枚举
 */
public enum MigrationState {
    PENDING,              // 等待执行
    MIGRATING_PREPARE,    // 准备阶段
    MIGRATING_SYNC,       // 数据同步阶段
    MIGRATING_SWITCH,     // 切换阶段
    COMPLETED,            // 完成
    FAILED,               // 失败
    CANCELLED,            // 已取消
    ROLLING_BACK;         // 回滚中

    private static final Set<MigrationState> TERMINAL_STATES =
        EnumSet.of(COMPLETED, FAILED, CANCELLED);

    /**
     * 是否为终态
     */
    public boolean isTerminal() {
        return TERMINAL_STATES.contains(this);
    }

    /**
     * 是否可以转换到目标状态
     */
    public boolean canTransitionTo(MigrationState target) {
        if (target == null) {
            return false;
        }
        if (this.isTerminal()) {
            return false;
        }

        switch (this) {
            case PENDING:
                return target == MIGRATING_PREPARE || target == CANCELLED;
            case MIGRATING_PREPARE:
                return target == MIGRATING_SYNC || target == ROLLING_BACK || target == CANCELLED;
            case MIGRATING_SYNC:
                return target == MIGRATING_SWITCH || target == ROLLING_BACK || target == CANCELLED;
            case MIGRATING_SWITCH:
                return target == COMPLETED || target == ROLLING_BACK;
            case ROLLING_BACK:
                return target == FAILED;
            default:
                return false;
        }
    }
}
