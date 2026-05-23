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

/**
 * Master → Worker 调用适配接口。
 *
 * <p>Handler 注入此接口，唯一实现为 {@link GrpcWorkerCallAdapter}（{@code @Primary}）。</p>
 */
public interface WorkerCallAdapter {

    ExecResult executeCmd(String hostname, ExecuteCmdCommand cmd);

    // ─── Service Role ─────────────────────────────────────────────────────────

    ExecResult installServiceRole(String hostname, InstallServiceRoleCommand cmd);

    ExecResult configureServiceRole(String hostname, GenerateServiceConfigCommand cmd);

    ExecResult startServiceRole(String hostname, ServiceRoleOperateCommand cmd);

    ExecResult stopServiceRole(String hostname, ServiceRoleOperateCommand cmd);

    ExecResult restartServiceRole(String hostname, ServiceRoleOperateCommand cmd);

    ExecResult serviceRoleStatus(String hostname, ServiceRoleOperateCommand cmd);

    // ─── Auxiliary ───────────────────────────────────────────────────────────

    ExecResult createUnixGroup(String hostname, CreateUnixGroupCommand cmd);

    ExecResult deleteUnixGroup(String hostname, DelUnixGroupCommand cmd);

    ExecResult createUnixUser(String hostname, CreateUnixUserCommand cmd);

    ExecResult deleteUnixUser(String hostname, DelUnixUserCommand cmd);

    ExecResult operateFile(String hostname, FileOperateCommand cmd);

    ExecResult generateAlertConfig(String hostname, GenerateAlertConfigCommand cmd);
}
