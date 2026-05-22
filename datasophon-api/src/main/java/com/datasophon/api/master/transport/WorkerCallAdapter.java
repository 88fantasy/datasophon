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
 * Master → Worker 调用适配接口（Phase 2 + Phase 3）。
 *
 * <p>封装 Pekko actor ask 和 gRPC 两种传输方式，Handler 只依赖此接口，
 * 通过 {@link TransportWorkerCallAdapter}（@Primary）根据
 * {@code datasophon.transport} 配置路由到具体实现。</p>
 *
 * <p>在 Phase 5（删除 Pekko）前，此接口同时支持两路。</p>
 */
public interface WorkerCallAdapter {

    // ─── Phase 2: Service Role ────────────────────────────────────────────────

    ExecResult installServiceRole(String hostname, InstallServiceRoleCommand cmd);

    ExecResult configureServiceRole(String hostname, GenerateServiceConfigCommand cmd);

    ExecResult startServiceRole(String hostname, ServiceRoleOperateCommand cmd);

    ExecResult stopServiceRole(String hostname, ServiceRoleOperateCommand cmd);

    ExecResult restartServiceRole(String hostname, ServiceRoleOperateCommand cmd);

    ExecResult serviceRoleStatus(String hostname, ServiceRoleOperateCommand cmd);

    // ─── Phase 3: Auxiliary ───────────────────────────────────────────────────

    ExecResult createUnixGroup(String hostname, CreateUnixGroupCommand cmd);

    ExecResult deleteUnixGroup(String hostname, DelUnixGroupCommand cmd);

    ExecResult createUnixUser(String hostname, CreateUnixUserCommand cmd);

    ExecResult deleteUnixUser(String hostname, DelUnixUserCommand cmd);

    ExecResult operateFile(String hostname, FileOperateCommand cmd);

    ExecResult generateAlertConfig(String hostname, GenerateAlertConfigCommand cmd);
}
