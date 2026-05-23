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

import com.datasophon.api.grpc.WorkerCommandClient;
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
 * {@link WorkerCallAdapter} 的 gRPC 实现，通过 {@link WorkerCommandClient} 向 Worker 发送命令。
 * <p>
 * 标记 {@code @Primary}，所有注入 {@code WorkerCallAdapter} 的 Handler 均获取此实现。
 * </p>
 */
@Primary
@Component
public class GrpcWorkerCallAdapter implements WorkerCallAdapter {

    private final WorkerCommandClient client;

    public GrpcWorkerCallAdapter(WorkerCommandClient client) {
        this.client = client;
    }

    @Override
    public ExecResult executeCmd(String hostname, ExecuteCmdCommand cmd) {
        if (cmd.getCommandLine() != null && !cmd.getCommandLine().isEmpty()) {
            return client.executeCmdLine(hostname, cmd.getCommandLine());
        }
        return client.executeCmd(hostname, cmd.getCommands());
    }

    @Override
    public ExecResult installServiceRole(String hostname, InstallServiceRoleCommand cmd) {
        return client.installServiceRole(hostname, cmd);
    }

    @Override
    public ExecResult configureServiceRole(String hostname, GenerateServiceConfigCommand cmd) {
        return client.configureServiceRole(hostname, cmd);
    }

    @Override
    public ExecResult startServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return client.startServiceRole(hostname, cmd);
    }

    @Override
    public ExecResult stopServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return client.stopServiceRole(hostname, cmd);
    }

    @Override
    public ExecResult restartServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return client.restartServiceRole(hostname, cmd);
    }

    @Override
    public ExecResult serviceRoleStatus(String hostname, ServiceRoleOperateCommand cmd) {
        return client.serviceRoleStatus(hostname, cmd);
    }

    // ─── Phase 3 ──────────────────────────────────────────────────────────────

    @Override
    public ExecResult createUnixGroup(String hostname, CreateUnixGroupCommand cmd) {
        return client.createUnixGroup(hostname, cmd);
    }

    @Override
    public ExecResult deleteUnixGroup(String hostname, DelUnixGroupCommand cmd) {
        return client.deleteUnixGroup(hostname, cmd);
    }

    @Override
    public ExecResult createUnixUser(String hostname, CreateUnixUserCommand cmd) {
        return client.createUnixUser(hostname, cmd);
    }

    @Override
    public ExecResult deleteUnixUser(String hostname, DelUnixUserCommand cmd) {
        return client.deleteUnixUser(hostname, cmd);
    }

    @Override
    public ExecResult operateFile(String hostname, FileOperateCommand cmd) {
        return client.operateFile(hostname, cmd);
    }

    @Override
    public ExecResult generateAlertConfig(String hostname, GenerateAlertConfigCommand cmd) {
        return client.generateAlertConfig(hostname, cmd);
    }
}
