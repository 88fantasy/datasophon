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


package com.datasophon.api.master;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datasophon.api.master.service.ClusterStatusService;
import com.datasophon.api.master.service.HostCheckService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.strategy.ServiceRoleStrategy;
import com.datasophon.api.strategy.ServiceRoleStrategyContext;
import com.datasophon.api.utils.CheckUtils;
import com.datasophon.common.model.HostInfo;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Master 侧周期性巡检 Spring Service，替代 {@link ActorUtils#init()} 中的三个
 * {@code actorSystem.scheduler().scheduleWithFixedDelay()} 调用。
 *
 *
 * <ul>
 *   <li>节点检测：初始延迟 30s，间隔 300s（5 分钟）</li>
 *   <li>服务角色检测：初始延迟 15s，间隔 30s</li>
 *   <li>集群状态检测：初始延迟 30s，间隔 60s（1 分钟）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasterScheduledService {

    private final HostCheckService hostCheckService;
    private final ClusterStatusService clusterStatusService;

    /**
     * 节点检测任务，每 5 分钟巡检一次所有物理集群主机的在线状态。
     * 等价于原 ActorUtils: scheduleWithFixedDelay(30s, 300s, hostCheckActor)
     */
    @Scheduled(initialDelayString = "30000", fixedDelayString = "300000")
    public void checkHosts() {
        log.debug("Scheduled: start host check");
        try {
            hostCheckService.checkHosts(null);
        } catch (Throwable e) {
            log.error("Scheduled host check failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 服务角色状态检测，每 30s 巡检一次所有服务角色实例。
     * 等价于原 ActorUtils: scheduleWithFixedDelay(15s, 30s, serviceRoleCheckActor)
     *
     * <p>原 {@code ServiceRoleCheckActor.doOnReceive()} 逻辑内联于此，
     * 规避跨包 protected 访问限制。</p>
     */
    @Scheduled(initialDelayString = "15000", fixedDelayString = "30000")
    public void checkServiceRoles() {
        log.debug("Scheduled: start service role check");
        try {
            ClusterServiceRoleInstanceService roleInstanceService =
                    SpringUtil.getBean(ClusterServiceRoleInstanceService.class);
            List<ClusterServiceRoleInstanceEntity> list =
                    roleInstanceService.list(new QueryWrapper<>());
            Map<String, ClusterServiceRoleInstanceEntity> map = list.stream()
                    .collect(Collectors.toMap(
                            e -> e.getHostname() + e.getServiceRoleName(),
                            e -> e,
                            (v1, v2) -> v1));
            for (ClusterServiceRoleInstanceEntity roleInstanceEntity : list) {
                ServiceRoleStrategy handler = ServiceRoleStrategyContext.getServiceRoleHandler(
                        roleInstanceEntity.getServiceRoleName());
                if (Objects.nonNull(handler)) {
                    handler.handlerServiceRoleCheck(roleInstanceEntity, map);
                } else {
                    CheckUtils.handlerServiceRoleStatusRunnerCheck(roleInstanceEntity, map);
                }
            }
        } catch (Throwable e) {
            log.error("Scheduled service role check failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 集群整体状态检测，每 60s 检查一次所有集群的运行状态。
     * 等价于原 ActorUtils: scheduleWithFixedDelay(30s, 60s, clusterCheckActor)
     */
    @Scheduled(initialDelayString = "30000", fixedDelayString = "60000")
    public void checkClusterStatus() {
        log.debug("Scheduled: start cluster status check");
        try {
            clusterStatusService.checkClusterStatus(null);
        } catch (Throwable e) {
            log.error("Scheduled cluster status check failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 延迟后对单个主机执行状态检测（替代 actorSystem.scheduler().scheduleOnce + HostCheckActor）。
     * 用于 generateHostAgentCommand 等场景：等待 worker 重启后再检测状态。
     *
     * @param hostInfo     要检测的主机信息
     * @param delaySeconds 延迟秒数
     */
    @Async("masterExecutor")
    public void checkHostWithDelay(HostInfo hostInfo, long delaySeconds) {
        try {
            if (delaySeconds > 0) {
                Thread.sleep(delaySeconds * 1000L);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        try {
            hostCheckService.checkHosts(hostInfo);
        } catch (Throwable e) {
            log.warn("Delayed host check for {} failed: {}", hostInfo.getHostname(), e.getMessage(), e);
        }
    }
}
