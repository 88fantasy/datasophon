package com.datasophon.grpc.api;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 **
 * Worker 命令服务（Phase 1+2+3）：Master → Worker 的远程调用。
 * Phase 1：PingActor、ExecuteCmdActor、LogActor（及同构的 RMStateActor、NMStateActor）
 * Phase 2：InstallServiceRole、ConfigureServiceRole、Start/Stop/Restart/Status
 * Phase 3：Unix User/Group、FileOperate、AlertConfig
 * </pre>
 */
@javax.annotation.Generated(value = "by gRPC proto compiler (version 1.68.1)", comments = "Source: worker.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class WorkerCommandServiceGrpc {
    
    private WorkerCommandServiceGrpc() {
    }
    
    public static final java.lang.String SERVICE_NAME = "com.datasophon.grpc.WorkerCommandService";
    
    // Static method descriptors that strictly reflect the proto.
    private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.PingRequest, com.datasophon.grpc.api.ExecResultPb> getPingMethod;
    
    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "Ping", requestType = com.datasophon.grpc.api.PingRequest.class, responseType = com.datasophon.grpc.api.ExecResultPb.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.PingRequest, com.datasophon.grpc.api.ExecResultPb> getPingMethod() {
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
    
    private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.ExecuteCmdRequest, com.datasophon.grpc.api.ExecResultPb> getExecuteCmdMethod;
    
    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "ExecuteCmd", requestType = com.datasophon.grpc.api.ExecuteCmdRequest.class, responseType = com.datasophon.grpc.api.ExecResultPb.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.ExecuteCmdRequest, com.datasophon.grpc.api.ExecResultPb> getExecuteCmdMethod() {
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
    
    private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.GetLogRequest, com.datasophon.grpc.api.ExecResultPb> getGetLogMethod;
    
    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "GetLog", requestType = com.datasophon.grpc.api.GetLogRequest.class, responseType = com.datasophon.grpc.api.ExecResultPb.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.GetLogRequest, com.datasophon.grpc.api.ExecResultPb> getGetLogMethod() {
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
    
    private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getInstallServiceRoleMethod;
    
    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "InstallServiceRole", requestType = com.datasophon.grpc.api.ServiceRoleRequest.class, responseType = com.datasophon.grpc.api.ExecResultPb.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getInstallServiceRoleMethod() {
        io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getInstallServiceRoleMethod;
        if ((getInstallServiceRoleMethod = WorkerCommandServiceGrpc.getInstallServiceRoleMethod) == null) {
            synchronized (WorkerCommandServiceGrpc.class) {
                if ((getInstallServiceRoleMethod = WorkerCommandServiceGrpc.getInstallServiceRoleMethod) == null) {
                    WorkerCommandServiceGrpc.getInstallServiceRoleMethod = getInstallServiceRoleMethod =
                            io.grpc.MethodDescriptor.<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb>newBuilder()
                                    .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                                    .setFullMethodName(generateFullMethodName(SERVICE_NAME, "InstallServiceRole"))
                                    .setSampledToLocalTracing(true)
                                    .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ServiceRoleRequest.getDefaultInstance()))
                                    .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ExecResultPb.getDefaultInstance()))
                                    .setSchemaDescriptor(new WorkerCommandServiceMethodDescriptorSupplier("InstallServiceRole"))
                                    .build();
                }
            }
        }
        return getInstallServiceRoleMethod;
    }
    
    private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getConfigureServiceRoleMethod;
    
    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "ConfigureServiceRole", requestType = com.datasophon.grpc.api.ServiceRoleRequest.class, responseType = com.datasophon.grpc.api.ExecResultPb.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getConfigureServiceRoleMethod() {
        io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getConfigureServiceRoleMethod;
        if ((getConfigureServiceRoleMethod = WorkerCommandServiceGrpc.getConfigureServiceRoleMethod) == null) {
            synchronized (WorkerCommandServiceGrpc.class) {
                if ((getConfigureServiceRoleMethod = WorkerCommandServiceGrpc.getConfigureServiceRoleMethod) == null) {
                    WorkerCommandServiceGrpc.getConfigureServiceRoleMethod = getConfigureServiceRoleMethod =
                            io.grpc.MethodDescriptor.<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb>newBuilder()
                                    .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                                    .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ConfigureServiceRole"))
                                    .setSampledToLocalTracing(true)
                                    .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ServiceRoleRequest.getDefaultInstance()))
                                    .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ExecResultPb.getDefaultInstance()))
                                    .setSchemaDescriptor(new WorkerCommandServiceMethodDescriptorSupplier("ConfigureServiceRole"))
                                    .build();
                }
            }
        }
        return getConfigureServiceRoleMethod;
    }
    
    private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getStartServiceRoleMethod;
    
    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "StartServiceRole", requestType = com.datasophon.grpc.api.ServiceRoleRequest.class, responseType = com.datasophon.grpc.api.ExecResultPb.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getStartServiceRoleMethod() {
        io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getStartServiceRoleMethod;
        if ((getStartServiceRoleMethod = WorkerCommandServiceGrpc.getStartServiceRoleMethod) == null) {
            synchronized (WorkerCommandServiceGrpc.class) {
                if ((getStartServiceRoleMethod = WorkerCommandServiceGrpc.getStartServiceRoleMethod) == null) {
                    WorkerCommandServiceGrpc.getStartServiceRoleMethod = getStartServiceRoleMethod =
                            io.grpc.MethodDescriptor.<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb>newBuilder()
                                    .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                                    .setFullMethodName(generateFullMethodName(SERVICE_NAME, "StartServiceRole"))
                                    .setSampledToLocalTracing(true)
                                    .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ServiceRoleRequest.getDefaultInstance()))
                                    .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ExecResultPb.getDefaultInstance()))
                                    .setSchemaDescriptor(new WorkerCommandServiceMethodDescriptorSupplier("StartServiceRole"))
                                    .build();
                }
            }
        }
        return getStartServiceRoleMethod;
    }
    
    private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getStopServiceRoleMethod;
    
    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "StopServiceRole", requestType = com.datasophon.grpc.api.ServiceRoleRequest.class, responseType = com.datasophon.grpc.api.ExecResultPb.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getStopServiceRoleMethod() {
        io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getStopServiceRoleMethod;
        if ((getStopServiceRoleMethod = WorkerCommandServiceGrpc.getStopServiceRoleMethod) == null) {
            synchronized (WorkerCommandServiceGrpc.class) {
                if ((getStopServiceRoleMethod = WorkerCommandServiceGrpc.getStopServiceRoleMethod) == null) {
                    WorkerCommandServiceGrpc.getStopServiceRoleMethod = getStopServiceRoleMethod =
                            io.grpc.MethodDescriptor.<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb>newBuilder()
                                    .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                                    .setFullMethodName(generateFullMethodName(SERVICE_NAME, "StopServiceRole"))
                                    .setSampledToLocalTracing(true)
                                    .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ServiceRoleRequest.getDefaultInstance()))
                                    .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ExecResultPb.getDefaultInstance()))
                                    .setSchemaDescriptor(new WorkerCommandServiceMethodDescriptorSupplier("StopServiceRole"))
                                    .build();
                }
            }
        }
        return getStopServiceRoleMethod;
    }
    
    private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getRestartServiceRoleMethod;
    
    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "RestartServiceRole", requestType = com.datasophon.grpc.api.ServiceRoleRequest.class, responseType = com.datasophon.grpc.api.ExecResultPb.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getRestartServiceRoleMethod() {
        io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getRestartServiceRoleMethod;
        if ((getRestartServiceRoleMethod = WorkerCommandServiceGrpc.getRestartServiceRoleMethod) == null) {
            synchronized (WorkerCommandServiceGrpc.class) {
                if ((getRestartServiceRoleMethod = WorkerCommandServiceGrpc.getRestartServiceRoleMethod) == null) {
                    WorkerCommandServiceGrpc.getRestartServiceRoleMethod = getRestartServiceRoleMethod =
                            io.grpc.MethodDescriptor.<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb>newBuilder()
                                    .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                                    .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RestartServiceRole"))
                                    .setSampledToLocalTracing(true)
                                    .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ServiceRoleRequest.getDefaultInstance()))
                                    .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ExecResultPb.getDefaultInstance()))
                                    .setSchemaDescriptor(new WorkerCommandServiceMethodDescriptorSupplier("RestartServiceRole"))
                                    .build();
                }
            }
        }
        return getRestartServiceRoleMethod;
    }
    
    private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getServiceRoleStatusMethod;
    
    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "ServiceRoleStatus", requestType = com.datasophon.grpc.api.ServiceRoleRequest.class, responseType = com.datasophon.grpc.api.ExecResultPb.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getServiceRoleStatusMethod() {
        io.grpc.MethodDescriptor<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb> getServiceRoleStatusMethod;
        if ((getServiceRoleStatusMethod = WorkerCommandServiceGrpc.getServiceRoleStatusMethod) == null) {
            synchronized (WorkerCommandServiceGrpc.class) {
                if ((getServiceRoleStatusMethod = WorkerCommandServiceGrpc.getServiceRoleStatusMethod) == null) {
                    WorkerCommandServiceGrpc.getServiceRoleStatusMethod = getServiceRoleStatusMethod =
                            io.grpc.MethodDescriptor.<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb>newBuilder()
                                    .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                                    .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ServiceRoleStatus"))
                                    .setSampledToLocalTracing(true)
                                    .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ServiceRoleRequest.getDefaultInstance()))
                                    .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ExecResultPb.getDefaultInstance()))
                                    .setSchemaDescriptor(new WorkerCommandServiceMethodDescriptorSupplier("ServiceRoleStatus"))
                                    .build();
                }
            }
        }
        return getServiceRoleStatusMethod;
    }
    
    private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.UnixGroupRequest, com.datasophon.grpc.api.ExecResultPb> getCreateUnixGroupMethod;
    
    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "CreateUnixGroup", requestType = com.datasophon.grpc.api.UnixGroupRequest.class, responseType = com.datasophon.grpc.api.ExecResultPb.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.UnixGroupRequest, com.datasophon.grpc.api.ExecResultPb> getCreateUnixGroupMethod() {
        io.grpc.MethodDescriptor<com.datasophon.grpc.api.UnixGroupRequest, com.datasophon.grpc.api.ExecResultPb> getCreateUnixGroupMethod;
        if ((getCreateUnixGroupMethod = WorkerCommandServiceGrpc.getCreateUnixGroupMethod) == null) {
            synchronized (WorkerCommandServiceGrpc.class) {
                if ((getCreateUnixGroupMethod = WorkerCommandServiceGrpc.getCreateUnixGroupMethod) == null) {
                    WorkerCommandServiceGrpc.getCreateUnixGroupMethod = getCreateUnixGroupMethod =
                            io.grpc.MethodDescriptor.<com.datasophon.grpc.api.UnixGroupRequest, com.datasophon.grpc.api.ExecResultPb>newBuilder()
                                    .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                                    .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreateUnixGroup"))
                                    .setSampledToLocalTracing(true)
                                    .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.UnixGroupRequest.getDefaultInstance()))
                                    .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ExecResultPb.getDefaultInstance()))
                                    .setSchemaDescriptor(new WorkerCommandServiceMethodDescriptorSupplier("CreateUnixGroup"))
                                    .build();
                }
            }
        }
        return getCreateUnixGroupMethod;
    }
    
    private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.UnixGroupRequest, com.datasophon.grpc.api.ExecResultPb> getDeleteUnixGroupMethod;
    
    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "DeleteUnixGroup", requestType = com.datasophon.grpc.api.UnixGroupRequest.class, responseType = com.datasophon.grpc.api.ExecResultPb.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.UnixGroupRequest, com.datasophon.grpc.api.ExecResultPb> getDeleteUnixGroupMethod() {
        io.grpc.MethodDescriptor<com.datasophon.grpc.api.UnixGroupRequest, com.datasophon.grpc.api.ExecResultPb> getDeleteUnixGroupMethod;
        if ((getDeleteUnixGroupMethod = WorkerCommandServiceGrpc.getDeleteUnixGroupMethod) == null) {
            synchronized (WorkerCommandServiceGrpc.class) {
                if ((getDeleteUnixGroupMethod = WorkerCommandServiceGrpc.getDeleteUnixGroupMethod) == null) {
                    WorkerCommandServiceGrpc.getDeleteUnixGroupMethod = getDeleteUnixGroupMethod =
                            io.grpc.MethodDescriptor.<com.datasophon.grpc.api.UnixGroupRequest, com.datasophon.grpc.api.ExecResultPb>newBuilder()
                                    .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                                    .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DeleteUnixGroup"))
                                    .setSampledToLocalTracing(true)
                                    .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.UnixGroupRequest.getDefaultInstance()))
                                    .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ExecResultPb.getDefaultInstance()))
                                    .setSchemaDescriptor(new WorkerCommandServiceMethodDescriptorSupplier("DeleteUnixGroup"))
                                    .build();
                }
            }
        }
        return getDeleteUnixGroupMethod;
    }
    
    private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.UnixUserRequest, com.datasophon.grpc.api.ExecResultPb> getCreateUnixUserMethod;
    
    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "CreateUnixUser", requestType = com.datasophon.grpc.api.UnixUserRequest.class, responseType = com.datasophon.grpc.api.ExecResultPb.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.UnixUserRequest, com.datasophon.grpc.api.ExecResultPb> getCreateUnixUserMethod() {
        io.grpc.MethodDescriptor<com.datasophon.grpc.api.UnixUserRequest, com.datasophon.grpc.api.ExecResultPb> getCreateUnixUserMethod;
        if ((getCreateUnixUserMethod = WorkerCommandServiceGrpc.getCreateUnixUserMethod) == null) {
            synchronized (WorkerCommandServiceGrpc.class) {
                if ((getCreateUnixUserMethod = WorkerCommandServiceGrpc.getCreateUnixUserMethod) == null) {
                    WorkerCommandServiceGrpc.getCreateUnixUserMethod = getCreateUnixUserMethod =
                            io.grpc.MethodDescriptor.<com.datasophon.grpc.api.UnixUserRequest, com.datasophon.grpc.api.ExecResultPb>newBuilder()
                                    .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                                    .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreateUnixUser"))
                                    .setSampledToLocalTracing(true)
                                    .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.UnixUserRequest.getDefaultInstance()))
                                    .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ExecResultPb.getDefaultInstance()))
                                    .setSchemaDescriptor(new WorkerCommandServiceMethodDescriptorSupplier("CreateUnixUser"))
                                    .build();
                }
            }
        }
        return getCreateUnixUserMethod;
    }
    
    private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.UnixUserRequest, com.datasophon.grpc.api.ExecResultPb> getDeleteUnixUserMethod;
    
    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "DeleteUnixUser", requestType = com.datasophon.grpc.api.UnixUserRequest.class, responseType = com.datasophon.grpc.api.ExecResultPb.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.UnixUserRequest, com.datasophon.grpc.api.ExecResultPb> getDeleteUnixUserMethod() {
        io.grpc.MethodDescriptor<com.datasophon.grpc.api.UnixUserRequest, com.datasophon.grpc.api.ExecResultPb> getDeleteUnixUserMethod;
        if ((getDeleteUnixUserMethod = WorkerCommandServiceGrpc.getDeleteUnixUserMethod) == null) {
            synchronized (WorkerCommandServiceGrpc.class) {
                if ((getDeleteUnixUserMethod = WorkerCommandServiceGrpc.getDeleteUnixUserMethod) == null) {
                    WorkerCommandServiceGrpc.getDeleteUnixUserMethod = getDeleteUnixUserMethod =
                            io.grpc.MethodDescriptor.<com.datasophon.grpc.api.UnixUserRequest, com.datasophon.grpc.api.ExecResultPb>newBuilder()
                                    .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                                    .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DeleteUnixUser"))
                                    .setSampledToLocalTracing(true)
                                    .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.UnixUserRequest.getDefaultInstance()))
                                    .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ExecResultPb.getDefaultInstance()))
                                    .setSchemaDescriptor(new WorkerCommandServiceMethodDescriptorSupplier("DeleteUnixUser"))
                                    .build();
                }
            }
        }
        return getDeleteUnixUserMethod;
    }
    
    private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.FileOperateRequest, com.datasophon.grpc.api.ExecResultPb> getOperateFileMethod;
    
    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "OperateFile", requestType = com.datasophon.grpc.api.FileOperateRequest.class, responseType = com.datasophon.grpc.api.ExecResultPb.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.FileOperateRequest, com.datasophon.grpc.api.ExecResultPb> getOperateFileMethod() {
        io.grpc.MethodDescriptor<com.datasophon.grpc.api.FileOperateRequest, com.datasophon.grpc.api.ExecResultPb> getOperateFileMethod;
        if ((getOperateFileMethod = WorkerCommandServiceGrpc.getOperateFileMethod) == null) {
            synchronized (WorkerCommandServiceGrpc.class) {
                if ((getOperateFileMethod = WorkerCommandServiceGrpc.getOperateFileMethod) == null) {
                    WorkerCommandServiceGrpc.getOperateFileMethod = getOperateFileMethod =
                            io.grpc.MethodDescriptor.<com.datasophon.grpc.api.FileOperateRequest, com.datasophon.grpc.api.ExecResultPb>newBuilder()
                                    .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                                    .setFullMethodName(generateFullMethodName(SERVICE_NAME, "OperateFile"))
                                    .setSampledToLocalTracing(true)
                                    .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.FileOperateRequest.getDefaultInstance()))
                                    .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ExecResultPb.getDefaultInstance()))
                                    .setSchemaDescriptor(new WorkerCommandServiceMethodDescriptorSupplier("OperateFile"))
                                    .build();
                }
            }
        }
        return getOperateFileMethod;
    }
    
    private static volatile io.grpc.MethodDescriptor<com.datasophon.grpc.api.AlertConfigRequest, com.datasophon.grpc.api.ExecResultPb> getGenerateAlertConfigMethod;
    
    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "GenerateAlertConfig", requestType = com.datasophon.grpc.api.AlertConfigRequest.class, responseType = com.datasophon.grpc.api.ExecResultPb.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<com.datasophon.grpc.api.AlertConfigRequest, com.datasophon.grpc.api.ExecResultPb> getGenerateAlertConfigMethod() {
        io.grpc.MethodDescriptor<com.datasophon.grpc.api.AlertConfigRequest, com.datasophon.grpc.api.ExecResultPb> getGenerateAlertConfigMethod;
        if ((getGenerateAlertConfigMethod = WorkerCommandServiceGrpc.getGenerateAlertConfigMethod) == null) {
            synchronized (WorkerCommandServiceGrpc.class) {
                if ((getGenerateAlertConfigMethod = WorkerCommandServiceGrpc.getGenerateAlertConfigMethod) == null) {
                    WorkerCommandServiceGrpc.getGenerateAlertConfigMethod = getGenerateAlertConfigMethod =
                            io.grpc.MethodDescriptor.<com.datasophon.grpc.api.AlertConfigRequest, com.datasophon.grpc.api.ExecResultPb>newBuilder()
                                    .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                                    .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GenerateAlertConfig"))
                                    .setSampledToLocalTracing(true)
                                    .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.AlertConfigRequest.getDefaultInstance()))
                                    .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                            com.datasophon.grpc.api.ExecResultPb.getDefaultInstance()))
                                    .setSchemaDescriptor(new WorkerCommandServiceMethodDescriptorSupplier("GenerateAlertConfig"))
                                    .build();
                }
            }
        }
        return getGenerateAlertConfigMethod;
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
     * Worker 命令服务（Phase 1+2+3）：Master → Worker 的远程调用。
     * Phase 1：PingActor、ExecuteCmdActor、LogActor（及同构的 RMStateActor、NMStateActor）
     * Phase 2：InstallServiceRole、ConfigureServiceRole、Start/Stop/Restart/Status
     * Phase 3：Unix User/Group、FileOperate、AlertConfig
     * </pre>
     */
    public interface AsyncService {
        
        /**
         * <pre>
         * Phase 1
         * </pre>
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
        
        /**
         * <pre>
         * Phase 2
         * </pre>
         */
        default void installServiceRole(com.datasophon.grpc.api.ServiceRoleRequest request,
                                        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getInstallServiceRoleMethod(), responseObserver);
        }
        
        /**
         */
        default void configureServiceRole(com.datasophon.grpc.api.ServiceRoleRequest request,
                                          io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getConfigureServiceRoleMethod(), responseObserver);
        }
        
        /**
         */
        default void startServiceRole(com.datasophon.grpc.api.ServiceRoleRequest request,
                                      io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getStartServiceRoleMethod(), responseObserver);
        }
        
        /**
         */
        default void stopServiceRole(com.datasophon.grpc.api.ServiceRoleRequest request,
                                     io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getStopServiceRoleMethod(), responseObserver);
        }
        
        /**
         */
        default void restartServiceRole(com.datasophon.grpc.api.ServiceRoleRequest request,
                                        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRestartServiceRoleMethod(), responseObserver);
        }
        
        /**
         */
        default void serviceRoleStatus(com.datasophon.grpc.api.ServiceRoleRequest request,
                                       io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getServiceRoleStatusMethod(), responseObserver);
        }
        
        /**
         * <pre>
         * Phase 3
         * </pre>
         */
        default void createUnixGroup(com.datasophon.grpc.api.UnixGroupRequest request,
                                     io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCreateUnixGroupMethod(), responseObserver);
        }
        
        /**
         */
        default void deleteUnixGroup(com.datasophon.grpc.api.UnixGroupRequest request,
                                     io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDeleteUnixGroupMethod(), responseObserver);
        }
        
        /**
         */
        default void createUnixUser(com.datasophon.grpc.api.UnixUserRequest request,
                                    io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCreateUnixUserMethod(), responseObserver);
        }
        
        /**
         */
        default void deleteUnixUser(com.datasophon.grpc.api.UnixUserRequest request,
                                    io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDeleteUnixUserMethod(), responseObserver);
        }
        
        /**
         */
        default void operateFile(com.datasophon.grpc.api.FileOperateRequest request,
                                 io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getOperateFileMethod(), responseObserver);
        }
        
        /**
         */
        default void generateAlertConfig(com.datasophon.grpc.api.AlertConfigRequest request,
                                         io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGenerateAlertConfigMethod(), responseObserver);
        }
    }
    
    /**
     * Base class for the server implementation of the service WorkerCommandService.
     * <pre>
     **
     * Worker 命令服务（Phase 1+2+3）：Master → Worker 的远程调用。
     * Phase 1：PingActor、ExecuteCmdActor、LogActor（及同构的 RMStateActor、NMStateActor）
     * Phase 2：InstallServiceRole、ConfigureServiceRole、Start/Stop/Restart/Status
     * Phase 3：Unix User/Group、FileOperate、AlertConfig
     * </pre>
     */
    public static abstract class WorkerCommandServiceImplBase
            implements
                io.grpc.BindableService,
                AsyncService {
        
        @java.lang.Override
        public final io.grpc.ServerServiceDefinition bindService() {
            return WorkerCommandServiceGrpc.bindService(this);
        }
    }
    
    /**
     * A stub to allow clients to do asynchronous rpc calls to service WorkerCommandService.
     * <pre>
     **
     * Worker 命令服务（Phase 1+2+3）：Master → Worker 的远程调用。
     * Phase 1：PingActor、ExecuteCmdActor、LogActor（及同构的 RMStateActor、NMStateActor）
     * Phase 2：InstallServiceRole、ConfigureServiceRole、Start/Stop/Restart/Status
     * Phase 3：Unix User/Group、FileOperate、AlertConfig
     * </pre>
     */
    public static final class WorkerCommandServiceStub
            extends
                io.grpc.stub.AbstractAsyncStub<WorkerCommandServiceStub> {
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
         * <pre>
         * Phase 1
         * </pre>
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
        
        /**
         * <pre>
         * Phase 2
         * </pre>
         */
        public void installServiceRole(com.datasophon.grpc.api.ServiceRoleRequest request,
                                       io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getInstallServiceRoleMethod(), getCallOptions()), request, responseObserver);
        }
        
        /**
         */
        public void configureServiceRole(com.datasophon.grpc.api.ServiceRoleRequest request,
                                         io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getConfigureServiceRoleMethod(), getCallOptions()), request, responseObserver);
        }
        
        /**
         */
        public void startServiceRole(com.datasophon.grpc.api.ServiceRoleRequest request,
                                     io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getStartServiceRoleMethod(), getCallOptions()), request, responseObserver);
        }
        
        /**
         */
        public void stopServiceRole(com.datasophon.grpc.api.ServiceRoleRequest request,
                                    io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getStopServiceRoleMethod(), getCallOptions()), request, responseObserver);
        }
        
        /**
         */
        public void restartServiceRole(com.datasophon.grpc.api.ServiceRoleRequest request,
                                       io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getRestartServiceRoleMethod(), getCallOptions()), request, responseObserver);
        }
        
        /**
         */
        public void serviceRoleStatus(com.datasophon.grpc.api.ServiceRoleRequest request,
                                      io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getServiceRoleStatusMethod(), getCallOptions()), request, responseObserver);
        }
        
        /**
         * <pre>
         * Phase 3
         * </pre>
         */
        public void createUnixGroup(com.datasophon.grpc.api.UnixGroupRequest request,
                                    io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getCreateUnixGroupMethod(), getCallOptions()), request, responseObserver);
        }
        
        /**
         */
        public void deleteUnixGroup(com.datasophon.grpc.api.UnixGroupRequest request,
                                    io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getDeleteUnixGroupMethod(), getCallOptions()), request, responseObserver);
        }
        
        /**
         */
        public void createUnixUser(com.datasophon.grpc.api.UnixUserRequest request,
                                   io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getCreateUnixUserMethod(), getCallOptions()), request, responseObserver);
        }
        
        /**
         */
        public void deleteUnixUser(com.datasophon.grpc.api.UnixUserRequest request,
                                   io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getDeleteUnixUserMethod(), getCallOptions()), request, responseObserver);
        }
        
        /**
         */
        public void operateFile(com.datasophon.grpc.api.FileOperateRequest request,
                                io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getOperateFileMethod(), getCallOptions()), request, responseObserver);
        }
        
        /**
         */
        public void generateAlertConfig(com.datasophon.grpc.api.AlertConfigRequest request,
                                        io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(
                    getChannel().newCall(getGenerateAlertConfigMethod(), getCallOptions()), request, responseObserver);
        }
    }
    
    /**
     * A stub to allow clients to do synchronous rpc calls to service WorkerCommandService.
     * <pre>
     **
     * Worker 命令服务（Phase 1+2+3）：Master → Worker 的远程调用。
     * Phase 1：PingActor、ExecuteCmdActor、LogActor（及同构的 RMStateActor、NMStateActor）
     * Phase 2：InstallServiceRole、ConfigureServiceRole、Start/Stop/Restart/Status
     * Phase 3：Unix User/Group、FileOperate、AlertConfig
     * </pre>
     */
    public static final class WorkerCommandServiceBlockingStub
            extends
                io.grpc.stub.AbstractBlockingStub<WorkerCommandServiceBlockingStub> {
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
         * <pre>
         * Phase 1
         * </pre>
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
        
        /**
         * <pre>
         * Phase 2
         * </pre>
         */
        public com.datasophon.grpc.api.ExecResultPb installServiceRole(com.datasophon.grpc.api.ServiceRoleRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getInstallServiceRoleMethod(), getCallOptions(), request);
        }
        
        /**
         */
        public com.datasophon.grpc.api.ExecResultPb configureServiceRole(com.datasophon.grpc.api.ServiceRoleRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getConfigureServiceRoleMethod(), getCallOptions(), request);
        }
        
        /**
         */
        public com.datasophon.grpc.api.ExecResultPb startServiceRole(com.datasophon.grpc.api.ServiceRoleRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getStartServiceRoleMethod(), getCallOptions(), request);
        }
        
        /**
         */
        public com.datasophon.grpc.api.ExecResultPb stopServiceRole(com.datasophon.grpc.api.ServiceRoleRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getStopServiceRoleMethod(), getCallOptions(), request);
        }
        
        /**
         */
        public com.datasophon.grpc.api.ExecResultPb restartServiceRole(com.datasophon.grpc.api.ServiceRoleRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getRestartServiceRoleMethod(), getCallOptions(), request);
        }
        
        /**
         */
        public com.datasophon.grpc.api.ExecResultPb serviceRoleStatus(com.datasophon.grpc.api.ServiceRoleRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getServiceRoleStatusMethod(), getCallOptions(), request);
        }
        
        /**
         * <pre>
         * Phase 3
         * </pre>
         */
        public com.datasophon.grpc.api.ExecResultPb createUnixGroup(com.datasophon.grpc.api.UnixGroupRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getCreateUnixGroupMethod(), getCallOptions(), request);
        }
        
        /**
         */
        public com.datasophon.grpc.api.ExecResultPb deleteUnixGroup(com.datasophon.grpc.api.UnixGroupRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getDeleteUnixGroupMethod(), getCallOptions(), request);
        }
        
        /**
         */
        public com.datasophon.grpc.api.ExecResultPb createUnixUser(com.datasophon.grpc.api.UnixUserRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getCreateUnixUserMethod(), getCallOptions(), request);
        }
        
        /**
         */
        public com.datasophon.grpc.api.ExecResultPb deleteUnixUser(com.datasophon.grpc.api.UnixUserRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getDeleteUnixUserMethod(), getCallOptions(), request);
        }
        
        /**
         */
        public com.datasophon.grpc.api.ExecResultPb operateFile(com.datasophon.grpc.api.FileOperateRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getOperateFileMethod(), getCallOptions(), request);
        }
        
        /**
         */
        public com.datasophon.grpc.api.ExecResultPb generateAlertConfig(com.datasophon.grpc.api.AlertConfigRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(
                    getChannel(), getGenerateAlertConfigMethod(), getCallOptions(), request);
        }
    }
    
    /**
     * A stub to allow clients to do ListenableFuture-style rpc calls to service WorkerCommandService.
     * <pre>
     **
     * Worker 命令服务（Phase 1+2+3）：Master → Worker 的远程调用。
     * Phase 1：PingActor、ExecuteCmdActor、LogActor（及同构的 RMStateActor、NMStateActor）
     * Phase 2：InstallServiceRole、ConfigureServiceRole、Start/Stop/Restart/Status
     * Phase 3：Unix User/Group、FileOperate、AlertConfig
     * </pre>
     */
    public static final class WorkerCommandServiceFutureStub
            extends
                io.grpc.stub.AbstractFutureStub<WorkerCommandServiceFutureStub> {
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
         * <pre>
         * Phase 1
         * </pre>
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
        
        /**
         * <pre>
         * Phase 2
         * </pre>
         */
        public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.ExecResultPb> installServiceRole(
                                                                                                                           com.datasophon.grpc.api.ServiceRoleRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getInstallServiceRoleMethod(), getCallOptions()), request);
        }
        
        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.ExecResultPb> configureServiceRole(
                                                                                                                             com.datasophon.grpc.api.ServiceRoleRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getConfigureServiceRoleMethod(), getCallOptions()), request);
        }
        
        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.ExecResultPb> startServiceRole(
                                                                                                                         com.datasophon.grpc.api.ServiceRoleRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getStartServiceRoleMethod(), getCallOptions()), request);
        }
        
        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.ExecResultPb> stopServiceRole(
                                                                                                                        com.datasophon.grpc.api.ServiceRoleRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getStopServiceRoleMethod(), getCallOptions()), request);
        }
        
        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.ExecResultPb> restartServiceRole(
                                                                                                                           com.datasophon.grpc.api.ServiceRoleRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getRestartServiceRoleMethod(), getCallOptions()), request);
        }
        
        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.ExecResultPb> serviceRoleStatus(
                                                                                                                          com.datasophon.grpc.api.ServiceRoleRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getServiceRoleStatusMethod(), getCallOptions()), request);
        }
        
        /**
         * <pre>
         * Phase 3
         * </pre>
         */
        public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.ExecResultPb> createUnixGroup(
                                                                                                                        com.datasophon.grpc.api.UnixGroupRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getCreateUnixGroupMethod(), getCallOptions()), request);
        }
        
        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.ExecResultPb> deleteUnixGroup(
                                                                                                                        com.datasophon.grpc.api.UnixGroupRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getDeleteUnixGroupMethod(), getCallOptions()), request);
        }
        
        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.ExecResultPb> createUnixUser(
                                                                                                                       com.datasophon.grpc.api.UnixUserRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getCreateUnixUserMethod(), getCallOptions()), request);
        }
        
        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.ExecResultPb> deleteUnixUser(
                                                                                                                       com.datasophon.grpc.api.UnixUserRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getDeleteUnixUserMethod(), getCallOptions()), request);
        }
        
        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.ExecResultPb> operateFile(
                                                                                                                    com.datasophon.grpc.api.FileOperateRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getOperateFileMethod(), getCallOptions()), request);
        }
        
        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<com.datasophon.grpc.api.ExecResultPb> generateAlertConfig(
                                                                                                                            com.datasophon.grpc.api.AlertConfigRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(
                    getChannel().newCall(getGenerateAlertConfigMethod(), getCallOptions()), request);
        }
    }
    
    private static final int METHODID_PING = 0;
    private static final int METHODID_EXECUTE_CMD = 1;
    private static final int METHODID_GET_LOG = 2;
    private static final int METHODID_INSTALL_SERVICE_ROLE = 3;
    private static final int METHODID_CONFIGURE_SERVICE_ROLE = 4;
    private static final int METHODID_START_SERVICE_ROLE = 5;
    private static final int METHODID_STOP_SERVICE_ROLE = 6;
    private static final int METHODID_RESTART_SERVICE_ROLE = 7;
    private static final int METHODID_SERVICE_ROLE_STATUS = 8;
    private static final int METHODID_CREATE_UNIX_GROUP = 9;
    private static final int METHODID_DELETE_UNIX_GROUP = 10;
    private static final int METHODID_CREATE_UNIX_USER = 11;
    private static final int METHODID_DELETE_UNIX_USER = 12;
    private static final int METHODID_OPERATE_FILE = 13;
    private static final int METHODID_GENERATE_ALERT_CONFIG = 14;
    
    private static final class MethodHandlers<Req, Resp>
            implements
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
                case METHODID_INSTALL_SERVICE_ROLE:
                    serviceImpl.installServiceRole((com.datasophon.grpc.api.ServiceRoleRequest) request,
                            (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb>) responseObserver);
                    break;
                case METHODID_CONFIGURE_SERVICE_ROLE:
                    serviceImpl.configureServiceRole((com.datasophon.grpc.api.ServiceRoleRequest) request,
                            (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb>) responseObserver);
                    break;
                case METHODID_START_SERVICE_ROLE:
                    serviceImpl.startServiceRole((com.datasophon.grpc.api.ServiceRoleRequest) request,
                            (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb>) responseObserver);
                    break;
                case METHODID_STOP_SERVICE_ROLE:
                    serviceImpl.stopServiceRole((com.datasophon.grpc.api.ServiceRoleRequest) request,
                            (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb>) responseObserver);
                    break;
                case METHODID_RESTART_SERVICE_ROLE:
                    serviceImpl.restartServiceRole((com.datasophon.grpc.api.ServiceRoleRequest) request,
                            (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb>) responseObserver);
                    break;
                case METHODID_SERVICE_ROLE_STATUS:
                    serviceImpl.serviceRoleStatus((com.datasophon.grpc.api.ServiceRoleRequest) request,
                            (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb>) responseObserver);
                    break;
                case METHODID_CREATE_UNIX_GROUP:
                    serviceImpl.createUnixGroup((com.datasophon.grpc.api.UnixGroupRequest) request,
                            (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb>) responseObserver);
                    break;
                case METHODID_DELETE_UNIX_GROUP:
                    serviceImpl.deleteUnixGroup((com.datasophon.grpc.api.UnixGroupRequest) request,
                            (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb>) responseObserver);
                    break;
                case METHODID_CREATE_UNIX_USER:
                    serviceImpl.createUnixUser((com.datasophon.grpc.api.UnixUserRequest) request,
                            (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb>) responseObserver);
                    break;
                case METHODID_DELETE_UNIX_USER:
                    serviceImpl.deleteUnixUser((com.datasophon.grpc.api.UnixUserRequest) request,
                            (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb>) responseObserver);
                    break;
                case METHODID_OPERATE_FILE:
                    serviceImpl.operateFile((com.datasophon.grpc.api.FileOperateRequest) request,
                            (io.grpc.stub.StreamObserver<com.datasophon.grpc.api.ExecResultPb>) responseObserver);
                    break;
                case METHODID_GENERATE_ALERT_CONFIG:
                    serviceImpl.generateAlertConfig((com.datasophon.grpc.api.AlertConfigRequest) request,
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
                                new MethodHandlers<com.datasophon.grpc.api.PingRequest, com.datasophon.grpc.api.ExecResultPb>(
                                        service, METHODID_PING)))
                .addMethod(
                        getExecuteCmdMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(
                                new MethodHandlers<com.datasophon.grpc.api.ExecuteCmdRequest, com.datasophon.grpc.api.ExecResultPb>(
                                        service, METHODID_EXECUTE_CMD)))
                .addMethod(
                        getGetLogMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(
                                new MethodHandlers<com.datasophon.grpc.api.GetLogRequest, com.datasophon.grpc.api.ExecResultPb>(
                                        service, METHODID_GET_LOG)))
                .addMethod(
                        getInstallServiceRoleMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(
                                new MethodHandlers<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb>(
                                        service, METHODID_INSTALL_SERVICE_ROLE)))
                .addMethod(
                        getConfigureServiceRoleMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(
                                new MethodHandlers<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb>(
                                        service, METHODID_CONFIGURE_SERVICE_ROLE)))
                .addMethod(
                        getStartServiceRoleMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(
                                new MethodHandlers<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb>(
                                        service, METHODID_START_SERVICE_ROLE)))
                .addMethod(
                        getStopServiceRoleMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(
                                new MethodHandlers<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb>(
                                        service, METHODID_STOP_SERVICE_ROLE)))
                .addMethod(
                        getRestartServiceRoleMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(
                                new MethodHandlers<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb>(
                                        service, METHODID_RESTART_SERVICE_ROLE)))
                .addMethod(
                        getServiceRoleStatusMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(
                                new MethodHandlers<com.datasophon.grpc.api.ServiceRoleRequest, com.datasophon.grpc.api.ExecResultPb>(
                                        service, METHODID_SERVICE_ROLE_STATUS)))
                .addMethod(
                        getCreateUnixGroupMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(
                                new MethodHandlers<com.datasophon.grpc.api.UnixGroupRequest, com.datasophon.grpc.api.ExecResultPb>(
                                        service, METHODID_CREATE_UNIX_GROUP)))
                .addMethod(
                        getDeleteUnixGroupMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(
                                new MethodHandlers<com.datasophon.grpc.api.UnixGroupRequest, com.datasophon.grpc.api.ExecResultPb>(
                                        service, METHODID_DELETE_UNIX_GROUP)))
                .addMethod(
                        getCreateUnixUserMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(
                                new MethodHandlers<com.datasophon.grpc.api.UnixUserRequest, com.datasophon.grpc.api.ExecResultPb>(
                                        service, METHODID_CREATE_UNIX_USER)))
                .addMethod(
                        getDeleteUnixUserMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(
                                new MethodHandlers<com.datasophon.grpc.api.UnixUserRequest, com.datasophon.grpc.api.ExecResultPb>(
                                        service, METHODID_DELETE_UNIX_USER)))
                .addMethod(
                        getOperateFileMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(
                                new MethodHandlers<com.datasophon.grpc.api.FileOperateRequest, com.datasophon.grpc.api.ExecResultPb>(
                                        service, METHODID_OPERATE_FILE)))
                .addMethod(
                        getGenerateAlertConfigMethod(),
                        io.grpc.stub.ServerCalls.asyncUnaryCall(
                                new MethodHandlers<com.datasophon.grpc.api.AlertConfigRequest, com.datasophon.grpc.api.ExecResultPb>(
                                        service, METHODID_GENERATE_ALERT_CONFIG)))
                .build();
    }
    
    private static abstract class WorkerCommandServiceBaseDescriptorSupplier
            implements
                io.grpc.protobuf.ProtoFileDescriptorSupplier,
                io.grpc.protobuf.ProtoServiceDescriptorSupplier {
        WorkerCommandServiceBaseDescriptorSupplier() {
        }
        
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
            extends
                WorkerCommandServiceBaseDescriptorSupplier {
        WorkerCommandServiceFileDescriptorSupplier() {
        }
    }
    
    private static final class WorkerCommandServiceMethodDescriptorSupplier
            extends
                WorkerCommandServiceBaseDescriptorSupplier
            implements
                io.grpc.protobuf.ProtoMethodDescriptorSupplier {
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
                            .addMethod(getInstallServiceRoleMethod())
                            .addMethod(getConfigureServiceRoleMethod())
                            .addMethod(getStartServiceRoleMethod())
                            .addMethod(getStopServiceRoleMethod())
                            .addMethod(getRestartServiceRoleMethod())
                            .addMethod(getServiceRoleStatusMethod())
                            .addMethod(getCreateUnixGroupMethod())
                            .addMethod(getDeleteUnixGroupMethod())
                            .addMethod(getCreateUnixUserMethod())
                            .addMethod(getDeleteUnixUserMethod())
                            .addMethod(getOperateFileMethod())
                            .addMethod(getGenerateAlertConfigMethod())
                            .build();
                }
            }
        }
        return result;
    }
}
