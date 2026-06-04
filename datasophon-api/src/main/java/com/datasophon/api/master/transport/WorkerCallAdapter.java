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

/**
 * Master → Worker 调用适配接口。
 *
 * <p>Handler 注入此接口，唯一实现为 {@link GrpcWorkerCallAdapter}（{@code @Primary}）。</p>
 */
public interface WorkerCallAdapter {
    
    ExecResult executeCmd(String hostname, ExecuteCmdCommand cmd);
    
    // ─── Service Role ─────────────────────────────────────────────────────────
    
    ExecResult installServiceRole(String hostname, InstallServiceRoleCommand cmd);
    
    ExecResult configureServiceRole(String hostname, GenerateServiceConfigCommand cmd);
    
    ExecResult startServiceRole(String hostname, ServiceRoleOperateCommand cmd);
    
    ExecResult stopServiceRole(String hostname, ServiceRoleOperateCommand cmd);
    
    ExecResult restartServiceRole(String hostname, ServiceRoleOperateCommand cmd);
    
    ExecResult serviceRoleStatus(String hostname, ServiceRoleOperateCommand cmd);
    
    // ─── Auxiliary ───────────────────────────────────────────────────────────
    
    ExecResult createUnixGroup(String hostname, CreateUnixGroupCommand cmd);
    
    ExecResult deleteUnixGroup(String hostname, DelUnixGroupCommand cmd);
    
    ExecResult createUnixUser(String hostname, CreateUnixUserCommand cmd);
    
    ExecResult deleteUnixUser(String hostname, DelUnixUserCommand cmd);
    
    ExecResult operateFile(String hostname, FileOperateCommand cmd);
    
    ExecResult generateAlertConfig(String hostname, GenerateAlertConfigCommand cmd);
}
