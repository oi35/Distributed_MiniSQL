package com.minisql.master.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * ========== RegionServer生命周期管理（显式方法）==========
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.58.0)",
    comments = "Source: master.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class MasterServiceGrpc {

  private MasterServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "minisql.master.MasterService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.RegisterRegionServerRequest,
      com.minisql.master.proto.RegisterRegionServerResponse> getRegisterRegionServerMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RegisterRegionServer",
      requestType = com.minisql.master.proto.RegisterRegionServerRequest.class,
      responseType = com.minisql.master.proto.RegisterRegionServerResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.RegisterRegionServerRequest,
      com.minisql.master.proto.RegisterRegionServerResponse> getRegisterRegionServerMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.RegisterRegionServerRequest, com.minisql.master.proto.RegisterRegionServerResponse> getRegisterRegionServerMethod;
    if ((getRegisterRegionServerMethod = MasterServiceGrpc.getRegisterRegionServerMethod) == null) {
      synchronized (MasterServiceGrpc.class) {
        if ((getRegisterRegionServerMethod = MasterServiceGrpc.getRegisterRegionServerMethod) == null) {
          MasterServiceGrpc.getRegisterRegionServerMethod = getRegisterRegionServerMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.RegisterRegionServerRequest, com.minisql.master.proto.RegisterRegionServerResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RegisterRegionServer"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.RegisterRegionServerRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.RegisterRegionServerResponse.getDefaultInstance()))
              .setSchemaDescriptor(new MasterServiceMethodDescriptorSupplier("RegisterRegionServer"))
              .build();
        }
      }
    }
    return getRegisterRegionServerMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.HeartbeatRequest,
      com.minisql.master.proto.HeartbeatResponse> getSendHeartbeatMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SendHeartbeat",
      requestType = com.minisql.master.proto.HeartbeatRequest.class,
      responseType = com.minisql.master.proto.HeartbeatResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.HeartbeatRequest,
      com.minisql.master.proto.HeartbeatResponse> getSendHeartbeatMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.HeartbeatRequest, com.minisql.master.proto.HeartbeatResponse> getSendHeartbeatMethod;
    if ((getSendHeartbeatMethod = MasterServiceGrpc.getSendHeartbeatMethod) == null) {
      synchronized (MasterServiceGrpc.class) {
        if ((getSendHeartbeatMethod = MasterServiceGrpc.getSendHeartbeatMethod) == null) {
          MasterServiceGrpc.getSendHeartbeatMethod = getSendHeartbeatMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.HeartbeatRequest, com.minisql.master.proto.HeartbeatResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SendHeartbeat"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.HeartbeatRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.HeartbeatResponse.getDefaultInstance()))
              .setSchemaDescriptor(new MasterServiceMethodDescriptorSupplier("SendHeartbeat"))
              .build();
        }
      }
    }
    return getSendHeartbeatMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.UnregisterRegionServerRequest,
      com.minisql.master.proto.UnregisterRegionServerResponse> getUnregisterRegionServerMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "UnregisterRegionServer",
      requestType = com.minisql.master.proto.UnregisterRegionServerRequest.class,
      responseType = com.minisql.master.proto.UnregisterRegionServerResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.UnregisterRegionServerRequest,
      com.minisql.master.proto.UnregisterRegionServerResponse> getUnregisterRegionServerMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.UnregisterRegionServerRequest, com.minisql.master.proto.UnregisterRegionServerResponse> getUnregisterRegionServerMethod;
    if ((getUnregisterRegionServerMethod = MasterServiceGrpc.getUnregisterRegionServerMethod) == null) {
      synchronized (MasterServiceGrpc.class) {
        if ((getUnregisterRegionServerMethod = MasterServiceGrpc.getUnregisterRegionServerMethod) == null) {
          MasterServiceGrpc.getUnregisterRegionServerMethod = getUnregisterRegionServerMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.UnregisterRegionServerRequest, com.minisql.master.proto.UnregisterRegionServerResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "UnregisterRegionServer"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.UnregisterRegionServerRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.UnregisterRegionServerResponse.getDefaultInstance()))
              .setSchemaDescriptor(new MasterServiceMethodDescriptorSupplier("UnregisterRegionServer"))
              .build();
        }
      }
    }
    return getUnregisterRegionServerMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.ReportRegionOnlineRequest,
      com.minisql.master.proto.ReportRegionOnlineResponse> getReportRegionOnlineMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ReportRegionOnline",
      requestType = com.minisql.master.proto.ReportRegionOnlineRequest.class,
      responseType = com.minisql.master.proto.ReportRegionOnlineResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.ReportRegionOnlineRequest,
      com.minisql.master.proto.ReportRegionOnlineResponse> getReportRegionOnlineMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.ReportRegionOnlineRequest, com.minisql.master.proto.ReportRegionOnlineResponse> getReportRegionOnlineMethod;
    if ((getReportRegionOnlineMethod = MasterServiceGrpc.getReportRegionOnlineMethod) == null) {
      synchronized (MasterServiceGrpc.class) {
        if ((getReportRegionOnlineMethod = MasterServiceGrpc.getReportRegionOnlineMethod) == null) {
          MasterServiceGrpc.getReportRegionOnlineMethod = getReportRegionOnlineMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.ReportRegionOnlineRequest, com.minisql.master.proto.ReportRegionOnlineResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ReportRegionOnline"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.ReportRegionOnlineRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.ReportRegionOnlineResponse.getDefaultInstance()))
              .setSchemaDescriptor(new MasterServiceMethodDescriptorSupplier("ReportRegionOnline"))
              .build();
        }
      }
    }
    return getReportRegionOnlineMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.ReportRegionClosedRequest,
      com.minisql.master.proto.ReportRegionClosedResponse> getReportRegionClosedMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ReportRegionClosed",
      requestType = com.minisql.master.proto.ReportRegionClosedRequest.class,
      responseType = com.minisql.master.proto.ReportRegionClosedResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.ReportRegionClosedRequest,
      com.minisql.master.proto.ReportRegionClosedResponse> getReportRegionClosedMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.ReportRegionClosedRequest, com.minisql.master.proto.ReportRegionClosedResponse> getReportRegionClosedMethod;
    if ((getReportRegionClosedMethod = MasterServiceGrpc.getReportRegionClosedMethod) == null) {
      synchronized (MasterServiceGrpc.class) {
        if ((getReportRegionClosedMethod = MasterServiceGrpc.getReportRegionClosedMethod) == null) {
          MasterServiceGrpc.getReportRegionClosedMethod = getReportRegionClosedMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.ReportRegionClosedRequest, com.minisql.master.proto.ReportRegionClosedResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ReportRegionClosed"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.ReportRegionClosedRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.ReportRegionClosedResponse.getDefaultInstance()))
              .setSchemaDescriptor(new MasterServiceMethodDescriptorSupplier("ReportRegionClosed"))
              .build();
        }
      }
    }
    return getReportRegionClosedMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.ReportRegionSplitRequest,
      com.minisql.master.proto.ReportRegionSplitResponse> getReportRegionSplitMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ReportRegionSplit",
      requestType = com.minisql.master.proto.ReportRegionSplitRequest.class,
      responseType = com.minisql.master.proto.ReportRegionSplitResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.ReportRegionSplitRequest,
      com.minisql.master.proto.ReportRegionSplitResponse> getReportRegionSplitMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.ReportRegionSplitRequest, com.minisql.master.proto.ReportRegionSplitResponse> getReportRegionSplitMethod;
    if ((getReportRegionSplitMethod = MasterServiceGrpc.getReportRegionSplitMethod) == null) {
      synchronized (MasterServiceGrpc.class) {
        if ((getReportRegionSplitMethod = MasterServiceGrpc.getReportRegionSplitMethod) == null) {
          MasterServiceGrpc.getReportRegionSplitMethod = getReportRegionSplitMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.ReportRegionSplitRequest, com.minisql.master.proto.ReportRegionSplitResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ReportRegionSplit"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.ReportRegionSplitRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.ReportRegionSplitResponse.getDefaultInstance()))
              .setSchemaDescriptor(new MasterServiceMethodDescriptorSupplier("ReportRegionSplit"))
              .build();
        }
      }
    }
    return getReportRegionSplitMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.ReportMigrationProgressRequest,
      com.minisql.master.proto.ReportMigrationProgressResponse> getReportMigrationProgressMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ReportMigrationProgress",
      requestType = com.minisql.master.proto.ReportMigrationProgressRequest.class,
      responseType = com.minisql.master.proto.ReportMigrationProgressResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.ReportMigrationProgressRequest,
      com.minisql.master.proto.ReportMigrationProgressResponse> getReportMigrationProgressMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.ReportMigrationProgressRequest, com.minisql.master.proto.ReportMigrationProgressResponse> getReportMigrationProgressMethod;
    if ((getReportMigrationProgressMethod = MasterServiceGrpc.getReportMigrationProgressMethod) == null) {
      synchronized (MasterServiceGrpc.class) {
        if ((getReportMigrationProgressMethod = MasterServiceGrpc.getReportMigrationProgressMethod) == null) {
          MasterServiceGrpc.getReportMigrationProgressMethod = getReportMigrationProgressMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.ReportMigrationProgressRequest, com.minisql.master.proto.ReportMigrationProgressResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ReportMigrationProgress"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.ReportMigrationProgressRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.ReportMigrationProgressResponse.getDefaultInstance()))
              .setSchemaDescriptor(new MasterServiceMethodDescriptorSupplier("ReportMigrationProgress"))
              .build();
        }
      }
    }
    return getReportMigrationProgressMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.ReportRegionFailureRequest,
      com.minisql.master.proto.ReportRegionFailureResponse> getReportRegionFailureMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ReportRegionFailure",
      requestType = com.minisql.master.proto.ReportRegionFailureRequest.class,
      responseType = com.minisql.master.proto.ReportRegionFailureResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.ReportRegionFailureRequest,
      com.minisql.master.proto.ReportRegionFailureResponse> getReportRegionFailureMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.ReportRegionFailureRequest, com.minisql.master.proto.ReportRegionFailureResponse> getReportRegionFailureMethod;
    if ((getReportRegionFailureMethod = MasterServiceGrpc.getReportRegionFailureMethod) == null) {
      synchronized (MasterServiceGrpc.class) {
        if ((getReportRegionFailureMethod = MasterServiceGrpc.getReportRegionFailureMethod) == null) {
          MasterServiceGrpc.getReportRegionFailureMethod = getReportRegionFailureMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.ReportRegionFailureRequest, com.minisql.master.proto.ReportRegionFailureResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ReportRegionFailure"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.ReportRegionFailureRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.ReportRegionFailureResponse.getDefaultInstance()))
              .setSchemaDescriptor(new MasterServiceMethodDescriptorSupplier("ReportRegionFailure"))
              .build();
        }
      }
    }
    return getReportRegionFailureMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.minisql.master.proto.AdminOperationRequest,
      com.minisql.master.proto.AdminOperationResponse> getAdminOperationMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "AdminOperation",
      requestType = com.minisql.master.proto.AdminOperationRequest.class,
      responseType = com.minisql.master.proto.AdminOperationResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.minisql.master.proto.AdminOperationRequest,
      com.minisql.master.proto.AdminOperationResponse> getAdminOperationMethod() {
    io.grpc.MethodDescriptor<com.minisql.master.proto.AdminOperationRequest, com.minisql.master.proto.AdminOperationResponse> getAdminOperationMethod;
    if ((getAdminOperationMethod = MasterServiceGrpc.getAdminOperationMethod) == null) {
      synchronized (MasterServiceGrpc.class) {
        if ((getAdminOperationMethod = MasterServiceGrpc.getAdminOperationMethod) == null) {
          MasterServiceGrpc.getAdminOperationMethod = getAdminOperationMethod =
              io.grpc.MethodDescriptor.<com.minisql.master.proto.AdminOperationRequest, com.minisql.master.proto.AdminOperationResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "AdminOperation"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.AdminOperationRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.minisql.master.proto.AdminOperationResponse.getDefaultInstance()))
              .setSchemaDescriptor(new MasterServiceMethodDescriptorSupplier("AdminOperation"))
              .build();
        }
      }
    }
    return getAdminOperationMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static MasterServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MasterServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MasterServiceStub>() {
        @java.lang.Override
        public MasterServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MasterServiceStub(channel, callOptions);
        }
      };
    return MasterServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static MasterServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MasterServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MasterServiceBlockingStub>() {
        @java.lang.Override
        public MasterServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MasterServiceBlockingStub(channel, callOptions);
        }
      };
    return MasterServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static MasterServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MasterServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MasterServiceFutureStub>() {
        @java.lang.Override
        public MasterServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MasterServiceFutureStub(channel, callOptions);
        }
      };
    return MasterServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * ========== RegionServer生命周期管理（显式方法）==========
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * RegionServer启动时注册到Master
     * </pre>
     */
    default void registerRegionServer(com.minisql.master.proto.RegisterRegionServerRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.RegisterRegionServerResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRegisterRegionServerMethod(), responseObserver);
    }

    /**
     * <pre>
     * RegionServer定期发送心跳
     * </pre>
     */
    default void sendHeartbeat(com.minisql.master.proto.HeartbeatRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.HeartbeatResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSendHeartbeatMethod(), responseObserver);
    }

    /**
     * <pre>
     * RegionServer优雅关闭前注销
     * </pre>
     */
    default void unregisterRegionServer(com.minisql.master.proto.UnregisterRegionServerRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.UnregisterRegionServerResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getUnregisterRegionServerMethod(), responseObserver);
    }

    /**
     * <pre>
     * RegionServer确认Region已打开并在线
     * </pre>
     */
    default void reportRegionOnline(com.minisql.master.proto.ReportRegionOnlineRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportRegionOnlineResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReportRegionOnlineMethod(), responseObserver);
    }

    /**
     * <pre>
     * RegionServer确认Region已关闭
     * </pre>
     */
    default void reportRegionClosed(com.minisql.master.proto.ReportRegionClosedRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportRegionClosedResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReportRegionClosedMethod(), responseObserver);
    }

    /**
     * <pre>
     * RegionServer报告Region超过大小阈值
     * </pre>
     */
    default void reportRegionSplit(com.minisql.master.proto.ReportRegionSplitRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportRegionSplitResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReportRegionSplitMethod(), responseObserver);
    }

    /**
     * <pre>
     * RegionServer报告迁移进度
     * </pre>
     */
    default void reportMigrationProgress(com.minisql.master.proto.ReportMigrationProgressRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportMigrationProgressResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReportMigrationProgressMethod(), responseObserver);
    }

    /**
     * <pre>
     * RegionServer报告Region故障
     * </pre>
     */
    default void reportRegionFailure(com.minisql.master.proto.ReportRegionFailureRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportRegionFailureResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReportRegionFailureMethod(), responseObserver);
    }

    /**
     * <pre>
     * 通用管理操作接口（用于不常用的操作）
     * </pre>
     */
    default void adminOperation(com.minisql.master.proto.AdminOperationRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.AdminOperationResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getAdminOperationMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service MasterService.
   * <pre>
   * ========== RegionServer生命周期管理（显式方法）==========
   * </pre>
   */
  public static abstract class MasterServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return MasterServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service MasterService.
   * <pre>
   * ========== RegionServer生命周期管理（显式方法）==========
   * </pre>
   */
  public static final class MasterServiceStub
      extends io.grpc.stub.AbstractAsyncStub<MasterServiceStub> {
    private MasterServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MasterServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MasterServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * RegionServer启动时注册到Master
     * </pre>
     */
    public void registerRegionServer(com.minisql.master.proto.RegisterRegionServerRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.RegisterRegionServerResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRegisterRegionServerMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * RegionServer定期发送心跳
     * </pre>
     */
    public void sendHeartbeat(com.minisql.master.proto.HeartbeatRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.HeartbeatResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSendHeartbeatMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * RegionServer优雅关闭前注销
     * </pre>
     */
    public void unregisterRegionServer(com.minisql.master.proto.UnregisterRegionServerRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.UnregisterRegionServerResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getUnregisterRegionServerMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * RegionServer确认Region已打开并在线
     * </pre>
     */
    public void reportRegionOnline(com.minisql.master.proto.ReportRegionOnlineRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportRegionOnlineResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getReportRegionOnlineMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * RegionServer确认Region已关闭
     * </pre>
     */
    public void reportRegionClosed(com.minisql.master.proto.ReportRegionClosedRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportRegionClosedResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getReportRegionClosedMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * RegionServer报告Region超过大小阈值
     * </pre>
     */
    public void reportRegionSplit(com.minisql.master.proto.ReportRegionSplitRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportRegionSplitResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getReportRegionSplitMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * RegionServer报告迁移进度
     * </pre>
     */
    public void reportMigrationProgress(com.minisql.master.proto.ReportMigrationProgressRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportMigrationProgressResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getReportMigrationProgressMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * RegionServer报告Region故障
     * </pre>
     */
    public void reportRegionFailure(com.minisql.master.proto.ReportRegionFailureRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportRegionFailureResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getReportRegionFailureMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 通用管理操作接口（用于不常用的操作）
     * </pre>
     */
    public void adminOperation(com.minisql.master.proto.AdminOperationRequest request,
        io.grpc.stub.StreamObserver<com.minisql.master.proto.AdminOperationResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getAdminOperationMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service MasterService.
   * <pre>
   * ========== RegionServer生命周期管理（显式方法）==========
   * </pre>
   */
  public static final class MasterServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<MasterServiceBlockingStub> {
    private MasterServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MasterServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MasterServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * RegionServer启动时注册到Master
     * </pre>
     */
    public com.minisql.master.proto.RegisterRegionServerResponse registerRegionServer(com.minisql.master.proto.RegisterRegionServerRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRegisterRegionServerMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * RegionServer定期发送心跳
     * </pre>
     */
    public com.minisql.master.proto.HeartbeatResponse sendHeartbeat(com.minisql.master.proto.HeartbeatRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSendHeartbeatMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * RegionServer优雅关闭前注销
     * </pre>
     */
    public com.minisql.master.proto.UnregisterRegionServerResponse unregisterRegionServer(com.minisql.master.proto.UnregisterRegionServerRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getUnregisterRegionServerMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * RegionServer确认Region已打开并在线
     * </pre>
     */
    public com.minisql.master.proto.ReportRegionOnlineResponse reportRegionOnline(com.minisql.master.proto.ReportRegionOnlineRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReportRegionOnlineMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * RegionServer确认Region已关闭
     * </pre>
     */
    public com.minisql.master.proto.ReportRegionClosedResponse reportRegionClosed(com.minisql.master.proto.ReportRegionClosedRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReportRegionClosedMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * RegionServer报告Region超过大小阈值
     * </pre>
     */
    public com.minisql.master.proto.ReportRegionSplitResponse reportRegionSplit(com.minisql.master.proto.ReportRegionSplitRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReportRegionSplitMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * RegionServer报告迁移进度
     * </pre>
     */
    public com.minisql.master.proto.ReportMigrationProgressResponse reportMigrationProgress(com.minisql.master.proto.ReportMigrationProgressRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReportMigrationProgressMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * RegionServer报告Region故障
     * </pre>
     */
    public com.minisql.master.proto.ReportRegionFailureResponse reportRegionFailure(com.minisql.master.proto.ReportRegionFailureRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReportRegionFailureMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 通用管理操作接口（用于不常用的操作）
     * </pre>
     */
    public com.minisql.master.proto.AdminOperationResponse adminOperation(com.minisql.master.proto.AdminOperationRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getAdminOperationMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service MasterService.
   * <pre>
   * ========== RegionServer生命周期管理（显式方法）==========
   * </pre>
   */
  public static final class MasterServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<MasterServiceFutureStub> {
    private MasterServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MasterServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MasterServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * RegionServer启动时注册到Master
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.RegisterRegionServerResponse> registerRegionServer(
        com.minisql.master.proto.RegisterRegionServerRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRegisterRegionServerMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * RegionServer定期发送心跳
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.HeartbeatResponse> sendHeartbeat(
        com.minisql.master.proto.HeartbeatRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSendHeartbeatMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * RegionServer优雅关闭前注销
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.UnregisterRegionServerResponse> unregisterRegionServer(
        com.minisql.master.proto.UnregisterRegionServerRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getUnregisterRegionServerMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * RegionServer确认Region已打开并在线
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.ReportRegionOnlineResponse> reportRegionOnline(
        com.minisql.master.proto.ReportRegionOnlineRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getReportRegionOnlineMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * RegionServer确认Region已关闭
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.ReportRegionClosedResponse> reportRegionClosed(
        com.minisql.master.proto.ReportRegionClosedRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getReportRegionClosedMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * RegionServer报告Region超过大小阈值
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.ReportRegionSplitResponse> reportRegionSplit(
        com.minisql.master.proto.ReportRegionSplitRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getReportRegionSplitMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * RegionServer报告迁移进度
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.ReportMigrationProgressResponse> reportMigrationProgress(
        com.minisql.master.proto.ReportMigrationProgressRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getReportMigrationProgressMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * RegionServer报告Region故障
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.ReportRegionFailureResponse> reportRegionFailure(
        com.minisql.master.proto.ReportRegionFailureRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getReportRegionFailureMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 通用管理操作接口（用于不常用的操作）
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.minisql.master.proto.AdminOperationResponse> adminOperation(
        com.minisql.master.proto.AdminOperationRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getAdminOperationMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REGISTER_REGION_SERVER = 0;
  private static final int METHODID_SEND_HEARTBEAT = 1;
  private static final int METHODID_UNREGISTER_REGION_SERVER = 2;
  private static final int METHODID_REPORT_REGION_ONLINE = 3;
  private static final int METHODID_REPORT_REGION_CLOSED = 4;
  private static final int METHODID_REPORT_REGION_SPLIT = 5;
  private static final int METHODID_REPORT_MIGRATION_PROGRESS = 6;
  private static final int METHODID_REPORT_REGION_FAILURE = 7;
  private static final int METHODID_ADMIN_OPERATION = 8;

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
        case METHODID_REGISTER_REGION_SERVER:
          serviceImpl.registerRegionServer((com.minisql.master.proto.RegisterRegionServerRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.RegisterRegionServerResponse>) responseObserver);
          break;
        case METHODID_SEND_HEARTBEAT:
          serviceImpl.sendHeartbeat((com.minisql.master.proto.HeartbeatRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.HeartbeatResponse>) responseObserver);
          break;
        case METHODID_UNREGISTER_REGION_SERVER:
          serviceImpl.unregisterRegionServer((com.minisql.master.proto.UnregisterRegionServerRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.UnregisterRegionServerResponse>) responseObserver);
          break;
        case METHODID_REPORT_REGION_ONLINE:
          serviceImpl.reportRegionOnline((com.minisql.master.proto.ReportRegionOnlineRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportRegionOnlineResponse>) responseObserver);
          break;
        case METHODID_REPORT_REGION_CLOSED:
          serviceImpl.reportRegionClosed((com.minisql.master.proto.ReportRegionClosedRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportRegionClosedResponse>) responseObserver);
          break;
        case METHODID_REPORT_REGION_SPLIT:
          serviceImpl.reportRegionSplit((com.minisql.master.proto.ReportRegionSplitRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportRegionSplitResponse>) responseObserver);
          break;
        case METHODID_REPORT_MIGRATION_PROGRESS:
          serviceImpl.reportMigrationProgress((com.minisql.master.proto.ReportMigrationProgressRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportMigrationProgressResponse>) responseObserver);
          break;
        case METHODID_REPORT_REGION_FAILURE:
          serviceImpl.reportRegionFailure((com.minisql.master.proto.ReportRegionFailureRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.ReportRegionFailureResponse>) responseObserver);
          break;
        case METHODID_ADMIN_OPERATION:
          serviceImpl.adminOperation((com.minisql.master.proto.AdminOperationRequest) request,
              (io.grpc.stub.StreamObserver<com.minisql.master.proto.AdminOperationResponse>) responseObserver);
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
          getRegisterRegionServerMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.RegisterRegionServerRequest,
              com.minisql.master.proto.RegisterRegionServerResponse>(
                service, METHODID_REGISTER_REGION_SERVER)))
        .addMethod(
          getSendHeartbeatMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.HeartbeatRequest,
              com.minisql.master.proto.HeartbeatResponse>(
                service, METHODID_SEND_HEARTBEAT)))
        .addMethod(
          getUnregisterRegionServerMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.UnregisterRegionServerRequest,
              com.minisql.master.proto.UnregisterRegionServerResponse>(
                service, METHODID_UNREGISTER_REGION_SERVER)))
        .addMethod(
          getReportRegionOnlineMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.ReportRegionOnlineRequest,
              com.minisql.master.proto.ReportRegionOnlineResponse>(
                service, METHODID_REPORT_REGION_ONLINE)))
        .addMethod(
          getReportRegionClosedMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.ReportRegionClosedRequest,
              com.minisql.master.proto.ReportRegionClosedResponse>(
                service, METHODID_REPORT_REGION_CLOSED)))
        .addMethod(
          getReportRegionSplitMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.ReportRegionSplitRequest,
              com.minisql.master.proto.ReportRegionSplitResponse>(
                service, METHODID_REPORT_REGION_SPLIT)))
        .addMethod(
          getReportMigrationProgressMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.ReportMigrationProgressRequest,
              com.minisql.master.proto.ReportMigrationProgressResponse>(
                service, METHODID_REPORT_MIGRATION_PROGRESS)))
        .addMethod(
          getReportRegionFailureMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.ReportRegionFailureRequest,
              com.minisql.master.proto.ReportRegionFailureResponse>(
                service, METHODID_REPORT_REGION_FAILURE)))
        .addMethod(
          getAdminOperationMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.minisql.master.proto.AdminOperationRequest,
              com.minisql.master.proto.AdminOperationResponse>(
                service, METHODID_ADMIN_OPERATION)))
        .build();
  }

  private static abstract class MasterServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    MasterServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.minisql.master.proto.MasterProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("MasterService");
    }
  }

  private static final class MasterServiceFileDescriptorSupplier
      extends MasterServiceBaseDescriptorSupplier {
    MasterServiceFileDescriptorSupplier() {}
  }

  private static final class MasterServiceMethodDescriptorSupplier
      extends MasterServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    MasterServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (MasterServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new MasterServiceFileDescriptorSupplier())
              .addMethod(getRegisterRegionServerMethod())
              .addMethod(getSendHeartbeatMethod())
              .addMethod(getUnregisterRegionServerMethod())
              .addMethod(getReportRegionOnlineMethod())
              .addMethod(getReportRegionClosedMethod())
              .addMethod(getReportRegionSplitMethod())
              .addMethod(getReportMigrationProgressMethod())
              .addMethod(getReportRegionFailureMethod())
              .addMethod(getAdminOperationMethod())
              .build();
        }
      }
    }
    return result;
  }
}
