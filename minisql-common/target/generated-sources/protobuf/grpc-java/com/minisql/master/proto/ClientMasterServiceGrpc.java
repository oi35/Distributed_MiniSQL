package com.minisql.master.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * ========== 表DDL操作（显式方法）==========
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.58.0)",
    comments = "Source: master.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ClientMasterServiceGrpc {

  private ClientMasterServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "minisql.master.ClientMasterService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.CreateTableRequest,
      com.minisql.master.proto.CreateTableResponse> getCreateTableMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CreateTable",
      requestType = com.minisql.master.proto.CreateTableRequest.class,
      responseType = com.minisql.master.proto.CreateTableResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.CreateTableRequest,
      com.minisql.master.proto.CreateTableResponse> getCreateTableMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.CreateTableRequest, com.minisql.master.proto.CreateTableResponse> getCreateTableMethod;
    if ((getCreateTableMethod = ClientMasterServiceGrpc.getCreateTableMethod) == null) {
      synchronized (ClientMasterServiceGrpc.class) {
        if ((getCreateTableMethod = ClientMasterServiceGrpc.getCreateTableMethod) == null) {
          ClientMasterServiceGrpc.getCreateTableMethod = getCreateTableMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.CreateTableRequest, com.minisql.master.proto.CreateTableResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreateTable"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.CreateTableRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.CreateTableResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ClientMasterServiceMethodDescriptorSupplier("CreateTable"))
              .build();
        }
      }
    }
    return getCreateTableMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.DropTableRequest,
      com.minisql.master.proto.DropTableResponse> getDropTableMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "DropTable",
      requestType = com.minisql.master.proto.DropTableRequest.class,
      responseType = com.minisql.master.proto.DropTableResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.DropTableRequest,
      com.minisql.master.proto.DropTableResponse> getDropTableMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.DropTableRequest, com.minisql.master.proto.DropTableResponse> getDropTableMethod;
    if ((getDropTableMethod = ClientMasterServiceGrpc.getDropTableMethod) == null) {
      synchronized (ClientMasterServiceGrpc.class) {
        if ((getDropTableMethod = ClientMasterServiceGrpc.getDropTableMethod) == null) {
          ClientMasterServiceGrpc.getDropTableMethod = getDropTableMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.DropTableRequest, com.minisql.master.proto.DropTableResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DropTable"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.DropTableRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.DropTableResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ClientMasterServiceMethodDescriptorSupplier("DropTable"))
              .build();
        }
      }
    }
    return getDropTableMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.GetTableSchemaRequest,
      com.minisql.master.proto.GetTableSchemaResponse> getGetTableSchemaMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetTableSchema",
      requestType = com.minisql.master.proto.GetTableSchemaRequest.class,
      responseType = com.minisql.master.proto.GetTableSchemaResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.GetTableSchemaRequest,
      com.minisql.master.proto.GetTableSchemaResponse> getGetTableSchemaMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.GetTableSchemaRequest, com.minisql.master.proto.GetTableSchemaResponse> getGetTableSchemaMethod;
    if ((getGetTableSchemaMethod = ClientMasterServiceGrpc.getGetTableSchemaMethod) == null) {
      synchronized (ClientMasterServiceGrpc.class) {
        if ((getGetTableSchemaMethod = ClientMasterServiceGrpc.getGetTableSchemaMethod) == null) {
          ClientMasterServiceGrpc.getGetTableSchemaMethod = getGetTableSchemaMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.GetTableSchemaRequest, com.minisql.master.proto.GetTableSchemaResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetTableSchema"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.GetTableSchemaRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.GetTableSchemaResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ClientMasterServiceMethodDescriptorSupplier("GetTableSchema"))
              .build();
        }
      }
    }
    return getGetTableSchemaMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.ListTablesRequest,
      com.minisql.master.proto.ListTablesResponse> getListTablesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListTables",
      requestType = com.minisql.master.proto.ListTablesRequest.class,
      responseType = com.minisql.master.proto.ListTablesResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.ListTablesRequest,
      com.minisql.master.proto.ListTablesResponse> getListTablesMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.ListTablesRequest, com.minisql.master.proto.ListTablesResponse> getListTablesMethod;
    if ((getListTablesMethod = ClientMasterServiceGrpc.getListTablesMethod) == null) {
      synchronized (ClientMasterServiceGrpc.class) {
        if ((getListTablesMethod = ClientMasterServiceGrpc.getListTablesMethod) == null) {
          ClientMasterServiceGrpc.getListTablesMethod = getListTablesMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.ListTablesRequest, com.minisql.master.proto.ListTablesResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListTables"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.ListTablesRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.ListTablesResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ClientMasterServiceMethodDescriptorSupplier("ListTables"))
              .build();
        }
      }
    }
    return getListTablesMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.GetRouteTableRequest,
      com.minisql.master.proto.GetRouteTableResponse> getGetRouteTableMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetRouteTable",
      requestType = com.minisql.master.proto.GetRouteTableRequest.class,
      responseType = com.minisql.master.proto.GetRouteTableResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.GetRouteTableRequest,
      com.minisql.master.proto.GetRouteTableResponse> getGetRouteTableMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.GetRouteTableRequest, com.minisql.master.proto.GetRouteTableResponse> getGetRouteTableMethod;
    if ((getGetRouteTableMethod = ClientMasterServiceGrpc.getGetRouteTableMethod) == null) {
      synchronized (ClientMasterServiceGrpc.class) {
        if ((getGetRouteTableMethod = ClientMasterServiceGrpc.getGetRouteTableMethod) == null) {
          ClientMasterServiceGrpc.getGetRouteTableMethod = getGetRouteTableMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.GetRouteTableRequest, com.minisql.master.proto.GetRouteTableResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetRouteTable"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.GetRouteTableRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.GetRouteTableResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ClientMasterServiceMethodDescriptorSupplier("GetRouteTable"))
              .build();
        }
      }
    }
    return getGetRouteTableMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.GetRouteForKeyRequest,
      com.minisql.master.proto.GetRouteForKeyResponse> getGetRouteForKeyMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetRouteForKey",
      requestType = com.minisql.master.proto.GetRouteForKeyRequest.class,
      responseType = com.minisql.master.proto.GetRouteForKeyResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.GetRouteForKeyRequest,
      com.minisql.master.proto.GetRouteForKeyResponse> getGetRouteForKeyMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.GetRouteForKeyRequest, com.minisql.master.proto.GetRouteForKeyResponse> getGetRouteForKeyMethod;
    if ((getGetRouteForKeyMethod = ClientMasterServiceGrpc.getGetRouteForKeyMethod) == null) {
      synchronized (ClientMasterServiceGrpc.class) {
        if ((getGetRouteForKeyMethod = ClientMasterServiceGrpc.getGetRouteForKeyMethod) == null) {
          ClientMasterServiceGrpc.getGetRouteForKeyMethod = getGetRouteForKeyMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.GetRouteForKeyRequest, com.minisql.master.proto.GetRouteForKeyResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetRouteForKey"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.GetRouteForKeyRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.GetRouteForKeyResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ClientMasterServiceMethodDescriptorSupplier("GetRouteForKey"))
              .build();
        }
      }
    }
    return getGetRouteForKeyMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.GetRoutesForRangeRequest,
      com.minisql.master.proto.GetRoutesForRangeResponse> getGetRoutesForRangeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetRoutesForRange",
      requestType = com.minisql.master.proto.GetRoutesForRangeRequest.class,
      responseType = com.minisql.master.proto.GetRoutesForRangeResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.GetRoutesForRangeRequest,
      com.minisql.master.proto.GetRoutesForRangeResponse> getGetRoutesForRangeMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.GetRoutesForRangeRequest, com.minisql.master.proto.GetRoutesForRangeResponse> getGetRoutesForRangeMethod;
    if ((getGetRoutesForRangeMethod = ClientMasterServiceGrpc.getGetRoutesForRangeMethod) == null) {
      synchronized (ClientMasterServiceGrpc.class) {
        if ((getGetRoutesForRangeMethod = ClientMasterServiceGrpc.getGetRoutesForRangeMethod) == null) {
          ClientMasterServiceGrpc.getGetRoutesForRangeMethod = getGetRoutesForRangeMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.GetRoutesForRangeRequest, com.minisql.master.proto.GetRoutesForRangeResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetRoutesForRange"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.GetRoutesForRangeRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.GetRoutesForRangeResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ClientMasterServiceMethodDescriptorSupplier("GetRoutesForRange"))
              .build();
        }
      }
    }
    return getGetRoutesForRangeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.ReportStaleRouteRequest,
      com.minisql.master.proto.ReportStaleRouteResponse> getReportStaleRouteMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ReportStaleRoute",
      requestType = com.minisql.master.proto.ReportStaleRouteRequest.class,
      responseType = com.minisql.master.proto.ReportStaleRouteResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.ReportStaleRouteRequest,
      com.minisql.master.proto.ReportStaleRouteResponse> getReportStaleRouteMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.ReportStaleRouteRequest, com.minisql.master.proto.ReportStaleRouteResponse> getReportStaleRouteMethod;
    if ((getReportStaleRouteMethod = ClientMasterServiceGrpc.getReportStaleRouteMethod) == null) {
      synchronized (ClientMasterServiceGrpc.class) {
        if ((getReportStaleRouteMethod = ClientMasterServiceGrpc.getReportStaleRouteMethod) == null) {
          ClientMasterServiceGrpc.getReportStaleRouteMethod = getReportStaleRouteMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.ReportStaleRouteRequest, com.minisql.master.proto.ReportStaleRouteResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ReportStaleRoute"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.ReportStaleRouteRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.ReportStaleRouteResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ClientMasterServiceMethodDescriptorSupplier("ReportStaleRoute"))
              .build();
        }
      }
    }
    return getReportStaleRouteMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.GetClusterHealthRequest,
      com.minisql.master.proto.GetClusterHealthResponse> getGetClusterHealthMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetClusterHealth",
      requestType = com.minisql.master.proto.GetClusterHealthRequest.class,
      responseType = com.minisql.master.proto.GetClusterHealthResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.GetClusterHealthRequest,
      com.minisql.master.proto.GetClusterHealthResponse> getGetClusterHealthMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.GetClusterHealthRequest, com.minisql.master.proto.GetClusterHealthResponse> getGetClusterHealthMethod;
    if ((getGetClusterHealthMethod = ClientMasterServiceGrpc.getGetClusterHealthMethod) == null) {
      synchronized (ClientMasterServiceGrpc.class) {
        if ((getGetClusterHealthMethod = ClientMasterServiceGrpc.getGetClusterHealthMethod) == null) {
          ClientMasterServiceGrpc.getGetClusterHealthMethod = getGetClusterHealthMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.GetClusterHealthRequest, com.minisql.master.proto.GetClusterHealthResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetClusterHealth"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.GetClusterHealthRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.GetClusterHealthResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ClientMasterServiceMethodDescriptorSupplier("GetClusterHealth"))
              .build();
        }
      }
    }
    return getGetClusterHealthMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.GetClusterStatsRequest,
      com.minisql.master.proto.GetClusterStatsResponse> getGetClusterStatsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetClusterStats",
      requestType = com.minisql.master.proto.GetClusterStatsRequest.class,
      responseType = com.minisql.master.proto.GetClusterStatsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.GetClusterStatsRequest,
      com.minisql.master.proto.GetClusterStatsResponse> getGetClusterStatsMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.GetClusterStatsRequest, com.minisql.master.proto.GetClusterStatsResponse> getGetClusterStatsMethod;
    if ((getGetClusterStatsMethod = ClientMasterServiceGrpc.getGetClusterStatsMethod) == null) {
      synchronized (ClientMasterServiceGrpc.class) {
        if ((getGetClusterStatsMethod = ClientMasterServiceGrpc.getGetClusterStatsMethod) == null) {
          ClientMasterServiceGrpc.getGetClusterStatsMethod = getGetClusterStatsMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.GetClusterStatsRequest, com.minisql.master.proto.GetClusterStatsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetClusterStats"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.GetClusterStatsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.GetClusterStatsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ClientMasterServiceMethodDescriptorSupplier("GetClusterStats"))
              .build();
        }
      }
    }
    return getGetClusterStatsMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ClientMasterServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ClientMasterServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ClientMasterServiceStub>() {
        @java.lang.Override
        public ClientMasterServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ClientMasterServiceStub(channel, callOptions);
        }
      };
    return ClientMasterServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ClientMasterServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ClientMasterServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ClientMasterServiceBlockingStub>() {
        @java.lang.Override
        public ClientMasterServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ClientMasterServiceBlockingStub(channel, callOptions);
        }
      };
    return ClientMasterServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ClientMasterServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ClientMasterServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ClientMasterServiceFutureStub>() {
        @java.lang.Override
        public ClientMasterServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ClientMasterServiceFutureStub(channel, callOptions);
        }
      };
    return ClientMasterServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * ========== 表DDL操作（显式方法）==========
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * 创建新表
     * </pre>
     */
    default void createTable(com.minisql.master.proto.CreateTableRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.CreateTableResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCreateTableMethod(), responseObserver);
    }

    /**
     * <pre>
     * 删除表
     * </pre>
     */
    default void dropTable(com.minisql.master.proto.DropTableRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.DropTableResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDropTableMethod(), responseObserver);
    }

    /**
     * <pre>
     * 获取表结构
     * </pre>
     */
    default void getTableSchema(com.minisql.master.proto.GetTableSchemaRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.GetTableSchemaResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetTableSchemaMethod(), responseObserver);
    }

    /**
     * <pre>
     * 列出所有表
     * </pre>
     */
    default void listTables(com.minisql.master.proto.ListTablesRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.ListTablesResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListTablesMethod(), responseObserver);
    }

    /**
     * <pre>
     * 获取表的完整路由表
     * </pre>
     */
    default void getRouteTable(com.minisql.master.proto.GetRouteTableRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.GetRouteTableResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetRouteTableMethod(), responseObserver);
    }

    /**
     * <pre>
     * 获取指定键的路由信息
     * </pre>
     */
    default void getRouteForKey(com.minisql.master.proto.GetRouteForKeyRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.GetRouteForKeyResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetRouteForKeyMethod(), responseObserver);
    }

    /**
     * <pre>
     * 获取键范围的路由信息
     * </pre>
     */
    default void getRoutesForRange(com.minisql.master.proto.GetRoutesForRangeRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.GetRoutesForRangeResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetRoutesForRangeMethod(), responseObserver);
    }

    /**
     * <pre>
     * 客户端报告路由过期
     * </pre>
     */
    default void reportStaleRoute(com.minisql.master.proto.ReportStaleRouteRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportStaleRouteResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReportStaleRouteMethod(), responseObserver);
    }

    /**
     * <pre>
     * 获取集群健康状态
     * </pre>
     */
    default void getClusterHealth(com.minisql.master.proto.GetClusterHealthRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.GetClusterHealthResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetClusterHealthMethod(), responseObserver);
    }

    /**
     * <pre>
     * 获取集群统计信息
     * </pre>
     */
    default void getClusterStats(com.minisql.master.proto.GetClusterStatsRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.GetClusterStatsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetClusterStatsMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ClientMasterService.
   * <pre>
   * ========== 表DDL操作（显式方法）==========
   * </pre>
   */
  public static abstract class ClientMasterServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ClientMasterServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ClientMasterService.
   * <pre>
   * ========== 表DDL操作（显式方法）==========
   * </pre>
   */
  public static final class ClientMasterServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ClientMasterServiceStub> {
    private ClientMasterServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ClientMasterServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ClientMasterServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * 创建新表
     * </pre>
     */
    public void createTable(com.minisql.master.proto.CreateTableRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.CreateTableResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCreateTableMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 删除表
     * </pre>
     */
    public void dropTable(com.minisql.master.proto.DropTableRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.DropTableResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getDropTableMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 获取表结构
     * </pre>
     */
    public void getTableSchema(com.minisql.master.proto.GetTableSchemaRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.GetTableSchemaResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetTableSchemaMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 列出所有表
     * </pre>
     */
    public void listTables(com.minisql.master.proto.ListTablesRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.ListTablesResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListTablesMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 获取表的完整路由表
     * </pre>
     */
    public void getRouteTable(com.minisql.master.proto.GetRouteTableRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.GetRouteTableResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetRouteTableMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 获取指定键的路由信息
     * </pre>
     */
    public void getRouteForKey(com.minisql.master.proto.GetRouteForKeyRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.GetRouteForKeyResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetRouteForKeyMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 获取键范围的路由信息
     * </pre>
     */
    public void getRoutesForRange(com.minisql.master.proto.GetRoutesForRangeRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.GetRoutesForRangeResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetRoutesForRangeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 客户端报告路由过期
     * </pre>
     */
    public void reportStaleRoute(com.minisql.master.proto.ReportStaleRouteRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportStaleRouteResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getReportStaleRouteMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 获取集群健康状态
     * </pre>
     */
    public void getClusterHealth(com.minisql.master.proto.GetClusterHealthRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.GetClusterHealthResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetClusterHealthMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 获取集群统计信息
     * </pre>
     */
    public void getClusterStats(com.minisql.master.proto.GetClusterStatsRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.GetClusterStatsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetClusterStatsMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ClientMasterService.
   * <pre>
   * ========== 表DDL操作（显式方法）==========
   * </pre>
   */
  public static final class ClientMasterServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ClientMasterServiceBlockingStub> {
    private ClientMasterServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ClientMasterServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ClientMasterServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * 创建新表
     * </pre>
     */
    public com.minisql.master.proto.CreateTableResponse createTable(com.minisql.master.proto.CreateTableRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreateTableMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 删除表
     * </pre>
     */
    public com.minisql.master.proto.DropTableResponse dropTable(com.minisql.master.proto.DropTableRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDropTableMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 获取表结构
     * </pre>
     */
    public com.minisql.master.proto.GetTableSchemaResponse getTableSchema(com.minisql.master.proto.GetTableSchemaRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetTableSchemaMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 列出所有表
     * </pre>
     */
    public com.minisql.master.proto.ListTablesResponse listTables(com.minisql.master.proto.ListTablesRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListTablesMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 获取表的完整路由表
     * </pre>
     */
    public com.minisql.master.proto.GetRouteTableResponse getRouteTable(com.minisql.master.proto.GetRouteTableRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetRouteTableMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 获取指定键的路由信息
     * </pre>
     */
    public com.minisql.master.proto.GetRouteForKeyResponse getRouteForKey(com.minisql.master.proto.GetRouteForKeyRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetRouteForKeyMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 获取键范围的路由信息
     * </pre>
     */
    public com.minisql.master.proto.GetRoutesForRangeResponse getRoutesForRange(com.minisql.master.proto.GetRoutesForRangeRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetRoutesForRangeMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 客户端报告路由过期
     * </pre>
     */
    public com.minisql.master.proto.ReportStaleRouteResponse reportStaleRoute(com.minisql.master.proto.ReportStaleRouteRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReportStaleRouteMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 获取集群健康状态
     * </pre>
     */
    public com.minisql.master.proto.GetClusterHealthResponse getClusterHealth(com.minisql.master.proto.GetClusterHealthRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetClusterHealthMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 获取集群统计信息
     * </pre>
     */
    public com.minisql.master.proto.GetClusterStatsResponse getClusterStats(com.minisql.master.proto.GetClusterStatsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetClusterStatsMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ClientMasterService.
   * <pre>
   * ========== 表DDL操作（显式方法）==========
   * </pre>
   */
  public static final class ClientMasterServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ClientMasterServiceFutureStub> {
    private ClientMasterServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ClientMasterServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ClientMasterServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * 创建新表
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.CreateTableResponse> createTable(
        com.minisql.master.proto.CreateTableRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCreateTableMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 删除表
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.DropTableResponse> dropTable(
        com.minisql.master.proto.DropTableRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getDropTableMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 获取表结构
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.GetTableSchemaResponse> getTableSchema(
        com.minisql.master.proto.GetTableSchemaRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetTableSchemaMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 列出所有表
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.ListTablesResponse> listTables(
        com.minisql.master.proto.ListTablesRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListTablesMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 获取表的完整路由表
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.GetRouteTableResponse> getRouteTable(
        com.minisql.master.proto.GetRouteTableRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetRouteTableMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 获取指定键的路由信息
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.GetRouteForKeyResponse> getRouteForKey(
        com.minisql.master.proto.GetRouteForKeyRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetRouteForKeyMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 获取键范围的路由信息
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.GetRoutesForRangeResponse> getRoutesForRange(
        com.minisql.master.proto.GetRoutesForRangeRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetRoutesForRangeMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 客户端报告路由过期
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.ReportStaleRouteResponse> reportStaleRoute(
        com.minisql.master.proto.ReportStaleRouteRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getReportStaleRouteMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 获取集群健康状态
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.GetClusterHealthResponse> getClusterHealth(
        com.minisql.master.proto.GetClusterHealthRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetClusterHealthMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 获取集群统计信息
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.GetClusterStatsResponse> getClusterStats(
        com.minisql.master.proto.GetClusterStatsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetClusterStatsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CREATE_TABLE = 0;
  private static final int METHODID_DROP_TABLE = 1;
  private static final int METHODID_GET_TABLE_SCHEMA = 2;
  private static final int METHODID_LIST_TABLES = 3;
  private static final int METHODID_GET_ROUTE_TABLE = 4;
  private static final int METHODID_GET_ROUTE_FOR_KEY = 5;
  private static final int METHODID_GET_ROUTES_FOR_RANGE = 6;
  private static final int METHODID_REPORT_STALE_ROUTE = 7;
  private static final int METHODID_GET_CLUSTER_HEALTH = 8;
  private static final int METHODID_GET_CLUSTER_STATS = 9;

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
        case METHODID_CREATE_TABLE:
          serviceImpl.createTable((com.minisql.master.proto.CreateTableRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.CreateTableResponse>) responseObserver);
          break;
        case METHODID_DROP_TABLE:
          serviceImpl.dropTable((com.minisql.master.proto.DropTableRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.DropTableResponse>) responseObserver);
          break;
        case METHODID_GET_TABLE_SCHEMA:
          serviceImpl.getTableSchema((com.minisql.master.proto.GetTableSchemaRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.GetTableSchemaResponse>) responseObserver);
          break;
        case METHODID_LIST_TABLES:
          serviceImpl.listTables((com.minisql.master.proto.ListTablesRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.ListTablesResponse>) responseObserver);
          break;
        case METHODID_GET_ROUTE_TABLE:
          serviceImpl.getRouteTable((com.minisql.master.proto.GetRouteTableRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.GetRouteTableResponse>) responseObserver);
          break;
        case METHODID_GET_ROUTE_FOR_KEY:
          serviceImpl.getRouteForKey((com.minisql.master.proto.GetRouteForKeyRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.GetRouteForKeyResponse>) responseObserver);
          break;
        case METHODID_GET_ROUTES_FOR_RANGE:
          serviceImpl.getRoutesForRange((com.minisql.master.proto.GetRoutesForRangeRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.GetRoutesForRangeResponse>) responseObserver);
          break;
        case METHODID_REPORT_STALE_ROUTE:
          serviceImpl.reportStaleRoute((com.minisql.master.proto.ReportStaleRouteRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportStaleRouteResponse>) responseObserver);
          break;
        case METHODID_GET_CLUSTER_HEALTH:
          serviceImpl.getClusterHealth((com.minisql.master.proto.GetClusterHealthRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.GetClusterHealthResponse>) responseObserver);
          break;
        case METHODID_GET_CLUSTER_STATS:
          serviceImpl.getClusterStats((com.minisql.master.proto.GetClusterStatsRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.GetClusterStatsResponse>) responseObserver);
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
          getCreateTableMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.CreateTableRequest,
              com.minisql.master.proto.CreateTableResponse>(
                service, METHODID_CREATE_TABLE)))
        .addMethod(
          getDropTableMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.DropTableRequest,
              com.minisql.master.proto.DropTableResponse>(
                service, METHODID_DROP_TABLE)))
        .addMethod(
          getGetTableSchemaMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.GetTableSchemaRequest,
              com.minisql.master.proto.GetTableSchemaResponse>(
                service, METHODID_GET_TABLE_SCHEMA)))
        .addMethod(
          getListTablesMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.ListTablesRequest,
              com.minisql.master.proto.ListTablesResponse>(
                service, METHODID_LIST_TABLES)))
        .addMethod(
          getGetRouteTableMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.GetRouteTableRequest,
              com.minisql.master.proto.GetRouteTableResponse>(
                service, METHODID_GET_ROUTE_TABLE)))
        .addMethod(
          getGetRouteForKeyMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.GetRouteForKeyRequest,
              com.minisql.master.proto.GetRouteForKeyResponse>(
                service, METHODID_GET_ROUTE_FOR_KEY)))
        .addMethod(
          getGetRoutesForRangeMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.GetRoutesForRangeRequest,
              com.minisql.master.proto.GetRoutesForRangeResponse>(
                service, METHODID_GET_ROUTES_FOR_RANGE)))
        .addMethod(
          getReportStaleRouteMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.ReportStaleRouteRequest,
              com.minisql.master.proto.ReportStaleRouteResponse>(
                service, METHODID_REPORT_STALE_ROUTE)))
        .addMethod(
          getGetClusterHealthMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.GetClusterHealthRequest,
              com.minisql.master.proto.GetClusterHealthResponse>(
                service, METHODID_GET_CLUSTER_HEALTH)))
        .addMethod(
          getGetClusterStatsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.GetClusterStatsRequest,
              com.minisql.master.proto.GetClusterStatsResponse>(
                service, METHODID_GET_CLUSTER_STATS)))
        .build();
  }

  private static abstract class ClientMasterServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ClientMasterServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.minisql.master.proto.MasterProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ClientMasterService");
    }
  }

  private static final class ClientMasterServiceFileDescriptorSupplier
      extends ClientMasterServiceBaseDescriptorSupplier {
    ClientMasterServiceFileDescriptorSupplier() {}
  }

  private static final class ClientMasterServiceMethodDescriptorSupplier
      extends ClientMasterServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ClientMasterServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (ClientMasterServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ClientMasterServiceFileDescriptorSupplier())
              .addMethod(getCreateTableMethod())
              .addMethod(getDropTableMethod())
              .addMethod(getGetTableSchemaMethod())
              .addMethod(getListTablesMethod())
              .addMethod(getGetRouteTableMethod())
              .addMethod(getGetRouteForKeyMethod())
              .addMethod(getGetRoutesForRangeMethod())
              .addMethod(getReportStaleRouteMethod())
              .addMethod(getGetClusterHealthMethod())
              .addMethod(getGetClusterStatsMethod())
              .build();
        }
      }
    }
    return result;
  }
}
