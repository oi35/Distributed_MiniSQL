package com.minisql.regionserver.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * ========== 单行操作（显式方法）==========
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.58.0)",
    comments = "Source: regionserver.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class RegionServerServiceGrpc {

  private RegionServerServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "minisql.regionserver.RegionServerService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.minisql.regionserver.proto.PutRequest,
      com.minisql.regionserver.proto.PutResponse> getPutMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Put",
      requestType = com.minisql.regionserver.proto.PutRequest.class,
      responseType = com.minisql.regionserver.proto.PutResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.regionserver.proto.PutRequest,
      com.minisql.regionserver.proto.PutResponse> getPutMethod() {
    io.grpc.MethodDescriptor<com.minisql.regionserver.proto.PutRequest, com.minisql.regionserver.proto.PutResponse> getPutMethod;
    if ((getPutMethod = RegionServerServiceGrpc.getPutMethod) == null) {
      synchronized (RegionServerServiceGrpc.class) {
        if ((getPutMethod = RegionServerServiceGrpc.getPutMethod) == null) {
          RegionServerServiceGrpc.getPutMethod = getPutMethod =
              io.grpc.MethodDescriptor.<com.minisql.regionserver.proto.PutRequest, com.minisql.regionserver.proto.PutResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Put"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.PutRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.PutResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RegionServerServiceMethodDescriptorSupplier("Put"))
              .build();
        }
      }
    }
    return getPutMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.regionserver.proto.GetRequest,
      com.minisql.regionserver.proto.GetResponse> getGetMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Get",
      requestType = com.minisql.regionserver.proto.GetRequest.class,
      responseType = com.minisql.regionserver.proto.GetResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.regionserver.proto.GetRequest,
      com.minisql.regionserver.proto.GetResponse> getGetMethod() {
    io.grpc.MethodDescriptor<com.minisql.regionserver.proto.GetRequest, com.minisql.regionserver.proto.GetResponse> getGetMethod;
    if ((getGetMethod = RegionServerServiceGrpc.getGetMethod) == null) {
      synchronized (RegionServerServiceGrpc.class) {
        if ((getGetMethod = RegionServerServiceGrpc.getGetMethod) == null) {
          RegionServerServiceGrpc.getGetMethod = getGetMethod =
              io.grpc.MethodDescriptor.<com.minisql.regionserver.proto.GetRequest, com.minisql.regionserver.proto.GetResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Get"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.GetRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.GetResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RegionServerServiceMethodDescriptorSupplier("Get"))
              .build();
        }
      }
    }
    return getGetMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.regionserver.proto.DeleteRequest,
      com.minisql.regionserver.proto.DeleteResponse> getDeleteMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Delete",
      requestType = com.minisql.regionserver.proto.DeleteRequest.class,
      responseType = com.minisql.regionserver.proto.DeleteResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.regionserver.proto.DeleteRequest,
      com.minisql.regionserver.proto.DeleteResponse> getDeleteMethod() {
    io.grpc.MethodDescriptor<com.minisql.regionserver.proto.DeleteRequest, com.minisql.regionserver.proto.DeleteResponse> getDeleteMethod;
    if ((getDeleteMethod = RegionServerServiceGrpc.getDeleteMethod) == null) {
      synchronized (RegionServerServiceGrpc.class) {
        if ((getDeleteMethod = RegionServerServiceGrpc.getDeleteMethod) == null) {
          RegionServerServiceGrpc.getDeleteMethod = getDeleteMethod =
              io.grpc.MethodDescriptor.<com.minisql.regionserver.proto.DeleteRequest, com.minisql.regionserver.proto.DeleteResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Delete"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.DeleteRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.DeleteResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RegionServerServiceMethodDescriptorSupplier("Delete"))
              .build();
        }
      }
    }
    return getDeleteMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.regionserver.proto.ExistsRequest,
      com.minisql.regionserver.proto.ExistsResponse> getExistsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Exists",
      requestType = com.minisql.regionserver.proto.ExistsRequest.class,
      responseType = com.minisql.regionserver.proto.ExistsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.regionserver.proto.ExistsRequest,
      com.minisql.regionserver.proto.ExistsResponse> getExistsMethod() {
    io.grpc.MethodDescriptor<com.minisql.regionserver.proto.ExistsRequest, com.minisql.regionserver.proto.ExistsResponse> getExistsMethod;
    if ((getExistsMethod = RegionServerServiceGrpc.getExistsMethod) == null) {
      synchronized (RegionServerServiceGrpc.class) {
        if ((getExistsMethod = RegionServerServiceGrpc.getExistsMethod) == null) {
          RegionServerServiceGrpc.getExistsMethod = getExistsMethod =
              io.grpc.MethodDescriptor.<com.minisql.regionserver.proto.ExistsRequest, com.minisql.regionserver.proto.ExistsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Exists"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.ExistsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.ExistsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RegionServerServiceMethodDescriptorSupplier("Exists"))
              .build();
        }
      }
    }
    return getExistsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.regionserver.proto.BatchPutRequest,
      com.minisql.regionserver.proto.BatchPutResponse> getBatchPutMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "BatchPut",
      requestType = com.minisql.regionserver.proto.BatchPutRequest.class,
      responseType = com.minisql.regionserver.proto.BatchPutResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.regionserver.proto.BatchPutRequest,
      com.minisql.regionserver.proto.BatchPutResponse> getBatchPutMethod() {
    io.grpc.MethodDescriptor<com.minisql.regionserver.proto.BatchPutRequest, com.minisql.regionserver.proto.BatchPutResponse> getBatchPutMethod;
    if ((getBatchPutMethod = RegionServerServiceGrpc.getBatchPutMethod) == null) {
      synchronized (RegionServerServiceGrpc.class) {
        if ((getBatchPutMethod = RegionServerServiceGrpc.getBatchPutMethod) == null) {
          RegionServerServiceGrpc.getBatchPutMethod = getBatchPutMethod =
              io.grpc.MethodDescriptor.<com.minisql.regionserver.proto.BatchPutRequest, com.minisql.regionserver.proto.BatchPutResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "BatchPut"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.BatchPutRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.BatchPutResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RegionServerServiceMethodDescriptorSupplier("BatchPut"))
              .build();
        }
      }
    }
    return getBatchPutMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.regionserver.proto.BatchGetRequest,
      com.minisql.regionserver.proto.BatchGetResponse> getBatchGetMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "BatchGet",
      requestType = com.minisql.regionserver.proto.BatchGetRequest.class,
      responseType = com.minisql.regionserver.proto.BatchGetResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.regionserver.proto.BatchGetRequest,
      com.minisql.regionserver.proto.BatchGetResponse> getBatchGetMethod() {
    io.grpc.MethodDescriptor<com.minisql.regionserver.proto.BatchGetRequest, com.minisql.regionserver.proto.BatchGetResponse> getBatchGetMethod;
    if ((getBatchGetMethod = RegionServerServiceGrpc.getBatchGetMethod) == null) {
      synchronized (RegionServerServiceGrpc.class) {
        if ((getBatchGetMethod = RegionServerServiceGrpc.getBatchGetMethod) == null) {
          RegionServerServiceGrpc.getBatchGetMethod = getBatchGetMethod =
              io.grpc.MethodDescriptor.<com.minisql.regionserver.proto.BatchGetRequest, com.minisql.regionserver.proto.BatchGetResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "BatchGet"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.BatchGetRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.BatchGetResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RegionServerServiceMethodDescriptorSupplier("BatchGet"))
              .build();
        }
      }
    }
    return getBatchGetMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.regionserver.proto.BatchDeleteRequest,
      com.minisql.regionserver.proto.BatchDeleteResponse> getBatchDeleteMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "BatchDelete",
      requestType = com.minisql.regionserver.proto.BatchDeleteRequest.class,
      responseType = com.minisql.regionserver.proto.BatchDeleteResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.regionserver.proto.BatchDeleteRequest,
      com.minisql.regionserver.proto.BatchDeleteResponse> getBatchDeleteMethod() {
    io.grpc.MethodDescriptor<com.minisql.regionserver.proto.BatchDeleteRequest, com.minisql.regionserver.proto.BatchDeleteResponse> getBatchDeleteMethod;
    if ((getBatchDeleteMethod = RegionServerServiceGrpc.getBatchDeleteMethod) == null) {
      synchronized (RegionServerServiceGrpc.class) {
        if ((getBatchDeleteMethod = RegionServerServiceGrpc.getBatchDeleteMethod) == null) {
          RegionServerServiceGrpc.getBatchDeleteMethod = getBatchDeleteMethod =
              io.grpc.MethodDescriptor.<com.minisql.regionserver.proto.BatchDeleteRequest, com.minisql.regionserver.proto.BatchDeleteResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "BatchDelete"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.BatchDeleteRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.BatchDeleteResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RegionServerServiceMethodDescriptorSupplier("BatchDelete"))
              .build();
        }
      }
    }
    return getBatchDeleteMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.regionserver.proto.ScanRequest,
      com.minisql.regionserver.proto.ScanResponse> getScanMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Scan",
      requestType = com.minisql.regionserver.proto.ScanRequest.class,
      responseType = com.minisql.regionserver.proto.ScanResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<com.minisql.regionserver.proto.ScanRequest,
      com.minisql.regionserver.proto.ScanResponse> getScanMethod() {
    io.grpc.MethodDescriptor<com.minisql.regionserver.proto.ScanRequest, com.minisql.regionserver.proto.ScanResponse> getScanMethod;
    if ((getScanMethod = RegionServerServiceGrpc.getScanMethod) == null) {
      synchronized (RegionServerServiceGrpc.class) {
        if ((getScanMethod = RegionServerServiceGrpc.getScanMethod) == null) {
          RegionServerServiceGrpc.getScanMethod = getScanMethod =
              io.grpc.MethodDescriptor.<com.minisql.regionserver.proto.ScanRequest, com.minisql.regionserver.proto.ScanResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Scan"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.ScanRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.ScanResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RegionServerServiceMethodDescriptorSupplier("Scan"))
              .build();
        }
      }
    }
    return getScanMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.regionserver.proto.QueryRequest,
      com.minisql.regionserver.proto.QueryResponse> getQueryMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Query",
      requestType = com.minisql.regionserver.proto.QueryRequest.class,
      responseType = com.minisql.regionserver.proto.QueryResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.regionserver.proto.QueryRequest,
      com.minisql.regionserver.proto.QueryResponse> getQueryMethod() {
    io.grpc.MethodDescriptor<com.minisql.regionserver.proto.QueryRequest, com.minisql.regionserver.proto.QueryResponse> getQueryMethod;
    if ((getQueryMethod = RegionServerServiceGrpc.getQueryMethod) == null) {
      synchronized (RegionServerServiceGrpc.class) {
        if ((getQueryMethod = RegionServerServiceGrpc.getQueryMethod) == null) {
          RegionServerServiceGrpc.getQueryMethod = getQueryMethod =
              io.grpc.MethodDescriptor.<com.minisql.regionserver.proto.QueryRequest, com.minisql.regionserver.proto.QueryResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Query"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.QueryRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.QueryResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RegionServerServiceMethodDescriptorSupplier("Query"))
              .build();
        }
      }
    }
    return getQueryMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.regionserver.proto.OpenRegionRequest,
      com.minisql.regionserver.proto.OpenRegionResponse> getOpenRegionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "OpenRegion",
      requestType = com.minisql.regionserver.proto.OpenRegionRequest.class,
      responseType = com.minisql.regionserver.proto.OpenRegionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.regionserver.proto.OpenRegionRequest,
      com.minisql.regionserver.proto.OpenRegionResponse> getOpenRegionMethod() {
    io.grpc.MethodDescriptor<com.minisql.regionserver.proto.OpenRegionRequest, com.minisql.regionserver.proto.OpenRegionResponse> getOpenRegionMethod;
    if ((getOpenRegionMethod = RegionServerServiceGrpc.getOpenRegionMethod) == null) {
      synchronized (RegionServerServiceGrpc.class) {
        if ((getOpenRegionMethod = RegionServerServiceGrpc.getOpenRegionMethod) == null) {
          RegionServerServiceGrpc.getOpenRegionMethod = getOpenRegionMethod =
              io.grpc.MethodDescriptor.<com.minisql.regionserver.proto.OpenRegionRequest, com.minisql.regionserver.proto.OpenRegionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "OpenRegion"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.OpenRegionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.OpenRegionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RegionServerServiceMethodDescriptorSupplier("OpenRegion"))
              .build();
        }
      }
    }
    return getOpenRegionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.regionserver.proto.CloseRegionRequest,
      com.minisql.regionserver.proto.CloseRegionResponse> getCloseRegionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CloseRegion",
      requestType = com.minisql.regionserver.proto.CloseRegionRequest.class,
      responseType = com.minisql.regionserver.proto.CloseRegionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.regionserver.proto.CloseRegionRequest,
      com.minisql.regionserver.proto.CloseRegionResponse> getCloseRegionMethod() {
    io.grpc.MethodDescriptor<com.minisql.regionserver.proto.CloseRegionRequest, com.minisql.regionserver.proto.CloseRegionResponse> getCloseRegionMethod;
    if ((getCloseRegionMethod = RegionServerServiceGrpc.getCloseRegionMethod) == null) {
      synchronized (RegionServerServiceGrpc.class) {
        if ((getCloseRegionMethod = RegionServerServiceGrpc.getCloseRegionMethod) == null) {
          RegionServerServiceGrpc.getCloseRegionMethod = getCloseRegionMethod =
              io.grpc.MethodDescriptor.<com.minisql.regionserver.proto.CloseRegionRequest, com.minisql.regionserver.proto.CloseRegionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CloseRegion"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.CloseRegionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.CloseRegionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RegionServerServiceMethodDescriptorSupplier("CloseRegion"))
              .build();
        }
      }
    }
    return getCloseRegionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.regionserver.proto.MigrateRegionRequest,
      com.minisql.regionserver.proto.MigrateRegionResponse> getMigrateRegionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "MigrateRegion",
      requestType = com.minisql.regionserver.proto.MigrateRegionRequest.class,
      responseType = com.minisql.regionserver.proto.MigrateRegionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.regionserver.proto.MigrateRegionRequest,
      com.minisql.regionserver.proto.MigrateRegionResponse> getMigrateRegionMethod() {
    io.grpc.MethodDescriptor<com.minisql.regionserver.proto.MigrateRegionRequest, com.minisql.regionserver.proto.MigrateRegionResponse> getMigrateRegionMethod;
    if ((getMigrateRegionMethod = RegionServerServiceGrpc.getMigrateRegionMethod) == null) {
      synchronized (RegionServerServiceGrpc.class) {
        if ((getMigrateRegionMethod = RegionServerServiceGrpc.getMigrateRegionMethod) == null) {
          RegionServerServiceGrpc.getMigrateRegionMethod = getMigrateRegionMethod =
              io.grpc.MethodDescriptor.<com.minisql.regionserver.proto.MigrateRegionRequest, com.minisql.regionserver.proto.MigrateRegionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "MigrateRegion"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.MigrateRegionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.MigrateRegionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RegionServerServiceMethodDescriptorSupplier("MigrateRegion"))
              .build();
        }
      }
    }
    return getMigrateRegionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.regionserver.proto.GetReplicationLogRequest,
      com.minisql.regionserver.proto.ReplicationLogEntry> getGetReplicationLogMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetReplicationLog",
      requestType = com.minisql.regionserver.proto.GetReplicationLogRequest.class,
      responseType = com.minisql.regionserver.proto.ReplicationLogEntry.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<com.minisql.regionserver.proto.GetReplicationLogRequest,
      com.minisql.regionserver.proto.ReplicationLogEntry> getGetReplicationLogMethod() {
    io.grpc.MethodDescriptor<com.minisql.regionserver.proto.GetReplicationLogRequest, com.minisql.regionserver.proto.ReplicationLogEntry> getGetReplicationLogMethod;
    if ((getGetReplicationLogMethod = RegionServerServiceGrpc.getGetReplicationLogMethod) == null) {
      synchronized (RegionServerServiceGrpc.class) {
        if ((getGetReplicationLogMethod = RegionServerServiceGrpc.getGetReplicationLogMethod) == null) {
          RegionServerServiceGrpc.getGetReplicationLogMethod = getGetReplicationLogMethod =
              io.grpc.MethodDescriptor.<com.minisql.regionserver.proto.GetReplicationLogRequest, com.minisql.regionserver.proto.ReplicationLogEntry>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetReplicationLog"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.GetReplicationLogRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.ReplicationLogEntry.getDefaultInstance()))
              .setSchemaDescriptor(new RegionServerServiceMethodDescriptorSupplier("GetReplicationLog"))
              .build();
        }
      }
    }
    return getGetReplicationLogMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.regionserver.proto.ApplyReplicationLogRequest,
      com.minisql.regionserver.proto.ApplyReplicationLogResponse> getApplyReplicationLogMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ApplyReplicationLog",
      requestType = com.minisql.regionserver.proto.ApplyReplicationLogRequest.class,
      responseType = com.minisql.regionserver.proto.ApplyReplicationLogResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.regionserver.proto.ApplyReplicationLogRequest,
      com.minisql.regionserver.proto.ApplyReplicationLogResponse> getApplyReplicationLogMethod() {
    io.grpc.MethodDescriptor<com.minisql.regionserver.proto.ApplyReplicationLogRequest, com.minisql.regionserver.proto.ApplyReplicationLogResponse> getApplyReplicationLogMethod;
    if ((getApplyReplicationLogMethod = RegionServerServiceGrpc.getApplyReplicationLogMethod) == null) {
      synchronized (RegionServerServiceGrpc.class) {
        if ((getApplyReplicationLogMethod = RegionServerServiceGrpc.getApplyReplicationLogMethod) == null) {
          RegionServerServiceGrpc.getApplyReplicationLogMethod = getApplyReplicationLogMethod =
              io.grpc.MethodDescriptor.<com.minisql.regionserver.proto.ApplyReplicationLogRequest, com.minisql.regionserver.proto.ApplyReplicationLogResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ApplyReplicationLog"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.ApplyReplicationLogRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.regionserver.proto.ApplyReplicationLogResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RegionServerServiceMethodDescriptorSupplier("ApplyReplicationLog"))
              .build();
        }
      }
    }
    return getApplyReplicationLogMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static RegionServerServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RegionServerServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RegionServerServiceStub>() {
        @java.lang.Override
        public RegionServerServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RegionServerServiceStub(channel, callOptions);
        }
      };
    return RegionServerServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static RegionServerServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RegionServerServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RegionServerServiceBlockingStub>() {
        @java.lang.Override
        public RegionServerServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RegionServerServiceBlockingStub(channel, callOptions);
        }
      };
    return RegionServerServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static RegionServerServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RegionServerServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RegionServerServiceFutureStub>() {
        @java.lang.Override
        public RegionServerServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RegionServerServiceFutureStub(channel, callOptions);
        }
      };
    return RegionServerServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * ========== 单行操作（显式方法）==========
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * 插入单行数据
     * </pre>
     */
    default void put(com.minisql.regionserver.proto.PutRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.PutResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getPutMethod(), responseObserver);
    }

    /**
     * <pre>
     * 查询单行数据
     * </pre>
     */
    default void get(com.minisql.regionserver.proto.GetRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.GetResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetMethod(), responseObserver);
    }

    /**
     * <pre>
     * 删除单行数据
     * </pre>
     */
    default void delete(com.minisql.regionserver.proto.DeleteRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.DeleteResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDeleteMethod(), responseObserver);
    }

    /**
     * <pre>
     * 检查行是否存在
     * </pre>
     */
    default void exists(com.minisql.regionserver.proto.ExistsRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.ExistsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getExistsMethod(), responseObserver);
    }

    /**
     * <pre>
     * 批量插入
     * </pre>
     */
    default void batchPut(com.minisql.regionserver.proto.BatchPutRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.BatchPutResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getBatchPutMethod(), responseObserver);
    }

    /**
     * <pre>
     * 批量查询
     * </pre>
     */
    default void batchGet(com.minisql.regionserver.proto.BatchGetRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.BatchGetResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getBatchGetMethod(), responseObserver);
    }

    /**
     * <pre>
     * 批量删除
     * </pre>
     */
    default void batchDelete(com.minisql.regionserver.proto.BatchDeleteRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.BatchDeleteResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getBatchDeleteMethod(), responseObserver);
    }

    /**
     * <pre>
     * 范围扫描（流式返回）
     * </pre>
     */
    default void scan(com.minisql.regionserver.proto.ScanRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.ScanResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getScanMethod(), responseObserver);
    }

    /**
     * <pre>
     * 通用查询操作（聚合、过滤等）
     * </pre>
     */
    default void query(com.minisql.regionserver.proto.QueryRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.QueryResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getQueryMethod(), responseObserver);
    }

    /**
     * <pre>
     * Master指令：打开Region
     * </pre>
     */
    default void openRegion(com.minisql.regionserver.proto.OpenRegionRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.OpenRegionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getOpenRegionMethod(), responseObserver);
    }

    /**
     * <pre>
     * Master指令：关闭Region
     * </pre>
     */
    default void closeRegion(com.minisql.regionserver.proto.CloseRegionRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.CloseRegionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCloseRegionMethod(), responseObserver);
    }

    /**
     * <pre>
     * Master指令：迁移Region
     * </pre>
     */
    default void migrateRegion(com.minisql.regionserver.proto.MigrateRegionRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.MigrateRegionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getMigrateRegionMethod(), responseObserver);
    }

    /**
     * <pre>
     * 副本同步：获取WAL日志
     * </pre>
     */
    default void getReplicationLog(com.minisql.regionserver.proto.GetReplicationLogRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.ReplicationLogEntry> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetReplicationLogMethod(), responseObserver);
    }

    /**
     * <pre>
     * 副本同步：应用WAL日志
     * </pre>
     */
    default void applyReplicationLog(com.minisql.regionserver.proto.ApplyReplicationLogRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.ApplyReplicationLogResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getApplyReplicationLogMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service RegionServerService.
   * <pre>
   * ========== 单行操作（显式方法）==========
   * </pre>
   */
  public static abstract class RegionServerServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return RegionServerServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service RegionServerService.
   * <pre>
   * ========== 单行操作（显式方法）==========
   * </pre>
   */
  public static final class RegionServerServiceStub
      extends io.grpc.stub.AbstractAsyncStub<RegionServerServiceStub> {
    private RegionServerServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RegionServerServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RegionServerServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * 插入单行数据
     * </pre>
     */
    public void put(com.minisql.regionserver.proto.PutRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.PutResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getPutMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 查询单行数据
     * </pre>
     */
    public void get(com.minisql.regionserver.proto.GetRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.GetResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 删除单行数据
     * </pre>
     */
    public void delete(com.minisql.regionserver.proto.DeleteRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.DeleteResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getDeleteMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 检查行是否存在
     * </pre>
     */
    public void exists(com.minisql.regionserver.proto.ExistsRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.ExistsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getExistsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 批量插入
     * </pre>
     */
    public void batchPut(com.minisql.regionserver.proto.BatchPutRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.BatchPutResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getBatchPutMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 批量查询
     * </pre>
     */
    public void batchGet(com.minisql.regionserver.proto.BatchGetRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.BatchGetResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getBatchGetMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 批量删除
     * </pre>
     */
    public void batchDelete(com.minisql.regionserver.proto.BatchDeleteRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.BatchDeleteResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getBatchDeleteMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 范围扫描（流式返回）
     * </pre>
     */
    public void scan(com.minisql.regionserver.proto.ScanRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.ScanResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getScanMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 通用查询操作（聚合、过滤等）
     * </pre>
     */
    public void query(com.minisql.regionserver.proto.QueryRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.QueryResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getQueryMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Master指令：打开Region
     * </pre>
     */
    public void openRegion(com.minisql.regionserver.proto.OpenRegionRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.OpenRegionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getOpenRegionMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Master指令：关闭Region
     * </pre>
     */
    public void closeRegion(com.minisql.regionserver.proto.CloseRegionRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.CloseRegionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCloseRegionMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Master指令：迁移Region
     * </pre>
     */
    public void migrateRegion(com.minisql.regionserver.proto.MigrateRegionRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.MigrateRegionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getMigrateRegionMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 副本同步：获取WAL日志
     * </pre>
     */
    public void getReplicationLog(com.minisql.regionserver.proto.GetReplicationLogRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.ReplicationLogEntry> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getGetReplicationLogMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 副本同步：应用WAL日志
     * </pre>
     */
    public void applyReplicationLog(com.minisql.regionserver.proto.ApplyReplicationLogRequest request,
        io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.ApplyReplicationLogResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getApplyReplicationLogMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service RegionServerService.
   * <pre>
   * ========== 单行操作（显式方法）==========
   * </pre>
   */
  public static final class RegionServerServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<RegionServerServiceBlockingStub> {
    private RegionServerServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RegionServerServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RegionServerServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * 插入单行数据
     * </pre>
     */
    public com.minisql.regionserver.proto.PutResponse put(com.minisql.regionserver.proto.PutRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPutMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 查询单行数据
     * </pre>
     */
    public com.minisql.regionserver.proto.GetResponse get(com.minisql.regionserver.proto.GetRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 删除单行数据
     * </pre>
     */
    public com.minisql.regionserver.proto.DeleteResponse delete(com.minisql.regionserver.proto.DeleteRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDeleteMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 检查行是否存在
     * </pre>
     */
    public com.minisql.regionserver.proto.ExistsResponse exists(com.minisql.regionserver.proto.ExistsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getExistsMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 批量插入
     * </pre>
     */
    public com.minisql.regionserver.proto.BatchPutResponse batchPut(com.minisql.regionserver.proto.BatchPutRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getBatchPutMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 批量查询
     * </pre>
     */
    public com.minisql.regionserver.proto.BatchGetResponse batchGet(com.minisql.regionserver.proto.BatchGetRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getBatchGetMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 批量删除
     * </pre>
     */
    public com.minisql.regionserver.proto.BatchDeleteResponse batchDelete(com.minisql.regionserver.proto.BatchDeleteRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getBatchDeleteMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 范围扫描（流式返回）
     * </pre>
     */
    public java.util.Iterator<com.minisql.regionserver.proto.ScanResponse> scan(
        com.minisql.regionserver.proto.ScanRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getScanMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 通用查询操作（聚合、过滤等）
     * </pre>
     */
    public com.minisql.regionserver.proto.QueryResponse query(com.minisql.regionserver.proto.QueryRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getQueryMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Master指令：打开Region
     * </pre>
     */
    public com.minisql.regionserver.proto.OpenRegionResponse openRegion(com.minisql.regionserver.proto.OpenRegionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getOpenRegionMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Master指令：关闭Region
     * </pre>
     */
    public com.minisql.regionserver.proto.CloseRegionResponse closeRegion(com.minisql.regionserver.proto.CloseRegionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCloseRegionMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Master指令：迁移Region
     * </pre>
     */
    public com.minisql.regionserver.proto.MigrateRegionResponse migrateRegion(com.minisql.regionserver.proto.MigrateRegionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getMigrateRegionMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 副本同步：获取WAL日志
     * </pre>
     */
    public java.util.Iterator<com.minisql.regionserver.proto.ReplicationLogEntry> getReplicationLog(
        com.minisql.regionserver.proto.GetReplicationLogRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getGetReplicationLogMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 副本同步：应用WAL日志
     * </pre>
     */
    public com.minisql.regionserver.proto.ApplyReplicationLogResponse applyReplicationLog(com.minisql.regionserver.proto.ApplyReplicationLogRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getApplyReplicationLogMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service RegionServerService.
   * <pre>
   * ========== 单行操作（显式方法）==========
   * </pre>
   */
  public static final class RegionServerServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<RegionServerServiceFutureStub> {
    private RegionServerServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RegionServerServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RegionServerServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * 插入单行数据
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.regionserver.proto.PutResponse> put(
        com.minisql.regionserver.proto.PutRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getPutMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 查询单行数据
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.regionserver.proto.GetResponse> get(
        com.minisql.regionserver.proto.GetRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 删除单行数据
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.regionserver.proto.DeleteResponse> delete(
        com.minisql.regionserver.proto.DeleteRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getDeleteMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 检查行是否存在
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.regionserver.proto.ExistsResponse> exists(
        com.minisql.regionserver.proto.ExistsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getExistsMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 批量插入
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.regionserver.proto.BatchPutResponse> batchPut(
        com.minisql.regionserver.proto.BatchPutRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getBatchPutMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 批量查询
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.regionserver.proto.BatchGetResponse> batchGet(
        com.minisql.regionserver.proto.BatchGetRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getBatchGetMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 批量删除
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.regionserver.proto.BatchDeleteResponse> batchDelete(
        com.minisql.regionserver.proto.BatchDeleteRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getBatchDeleteMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 通用查询操作（聚合、过滤等）
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.regionserver.proto.QueryResponse> query(
        com.minisql.regionserver.proto.QueryRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getQueryMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Master指令：打开Region
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.regionserver.proto.OpenRegionResponse> openRegion(
        com.minisql.regionserver.proto.OpenRegionRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getOpenRegionMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Master指令：关闭Region
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.regionserver.proto.CloseRegionResponse> closeRegion(
        com.minisql.regionserver.proto.CloseRegionRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCloseRegionMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Master指令：迁移Region
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.regionserver.proto.MigrateRegionResponse> migrateRegion(
        com.minisql.regionserver.proto.MigrateRegionRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getMigrateRegionMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 副本同步：应用WAL日志
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.regionserver.proto.ApplyReplicationLogResponse> applyReplicationLog(
        com.minisql.regionserver.proto.ApplyReplicationLogRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getApplyReplicationLogMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_PUT = 0;
  private static final int METHODID_GET = 1;
  private static final int METHODID_DELETE = 2;
  private static final int METHODID_EXISTS = 3;
  private static final int METHODID_BATCH_PUT = 4;
  private static final int METHODID_BATCH_GET = 5;
  private static final int METHODID_BATCH_DELETE = 6;
  private static final int METHODID_SCAN = 7;
  private static final int METHODID_QUERY = 8;
  private static final int METHODID_OPEN_REGION = 9;
  private static final int METHODID_CLOSE_REGION = 10;
  private static final int METHODID_MIGRATE_REGION = 11;
  private static final int METHODID_GET_REPLICATION_LOG = 12;
  private static final int METHODID_APPLY_REPLICATION_LOG = 13;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_PUT:
          serviceImpl.put((com.minisql.regionserver.proto.PutRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.PutResponse>) responseObserver);
          break;
        case METHODID_GET:
          serviceImpl.get((com.minisql.regionserver.proto.GetRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.GetResponse>) responseObserver);
          break;
        case METHODID_DELETE:
          serviceImpl.delete((com.minisql.regionserver.proto.DeleteRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.DeleteResponse>) responseObserver);
          break;
        case METHODID_EXISTS:
          serviceImpl.exists((com.minisql.regionserver.proto.ExistsRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.ExistsResponse>) responseObserver);
          break;
        case METHODID_BATCH_PUT:
          serviceImpl.batchPut((com.minisql.regionserver.proto.BatchPutRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.BatchPutResponse>) responseObserver);
          break;
        case METHODID_BATCH_GET:
          serviceImpl.batchGet((com.minisql.regionserver.proto.BatchGetRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.BatchGetResponse>) responseObserver);
          break;
        case METHODID_BATCH_DELETE:
          serviceImpl.batchDelete((com.minisql.regionserver.proto.BatchDeleteRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.BatchDeleteResponse>) responseObserver);
          break;
        case METHODID_SCAN:
          serviceImpl.scan((com.minisql.regionserver.proto.ScanRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.ScanResponse>) responseObserver);
          break;
        case METHODID_QUERY:
          serviceImpl.query((com.minisql.regionserver.proto.QueryRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.QueryResponse>) responseObserver);
          break;
        case METHODID_OPEN_REGION:
          serviceImpl.openRegion((com.minisql.regionserver.proto.OpenRegionRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.OpenRegionResponse>) responseObserver);
          break;
        case METHODID_CLOSE_REGION:
          serviceImpl.closeRegion((com.minisql.regionserver.proto.CloseRegionRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.CloseRegionResponse>) responseObserver);
          break;
        case METHODID_MIGRATE_REGION:
          serviceImpl.migrateRegion((com.minisql.regionserver.proto.MigrateRegionRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.MigrateRegionResponse>) responseObserver);
          break;
        case METHODID_GET_REPLICATION_LOG:
          serviceImpl.getReplicationLog((com.minisql.regionserver.proto.GetReplicationLogRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.ReplicationLogEntry>) responseObserver);
          break;
        case METHODID_APPLY_REPLICATION_LOG:
          serviceImpl.applyReplicationLog((com.minisql.regionserver.proto.ApplyReplicationLogRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.regionserver.proto.ApplyReplicationLogResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getPutMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.regionserver.proto.PutRequest,
              com.minisql.regionserver.proto.PutResponse>(
                service, METHODID_PUT)))
        .addMethod(
          getGetMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.regionserver.proto.GetRequest,
              com.minisql.regionserver.proto.GetResponse>(
                service, METHODID_GET)))
        .addMethod(
          getDeleteMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.regionserver.proto.DeleteRequest,
              com.minisql.regionserver.proto.DeleteResponse>(
                service, METHODID_DELETE)))
        .addMethod(
          getExistsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.regionserver.proto.ExistsRequest,
              com.minisql.regionserver.proto.ExistsResponse>(
                service, METHODID_EXISTS)))
        .addMethod(
          getBatchPutMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.regionserver.proto.BatchPutRequest,
              com.minisql.regionserver.proto.BatchPutResponse>(
                service, METHODID_BATCH_PUT)))
        .addMethod(
          getBatchGetMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.regionserver.proto.BatchGetRequest,
              com.minisql.regionserver.proto.BatchGetResponse>(
                service, METHODID_BATCH_GET)))
        .addMethod(
          getBatchDeleteMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.regionserver.proto.BatchDeleteRequest,
              com.minisql.regionserver.proto.BatchDeleteResponse>(
                service, METHODID_BATCH_DELETE)))
        .addMethod(
          getScanMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              com.minisql.regionserver.proto.ScanRequest,
              com.minisql.regionserver.proto.ScanResponse>(
                service, METHODID_SCAN)))
        .addMethod(
          getQueryMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.regionserver.proto.QueryRequest,
              com.minisql.regionserver.proto.QueryResponse>(
                service, METHODID_QUERY)))
        .addMethod(
          getOpenRegionMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.regionserver.proto.OpenRegionRequest,
              com.minisql.regionserver.proto.OpenRegionResponse>(
                service, METHODID_OPEN_REGION)))
        .addMethod(
          getCloseRegionMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.regionserver.proto.CloseRegionRequest,
              com.minisql.regionserver.proto.CloseRegionResponse>(
                service, METHODID_CLOSE_REGION)))
        .addMethod(
          getMigrateRegionMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.regionserver.proto.MigrateRegionRequest,
              com.minisql.regionserver.proto.MigrateRegionResponse>(
                service, METHODID_MIGRATE_REGION)))
        .addMethod(
          getGetReplicationLogMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              com.minisql.regionserver.proto.GetReplicationLogRequest,
              com.minisql.regionserver.proto.ReplicationLogEntry>(
                service, METHODID_GET_REPLICATION_LOG)))
        .addMethod(
          getApplyReplicationLogMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.regionserver.proto.ApplyReplicationLogRequest,
              com.minisql.regionserver.proto.ApplyReplicationLogResponse>(
                service, METHODID_APPLY_REPLICATION_LOG)))
        .build();
  }

  private static abstract class RegionServerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    RegionServerServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.minisql.regionserver.proto.RegionServerProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("RegionServerService");
    }
  }

  private static final class RegionServerServiceFileDescriptorSupplier
      extends RegionServerServiceBaseDescriptorSupplier {
    RegionServerServiceFileDescriptorSupplier() {}
  }

  private static final class RegionServerServiceMethodDescriptorSupplier
      extends RegionServerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    RegionServerServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (RegionServerServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new RegionServerServiceFileDescriptorSupplier())
              .addMethod(getPutMethod())
              .addMethod(getGetMethod())
              .addMethod(getDeleteMethod())
              .addMethod(getExistsMethod())
              .addMethod(getBatchPutMethod())
              .addMethod(getBatchGetMethod())
              .addMethod(getBatchDeleteMethod())
              .addMethod(getScanMethod())
              .addMethod(getQueryMethod())
              .addMethod(getOpenRegionMethod())
              .addMethod(getCloseRegionMethod())
              .addMethod(getMigrateRegionMethod())
              .addMethod(getGetReplicationLogMethod())
              .addMethod(getApplyReplicationLogMethod())
              .build();
        }
      }
    }
    return result;
  }
}
