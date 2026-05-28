/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

 *
 */

package com.datasophon.worker;

import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.lifecycle.ServerLifeCycleManager;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.grpc.MasterCallbackClient;
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

        // 初始化 Master 回调客户端（供策略类静态获取），在注册前就绪
        MasterCallbackClient.init(masterHost);

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
                MasterCallbackClient client = MasterCallbackClient.getInstance();
                if (client != null) {
                    client.close();
                }
                workerGrpcServer.stop();
                close("WorkerServer shutdown hook");
            }
        }));

        // 阻塞主线程，防止 JVM 因无非守护线程退出（容器/前台运行）
        try {
            workerGrpcServer.awaitTermination();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
