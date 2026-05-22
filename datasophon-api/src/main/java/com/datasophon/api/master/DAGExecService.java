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
 * 物理集群 DAG 执行 Spring Service，在同包访问 {@link DAGExecActor#doOnReceive} 的 protected 方法。
 *
 * <p>与 {@link DAGExecActor} 位于同一包，因此可以合法访问其 {@code protected} 方法，
 * 无需修改 Actor 类，也无需重复业务逻辑。</p>
 */
@Slf4j
@Service
public class DAGExecService {

    /**
     * 异步执行物理集群 DAG（替代 DAGExecActor.tell(cmd)）。
     */
    @Async("masterExecutor")
    public void execDAG(DAGExecCommand cmd) {
        // DAGExecActor.doOnReceive is protected — accessible from same package (com.datasophon.api.master)
        // AbstractActor constructor is safe to call outside ActorSystem when no Pekka APIs are used
        try {
            DAGExecActor actor = new DAGExecActor();
            actor.doOnReceive(cmd);
        } catch (Throwable e) {
            log.error("DAG execution failed for dagId={}: {}", cmd.getDagId(), e.getMessage(), e);
        }
    }
}
