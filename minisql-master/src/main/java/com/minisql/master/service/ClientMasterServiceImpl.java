package com.minisql.master.service;

import com.google.protobuf.ByteString;
import com.minisql.master.metadata.MetadataManager;
import com.minisql.master.metadata.RegionMetadata;
import com.minisql.master.metadata.TableMetadata;
import com.minisql.master.proto.*;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.RouteEntry;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 客户端Master服务实现 - Master ↔ Client通信
 *
 * 负责处理客户端的DDL请求、路由查询等
 */
public class ClientMasterServiceImpl extends ClientMasterServiceGrpc.ClientMasterServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(ClientMasterServiceImpl.class);

    private final MetadataManager metadataManager;

    public ClientMasterServiceImpl(MetadataManager metadataManager) {
        this.metadataManager = metadataManager;
    }

    @Override
    public void createTable(CreateTableRequest request,
                           StreamObserver<CreateTableResponse> responseObserver) {
        logger.info("Received CreateTable request: {}", request.getSchema().getTableName());

        try {
            String tableName = request.getSchema().getTableName();
            String partitionKey = ""; // TODO: 从Schema中提取分区键

            boolean success = metadataManager.createTable(tableName, request.getSchema(), partitionKey);

            CreateTableResponse.Builder responseBuilder = CreateTableResponse.newBuilder()
                    .setSuccess(success);

            if (success) {
                responseBuilder.setErrorCode(ErrorCode.ERROR_OK);
                logger.info("Table created successfully: {}", tableName);
            } else {
                responseBuilder
                        .setErrorCode(ErrorCode.ERROR_TABLE_ALREADY_EXISTS)
                        .setErrorMessage("Table already exists: " + tableName);
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to create table", e);

            CreateTableResponse response = CreateTableResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorCode(ErrorCode.ERROR_INTERNAL)
                    .setErrorMessage("Internal error: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void dropTable(DropTableRequest request,
                         StreamObserver<DropTableResponse> responseObserver) {
        logger.info("Received DropTable request: {}", request.getTableName());

        try {
            boolean success = metadataManager.dropTable(request.getTableName());

            DropTableResponse.Builder responseBuilder = DropTableResponse.newBuilder()
                    .setSuccess(success);

            if (success) {
                responseBuilder.setErrorCode(ErrorCode.ERROR_OK);
                logger.info("Table dropped successfully: {}", request.getTableName());
            } else {
                responseBuilder
                        .setErrorCode(ErrorCode.ERROR_TABLE_NOT_FOUND)
                        .setErrorMessage("Table not found: " + request.getTableName());
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to drop table", e);

            DropTableResponse response = DropTableResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorCode(ErrorCode.ERROR_INTERNAL)
                    .setErrorMessage("Internal error: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getTableSchema(GetTableSchemaRequest request,
                              StreamObserver<GetTableSchemaResponse> responseObserver) {
        logger.info("Received GetTableSchema request: {}", request.getTableName());

        try {
            TableMetadata table = metadataManager.getTable(request.getTableName());

            GetTableSchemaResponse.Builder responseBuilder = GetTableSchemaResponse.newBuilder();

            if (table != null) {
                responseBuilder
                        .setSuccess(true)
                        .setErrorCode(ErrorCode.ERROR_OK)
                        .setSchema(table.getSchema());
            } else {
                responseBuilder
                        .setSuccess(false)
                        .setErrorCode(ErrorCode.ERROR_TABLE_NOT_FOUND)
                        .setErrorMessage("Table not found: " + request.getTableName());
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to get table schema", e);

            GetTableSchemaResponse response = GetTableSchemaResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorCode(ErrorCode.ERROR_INTERNAL)
                    .setErrorMessage("Internal error: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listTables(ListTablesRequest request,
                          StreamObserver<ListTablesResponse> responseObserver) {
        logger.info("Received ListTables request");

        try {
            List<String> tableNames = metadataManager.listTables();

            ListTablesResponse response = ListTablesResponse.newBuilder()
                    .addAllTableNames(tableNames)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to list tables", e);

            ListTablesResponse response = ListTablesResponse.newBuilder()
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getRouteTable(GetRouteTableRequest request,
                             StreamObserver<GetRouteTableResponse> responseObserver) {
        logger.info("Received GetRouteTable request: table={}, cachedVersion={}",
                   request.getTableName(), request.getCachedVersion());

        try {
            TableMetadata table = metadataManager.getTable(request.getTableName());

            if (table == null) {
                GetRouteTableResponse response = GetRouteTableResponse.newBuilder()
                        .setSuccess(false)
                        .setErrorCode(ErrorCode.ERROR_TABLE_NOT_FOUND)
                        .setErrorMessage("Table not found: " + request.getTableName())
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }

            // 检查版本是否需要更新
            if (request.getCachedVersion() >= table.getVersion()) {
                GetRouteTableResponse response = GetRouteTableResponse.newBuilder()
                        .setSuccess(true)
                        .setErrorCode(ErrorCode.ERROR_OK)
                        .setCacheValid(true)
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }

            // 构建路由表
            List<RegionMetadata> regions = metadataManager.getTableRegions(request.getTableName());
            com.minisql.common.proto.RegionRouteTable.Builder routeTableBuilder =
                    com.minisql.common.proto.RegionRouteTable.newBuilder()
                    .setTableName(request.getTableName())
                    .setVersion(table.getVersion())
                    .setUpdateTime(table.getUpdateTime());

            for (RegionMetadata region : regions) {
                RouteEntry routeEntry = RouteEntry.newBuilder()
                        .setRegionId(region.getRegionId())
                        .setStartKey(ByteString.copyFromUtf8(region.getStartKey()))
                        .setEndKey(ByteString.copyFromUtf8(region.getEndKey()))
                        .setPrimaryServer(region.getPrimaryServer() != null ? region.getPrimaryServer() : "")
                        .addAllReplicaServers(region.getReplicas())
                        .build();

                routeTableBuilder.addRoutes(routeEntry);
            }

            GetRouteTableResponse response = GetRouteTableResponse.newBuilder()
                    .setSuccess(true)
                    .setErrorCode(ErrorCode.ERROR_OK)
                    .setCacheValid(false)
                    .setRouteTable(routeTableBuilder.build())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to get route table", e);

            GetRouteTableResponse response = GetRouteTableResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorCode(ErrorCode.ERROR_INTERNAL)
                    .setErrorMessage("Internal error: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getRouteForKey(GetRouteForKeyRequest request,
                               StreamObserver<GetRouteForKeyResponse> responseObserver) {
        logger.info("Received GetRouteForKey request: table={}",
                   request.getTableName());

        try {
            RegionMetadata region = metadataManager.findRegionForKey(
                    request.getTableName(),
                    request.getKey().toStringUtf8()
            );

            GetRouteForKeyResponse.Builder responseBuilder = GetRouteForKeyResponse.newBuilder();

            if (region != null) {
                RouteEntry routeEntry = RouteEntry.newBuilder()
                        .setRegionId(region.getRegionId())
                        .setStartKey(ByteString.copyFromUtf8(region.getStartKey()))
                        .setEndKey(ByteString.copyFromUtf8(region.getEndKey()))
                        .setPrimaryServer(region.getPrimaryServer() != null ? region.getPrimaryServer() : "")
                        .addAllReplicaServers(region.getReplicas())
                        .build();

                responseBuilder
                        .setSuccess(true)
                        .setErrorCode(ErrorCode.ERROR_OK)
                        .setRoute(routeEntry);
            } else {
                responseBuilder
                        .setSuccess(false)
                        .setErrorCode(ErrorCode.ERROR_REGION_NOT_FOUND)
                        .setErrorMessage("No region found for key");
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to get route for key", e);

            GetRouteForKeyResponse response = GetRouteForKeyResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorCode(ErrorCode.ERROR_INTERNAL)
                    .setErrorMessage("Internal error: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getRoutesForRange(GetRoutesForRangeRequest request,
                                  StreamObserver<GetRoutesForRangeResponse> responseObserver) {
        logger.info("Received GetRoutesForRange request: table={}",
                   request.getTableName());

        try {
            List<RegionMetadata> regions = metadataManager.findRegionsForRange(
                    request.getTableName(),
                    request.getStartKey().toStringUtf8(),
                    request.getEndKey().toStringUtf8()
            );

            GetRoutesForRangeResponse.Builder responseBuilder = GetRoutesForRangeResponse.newBuilder()
                    .setSuccess(true)
                    .setErrorCode(ErrorCode.ERROR_OK);

            for (RegionMetadata region : regions) {
                RouteEntry routeEntry = RouteEntry.newBuilder()
                        .setRegionId(region.getRegionId())
                        .setStartKey(ByteString.copyFromUtf8(region.getStartKey()))
                        .setEndKey(ByteString.copyFromUtf8(region.getEndKey()))
                        .setPrimaryServer(region.getPrimaryServer() != null ? region.getPrimaryServer() : "")
                        .addAllReplicaServers(region.getReplicas())
                        .build();

                responseBuilder.addRoutes(routeEntry);
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to get routes for range", e);

            GetRoutesForRangeResponse response = GetRoutesForRangeResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorCode(ErrorCode.ERROR_INTERNAL)
                    .setErrorMessage("Internal error: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
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
