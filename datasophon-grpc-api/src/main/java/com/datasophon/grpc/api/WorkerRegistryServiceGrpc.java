package com.datasophon.grpc.api;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * Worker 向 Master 注册自身的服务
 * Master 监听 grpc.server.port（默认 18081）
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.68.1)",
    comments = "Source: registry.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class WorkerRegistryServiceGrpc {

  private WorkerRegistryServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "com.datasophon.grpc.WorkerRegistryService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.RegisterRequest,
      com.datasophon.grpc.api.RegisterResponse> getRegisterMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Register",
      requestType = com.datasophon.grpc.api.RegisterRequest.class,
      responseType = com.datasophon.grpc.api.RegisterResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.RegisterRequest,
      com.datasophon.grpc.api.RegisterResponse> getRegisterMethod() {
    io.grpc.MethodDescriptor<com.datasophon.grpc.api.RegisterRequest, com.datasophon.grpc.api.RegisterResponse> getRegisterMethod;
    if ((getRegisterMethod = WorkerRegistryServiceGrpc.getRegisterMethod) == null) {
      synchronized (WorkerRegistryServiceGrpc.class) {
        if ((getRegisterMethod = WorkerRegistryServiceGrpc.getRegisterMethod) == null) {
          WorkerRegistryServiceGrpc.getRegisterMethod = getRegisterMethod =
              io.grpc.MethodDescriptor.<com.datasophon.grpc.api.RegisterRequest, com.datasophon.grpc.api.RegisterResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Register"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.datasophon.grpc.api.RegisterRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.datasophon.grpc.api.RegisterResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WorkerRegistryServiceMethodDescriptorSupplier("Register"))
              .build();
        }
      }
    }
    return getRegisterMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.HeartbeatRequest,
      com.datasophon.grpc.api.HeartbeatResponse> getHeartbeatMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Heartbeat",
      requestType = com.datasophon.grpc.api.HeartbeatRequest.class,
      responseType = com.datasophon.grpc.api.HeartbeatResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.HeartbeatRequest,
      com.datasophon.grpc.api.HeartbeatResponse> getHeartbeatMethod() {
    io.grpc.MethodDescriptor<com.datasophon.grpc.api.HeartbeatRequest, com.datasophon.grpc.api.HeartbeatResponse> getHeartbeatMethod;
    if ((getHeartbeatMethod = WorkerRegistryServiceGrpc.getHeartbeatMethod) == null) {
      synchronized (WorkerRegistryServiceGrpc.class) {
        if ((getHeartbeatMethod = WorkerRegistryServiceGrpc.getHeartbeatMethod) == null) {
          WorkerRegistryServiceGrpc.getHeartbeatMethod = getHeartbeatMethod =
              io.grpc.MethodDescriptor.<com.datasophon.grpc.api.HeartbeatRequest, com.datasophon.grpc.api.HeartbeatResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Heartbeat"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.datasophon.grpc.api.HeartbeatRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.datasophon.grpc.api.HeartbeatResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WorkerRegistryServiceMethodDescriptorSupplier("Heartbeat"))
              .build();
        }
      }
    }
    return getHeartbeatMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.UnregisterRequest,
      com.datasophon.grpc.api.UnregisterResponse> getUnregisterMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Unregister",
      requestType = com.datasophon.grpc.api.UnregisterRequest.class,
      responseType = com.datasophon.grpc.api.UnregisterResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.UnregisterRequest,
      com.datasophon.grpc.api.UnregisterResponse> getUnregisterMethod() {
    io.grpc.MethodDescriptor<com.datasophon.grpc.api.UnregisterRequest, com.datasophon.grpc.api.UnregisterResponse> getUnregisterMethod;
    if ((getUnregisterMethod = WorkerRegistryServiceGrpc.getUnregisterMethod) == null) {
      synchronized (WorkerRegistryServiceGrpc.class) {
        if ((getUnregisterMethod = WorkerRegistryServiceGrpc.getUnregisterMethod) == null) {
          WorkerRegistryServiceGrpc.getUnregisterMethod = getUnregisterMethod =
              io.grpc.MethodDescriptor.<com.datasophon.grpc.api.UnregisterRequest, com.datasophon.grpc.api.UnregisterResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Unregister"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.datasophon.grpc.api.UnregisterRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.datasophon.grpc.api.UnregisterResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WorkerRegistryServiceMethodDescriptorSupplier("Unregister"))
              .build();
        }
      }
    }
    return getUnregisterMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static WorkerRegistryServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WorkerRegistryServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WorkerRegistryServiceStub>() {
        @java.lang.Override
        public WorkerRegistryServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WorkerRegistryServiceStub(channel, callOptions);
        }
      };
    return WorkerRegistryServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static WorkerRegistryServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WorkerRegistryServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WorkerRegistryServiceBlockingStub>() {
        @java.lang.Override
        public WorkerRegistryServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WorkerRegistryServiceBlockingStub(channel, callOptions);
        }
      };
    return WorkerRegistryServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static WorkerRegistryServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WorkerRegistryServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WorkerRegistryServiceFutureStub>() {
        @java.lang.Override
        public WorkerRegistryServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WorkerRegistryServiceFutureStub(channel, callOptions);
        }
      };
    return WorkerRegistryServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * Worker 向 Master 注册自身的服务
   * Master 监听 grpc.server.port（默认 18081）
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Worker 启动时调用，向 Master 注册节点信息
     * </pre>
     */
    default void register(com.datasophon.grpc.api.RegisterRequest request,
        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.RegisterResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRegisterMethod(), responseObserver);
    }

    /**
     * <pre>
     * Worker 定期心跳（默认 30s），Master 超过 3 次（90s）未收到则标记 worker 离线
     * </pre>
     */
    default void heartbeat(com.datasophon.grpc.api.HeartbeatRequest request,
        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.HeartbeatResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getHeartbeatMethod(), responseObserver);
    }

    /**
     * <pre>
     * Worker 正常关闭时主动注销（可选，Master 依赖心跳超时也能感知）
     * </pre>
     */
    default void unregister(com.datasophon.grpc.api.UnregisterRequest request,
        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.UnregisterResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getUnregisterMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service WorkerRegistryService.
   * <pre>
   * Worker 向 Master 注册自身的服务
   * Master 监听 grpc.server.port（默认 18081）
   * </pre>
   */
  public static abstract class WorkerRegistryServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return WorkerRegistryServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service WorkerRegistryService.
   * <pre>
   * Worker 向 Master 注册自身的服务
   * Master 监听 grpc.server.port（默认 18081）
   * </pre>
   */
  public static final class WorkerRegistryServiceStub
      extends io.grpc.stub.AbstractAsyncStub<WorkerRegistryServiceStub> {
    private WorkerRegistryServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WorkerRegistryServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WorkerRegistryServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Worker 启动时调用，向 Master 注册节点信息
     * </pre>
     */
    public void register(com.datasophon.grpc.api.RegisterRequest request,
        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.RegisterResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRegisterMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Worker 定期心跳（默认 30s），Master 超过 3 次（90s）未收到则标记 worker 离线
     * </pre>
     */
    public void heartbeat(com.datasophon.grpc.api.HeartbeatRequest request,
        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.HeartbeatResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getHeartbeatMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Worker 正常关闭时主动注销（可选，Master 依赖心跳超时也能感知）
     * </pre>
     */
    public void unregister(com.datasophon.grpc.api.UnregisterRequest request,
        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.UnregisterResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getUnregisterMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service WorkerRegistryService.
   * <pre>
   * Worker 向 Master 注册自身的服务
   * Master 监听 grpc.server.port（默认 18081）
   * </pre>
   */
  public static final class WorkerRegistryServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<WorkerRegistryServiceBlockingStub> {
    private WorkerRegistryServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WorkerRegistryServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WorkerRegistryServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Worker 启动时调用，向 Master 注册节点信息
     * </pre>
     */
    public com.datasophon.grpc.api.RegisterResponse register(com.datasophon.grpc.api.RegisterRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRegisterMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Worker 定期心跳（默认 30s），Master 超过 3 次（90s）未收到则标记 worker 离线
     * </pre>
     */
    public com.datasophon.grpc.api.HeartbeatResponse heartbeat(com.datasophon.grpc.api.HeartbeatRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getHeartbeatMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Worker 正常关闭时主动注销（可选，Master 依赖心跳超时也能感知）
     * </pre>
     */
    public com.datasophon.grpc.api.UnregisterResponse unregister(com.datasophon.grpc.api.UnregisterRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getUnregisterMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service WorkerRegistryService.
   * <pre>
   * Worker 向 Master 注册自身的服务
   * Master 监听 grpc.server.port（默认 18081）
   * </pre>
   */
  public static final class WorkerRegistryServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<WorkerRegistryServiceFutureStub> {
    private WorkerRegistryServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WorkerRegistryServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WorkerRegistryServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Worker 启动时调用，向 Master 注册节点信息
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.RegisterResponse> register(
        com.datasophon.grpc.api.RegisterRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRegisterMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Worker 定期心跳（默认 30s），Master 超过 3 次（90s）未收到则标记 worker 离线
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.HeartbeatResponse> heartbeat(
        com.datasophon.grpc.api.HeartbeatRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getHeartbeatMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Worker 正常关闭时主动注销（可选，Master 依赖心跳超时也能感知）
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.UnregisterResponse> unregister(
        com.datasophon.grpc.api.UnregisterRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getUnregisterMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REGISTER = 0;
  private static final int METHODID_HEARTBEAT = 1;
  private static final int METHODID_UNREGISTER = 2;

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
        case METHODID_REGISTER:
          serviceImpl.register((com.datasophon.grpc.api.RegisterRequest) request,
              (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.RegisterResponse>) responseObserver);
          break;
        case METHODID_HEARTBEAT:
          serviceImpl.heartbeat((com.datasophon.grpc.api.HeartbeatRequest) request,
              (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.HeartbeatResponse>) responseObserver);
          break;
        case METHODID_UNREGISTER:
          serviceImpl.unregister((com.datasophon.grpc.api.UnregisterRequest) request,
              (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.UnregisterResponse>) responseObserver);
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
          getRegisterMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.datasophon.grpc.api.RegisterRequest,
              com.datasophon.grpc.api.RegisterResponse>(
                service, METHODID_REGISTER)))
        .addMethod(
          getHeartbeatMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.datasophon.grpc.api.HeartbeatRequest,
              com.datasophon.grpc.api.HeartbeatResponse>(
                service, METHODID_HEARTBEAT)))
        .addMethod(
          getUnregisterMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.datasophon.grpc.api.UnregisterRequest,
              com.datasophon.grpc.api.UnregisterResponse>(
                service, METHODID_UNREGISTER)))
        .build();
  }

  private static abstract class WorkerRegistryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    WorkerRegistryServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.datasophon.grpc.api.RegistryProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("WorkerRegistryService");
    }
  }

  private static final class WorkerRegistryServiceFileDescriptorSupplier
      extends WorkerRegistryServiceBaseDescriptorSupplier {
    WorkerRegistryServiceFileDescriptorSupplier() {}
  }

  private static final class WorkerRegistryServiceMethodDescriptorSupplier
      extends WorkerRegistryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    WorkerRegistryServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (WorkerRegistryServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new WorkerRegistryServiceFileDescriptorSupplier())
              .addMethod(getRegisterMethod())
              .addMethod(getHeartbeatMethod())
              .addMethod(getUnregisterMethod())
              .build();
        }
      }
    }
    return result;
  }
}
