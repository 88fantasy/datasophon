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

package com.datasophon.api.master.transport;

import com.datasophon.api.grpc.WorkerCommandClient;
import com.datasophon.common.command.ExecuteCmdCommand;
import com.datasophon.common.command.FileOperateCommand;
import com.datasophon.common.command.GenerateServiceConfigCommand;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.command.remote.CreateUnixGroupCommand;
import com.datasophon.common.command.remote.CreateUnixUserCommand;
import com.datasophon.common.command.remote.DelUnixGroupCommand;
import com.datasophon.common.command.remote.DelUnixUserCommand;
import com.datasophon.common.utils.ExecResult;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * {@link WorkerCallAdapter} 的 gRPC 实现，通过 {@link WorkerCommandClient} 向 Worker 发送命令。
 * <p>
 * 标记 {@code @Primary}，所有注入 {@code WorkerCallAdapter} 的 Handler 均获取此实现。
 * </p>
 */
@Primary
@Component
public class GrpcWorkerCallAdapter implements WorkerCallAdapter {
    
    private final WorkerCommandClient client;
    
    public GrpcWorkerCallAdapter(WorkerCommandClient client) {
        this.client = client;
    }
    
    @Override
    public ExecResult executeCmd(String hostname, ExecuteCmdCommand cmd) {
        if (cmd.getCommandLine() != null && !cmd.getCommandLine().isEmpty()) {
            return client.executeCmdLine(hostname, cmd.getCommandLine());
        }
        return client.executeCmd(hostname, cmd.getCommands());
    }
    
    @Override
    public ExecResult installServiceRole(String hostname, InstallServiceRoleCommand cmd) {
        return client.installServiceRole(hostname, cmd);
    }
    
    @Override
    public ExecResult configureServiceRole(String hostname, GenerateServiceConfigCommand cmd) {
        return client.configureServiceRole(hostname, cmd);
    }
    
    @Override
    public ExecResult startServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return client.startServiceRole(hostname, cmd);
    }
    
    @Override
    public ExecResult stopServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return client.stopServiceRole(hostname, cmd);
    }
    
    @Override
    public ExecResult restartServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return client.restartServiceRole(hostname, cmd);
    }
    
    @Override
    public ExecResult serviceRoleStatus(String hostname, ServiceRoleOperateCommand cmd) {
        return client.serviceRoleStatus(hostname, cmd);
    }
    
    // ─── Phase 3 ──────────────────────────────────────────────────────────────
    
    @Override
    public ExecResult createUnixGroup(String hostname, CreateUnixGroupCommand cmd) {
        return client.createUnixGroup(hostname, cmd);
    }
    
    @Override
    public ExecResult deleteUnixGroup(String hostname, DelUnixGroupCommand cmd) {
        return client.deleteUnixGroup(hostname, cmd);
    }
    
    @Override
    public ExecResult createUnixUser(String hostname, CreateUnixUserCommand cmd) {
        return client.createUnixUser(hostname, cmd);
    }
    
    @Override
    public ExecResult deleteUnixUser(String hostname, DelUnixUserCommand cmd) {
        return client.deleteUnixUser(hostname, cmd);
    }
    
    @Override
    public ExecResult operateFile(String hostname, FileOperateCommand cmd) {
        return client.operateFile(hostname, cmd);
    }
}
