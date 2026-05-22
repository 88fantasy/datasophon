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

package com.datasophon.api.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;

/**
 * 为 Master 端本地 Actor 收敛后的异步执行配置：
 * <ul>
 *   <li>{@code masterExecutor} — 通用 @Async 线程池（替代 Pekka fork-join dispatcher）</li>
 *   <li>{@code taskScheduler} — 延迟/定时任务调度器（替代 actorSystem.scheduler().scheduleOnce）</li>
 * </ul>
 */
@Configuration
public class MasterAsyncConfig {

    /**
     * 通用异步执行线程池，供 @Async("masterExecutor") 使用。
     * 核心 10 线程 / 最大 50 线程 / 队列 200，对应原 Pekka my-forkjoin-dispatcher。
     */
    @Bean("masterExecutor")
    public Executor masterExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("master-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * 任务调度器，供延迟执行（scheduleOnce 等价）以及 @Scheduled 方法使用。
     * Spring Boot 的 @EnableScheduling 默认使用此 bean（名称固定为 "taskScheduler"）。
     */
    @Bean("taskScheduler")
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("master-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        return scheduler;
    }
}
