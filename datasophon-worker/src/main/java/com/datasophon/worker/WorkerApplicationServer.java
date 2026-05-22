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
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.grpc.MasterRegistryClient;
import com.datasophon.worker.grpc.WorkerGrpcServer;
import com.datasophon.worker.utils.UnixUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker 进程入口。
 *
 * <p>Phase 5：移除 Pekka ActorSystem；全量走 gRPC 通信。</p>
 */
public class WorkerApplicationServer {

    private static final Logger logger = LoggerFactory.getLogger(WorkerApplicationServer.class);

    private static final String USER_DIR = "user.dir";
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

        startNodeExporter(workDir, cpuArchitecture);

        Map<String, String> userMap = new HashMap<>(16);
        initUserMap(userMap);
        createDefaultUser(userMap);

        // 启动 gRPC Server，再向 Master 注册，确保注册成功时 Server 已就绪
        WorkerGrpcServer workerGrpcServer = new WorkerGrpcServer();
        try {
            workerGrpcServer.start();
        } catch (Exception e) {
            logger.error("Failed to start worker gRPC server, communication with master will fail", e);
        }

        MasterRegistryClient registryClient =
                new MasterRegistryClient(masterHost, hostname, cpuArchitecture, clusterId);
        registryClient.register();
        logger.info("Worker started, hostname={}", hostname);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!ServerLifeCycleManager.isStopped()) {
                try {
                    registryClient.close();
                } catch (Exception e) {
                    logger.warn("Failed to close gRPC registry client", e);
                }
                workerGrpcServer.stop();
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

    public static void close(String cause) {
        stopNodeExporter();
        logger.info("Worker server stopped, cause: {}", cause);
    }

    private static void stopNodeExporter() {
        String workDir = System.getProperty(USER_DIR);
        String cpuArchitecture = ShellUtils.getCpuArchitecture();
        operateNodeExporter(workDir, cpuArchitecture, "stop");
    }

    private static void startNodeExporter(String workDir, String cpuArchitecture) {
        operateNodeExporter(workDir, cpuArchitecture, "restart");
    }

    private static void operateNodeExporter(String workDir, String cpuArchitecture, String operate) {
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
