/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datasophon.api.master.transport;

import com.datasophon.common.command.ExecuteCmdCommand;
import com.datasophon.common.command.FileOperateCommand;
import com.datasophon.common.command.GenerateAlertConfigCommand;
import com.datasophon.common.command.GenerateServiceConfigCommand;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.command.remote.CreateUnixGroupCommand;
import com.datasophon.common.command.remote.CreateUnixUserCommand;
import com.datasophon.common.command.remote.DelUnixGroupCommand;
import com.datasophon.common.command.remote.DelUnixUserCommand;
import com.datasophon.common.utils.ExecResult;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 路由 WorkerCallAdapter（Phase 5+）：直接委托到 {@link GrpcWorkerCallAdapter}。
 *
 * <p>标记 {@code @Primary}，业务组件注入 {@code WorkerCallAdapter} 时获取此类。</p>
 */
@Primary
@Component
public class TransportWorkerCallAdapter implements WorkerCallAdapter {

    private final GrpcWorkerCallAdapter grpc;

    public TransportWorkerCallAdapter(GrpcWorkerCallAdapter grpc) {
        this.grpc = grpc;
    }

    @Override
    public ExecResult executeCmd(String hostname, ExecuteCmdCommand cmd) {
        return grpc.executeCmd(hostname, cmd);
    }

    @Override
    public ExecResult installServiceRole(String hostname, InstallServiceRoleCommand cmd) {
        return grpc.installServiceRole(hostname, cmd);
    }

    @Override
    public ExecResult configureServiceRole(String hostname, GenerateServiceConfigCommand cmd) {
        return grpc.configureServiceRole(hostname, cmd);
    }

    @Override
    public ExecResult startServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return grpc.startServiceRole(hostname, cmd);
    }

    @Override
    public ExecResult stopServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return grpc.stopServiceRole(hostname, cmd);
    }

    @Override
    public ExecResult restartServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return grpc.restartServiceRole(hostname, cmd);
    }

    @Override
    public ExecResult serviceRoleStatus(String hostname, ServiceRoleOperateCommand cmd) {
        return grpc.serviceRoleStatus(hostname, cmd);
    }

    @Override
    public ExecResult createUnixGroup(String hostname, CreateUnixGroupCommand cmd) {
        return grpc.createUnixGroup(hostname, cmd);
    }

    @Override
    public ExecResult deleteUnixGroup(String hostname, DelUnixGroupCommand cmd) {
        return grpc.deleteUnixGroup(hostname, cmd);
    }

    @Override
    public ExecResult createUnixUser(String hostname, CreateUnixUserCommand cmd) {
        return grpc.createUnixUser(hostname, cmd);
    }

    @Override
    public ExecResult deleteUnixUser(String hostname, DelUnixUserCommand cmd) {
        return grpc.deleteUnixUser(hostname, cmd);
    }

    @Override
    public ExecResult operateFile(String hostname, FileOperateCommand cmd) {
        return grpc.operateFile(hostname, cmd);
    }

    @Override
    public ExecResult generateAlertConfig(String hostname, GenerateAlertConfigCommand cmd) {
        return grpc.generateAlertConfig(hostname, cmd);
    }
}
