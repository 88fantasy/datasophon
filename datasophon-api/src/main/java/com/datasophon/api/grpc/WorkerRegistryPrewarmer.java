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

package com.datasophon.api.grpc;

import com.datasophon.common.Constants;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.mapper.ClusterHostMapper;
import com.datasophon.domain.host.enums.HostState;
import com.datasophon.grpc.api.GrpcConstants;

import jakarta.annotation.PostConstruct;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * Master 启动时预热 {@link WorkerRegistry}，消除重启窗口期（H3 修复）。
 *
 * <p>从数据库查询所有已纳管（managed=YES）且处于 RUNNING 状态的主机，
 * 以默认 gRPC 端口（18082）预填注册表。预热条目在 90 秒内有效，
 * Worker 在首次心跳/注册时会覆盖为真实端点。</p>
 *
 * <p>这样 Master 重启后注册表不为空，避免 30~60 秒窗口期内 DAG 操作因
 * "Worker not registered" 而成片失败。</p>
 */
@Component
public class WorkerRegistryPrewarmer {
    
    private static final Logger log = LoggerFactory.getLogger(WorkerRegistryPrewarmer.class);
    
    // 端口常量统一从 GrpcConstants 读取，与 Worker 侧保持单点事实（SSOT）
    
    private final WorkerRegistry workerRegistry;
    private final ClusterHostMapper clusterHostMapper;
    
    public WorkerRegistryPrewarmer(WorkerRegistry workerRegistry, ClusterHostMapper clusterHostMapper) {
        this.workerRegistry = workerRegistry;
        this.clusterHostMapper = clusterHostMapper;
    }
    
    @PostConstruct
    public void prewarm() {
        try {
            List<ClusterHostDO> hosts = clusterHostMapper.selectList(
                    new QueryWrapper<ClusterHostDO>()
                            .eq(Constants.MANAGED, 1)
                            .eq(Constants.HOST_STATE, HostState.RUNNING));
            
            if (hosts.isEmpty()) {
                log.info("WorkerRegistry prewarm: no managed+running hosts found in DB");
                return;
            }
            
            for (ClusterHostDO host : hosts) {
                workerRegistry.preRegister(host.getHostname(), GrpcConstants.WORKER_GRPC_PORT, host.getClusterId(), host.getIp());
            }
            log.info("WorkerRegistry prewarm: pre-registered {} hosts from DB (port={})",
                    hosts.size(), GrpcConstants.WORKER_GRPC_PORT);
        } catch (Exception e) {
            // 预热失败为非致命错误：注册表仍可正常工作，Workers 在首次心跳时会主动注册
            log.warn("WorkerRegistry prewarm failed (non-fatal, workers will re-register via heartbeat): {}",
                    e.getMessage());
        }
    }
}
