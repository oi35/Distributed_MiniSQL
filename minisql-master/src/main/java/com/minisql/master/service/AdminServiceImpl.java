package com.minisql.master.service;

import com.minisql.master.balance.MigrationTask;
import com.minisql.master.balance.RegionMigrationManager;
import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.cluster.ServerInfo;
import com.minisql.master.proto.*;
import com.minisql.common.proto.ServerState;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Admin服务实现 - CLI管理工具通信
 *
 * 负责处理CLI管理工具的管理操作请求
 */
public class AdminServiceImpl extends AdminServiceGrpc.AdminServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(AdminServiceImpl.class);

    private final ClusterManager clusterManager;
    private final RegionMigrationManager migrationManager;

    public AdminServiceImpl(ClusterManager clusterManager, RegionMigrationManager migrationManager) {
        this.clusterManager = clusterManager;
        this.migrationManager = migrationManager;
    }

    @Override
    public void listServers(ListServersRequest request,
                            StreamObserver<ListServersResponse> responseObserver) {
        logger.info("Received ListServers request");

        try {
            List<ServerInfo> allServers = clusterManager.getAllServers();
            ListServersResponse.Builder responseBuilder = ListServersResponse.newBuilder();

            int onlineCount = 0;
            for (ServerInfo server : allServers) {
                ServerDetail.Builder detail = ServerDetail.newBuilder()
                        .setServerId(server.getServerId())
                        .setHost(server.getHost())
                        .setPort(server.getPort())
                        .setState(server.getState())
                        .setLoadScore(server.getLoadScore())
                        .setRegionCount(server.getRegionCount())
                        .setTotalSizeBytes(0)
                        .setLastHeartbeatTime(server.getLastHeartbeatTime())
                        .setUptimeMs(System.currentTimeMillis() - server.getRegistrationTime())
                        .setAddress(server.getAddress());

                if (server.getState() == ServerState.SERVER_ONLINE) {
                    onlineCount++;
                }

                responseBuilder.addServers(detail.build());
            }

            responseBuilder
                    .setTotalCount(allServers.size())
                    .setOnlineCount(onlineCount);

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to list servers", e);
            responseObserver.onNext(ListServersResponse.newBuilder()
                    .setTotalCount(0)
                    .setOnlineCount(0)
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void triggerBalance(TriggerBalanceRequest request,
                               StreamObserver<TriggerBalanceResponse> responseObserver) {
        logger.info("Received TriggerBalance request");

        try {
            List<ServerInfo> servers = clusterManager.getOnlineServers();
            boolean needsBalance = servers.size() >= 2;

            int plansGenerated = 0;
            if (needsBalance && migrationManager != null) {
                List<MigrationTask> existingTasks = migrationManager.getActiveMigrations();
                plansGenerated = existingTasks.size();
            }

            TriggerBalanceResponse response = TriggerBalanceResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage(needsBalance
                            ? "Balance check completed. " + plansGenerated + " active migrations."
                            : "Not enough servers for balancing (need at least 2).")
                    .setPlansGenerated(plansGenerated)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to trigger balance", e);
            responseObserver.onNext(TriggerBalanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error: " + e.getMessage())
                    .setPlansGenerated(0)
                    .build());
            responseObserver.onCompleted();
        }
    }
}
