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

package com.datasophon.api.master.service;

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.common.command.HdfsEcCommand;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;

import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * HDFS 扩缩容白名单管理 Spring Service，业务逻辑来自 {@link HdfsECActor}。
 */
@Slf4j
@Service
public class HdfsECService {
    
    private final ClusterServiceRoleInstanceService roleInstanceService;
    
    public HdfsECService(ClusterServiceRoleInstanceService roleInstanceService) {
        this.roleInstanceService = roleInstanceService;
    }
    
    /**
     * 异步更新 HDFS DataNode 白名单（替代 HdfsECActor.tell(command)）。
     */
    @Async("masterExecutor")
    public void manageHdfsEC(HdfsEcCommand command) {
        List<ClusterServiceRoleInstanceEntity> datanodes = roleInstanceService.lambdaQuery()
                .eq(ClusterServiceRoleInstanceEntity::getServiceId, command.getServiceInstanceId())
                .eq(ClusterServiceRoleInstanceEntity::getServiceRoleName, "DataNode")
                .list();
        TreeSet<String> hostnameSet = datanodes.stream()
                .map(ClusterServiceRoleInstanceEntity::getHostname)
                .collect(Collectors.toCollection(TreeSet::new));
        try {
            ProcessUtils.hdfsEcMethond(command.getServiceInstanceId(), roleInstanceService, hostnameSet,
                    "whitelist", "NameNode");
        } catch (Exception e) {
            log.error("HDFS EC manage failed for serviceInstanceId={}: {}",
                    command.getServiceInstanceId(), e.getMessage(), e);
        }
    }
}
