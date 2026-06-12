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

package com.datasophon.api.utils;

import com.datasophon.api.grpc.WorkerCommandClient;
import com.datasophon.api.master.transport.WorkerCallAdapter;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.common.Constants;
import com.datasophon.common.command.FileOperateCommand;
import com.datasophon.common.command.remote.CreateUnixGroupCommand;
import com.datasophon.common.command.remote.DelUnixGroupCommand;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessUtils.class);
    
    public static void hdfsEcMethond(Integer serviceInstanceId, ClusterServiceRoleInstanceService roleInstanceService,
                                     TreeSet<String> list, String type, String roleName) throws Exception {
        
        List<ClusterServiceRoleInstanceEntity> namenodes = roleInstanceService.lambdaQuery()
                .eq(ClusterServiceRoleInstanceEntity::getServiceId, serviceInstanceId)
                .eq(ClusterServiceRoleInstanceEntity::getServiceRoleName, roleName)
                .list();
        
        // 更新namenode节点的whitelist白名单（按主机 fan-out 并行下发）
        WorkerCallAdapter adapter = SpringTool.getApplicationContext().getBean(WorkerCallAdapter.class);
        WorkerCommandClient workerCommandClient =
                SpringTool.getApplicationContext().getBean(WorkerCommandClient.class);
        List<Runnable> tasks = new ArrayList<>(namenodes.size());
        for (ClusterServiceRoleInstanceEntity namenode : namenodes) {
            tasks.add(() -> {
                FileOperateCommand fileOperateCommand = new FileOperateCommand();
                fileOperateCommand.setLines(list);
                fileOperateCommand.setPath(Constants.INSTALL_PATH + "/hadoop/etc/hadoop/" + type);
                ExecResult fileOperateResult = adapter.operateFile(namenode.getHostname(), fileOperateCommand);
                if (Objects.nonNull(fileOperateResult) && fileOperateResult.getExecResult()) {
                    logger.info("write {} success in namenode {}", type, namenode.getHostname());
                    // 刷新白名单
                    ArrayList<String> refreshCmds = new ArrayList<>();
                    refreshCmds.add(Constants.INSTALL_PATH + "/hadoop/bin/hdfs");
                    refreshCmds.add("dfsadmin");
                    refreshCmds.add("-refreshNodes");
                    ExecResult execResult = workerCommandClient.executeCmd(namenode.getHostname(), refreshCmds);
                    if (execResult.getExecResult()) {
                        logger.info("hdfs dfsadmin -refreshNodes success at {}", namenode.getHostname());
                    }
                }
            });
        }
        runConcurrently(tasks);
    }
    
    /**
     * 把一组阻塞任务（通常是逐主机 gRPC 调用）fan-out 到 masterExecutor 并发执行并等待全部完成。
     * 线程池满时退化为调用线程串行执行；任一任务异常经 join 以 CompletionException 抛出。
     */
    private static void runConcurrently(List<Runnable> tasks) {
        Executor executor = (Executor) SpringTool.getApplicationContext().getBean("masterExecutor");
        List<CompletableFuture<Void>> futures = new ArrayList<>(tasks.size());
        for (Runnable task : tasks) {
            try {
                futures.add(CompletableFuture.runAsync(task, executor));
            } catch (RejectedExecutionException e) {
                task.run();
            }
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
    
    public static String getExceptionMessage(Exception ex) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream pout = new PrintStream(out);
        ex.printStackTrace(pout);
        String ret = out.toString();
        pout.close();
        try {
            out.close();
        } catch (Exception ignored) {
        }
        return ret;
    }
    
    public static void syncUserGroupToHosts(List<ClusterHostDO> hostList, String groupName, String operate) {
        WorkerCallAdapter workerCallAdapter =
                SpringTool.getApplicationContext().getBean(WorkerCallAdapter.class);
        List<Runnable> tasks = new ArrayList<>(hostList.size());
        for (ClusterHostDO hostEntity : hostList) {
            tasks.add(() -> {
                try {
                    if ("groupadd".equalsIgnoreCase(operate)) {
                        CreateUnixGroupCommand cmd = new CreateUnixGroupCommand();
                        cmd.setGroupName(groupName);
                        workerCallAdapter.createUnixGroup(hostEntity.getHostname(), cmd);
                    } else {
                        DelUnixGroupCommand cmd = new DelUnixGroupCommand();
                        cmd.setGroupName(groupName);
                        workerCallAdapter.deleteUnixGroup(hostEntity.getHostname(), cmd);
                    }
                } catch (Exception e) {
                    logger.warn("syncUserGroupToHosts failed for host {}: {}", hostEntity.getHostname(), e.getMessage());
                }
            });
        }
        runConcurrently(tasks);
    }
    
}
