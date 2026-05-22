/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.datasophon.worker;

import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.lifecycle.ServerLifeCycleManager;
import com.datasophon.common.model.StartWorkerMessage;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.actor.RemoteEventActor;
import com.datasophon.worker.actor.WorkerActor;
import com.datasophon.worker.grpc.MasterRegistryClient;
import com.datasophon.worker.grpc.WorkerGrpcServer;
import com.datasophon.worker.utils.ActorUtils;
import com.datasophon.worker.utils.UnixUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSONObject;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSelection;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.event.EventStream;
import org.apache.pekko.remote.AssociatedEvent;
import org.apache.pekko.remote.AssociationErrorEvent;
import org.apache.pekko.remote.DisassociatedEvent;

public class WorkerApplicationServer {
    
    private static final Logger logger = LoggerFactory.getLogger(WorkerApplicationServer.class);
    
    private static final String USER_DIR = "user.dir";

    private static final String WORKER = "worker";
    
    private static final String SH = "sh";
    
    private static final String NODE = "node";
    
    private static final String HADOOP = "hadoop";
    
    public static void main(String[] args) throws UnknownHostException {
        String hostname = InetAddress.getLocalHost().getHostName();
        String workDir = System.getProperty(USER_DIR);
        String masterHost = PropertyUtils.getString(Constants.MASTER_HOST);
        String cpuArchitecture = ShellUtils.getCpuArchitecture();
        int clusterId = PropertyUtils.getInt("clusterId");

        CacheUtils.put(Constants.HOSTNAME, hostname);
        CacheUtils.put(Constants.CPU_ARCH, cpuArchitecture);
        // init actor
        ActorSystem system = initActor(hostname);
        ActorUtils.setActorSystem(system);

        subscribeRemoteEvent(system);

        startNodeExporter(workDir, cpuArchitecture);

        Map<String, String> userMap = new HashMap<>(16);
        initUserMap(userMap);

        createDefaultUser(userMap);

        tellToMaster(hostname, workDir, masterHost, cpuArchitecture, system);
        logger.info("start worker");

        // ── gRPC 双轨（transport=grpc 或 both 时启用） ───────────────────────
        // transport 属性从 conf/common.properties 读取，默认 pekko
        String transport = PropertyUtils.getString("transport", "pekko");
        MasterRegistryClient registryClient = null;
        WorkerGrpcServer workerGrpcServer = null;
        if ("grpc".equals(transport) || "both".equals(transport)) {
            // 1. 先启动 gRPC Server，再向 Master 注册，确保注册成功时 Server 已就绪
            workerGrpcServer = new WorkerGrpcServer();
            try {
                workerGrpcServer.start();
            } catch (Exception e) {
                logger.error("Failed to start worker gRPC server, gRPC channel will be unavailable", e);
            }
            // 2. 向 Master 注册（携带 grpcPort=18082）
            registryClient = new MasterRegistryClient(masterHost, hostname, cpuArchitecture, clusterId);
            registryClient.register();
            logger.info("Worker gRPC started (transport={})", transport);
        }
        final MasterRegistryClient finalRegistryClient = registryClient;
        final WorkerGrpcServer finalWorkerGrpcServer = workerGrpcServer;

        /*
         * registry hooks, which are called before the process exits
         */
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    if (!ServerLifeCycleManager.isStopped()) {
                                        if (finalRegistryClient != null) {
                                            try {
                                                finalRegistryClient.close();
                                            } catch (Exception e) {
                                                logger.warn("Failed to close gRPC registry client", e);
                                            }
                                        }
                                        if (finalWorkerGrpcServer != null) {
                                            finalWorkerGrpcServer.stop();
                                        }
                                        close("WorkerServer shutdown hook");
                                    }
                                }));
    }
    
    private static void initUserMap(Map<String, String> userMap) {
        userMap.put("hdfs", HADOOP);
        userMap.put("yarn", HADOOP);
        userMap.put("hive", HADOOP);
        userMap.put("mapred", HADOOP);
        userMap.put("hbase", HADOOP);
        userMap.put("kyuubi", HADOOP);
        userMap.put("flink", HADOOP);
        userMap.put("elastic", "elastic");
    }
    
    private static void createDefaultUser(Map<String, String> userMap) {
        for (Map.Entry<String, String> entry : userMap.entrySet()) {
            String user = entry.getKey();
            String group = entry.getValue();
            if (!UnixUtils.isGroupExists(group)) {
                UnixUtils.createUnixGroup(group);
            }
            UnixUtils.createUnixUser(user, group, null);
        }
    }
    
    private static ActorSystem initActor(String hostname) {
        Config config = ConfigFactory.parseString("pekko.remote.artery.canonical.hostname=" + hostname);
        ActorSystem system =
                ActorSystem.create(Constants.DATASOPHON, config.withFallback(ConfigFactory.load()));
        system.actorOf(Props.create(WorkerActor.class), WORKER);
        return system;
    }
    
    private static void subscribeRemoteEvent(ActorSystem system) {
        ActorRef remoteEventActor =
                system.actorOf(Props.create(RemoteEventActor.class), "remoteEventActor");
        EventStream eventStream = system.eventStream();
        eventStream.subscribe(remoteEventActor, AssociationErrorEvent.class);
        eventStream.subscribe(remoteEventActor, AssociatedEvent.class);
        eventStream.subscribe(remoteEventActor, DisassociatedEvent.class);
    }
    
    private static void tellToMaster(
                                     String hostname,
                                     String workDir,
                                     String masterHost,
                                     String cpuArchitecture,
                                     ActorSystem system) {
        ActorSelection workerStartActor =
                system.actorSelection(
                        "pekko://datasophon@" + masterHost + ":2551/user/workerStartActor");
        ExecResult result = ShellUtils.execShell(workDir + "/script/host-info-collect.sh");
        if (!result.getExecResult()) {
            logger.error("host info collect error:{}", result.getExecErrOut());
        } else {
            logger.info("host info collect success:{}", result.getExecOut());
        }
        StartWorkerMessage startWorkerMessage =
                JSONObject.parseObject(result.getExecOut(), StartWorkerMessage.class);
        startWorkerMessage.setCpuArchitecture(cpuArchitecture);
        startWorkerMessage.setClusterId(PropertyUtils.getInt("clusterId"));
        startWorkerMessage.setHostname(hostname);
        workerStartActor.tell(startWorkerMessage, ActorRef.noSender());
    }
    
    public static void close(String cause) {
        stopNodeExporter();
        logger.info("Worker server stopped");
    }
    
    private static void stopNodeExporter() {
        String workDir = System.getProperty(USER_DIR);
        String cpuArchitecture = ShellUtils.getCpuArchitecture();
        operateNodeExporter(workDir, cpuArchitecture, "stop");
    }
    
    private static void startNodeExporter(String workDir, String cpuArchitecture) {
        operateNodeExporter(workDir, cpuArchitecture, "restart");
    }
    
    private static void operateNodeExporter(
                                            String workDir, String cpuArchitecture, String operate) {
        ArrayList<String> commands = new ArrayList<>();
        commands.add(SH);
        if (Constants.X86_64.equals(cpuArchitecture)) {
            commands.add(workDir + "/node/x86/control.sh");
        } else {
            commands.add(workDir + "/node/arm/control.sh");
        }
        commands.add(operate);
        commands.add(NODE);
        ShellUtils.execWithStatus(Constants.INSTALL_PATH, commands, 60L, logger);
    }
}
