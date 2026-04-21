package com.minisql.master.balance;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.cluster.ServerInfo;
import com.minisql.master.metadata.MetadataManager;
import com.minisql.master.zk.MasterElection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 负载均衡器
 */
public class LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancer.class);

    private final ClusterManager clusterManager;
    private final MetadataManager metadataManager;
    private final RegionMigrationManager migrationManager;
    private final MasterElection masterElection;
    private final LoadBalancerConfig config;

    private volatile boolean running;
    private volatile long lastBalanceTime;
    private ScheduledExecutorService scheduler;

    public LoadBalancer(ClusterManager clusterManager,
                       MetadataManager metadataManager,
                       RegionMigrationManager migrationManager,
                       MasterElection masterElection,
                       LoadBalancerConfig config) {
        this.clusterManager = clusterManager;
        this.metadataManager = metadataManager;
        this.migrationManager = migrationManager;
        this.masterElection = masterElection;
        this.config = config;
        this.running = false;
        this.lastBalanceTime = 0;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * 启动LoadBalancer
     *
     * <p>只有当前节点是Leader时才会启动。启动后会定期执行负载检查。
     * 该方法是幂等的，多次调用不会产生副作用。
     *
     * @see #stop()
     */
    public synchronized void start() {
        if (running) {
            logger.warn("LoadBalancer already running");
            return;
        }

        if (!masterElection.isLeader()) {
            logger.warn("Not the leader, LoadBalancer will not start");
            return;
        }

        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "load-balancer");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
            this::checkAndBalance,
            config.getCheckPeriodMs(),
            config.getCheckPeriodMs(),
            TimeUnit.MILLISECONDS
        );

        logger.info("LoadBalancer started, check period: {}ms", config.getCheckPeriodMs());
    }

    /**
     * 停止LoadBalancer
     *
     * <p>优雅地关闭调度器，等待最多10秒。如果超时则强制关闭。
     *
     * @see #start()
     */
    public void stop() {
        if (!running) {
            logger.warn("LoadBalancer not running");
            return;
        }

        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("LoadBalancer stopped");
    }

    private void checkAndBalance() {
        // Placeholder - will implement in next task
    }

    /**
     * 检查是否需要进行负载均衡
     *
     * <p>检查以下条件：
     * <ul>
     *   <li>至少有2个在线服务器</li>
     *   <li>存在过载服务器（负载 > 平均负载 * 1.5）</li>
     *   <li>过载服务器至少有2个Region</li>
     *   <li>最大负载与最小负载的差值 > 平均负载 * 0.3</li>
     *   <li>存在最轻负载的服务器</li>
     * </ul>
     *
     * @return 如果需要负载均衡返回true，否则返回false
     */
    public boolean needsBalance() {
        List<ServerInfo> onlineServers = clusterManager.getOnlineServers();
        if (onlineServers.size() < 2) {
            return false;
        }

        double totalLoad = onlineServers.stream()
            .mapToDouble(ServerInfo::getLoadScore)
            .sum();
        double avgLoad = totalLoad / onlineServers.size();

        List<ServerInfo> overloaded = onlineServers.stream()
            .filter(s -> s.getLoadScore() > avgLoad * config.getLoadThreshold())
            .filter(s -> s.getRegionCount() >= config.getMinRegionCount())
            .collect(Collectors.toList());

        if (overloaded.isEmpty()) {
            return false;
        }

        ServerInfo lightest = onlineServers.stream()
            .min(Comparator.comparingDouble(ServerInfo::getLoadScore))
            .orElse(null);

        if (lightest == null) {
            return false;
        }

        double maxLoad = overloaded.stream()
            .mapToDouble(ServerInfo::getLoadScore)
            .max()
            .orElse(0);

        double loadDiff = maxLoad - lightest.getLoadScore();
        return loadDiff > avgLoad * config.getMinLoadDiff();
    }

    /**
     * 检查是否可以启动新的迁移任务
     *
     * <p>检查以下条件：
     * <ul>
     *   <li>全局并发限制：活跃迁移数 < maxConcurrentMigrations</li>
     *   <li>服务器级限制：目标服务器未参与任何活跃迁移（作为源或目标）</li>
     * </ul>
     *
     * @param serverId 要检查的服务器ID
     * @return 如果可以启动新迁移返回true，否则返回false
     */
    boolean canStartNewMigration(String serverId) {
        List<MigrationTask> activeMigrations = migrationManager.getActiveMigrations();

        if (activeMigrations.size() >= config.getMaxConcurrentMigrations()) {
            return false;
        }

        for (MigrationTask task : activeMigrations) {
            if (task.getSourceServerId().equals(serverId) ||
                task.getTargetServerId().equals(serverId)) {
                return false;
            }
        }

        return true;
    }
}
