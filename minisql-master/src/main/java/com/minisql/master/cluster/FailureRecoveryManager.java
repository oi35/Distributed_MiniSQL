package com.minisql.master.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 故障恢复管理器
 *
 * 处理RegionServer故障，触发Region重新分配
 */
public class FailureRecoveryManager implements HeartbeatMonitor.FailureHandler {

    private static final Logger logger = LoggerFactory.getLogger(FailureRecoveryManager.class);

    private final ClusterManager clusterManager;

    /**
     * Region重新分配回调接口
     */
    public interface RegionReassignmentCallback {
        /**
         * 重新分配Region
         *
         * @param regionId 需要重新分配的Region ID
         * @param failedServerId 故障的服务器ID
         * @param targetServerId 目标服务器ID
         */
        void reassignRegion(String regionId, String failedServerId, String targetServerId);
    }

    private RegionReassignmentCallback reassignmentCallback;

    public FailureRecoveryManager(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }

    /**
     * 设置Region重新分配回调
     */
    public void setReassignmentCallback(RegionReassignmentCallback callback) {
        this.reassignmentCallback = callback;
    }

    @Override
    public void handleServerFailure(String serverId) {
        logger.error("Handling server failure: {}", serverId);

        ServerInfo failedServer = clusterManager.getServerInfo(serverId);
        if (failedServer == null) {
            logger.warn("Failed server not found: {}", serverId);
            return;
        }

        // 获取故障服务器上的所有Region
        Map<String, Long> regions = failedServer.getRegions();
        if (regions.isEmpty()) {
            logger.info("No regions on failed server: {}", serverId);
            return;
        }

        logger.warn("Server {} has {} regions to reassign", serverId, regions.size());

        // 选择目标服务器并重新分配Region
        List<String> failedReassignments = new ArrayList<>();

        for (String regionId : regions.keySet()) {
            try {
                ServerInfo targetServer = clusterManager.selectLeastLoadedServer();

                if (targetServer == null) {
                    logger.error("No available server for region reassignment: {}", regionId);
                    failedReassignments.add(regionId);
                    continue;
                }

                logger.info("Reassigning region {} from {} to {}",
                           regionId, serverId, targetServer.getServerId());

                // 触发重新分配回调
                if (reassignmentCallback != null) {
                    reassignmentCallback.reassignRegion(
                            regionId,
                            serverId,
                            targetServer.getServerId()
                    );
                }

                // 更新集群状态
                clusterManager.removeRegionFromServer(serverId, regionId);
                clusterManager.addRegionToServer(
                        targetServer.getServerId(),
                        regionId,
                        regions.get(regionId)
                );

            } catch (Exception e) {
                logger.error("Failed to reassign region: {}", regionId, e);
                failedReassignments.add(regionId);
            }
        }

        if (!failedReassignments.isEmpty()) {
            logger.error("Failed to reassign {} regions: {}",
                        failedReassignments.size(), failedReassignments);
        } else {
            logger.info("Successfully reassigned all regions from failed server: {}", serverId);
        }
    }

    /**
     * 手动触发故障恢复
     *
     * @param serverId 服务器ID
     */
    public void triggerRecovery(String serverId) {
        logger.info("Manually triggering recovery for server: {}", serverId);
        handleServerFailure(serverId);
    }
}
