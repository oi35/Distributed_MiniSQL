package com.minisql.master.balance;

public interface MigrationStateHandler {

    /**
     * 处理当前状态的任务
     *
     * @param task 迁移任务
     * @param executor 执行器
     * @return 下一个状态，如果保持当前状态返回 null
     * @throws MigrationException 处理失败
     */
    MigrationState handle(MigrationTask task, MigrationExecutor executor)
        throws MigrationException;

    /**
     * 该处理器支持的状态
     *
     * @return 支持的状态
     */
    MigrationState supportedState();
}
