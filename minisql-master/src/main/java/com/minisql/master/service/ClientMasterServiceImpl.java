package com.minisql.master.service;

import com.google.protobuf.ByteString;
import com.minisql.master.cluster.ClusterManager;
import com.minisql.master.cluster.ServerInfo;
import com.minisql.master.metadata.MetadataManager;
import com.minisql.master.metadata.RegionMetadata;
import com.minisql.master.metadata.TableMetadata;
import com.minisql.master.proto.*;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.RegionInfo;
import com.minisql.common.proto.RegionState;
import com.minisql.common.proto.RouteEntry;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 客户端Master服务实现 - Master ↔ Client通信
 *
 * 负责处理客户端的DDL请求、路由查询等
 */
public class ClientMasterServiceImpl extends ClientMasterServiceGrpc.ClientMasterServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(ClientMasterServiceImpl.class);

    private final MetadataManager metadataManager;
    private final ClusterManager clusterManager;

    public ClientMasterServiceImpl(MetadataManager metadataManager, ClusterManager clusterManager) {
        this.metadataManager = metadataManager;
        this.clusterManager = clusterManager;
    }

    @Override
    public void createTable(CreateTableRequest request,
                           StreamObserver<CreateTableResponse> responseObserver) {
        logger.info("Received CreateTable request: {}", request.getSchema().getTableName());

        try {
            String tableName = request.getSchema().getTableName();
            String partitionKey = ""; // TODO: 从Schema中提取分区键

            boolean success = metadataManager.createTable(tableName, request.getSchema(), partitionKey);

            if (success) {
                // 自动创建默认Region（覆盖所有键范围），确保路由表可查询
                String regionId = tableName + "-region-0";
                boolean regionCreated = metadataManager.createRegion(regionId, tableName, "", "");

                if (regionCreated) {
                    // 将Region分配到最低负载的在线服务器
                    ServerInfo leastLoaded = clusterManager.selectLeastLoadedServer();
                    if (leastLoaded != null) {
                        // 先将服务器添加为副本，再设置为主副本
                        metadataManager.addRegionReplica(regionId, leastLoaded.getServerId());
                        metadataManager.setRegionPrimary(regionId, leastLoaded.getServerId());
                        logger.info("Default region {} created for table {}, assigned to {}",
                                regionId, tableName, leastLoaded.getServerId());
                    } else {
                        logger.warn("No online server available to assign default region {}", regionId);
                    }
                } else {
                    logger.warn("Failed to create default region {} for table {}", regionId, tableName);
                }

                logger.info("Table created successfully: {}", tableName);
            }

            CreateTableResponse.Builder responseBuilder = CreateTableResponse.newBuilder()
                    .setSuccess(success);

            if (success) {
                // 填充初始Region信息
                RegionMetadata regionMeta = metadataManager.getRegion(tableName + "-region-0");
                if (regionMeta != null) {
                    RegionInfo regionInfo = RegionInfo.newBuilder()
                            .setRegionId(regionMeta.getRegionId())
                            .setTableName(regionMeta.getTableName())
                            .setStartKey(ByteString.copyFromUtf8(regionMeta.getStartKey()))
                            .setEndKey(ByteString.copyFromUtf8(regionMeta.getEndKey()))
                            .setPrimaryServer(regionMeta.getPrimaryServer() != null ? regionMeta.getPrimaryServer() : "")
                            .setState(regionMeta.getState())
                            .setCreateTime(regionMeta.getCreateTime())
                            .build();
                    responseBuilder.addInitialRegions(regionInfo);
                }

                TableMetadata tableMeta = metadataManager.getTable(tableName);
                if (tableMeta != null) {
                    responseBuilder.setRouteTableVersion(tableMeta.getVersion());
                }

                responseBuilder.setErrorCode(ErrorCode.ERROR_OK);
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
                RouteEntry.Builder routeBuilder = RouteEntry.newBuilder()
                        .setRegionId(region.getRegionId())
                        .setStartKey(ByteString.copyFromUtf8(region.getStartKey()))
                        .setEndKey(ByteString.copyFromUtf8(region.getEndKey()))
                        .setPrimaryServer(region.getPrimaryServer() != null ? region.getPrimaryServer() : "")
                        .addAllReplicaServers(region.getReplicas());
                resolveAddress(routeBuilder, region.getPrimaryServer());

                routeTableBuilder.addRoutes(routeBuilder.build());
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
                RouteEntry.Builder routeBuilder = RouteEntry.newBuilder()
                        .setRegionId(region.getRegionId())
                        .setStartKey(ByteString.copyFromUtf8(region.getStartKey()))
                        .setEndKey(ByteString.copyFromUtf8(region.getEndKey()))
                        .setPrimaryServer(region.getPrimaryServer() != null ? region.getPrimaryServer() : "")
                        .addAllReplicaServers(region.getReplicas());
                resolveAddress(routeBuilder, region.getPrimaryServer());

                responseBuilder
                        .setSuccess(true)
                        .setErrorCode(ErrorCode.ERROR_OK)
                        .setRoute(routeBuilder.build());
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
                RouteEntry.Builder routeBuilder = RouteEntry.newBuilder()
                        .setRegionId(region.getRegionId())
                        .setStartKey(ByteString.copyFromUtf8(region.getStartKey()))
                        .setEndKey(ByteString.copyFromUtf8(region.getEndKey()))
                        .setPrimaryServer(region.getPrimaryServer() != null ? region.getPrimaryServer() : "")
                        .addAllReplicaServers(region.getReplicas());
                resolveAddress(routeBuilder, region.getPrimaryServer());

                responseBuilder.addRoutes(routeBuilder.build());
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

        try {
            Map<String, Object> stats = clusterManager.getClusterStats();
            List<ServerInfo> onlineServers = clusterManager.getOnlineServers();
            List<ServerInfo> allServers = clusterManager.getAllServers();

            int totalRegions = allServers.stream()
                    .mapToInt(ServerInfo::getRegionCount)
                    .sum();
            int onlineRegions = onlineServers.stream()
                    .mapToInt(ServerInfo::getRegionCount)
                    .sum();

            GetClusterHealthResponse.HealthStatus status;
            java.util.List<String> issues = new java.util.ArrayList<>();

            long deadCount = allServers.stream()
                    .filter(s -> s.getState() == com.minisql.common.proto.ServerState.SERVER_DEAD)
                    .count();

            if (deadCount > 0) {
                status = GetClusterHealthResponse.HealthStatus.DEGRADED;
                issues.add(deadCount + " server(s) are DEAD");
            } else if (allServers.isEmpty()) {
                status = GetClusterHealthResponse.HealthStatus.CRITICAL;
                issues.add("No servers registered");
            } else {
                status = GetClusterHealthResponse.HealthStatus.HEALTHY;
            }

            GetClusterHealthResponse response = GetClusterHealthResponse.newBuilder()
                    .setStatus(status)
                    .setTotalServers(allServers.size())
                    .setOnlineServers(onlineServers.size())
                    .setTotalRegions(totalRegions)
                    .setOnlineRegions(onlineRegions)
                    .addAllIssues(issues)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to get cluster health", e);
            responseObserver.onNext(GetClusterHealthResponse.newBuilder()
                    .setStatus(GetClusterHealthResponse.HealthStatus.CRITICAL)
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getClusterStats(GetClusterStatsRequest request,
                               StreamObserver<GetClusterStatsResponse> responseObserver) {
        logger.info("Received GetClusterStats request");

        try {
            Map<String, Object> stats = clusterManager.getClusterStats();
            List<ServerInfo> allServers = clusterManager.getAllServers();

            int totalTables = metadataManager.listTables().size();
            int totalRegions = allServers.stream()
                    .mapToInt(ServerInfo::getRegionCount)
                    .sum();

            GetClusterStatsResponse response = GetClusterStatsResponse.newBuilder()
                    .setTotalTables(totalTables)
                    .setTotalRegions(totalRegions)
                    .setTotalDataSizeBytes(0)
                    .setTotalRowCount(0)
                    .setAverageRegionSizeMb(totalRegions > 0 ? 0.0 : 0.0)
                    .setRegionsSplitting(0)
                    .setRegionsMigrating(0)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Failed to get cluster stats", e);
            responseObserver.onNext(GetClusterStatsResponse.newBuilder().build());
            responseObserver.onCompleted();
        }
    }

    /**
     * Resolve and set the primary_address on a RouteEntry from the server ID.
     */
    private void resolveAddress(RouteEntry.Builder builder, String serverId) {
        if (serverId == null || serverId.isEmpty()) {
            return;
        }
        ServerInfo info = clusterManager.getServerInfo(serverId);
        if (info != null && info.getHost() != null && !info.getHost().isEmpty()) {
            builder.setPrimaryAddress(info.getHost() + ":" + info.getPort());
        }
    }
}
