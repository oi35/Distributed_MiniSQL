package com.minisql.master.balance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Region迁移管理器（占位符，后续实现）
 */
public class RegionMigrationManager {

    private final Map<String, MigrationTask> migrations = new ConcurrentHashMap<>();

    /**
     * 获取所有活跃的迁移任务（非终态）
     *
     * @return 活跃的迁移任务列表
     */
    public List<MigrationTask> getActiveMigrations() {
        return migrations.values().stream()
            .filter(task -> !task.getState().isTerminal())
            .collect(Collectors.toList());
    }
}
