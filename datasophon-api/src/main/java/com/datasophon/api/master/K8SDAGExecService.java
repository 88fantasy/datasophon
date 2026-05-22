/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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

package com.datasophon.api.master;

import com.datasophon.common.command.dag.DAGExecCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * K8s DAG 执行 Spring Service，与 {@link K8SDAGExecActor} 同包，
 * 可合法调用其 protected {@code doOnReceive} 方法，替代 Actor.tell()。
 */
@Slf4j
@Service
public class K8SDAGExecService {

    /**
     * 异步执行 K8s DAG（替代 K8SDAGExecActor.tell(cmd)）。
     */
    @Async("masterExecutor")
    public void execK8SDAG(DAGExecCommand cmd) {
        try {
            K8SDAGExecActor actor = new K8SDAGExecActor();
            actor.doOnReceive(cmd);
        } catch (Throwable e) {
            log.error("K8S DAG execution failed for dagId={}: {}", cmd.getDagId(), e.getMessage(), e);
        }
    }
}
