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

import com.datasophon.api.master.ActorUtils;
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
import org.apache.pekko.actor.ActorSelection;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

/**
 * Pekko 传输实现：通过 ActorSelection ask/await 调用 Worker Actor。
 *
 * <p>当 {@code datasophon.transport=pekko} 或 {@code both} 时由
 * {@link TransportWorkerCallAdapter} 委托调用。</p>
 */
@Component
public class PekkaWorkerCallAdapter implements WorkerCallAdapter {

    private static final Logger log = LoggerFactory.getLogger(PekkaWorkerCallAdapter.class);

    private static final String PEKKA_BASE = "pekko://datasophon@%s:2552/user/worker/";
    private static final int DEFAULT_TIMEOUT_SECONDS = 180;

    @Override
    public ExecResult executeCmd(String hostname, ExecuteCmdCommand cmd) {
        return ask(hostname, "executeCmdActor", cmd, DEFAULT_TIMEOUT_SECONDS);
    }

    @Override
    public ExecResult installServiceRole(String hostname, InstallServiceRoleCommand cmd) {
        return ask(hostname, "installServiceActor", cmd, DEFAULT_TIMEOUT_SECONDS);
    }

    @Override
    public ExecResult configureServiceRole(String hostname, GenerateServiceConfigCommand cmd) {
        return ask(hostname, "configureServiceActor", cmd, DEFAULT_TIMEOUT_SECONDS);
    }

    @Override
    public ExecResult startServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return ask(hostname, "startServiceActor", cmd, DEFAULT_TIMEOUT_SECONDS);
    }

    @Override
    public ExecResult stopServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return ask(hostname, "stopServiceActor", cmd, DEFAULT_TIMEOUT_SECONDS);
    }

    @Override
    public ExecResult restartServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return ask(hostname, "restartServiceActor", cmd, DEFAULT_TIMEOUT_SECONDS);
    }

    @Override
    public ExecResult serviceRoleStatus(String hostname, ServiceRoleOperateCommand cmd) {
        return ask(hostname, "serviceStatusActor", cmd, DEFAULT_TIMEOUT_SECONDS);
    }

    // ─── Phase 3 ──────────────────────────────────────────────────────────────

    @Override
    public ExecResult createUnixGroup(String hostname, CreateUnixGroupCommand cmd) {
        return ask(hostname, "unixGroupActor", cmd, DEFAULT_TIMEOUT_SECONDS);
    }

    @Override
    public ExecResult deleteUnixGroup(String hostname, DelUnixGroupCommand cmd) {
        return ask(hostname, "unixGroupActor", cmd, DEFAULT_TIMEOUT_SECONDS);
    }

    @Override
    public ExecResult createUnixUser(String hostname, CreateUnixUserCommand cmd) {
        return ask(hostname, "unixUserActor", cmd, DEFAULT_TIMEOUT_SECONDS);
    }

    @Override
    public ExecResult deleteUnixUser(String hostname, DelUnixUserCommand cmd) {
        return ask(hostname, "unixUserActor", cmd, DEFAULT_TIMEOUT_SECONDS);
    }

    @Override
    public ExecResult operateFile(String hostname, FileOperateCommand cmd) {
        return ask(hostname, "fileOperateActor", cmd, DEFAULT_TIMEOUT_SECONDS);
    }

    @Override
    public ExecResult generateAlertConfig(String hostname, GenerateAlertConfigCommand cmd) {
        return ask(hostname, "alertConfigActor", cmd, DEFAULT_TIMEOUT_SECONDS);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static ExecResult ask(String hostname, String actorName, Object cmd, int timeoutSeconds) {
        ActorSelection actor = ActorUtils.actorSystem.actorSelection(
                String.format(PEKKA_BASE + actorName, hostname));
        Timeout timeout = new Timeout(Duration.create(timeoutSeconds, TimeUnit.SECONDS));
        Future<Object> future = Patterns.ask(actor, cmd, timeout);
        try {
            return (ExecResult) Await.result(future, timeout.duration());
        } catch (Exception e) {
            log.error("Pekka ask {} {} failed: {}", hostname, actorName, e.getMessage(), e);
            return new ExecResult();
        }
    }
}
