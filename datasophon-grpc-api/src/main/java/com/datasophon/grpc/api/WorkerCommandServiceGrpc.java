package com.datasophon.grpc.api;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 **
 * Worker 命令服务（Phase 1）：Master → Worker 的远程调用。
 * Phase 1 覆盖：PingActor、ExecuteCmdActor、LogActor（及同构的 RMStateActor、NMStateActor）
 * Phase 2+ 追加：InstallServiceRole、ConfigureServiceRole、StartServiceRole 等
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.68.1)",
    comments = "Source: worker.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class WorkerCommandServiceGrpc {

  private WorkerCommandServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "com.datasophon.grpc.WorkerCommandService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.PingRequest,
      com.datasophon.grpc.api.ExecResultPb> getPingMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Ping",
      requestType = com.datasophon.grpc.api.PingRequest.class,
      responseType = com.datasophon.grpc.api.ExecResultPb.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.PingRequest,
      com.datasophon.grpc.api.ExecResultPb> getPingMethod() {
    io.grpc.MethodDescriptor<com.datasophon.grpc.api.PingRequest, com.datasophon.grpc.api.ExecResultPb> getPingMethod;
    if ((getPingMethod = WorkerCommandServiceGrpc.getPingMethod) == null) {
      synchronized (WorkerCommandServiceGrpc.class) {
        if ((getPingMethod = WorkerCommandServiceGrpc.getPingMethod) == null) {
          WorkerCommandServiceGrpc.getPingMethod = getPingMethod =
              io.grpc.MethodDescriptor.<com.datasophon.grpc.api.PingRequest, com.datasophon.grpc.api.ExecResultPb>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Ping"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.datasophon.grpc.api.PingRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.datasophon.grpc.api.ExecResultPb.getDefaultInstance()))
              .setSchemaDescriptor(new WorkerCommandServiceMethodDescriptorSupplier("Ping"))
              .build();
        }
      }
    }
    return getPingMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.ExecuteCmdRequest,
      com.datasophon.grpc.api.ExecResultPb> getExecuteCmdMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ExecuteCmd",
      requestType = com.datasophon.grpc.api.ExecuteCmdRequest.class,
      responseType = com.datasophon.grpc.api.ExecResultPb.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.ExecuteCmdRequest,
      com.datasophon.grpc.api.ExecResultPb> getExecuteCmdMethod() {
    io.grpc.MethodDescriptor<com.datasophon.grpc.api.ExecuteCmdRequest, com.datasophon.grpc.api.ExecResultPb> getExecuteCmdMethod;
    if ((getExecuteCmdMethod = WorkerCommandServiceGrpc.getExecuteCmdMethod) == null) {
      synchronized (WorkerCommandServiceGrpc.class) {
        if ((getExecuteCmdMethod = WorkerCommandServiceGrpc.getExecuteCmdMethod) == null) {
          WorkerCommandServiceGrpc.getExecuteCmdMethod = getExecuteCmdMethod =
              io.grpc.MethodDescriptor.<com.datasophon.grpc.api.ExecuteCmdRequest, com.datasophon.grpc.api.ExecResultPb>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ExecuteCmd"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.datasophon.grpc.api.ExecuteCmdRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.datasophon.grpc.api.ExecResultPb.getDefaultInstance()))
              .setSchemaDescriptor(new WorkerCommandServiceMethodDescriptorSupplier("ExecuteCmd"))
              .build();
        }
      }
    }
    return getExecuteCmdMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.GetLogRequest,
      com.datasophon.grpc.api.ExecResultPb> getGetLogMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetLog",
      requestType = com.datasophon.grpc.api.GetLogRequest.class,
      responseType = com.datasophon.grpc.api.ExecResultPb.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.GetLogRequest,
      com.datasophon.grpc.api.ExecResultPb> getGetLogMethod() {
    io.grpc.MethodDescriptor<com.datasophon.grpc.api.GetLogRequest, com.datasophon.grpc.api.ExecResultPb> getGetLogMethod;
    if ((getGetLogMethod = WorkerCommandServiceGrpc.getGetLogMethod) == null) {
      synchronized (WorkerCommandServiceGrpc.class) {
        if ((getGetLogMethod = WorkerCommandServiceGrpc.getGetLogMethod) == null) {
          WorkerCommandServiceGrpc.getGetLogMethod = getGetLogMethod =
              io.grpc.MethodDescriptor.<com.datasophon.grpc.api.GetLogRequest, com.datasophon.grpc.api.ExecResultPb>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetLog"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.datasophon.grpc.api.GetLogRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.datasophon.grpc.api.ExecResultPb.getDefaultInstance()))
              .setSchemaDescriptor(new WorkerCommandServiceMethodDescriptorSupplier("GetLog"))
              .build();
        }
      }
    }
    return getGetLogMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static WorkerCommandServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WorkerCommandServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WorkerCommandServiceStub>() {
        @java.lang.Override
        public WorkerCommandServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WorkerCommandServiceStub(channel, callOptions);
        }
      };
    return WorkerCommandServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static WorkerCommandServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WorkerCommandServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WorkerCommandServiceBlockingStub>() {
        @java.lang.Override
        public WorkerCommandServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WorkerCommandServiceBlockingStub(channel, callOptions);
        }
      };
    return WorkerCommandServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static WorkerCommandServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WorkerCommandServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WorkerCommandServiceFutureStub>() {
        @java.lang.Override
        public WorkerCommandServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WorkerCommandServiceFutureStub(channel, callOptions);
        }
      };
    return WorkerCommandServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   **
   * Worker 命令服务（Phase 1）：Master → Worker 的远程调用。
   * Phase 1 覆盖：PingActor、ExecuteCmdActor、LogActor（及同构的 RMStateActor、NMStateActor）
   * Phase 2+ 追加：InstallServiceRole、ConfigureServiceRole、StartServiceRole 等
   * </pre>
   */
  public interface AsyncService {

    /**
     */
    default void ping(com.datasophon.grpc.api.PingRequest request,
        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getPingMethod(), responseObserver);
    }

    /**
     */
    default void executeCmd(com.datasophon.grpc.api.ExecuteCmdRequest request,
        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getExecuteCmdMethod(), responseObserver);
    }

    /**
     */
    default void getLog(com.datasophon.grpc.api.GetLogRequest request,
        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetLogMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service WorkerCommandService.
   * <pre>
   **
   * Worker 命令服务（Phase 1）：Master → Worker 的远程调用。
   * Phase 1 覆盖：PingActor、ExecuteCmdActor、LogActor（及同构的 RMStateActor、NMStateActor）
   * Phase 2+ 追加：InstallServiceRole、ConfigureServiceRole、StartServiceRole 等
   * </pre>
   */
  public static abstract class WorkerCommandServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return WorkerCommandServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service WorkerCommandService.
   * <pre>
   **
   * Worker 命令服务（Phase 1）：Master → Worker 的远程调用。
   * Phase 1 覆盖：PingActor、ExecuteCmdActor、LogActor（及同构的 RMStateActor、NMStateActor）
   * Phase 2+ 追加：InstallServiceRole、ConfigureServiceRole、StartServiceRole 等
   * </pre>
   */
  public static final class WorkerCommandServiceStub
      extends io.grpc.stub.AbstractAsyncStub<WorkerCommandServiceStub> {
    private WorkerCommandServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WorkerCommandServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WorkerCommandServiceStub(channel, callOptions);
    }

    /**
     */
    public void ping(com.datasophon.grpc.api.PingRequest request,
        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getPingMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void executeCmd(com.datasophon.grpc.api.ExecuteCmdRequest request,
        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getExecuteCmdMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getLog(com.datasophon.grpc.api.GetLogRequest request,
        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetLogMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service WorkerCommandService.
   * <pre>
   **
   * Worker 命令服务（Phase 1）：Master → Worker 的远程调用。
   * Phase 1 覆盖：PingActor、ExecuteCmdActor、LogActor（及同构的 RMStateActor、NMStateActor）
   * Phase 2+ 追加：InstallServiceRole、ConfigureServiceRole、StartServiceRole 等
   * </pre>
   */
  public static final class WorkerCommandServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<WorkerCommandServiceBlockingStub> {
    private WorkerCommandServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WorkerCommandServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WorkerCommandServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.datasophon.grpc.api.ExecResultPb ping(com.datasophon.grpc.api.PingRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPingMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.datasophon.grpc.api.ExecResultPb executeCmd(com.datasophon.grpc.api.ExecuteCmdRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getExecuteCmdMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.datasophon.grpc.api.ExecResultPb getLog(com.datasophon.grpc.api.GetLogRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetLogMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service WorkerCommandService.
   * <pre>
   **
   * Worker 命令服务（Phase 1）：Master → Worker 的远程调用。
   * Phase 1 覆盖：PingActor、ExecuteCmdActor、LogActor（及同构的 RMStateActor、NMStateActor）
   * Phase 2+ 追加：InstallServiceRole、ConfigureServiceRole、StartServiceRole 等
   * </pre>
   */
  public static final class WorkerCommandServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<WorkerCommandServiceFutureStub> {
    private WorkerCommandServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WorkerCommandServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WorkerCommandServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.ExecResultPb> ping(
        com.datasophon.grpc.api.PingRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getPingMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.ExecResultPb> executeCmd(
        com.datasophon.grpc.api.ExecuteCmdRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getExecuteCmdMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.ExecResultPb> getLog(
        com.datasophon.grpc.api.GetLogRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetLogMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_PING = 0;
  private static final int METHODID_EXECUTE_CMD = 1;
  private static final int METHODID_GET_LOG = 2;

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
        case METHODID_PING:
          serviceImpl.ping((com.datasophon.grpc.api.PingRequest) request,
              (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb>) responseObserver);
          break;
        case METHODID_EXECUTE_CMD:
          serviceImpl.executeCmd((com.datasophon.grpc.api.ExecuteCmdRequest) request,
              (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb>) responseObserver);
          break;
        case METHODID_GET_LOG:
          serviceImpl.getLog((com.datasophon.grpc.api.GetLogRequest) request,
              (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb>) responseObserver);
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
          getPingMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.datasophon.grpc.api.PingRequest,
              com.datasophon.grpc.api.ExecResultPb>(
                service, METHODID_PING)))
        .addMethod(
          getExecuteCmdMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.datasophon.grpc.api.ExecuteCmdRequest,
              com.datasophon.grpc.api.ExecResultPb>(
                service, METHODID_EXECUTE_CMD)))
        .addMethod(
          getGetLogMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.datasophon.grpc.api.GetLogRequest,
              com.datasophon.grpc.api.ExecResultPb>(
                service, METHODID_GET_LOG)))
        .build();
  }

  private static abstract class WorkerCommandServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    WorkerCommandServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.datasophon.grpc.api.WorkerProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("WorkerCommandService");
    }
  }

  private static final class WorkerCommandServiceFileDescriptorSupplier
      extends WorkerCommandServiceBaseDescriptorSupplier {
    WorkerCommandServiceFileDescriptorSupplier() {}
  }

  private static final class WorkerCommandServiceMethodDescriptorSupplier
      extends WorkerCommandServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    WorkerCommandServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (WorkerCommandServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new WorkerCommandServiceFileDescriptorSupplier())
              .addMethod(getPingMethod())
              .addMethod(getExecuteCmdMethod())
              .addMethod(getGetLogMethod())
              .build();
        }
      }
    }
    return result;
  }
}
