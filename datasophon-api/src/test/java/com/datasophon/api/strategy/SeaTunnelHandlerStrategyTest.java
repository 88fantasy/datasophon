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

package com.datasophon.api.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.load.ServiceConfigMap;
import com.datasophon.api.utils.ServiceConfigUtils;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.dao.entity.ClusterInfoEntity;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SeaTunnelHandlerStrategyTest {

    private static final Integer CLUSTER_ID = 74105;
    private static final String SERVICE_NAME = "SEATUNNEL";

    @Test
    void rebuildsMembersFromPortsSubmittedInConfigStep() {
        GlobalVariables.putValue(CLUSTER_ID, SERVICE_NAME, "SeaTunnelMaster.__host__", "m1,m2");
        GlobalVariables.putValue(CLUSTER_ID, SERVICE_NAME, "SeaTunnelWorker.__host__", "w1");
        // 角色分配阶段端口未注册，成员列表按默认端口生成
        ServiceConfig masterMembers = config("masterMembers", "m1:5801,m2:5801");
        ServiceConfig workerMembers = config("workerMembers", "w1:5802");
        List<ServiceConfig> list = List.of(
                config("masterHazelcastPort", "15801"), config("workerHazelcastPort", 15802),
                masterMembers, workerMembers);

        try (MockedStatic<ServiceConfigUtils> configUtils = mockStatic(ServiceConfigUtils.class)) {
            configUtils.when(() -> ServiceConfigUtils.translateToMap(any())).thenCallRealMethod();
            configUtils.when(() -> ServiceConfigUtils.getClusterInfo(CLUSTER_ID)).thenReturn(new ClusterInfoEntity());

            new SeaTunnelHandlerStrategy().handlerConfig(CLUSTER_ID, list, SERVICE_NAME);

            configUtils.verify(() -> ServiceConfigUtils.generateClusterVariable(
                    CLUSTER_ID, SERVICE_NAME, "masterMembers", "m1:15801,m2:15801"));
            configUtils.verify(() -> ServiceConfigUtils.generateClusterVariable(
                    CLUSTER_ID, SERVICE_NAME, "workerMembers", "w1:15802"));
        }
        assertEquals("m1:15801,m2:15801", masterMembers.getValue());
        assertEquals("w1:15802", workerMembers.getValue());
        assertInstanceOf(SeaTunnelHandlerStrategy.class,
                ServiceRoleStrategyContext.getServiceRoleHandler(SERVICE_NAME));
    }

    /** ddh 实测形态：lineage* 在 PR 之前就已安装的实例里是"条目在、值为 null"，保存时必须回落到解析后的默认值。 */
    @Test
    void backfillsNullValuesWithResolvedDefaultsForParamsAddedAfterInstall() {
        Integer clusterId = 74106;
        GlobalVariables.putValue(clusterId, "GRAVITINO", "GravitinoServer.__hostIp__", "192.168.10.132");
        GlobalVariables.putValue(clusterId, "GRAVITINO", "gravitino.server.webserver.httpPort", "8090");
        ServiceConfig enabled = config("lineageEnabled", null);
        enabled.setDefaultValue(true);
        // 持久化条目带的是安装时那版 DDL 的旧默认值（引用了不存在的变量），应以当前 DDL 为准
        ServiceConfig url = config("lineageUrl", null);
        url.setDefaultValue("http://${ROOT.GRAVITINO.__hostIp__}:${ROOT.GRAVITINO.__port__}/api/lineage");
        ServiceConfig currentUrl = config("lineageUrl", null);
        currentUrl.setDefaultValue("http://${GRAVITINO.GravitinoServer.__hostIp__}:${GRAVITINO.gravitino.server.webserver.httpPort}/api/lineage");
        ServiceConfigMap.put("udh_SEATUNNEL_config", List.of(currentUrl));
        ClusterInfoEntity clusterInfo = new ClusterInfoEntity();
        clusterInfo.setClusterFrame("udh");
        ServiceConfig userValue = config("clusterName", "kept");
        userValue.setDefaultValue("${SEATUNNEL.clusterName}");

        try (MockedStatic<ServiceConfigUtils> configUtils = mockStatic(ServiceConfigUtils.class)) {
            configUtils.when(() -> ServiceConfigUtils.translateToMap(any())).thenCallRealMethod();
            configUtils.when(() -> ServiceConfigUtils.getClusterInfo(clusterId)).thenReturn(clusterInfo);
            new SeaTunnelHandlerStrategy().handlerConfig(clusterId, List.of(enabled, url, userValue), SERVICE_NAME);
        }
        assertEquals(true, enabled.getValue());
        assertEquals("http://192.168.10.132:8090/api/lineage", url.getValue());
        assertEquals("kept", userValue.getValue(), "已有值不能被默认值覆盖");
    }

    private static ServiceConfig config(String name, Object value) {
        ServiceConfig config = new ServiceConfig();
        config.setName(name);
        config.setValue(value);
        return config;
    }
}
