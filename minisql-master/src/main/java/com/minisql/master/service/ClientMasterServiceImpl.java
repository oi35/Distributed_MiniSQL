package com.minisql.master.service;

import com.minisql.master.proto.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端Master服务实现 - Master ↔ Client通信
 *
 * 负责处理客户端的DDL请求、路由查询等
 */
public class ClientMasterServiceImpl extends ClientMasterServiceGrpc.ClientMasterServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(ClientMasterServiceImpl.class);

    @Override
    public void createTable(CreateTableRequest request,
                           StreamObserver<CreateTableResponse> responseObserver) {
        logger.info("Received CreateTable request: {}", request.getSchema().getTableName());

        // TODO: 实现创建表逻辑
        CreateTableResponse response = CreateTableResponse.newBuilder()
                .setSuccess(false)
                .setErrorCode(com.minisql.common.proto.ErrorCode.ERROR_UNIMPLEMENTED)
                .setErrorMessage("CreateTable not implemented yet")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void dropTable(DropTableRequest request,
                         StreamObserver<DropTableResponse> responseObserver) {
        logger.info("Received DropTable request: {}", request.getTableName());

        // TODO: 实现删除表逻辑
        DropTableResponse response = DropTableResponse.newBuilder()
                .setSuccess(false)
                .setErrorCode(com.minisql.common.proto.ErrorCode.ERROR_UNIMPLEMENTED)
                .setErrorMessage("DropTable not implemented yet")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getTableSchema(GetTableSchemaRequest request,
                              StreamObserver<GetTableSchemaResponse> responseObserver) {
        logger.info("Received GetTableSchema request: {}", request.getTableName());

        // TODO: 实现获取表结构逻辑
        GetTableSchemaResponse response = GetTableSchemaResponse.newBuilder()
                .setSuccess(false)
                .setErrorCode(com.minisql.common.proto.ErrorCode.ERROR_TABLE_NOT_FOUND)
                .setErrorMessage("Table not found")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void listTables(ListTablesRequest request,
                          StreamObserver<ListTablesResponse> responseObserver) {
        logger.info("Received ListTables request");

        // TODO: 实现列出表逻辑
        ListTablesResponse response = ListTablesResponse.newBuilder()
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getRouteTable(GetRouteTableRequest request,
                             StreamObserver<GetRouteTableResponse> responseObserver) {
        logger.info("Received GetRouteTable request: table={}, cachedVersion={}",
                   request.getTableName(), request.getCachedVersion());

        // TODO: 实现获取路由表逻辑
        GetRouteTableResponse response = GetRouteTableResponse.newBuilder()
                .setSuccess(false)
                .setErrorCode(com.minisql.common.proto.ErrorCode.ERROR_TABLE_NOT_FOUND)
                .setErrorMessage("Table not found")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getRouteForKey(GetRouteForKeyRequest request,
                               StreamObserver<GetRouteForKeyResponse> responseObserver) {
        logger.info("Received GetRouteForKey request: table={}",
                   request.getTableName());

        // TODO: 实现获取键路由逻辑
        GetRouteForKeyResponse response = GetRouteForKeyResponse.newBuilder()
                .setSuccess(false)
                .setErrorCode(com.minisql.common.proto.ErrorCode.ERROR_TABLE_NOT_FOUND)
                .setErrorMessage("Table not found")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getRoutesForRange(GetRoutesForRangeRequest request,
                                  StreamObserver<GetRoutesForRangeResponse> responseObserver) {
        logger.info("Received GetRoutesForRange request: table={}",
                   request.getTableName());

        // TODO: 实现获取范围路由逻辑
        GetRoutesForRangeResponse response = GetRoutesForRangeResponse.newBuilder()
                .setSuccess(false)
                .setErrorCode(com.minisql.common.proto.ErrorCode.ERROR_TABLE_NOT_FOUND)
                .setErrorMessage("Table not found")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void reportStaleRoute(ReportStaleRouteRequest request,
                                 StreamObserver<ReportStaleRouteResponse> responseObserver) {
        logger.warn("Stale route reported: table={}, region={}, expected={}",
                   request.getTableName(), request.getRegionId(), request.getExpectedServer());

        // TODO: 实现过期路由处理逻辑
        ReportStaleRouteResponse response = ReportStaleRouteResponse.newBuilder()
                .setAcknowledged(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getClusterHealth(GetClusterHealthRequest request,
                                StreamObserver<GetClusterHealthResponse> responseObserver) {
        logger.info("Received GetClusterHealth request");

        // TODO: 实现集群健康检查逻辑
        GetClusterHealthResponse response = GetClusterHealthResponse.newBuilder()
                .setStatus(GetClusterHealthResponse.HealthStatus.HEALTHY)
                .setTotalServers(0)
                .setOnlineServers(0)
                .setTotalRegions(0)
                .setOnlineRegions(0)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getClusterStats(GetClusterStatsRequest request,
                               StreamObserver<GetClusterStatsResponse> responseObserver) {
        logger.info("Received GetClusterStats request");

        // TODO: 实现集群统计逻辑
        GetClusterStatsResponse response = GetClusterStatsResponse.newBuilder()
                .setTotalTables(0)
                .setTotalRegions(0)
                .setTotalDataSizeBytes(0)
                .setTotalRowCount(0)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
