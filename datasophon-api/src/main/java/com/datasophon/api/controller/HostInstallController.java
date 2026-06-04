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

package com.datasophon.api.controller;

import com.datasophon.api.security.UserPermission;
import com.datasophon.api.service.HostInstallService;
import com.datasophon.common.utils.Result;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("host/install")
public class HostInstallController extends ApiController {
    
    @Autowired
    private HostInstallService hostInstallService;
    
    /**
     * 获取安装步骤
     */
    @GetMapping("/getInstallStep")
    public Result getInstallStep(Integer type) {
        return hostInstallService.getInstallStep(type);
    }
    
    /**
     * 解析主机列表
     */
    @PostMapping("/analysisHostList")
    @UserPermission
    public Result analysisHostList(@RequestParam Integer clusterId,
                                   @RequestParam @NotBlank(message = "主机列表不能为空") String hosts,
                                   @RequestParam @Pattern(regexp = "(?=.*?[a-z_])[a-zA-Z0-9._\\-]{1,30}", message = "非法的SSH用户名") String sshUser,
                                   @RequestParam(required = false) String sshPass,
                                   @RequestParam @NotNull(message = "SSH端口必填") @Min(value = 1, message = "非法的SSH端口") @Max(value = 65535, message = "非法的SSH端口") Integer sshPort,
                                   @RequestParam Integer page,
                                   @RequestParam Integer pageSize) {
        return hostInstallService.analysisHostList(clusterId, hosts, sshUser, sshPass, sshPort, page, pageSize);
    }
    
    /**
     * 查询主机校验状态
     */
    @PostMapping("/getHostCheckStatus")
    @UserPermission
    public Result getHostCheckStatus(Integer clusterId, String sshUser, Integer sshPort) {
        return hostInstallService.getHostCheckStatus(clusterId, sshUser, sshPort);
    }
    
    /**
     * 重新进行主机环境校验
     */
    @PostMapping("/rehostCheck")
    @UserPermission
    public Result rehostCheck(Integer clusterId, String hostnames, String sshUser, Integer sshPort) {
        return hostInstallService.rehostCheck(clusterId, hostnames, sshUser, sshPort);
    }
    
    /**
     * 查询主机校验是否全部完成
     */
    @PostMapping("/hostCheckCompleted")
    @UserPermission
    public Result hostCheckCompleted(Integer clusterId) {
        return hostInstallService.hostCheckCompleted(clusterId);
    }
    
    /**
     * 主机管理agent分发安装进度列表
     */
    @PostMapping("/dispatcherHostAgentList")
    @UserPermission
    public Result dispatcherHostAgentList(Integer clusterId, Integer installStateCode, Integer page, Integer pageSize) {
        return hostInstallService.dispatcherHostAgentList(clusterId, installStateCode, page, pageSize);
    }
    
    @PostMapping("/dispatcherHostAgentCompleted")
    public Result dispatcherHostAgentCompleted(Integer clusterId) {
        return hostInstallService.dispatcherHostAgentCompleted(clusterId);
    }
    
    /**
     * 主机管理agent分发取消
     */
    @PostMapping("/cancelDispatcherHostAgent")
    public Result cancelDispatcherHostAgent(Integer clusterId, String hostname, Integer installStateCode) {
        return hostInstallService.cancelDispatcherHostAgent(clusterId, hostname, installStateCode);
    }
    
    /**
     * 主机管理agent分发安装重试
     *
     * @param clusterId
     * @param hostnames
     * @return
     */
    @PostMapping("/reStartDispatcherHostAgent")
    public Result reStartDispatcherHostAgent(Integer clusterId, String hostnames) {
        return hostInstallService.reStartDispatcherHostAgent(clusterId, hostnames);
    }
    
    /**
     * 主机管理agent操作(启动(start)、停止(stop)、重启(restart))
     * @param clusterHostIds
     * @param commandType
     * @return
     */
    @PostMapping("/generateHostAgentCommand")
    public Result generateHostAgentCommand(
                                           @RequestParam String clusterHostIds,
                                           @RequestParam String commandType) throws Exception {
        return hostInstallService.generateHostAgentCommand(clusterHostIds, commandType);
    }
    
    /**
     * 启动/停止 主机上服务启动
     * @param clusterHostIds
     * @param commandType
     * @return
     */
    @PostMapping("/generateHostServiceCommand")
    public Result generateHostServiceCommand(
                                             @RequestParam String clusterHostIds,
                                             @RequestParam String commandType) throws Exception {
        return hostInstallService.generateHostServiceCommand(clusterHostIds, commandType);
    }
    
}
