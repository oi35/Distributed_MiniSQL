package com.minisql.master.service;

import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.cluster.ServerInfo;
import com.minisql.master.proto.*;
import com.minisql.common.proto.ErrorCode;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Master服务实现 - Master ↔ RegionServer通信
 *
 * 负责处理RegionServer的注册、心跳、Region管理等请求
 */
public class MasterServiceImpl extends MasterServiceGrpc.MasterServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(MasterServiceImpl.class);

    private final ClusterManager clusterManager;

    public MasterServiceImpl(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }

    @Override
    public void registerRegionServer(RegisterRegionServerRequest request,
                                     StreamObserver<RegisterRegionServerResponse> responseObserver) {
        logger.info("Received RegisterRegionServer request from: {}", request.getServerId());

        try {
            // 注册服务器
            String assignedId = clusterManager.registerServer(
                    request.getServerId(),
                    request.getHost(),
                    request.getPort()
            );

            RegisterRegionServerResponse response = RegisterRegionServerResponse.newBuilder()
                    .setSuccess(true)
                    .setAssignedServerId(assignedId)
                    .setHeartbeatIntervalMs(clusterManager.getHeartbeatIntervalMs())
                    .setErrorCode(ErrorCode.ERROR_OK)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to register RegionServer", e);

            RegisterRegionServerResponse response = RegisterRegionServerResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorCode(ErrorCode.ERROR_INTERNAL)
                    .setErrorMessage("Registration failed: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void sendHeartbeat(HeartbeatRequest request,
                             StreamObserver<HeartbeatResponse> responseObserver) {
        logger.debug("Received heartbeat from: {}", request.getServerId());

        try {
            // 更新心跳
            boolean success = clusterManager.updateHeartbeat(
                    request.getServerId(),
                    request.getMetrics()
            );

            if (!success) {
                logger.warn("Heartbeat failed: server not found: {}", request.getServerId());
            }

            // 更新Region列表
            ServerInfo serverInfo = clusterManager.getServerInfo(request.getServerId());
            if (serverInfo != null) {
                // 同步Region列表
                for (String regionId : request.getRegionIdsList()) {
                    if (!serverInfo.getRegions().containsKey(regionId)) {
                        clusterManager.addRegionToServer(request.getServerId(), regionId, 0);
                    }
                }
            }

            HeartbeatResponse response = HeartbeatResponse.newBuilder()
                    .setAcknowledged(true)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to process heartbeat", e);

            HeartbeatResponse response = HeartbeatResponse.newBuilder()
                    .setAcknowledged(false)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void unregisterRegionServer(UnregisterRegionServerRequest request,
                                       StreamObserver<UnregisterRegionServerResponse> responseObserver) {
        logger.info("Received UnregisterRegionServer request from: {}", request.getServerId());

        try {
            boolean success = clusterManager.unregisterServer(request.getServerId());

            // 获取需要迁移的Region列表
            ServerInfo serverInfo = clusterManager.getServerInfo(request.getServerId());
            UnregisterRegionServerResponse.Builder responseBuilder =
                    UnregisterRegionServerResponse.newBuilder()
                    .setSuccess(success);

            if (serverInfo != null) {
                responseBuilder.addAllRegionsToMigrate(serverInfo.getRegions().keySet());
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to unregister RegionServer", e);

            UnregisterRegionServerResponse response = UnregisterRegionServerResponse.newBuilder()
                    .setSuccess(false)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void reportRegionOnline(ReportRegionOnlineRequest request,
                                   StreamObserver<ReportRegionOnlineResponse> responseObserver) {
        logger.info("Region {} is online on server: {}",
                   request.getRegionId(), request.getServerId());

        try {
            // 添加Region到服务器
            clusterManager.addRegionToServer(
                    request.getServerId(),
                    request.getRegionId(),
                    request.getSizeBytes()
            );

            ReportRegionOnlineResponse response = ReportRegionOnlineResponse.newBuilder()
                    .setAcknowledged(true)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to report region online", e);

            ReportRegionOnlineResponse response = ReportRegionOnlineResponse.newBuilder()
                    .setAcknowledged(false)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void reportRegionClosed(ReportRegionClosedRequest request,
                                   StreamObserver<ReportRegionClosedResponse> responseObserver) {
        logger.info("Region {} is closed on server: {}",
                   request.getRegionId(), request.getServerId());

        try {
            // 从服务器移除Region
            clusterManager.removeRegionFromServer(
                    request.getServerId(),
                    request.getRegionId()
            );

            ReportRegionClosedResponse response = ReportRegionClosedResponse.newBuilder()
                    .setAcknowledged(true)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to report region closed", e);

            ReportRegionClosedResponse response = ReportRegionClosedResponse.newBuilder()
                    .setAcknowledged(false)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void reportRegionSplit(ReportRegionSplitRequest request,
                                  StreamObserver<ReportRegionSplitResponse> responseObserver) {
        logger.info("Region {} split reported by server: {}",
                   request.getParentRegionId(), request.getServerId());

        // TODO: 实现Region分裂处理逻辑
        ReportRegionSplitResponse response = ReportRegionSplitResponse.newBuilder()
                .setAcknowledged(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void reportMigrationProgress(ReportMigrationProgressRequest request,
                                       StreamObserver<ReportMigrationProgressResponse> responseObserver) {
        logger.info("Migration progress for region {}: phase={}",
                   request.getRegionId(), request.getPhase());

        // TODO: 实现迁移进度处理逻辑
        ReportMigrationProgressResponse response = ReportMigrationProgressResponse.newBuilder()
                .setAcknowledged(true)
                .setShouldAbort(false)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void reportRegionFailure(ReportRegionFailureRequest request,
                                    StreamObserver<ReportRegionFailureResponse> responseObserver) {
        logger.error("Region failure reported: region={}, type={}, server={}",
                    request.getRegionId(), request.getFailureType(), request.getServerId());

        // TODO: 实现故障处理逻辑
        ReportRegionFailureResponse response = ReportRegionFailureResponse.newBuilder()
                .setAcknowledged(true)
                .setAction(ReportRegionFailureResponse.RecoveryAction.NONE)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void adminOperation(AdminOperationRequest request,
                              StreamObserver<AdminOperationResponse> responseObserver) {
        logger.info("Admin operation requested by server: {}", request.getServerId());

        // TODO: 实现管理操作逻辑
        AdminOperationResponse response = AdminOperationResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Admin operations not implemented yet")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
