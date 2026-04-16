package com.minisql.master.service;

import com.minisql.master.proto.*;
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

    @Override
    public void registerRegionServer(RegisterRegionServerRequest request,
                                     StreamObserver<RegisterRegionServerResponse> responseObserver) {
        logger.info("Received RegisterRegionServer request from: {}", request.getServerId());

        // TODO: 实现注册逻辑
        RegisterRegionServerResponse response = RegisterRegionServerResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage("Not implemented yet")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void sendHeartbeat(HeartbeatRequest request,
                             StreamObserver<HeartbeatResponse> responseObserver) {
        logger.debug("Received heartbeat from: {}", request.getServerId());

        // TODO: 实现心跳处理逻辑
        HeartbeatResponse response = HeartbeatResponse.newBuilder()
                .setAcknowledged(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void unregisterRegionServer(UnregisterRegionServerRequest request,
                                       StreamObserver<UnregisterRegionServerResponse> responseObserver) {
        logger.info("Received UnregisterRegionServer request from: {}", request.getServerId());

        // TODO: 实现注销逻辑
        UnregisterRegionServerResponse response = UnregisterRegionServerResponse.newBuilder()
                .setSuccess(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void reportRegionOnline(ReportRegionOnlineRequest request,
                                   StreamObserver<ReportRegionOnlineResponse> responseObserver) {
        logger.info("Region {} is online on server: {}",
                   request.getRegionId(), request.getServerId());

        // TODO: 实现Region上线处理逻辑
        ReportRegionOnlineResponse response = ReportRegionOnlineResponse.newBuilder()
                .setAcknowledged(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void reportRegionClosed(ReportRegionClosedRequest request,
                                   StreamObserver<ReportRegionClosedResponse> responseObserver) {
        logger.info("Region {} is closed on server: {}",
                   request.getRegionId(), request.getServerId());

        // TODO: 实现Region关闭处理逻辑
        ReportRegionClosedResponse response = ReportRegionClosedResponse.newBuilder()
                .setAcknowledged(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
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
