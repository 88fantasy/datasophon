package com.datasophon.grpc.api;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * Worker → Master 反向回调服务。
 * Worker 安装 Doris/StarRocks 节点后通知 Master 执行集群注册 SQL，
 * 避免 Worker 进程直连 OLAP 数据库。
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.68.1)",
    comments = "Source: master.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class MasterCallbackServiceGrpc {

  private MasterCallbackServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "com.datasophon.grpc.MasterCallbackService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.OlapRegistrationRequest,
      com.datasophon.grpc.api.OlapRegistrationResponse> getRegisterOlapNodeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RegisterOlapNode",
      requestType = com.datasophon.grpc.api.OlapRegistrationRequest.class,
      responseType = com.datasophon.grpc.api.OlapRegistrationResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.OlapRegistrationRequest,
      com.datasophon.grpc.api.OlapRegistrationResponse> getRegisterOlapNodeMethod() {
    io.grpc.MethodDescriptor<com.datasophon.grpc.api.OlapRegistrationRequest, com.datasophon.grpc.api.OlapRegistrationResponse> getRegisterOlapNodeMethod;
    if ((getRegisterOlapNodeMethod = MasterCallbackServiceGrpc.getRegisterOlapNodeMethod) == null) {
      synchronized (MasterCallbackServiceGrpc.class) {
        if ((getRegisterOlapNodeMethod = MasterCallbackServiceGrpc.getRegisterOlapNodeMethod) == null) {
          MasterCallbackServiceGrpc.getRegisterOlapNodeMethod = getRegisterOlapNodeMethod =
              io.grpc.MethodDescriptor.<com.datasophon.grpc.api.OlapRegistrationRequest, com.datasophon.grpc.api.OlapRegistrationResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RegisterOlapNode"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.datasophon.grpc.api.OlapRegistrationRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.datasophon.grpc.api.OlapRegistrationResponse.getDefaultInstance()))
              .setSchemaDescriptor(new MasterCallbackServiceMethodDescriptorSupplier("RegisterOlapNode"))
              .build();
        }
      }
    }
    return getRegisterOlapNodeMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static MasterCallbackServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MasterCallbackServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MasterCallbackServiceStub>() {
        @java.lang.Override
        public MasterCallbackServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MasterCallbackServiceStub(channel, callOptions);
        }
      };
    return MasterCallbackServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static MasterCallbackServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MasterCallbackServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MasterCallbackServiceBlockingStub>() {
        @java.lang.Override
        public MasterCallbackServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MasterCallbackServiceBlockingStub(channel, callOptions);
        }
      };
    return MasterCallbackServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static MasterCallbackServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MasterCallbackServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MasterCallbackServiceFutureStub>() {
        @java.lang.Override
        public MasterCallbackServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MasterCallbackServiceFutureStub(channel, callOptions);
        }
      };
    return MasterCallbackServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * Worker → Master 反向回调服务。
   * Worker 安装 Doris/StarRocks 节点后通知 Master 执行集群注册 SQL，
   * 避免 Worker 进程直连 OLAP 数据库。
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Worker 新节点启动成功后，请求 Master 将其注册到 OLAP 集群。
     * Master 立即返回 success，异步执行注册 SQL（fire-and-forget 语义）。
     * </pre>
     */
    default void registerOlapNode(com.datasophon.grpc.api.OlapRegistrationRequest request,
        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.OlapRegistrationResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRegisterOlapNodeMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service MasterCallbackService.
   * <pre>
   * Worker → Master 反向回调服务。
   * Worker 安装 Doris/StarRocks 节点后通知 Master 执行集群注册 SQL，
   * 避免 Worker 进程直连 OLAP 数据库。
   * </pre>
   */
  public static abstract class MasterCallbackServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return MasterCallbackServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service MasterCallbackService.
   * <pre>
   * Worker → Master 反向回调服务。
   * Worker 安装 Doris/StarRocks 节点后通知 Master 执行集群注册 SQL，
   * 避免 Worker 进程直连 OLAP 数据库。
   * </pre>
   */
  public static final class MasterCallbackServiceStub
      extends io.grpc.stub.AbstractAsyncStub<MasterCallbackServiceStub> {
    private MasterCallbackServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MasterCallbackServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MasterCallbackServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Worker 新节点启动成功后，请求 Master 将其注册到 OLAP 集群。
     * Master 立即返回 success，异步执行注册 SQL（fire-and-forget 语义）。
     * </pre>
     */
    public void registerOlapNode(com.datasophon.grpc.api.OlapRegistrationRequest request,
        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.OlapRegistrationResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRegisterOlapNodeMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service MasterCallbackService.
   * <pre>
   * Worker → Master 反向回调服务。
   * Worker 安装 Doris/StarRocks 节点后通知 Master 执行集群注册 SQL，
   * 避免 Worker 进程直连 OLAP 数据库。
   * </pre>
   */
  public static final class MasterCallbackServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<MasterCallbackServiceBlockingStub> {
    private MasterCallbackServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MasterCallbackServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MasterCallbackServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Worker 新节点启动成功后，请求 Master 将其注册到 OLAP 集群。
     * Master 立即返回 success，异步执行注册 SQL（fire-and-forget 语义）。
     * </pre>
     */
    public com.datasophon.grpc.api.OlapRegistrationResponse registerOlapNode(com.datasophon.grpc.api.OlapRegistrationRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRegisterOlapNodeMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service MasterCallbackService.
   * <pre>
   * Worker → Master 反向回调服务。
   * Worker 安装 Doris/StarRocks 节点后通知 Master 执行集群注册 SQL，
   * 避免 Worker 进程直连 OLAP 数据库。
   * </pre>
   */
  public static final class MasterCallbackServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<MasterCallbackServiceFutureStub> {
    private MasterCallbackServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MasterCallbackServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MasterCallbackServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Worker 新节点启动成功后，请求 Master 将其注册到 OLAP 集群。
     * Master 立即返回 success，异步执行注册 SQL（fire-and-forget 语义）。
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.OlapRegistrationResponse> registerOlapNode(
        com.datasophon.grpc.api.OlapRegistrationRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRegisterOlapNodeMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REGISTER_OLAP_NODE = 0;

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
        case METHODID_REGISTER_OLAP_NODE:
          serviceImpl.registerOlapNode((com.datasophon.grpc.api.OlapRegistrationRequest) request,
              (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.OlapRegistrationResponse>) responseObserver);
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
          getRegisterOlapNodeMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.datasophon.grpc.api.OlapRegistrationRequest,
              com.datasophon.grpc.api.OlapRegistrationResponse>(
                service, METHODID_REGISTER_OLAP_NODE)))
        .build();
  }

  private static abstract class MasterCallbackServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    MasterCallbackServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.datasophon.grpc.api.MasterProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("MasterCallbackService");
    }
  }

  private static final class MasterCallbackServiceFileDescriptorSupplier
      extends MasterCallbackServiceBaseDescriptorSupplier {
    MasterCallbackServiceFileDescriptorSupplier() {}
  }

  private static final class MasterCallbackServiceMethodDescriptorSupplier
      extends MasterCallbackServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    MasterCallbackServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (MasterCallbackServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new MasterCallbackServiceFileDescriptorSupplier())
              .addMethod(getRegisterOlapNodeMethod())
              .build();
        }
      }
    }
    return result;
  }
}
