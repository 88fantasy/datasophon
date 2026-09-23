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

import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.load.ServiceConfigMap;
import com.datasophon.api.utils.ServiceConfigUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.PlaceholderUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 保存配置时：
 * <ul>
 * <li>按表单里的 Hazelcast 端口重算成员列表：角色分配早于配置步骤，角色策略生成 members 时端口尚未注册，
 * 改端口后 members 也不会自动跟随；</li>
 * <li>值为 null 的参数回落到<b>当前 DDL</b> 解析后的 defaultValue：安装后才加入 DDL 的参数（lineage*）在已装实例里
 * 是"条目在、值为 null"，不补就会让模板渲染失败、角色重启后起不来；持久化条目自带的 defaultValue
 * 是安装时那版 DDL 的，可能已被修正过，所以优先取当前 DDL。</li>
 * </ul>
 */
public class SeaTunnelHandlerStrategy implements ServiceRoleStrategy {

    private static final String SERVICE_NAME = "SEATUNNEL";

    @Override
    public void handlerConfig(Integer clusterId, List<ServiceConfig> list, String serviceName) {
        Map<String, String> variables = GlobalVariables.getVariables(clusterId);
        List<ServiceConfig> ddlParams = ServiceConfigMap.get(
                ServiceConfigUtils.getClusterInfo(clusterId).getClusterFrame() + Constants.UNDERLINE + SERVICE_NAME + Constants.CONFIG);
        Map<String, ServiceConfig> ddl = ddlParams == null ? Map.of() : ServiceConfigUtils.translateToMap(ddlParams);
        for (ServiceConfig config : list) {
            ServiceConfig current = ddl.get(config.getName());
            Object defaultValue = current != null && current.getDefaultValue() != null
                    ? current.getDefaultValue()
                    : config.getDefaultValue();
            if (config.getValue() == null && defaultValue != null) {
                config.setValue(defaultValue instanceof String text
                        ? PlaceholderUtils.replacePlaceholders(text, variables, Constants.REGEX_VARIABLE)
                        : defaultValue);
            }
        }
        Map<String, ServiceConfig> map = ServiceConfigUtils.translateToMap(list);
        refreshMembers(clusterId, map, "SeaTunnelMaster", "masterHazelcastPort", "masterMembers");
        refreshMembers(clusterId, map, "SeaTunnelWorker", "workerHazelcastPort", "workerMembers");
    }

    private void refreshMembers(Integer clusterId, Map<String, ServiceConfig> map,
                                String role, String portName, String membersName) {
        String hosts = GlobalVariables.getValueByService(clusterId, SERVICE_NAME, role + "." + GlobalVariables.HOST);
        ServiceConfig port = map.get(portName);
        ServiceConfig members = map.get(membersName);
        if (StringUtils.isBlank(hosts) || port == null || port.getValue() == null || members == null) {
            return;
        }
        String value = Arrays.stream(hosts.split(","))
                .map(host -> host + ":" + port.getValue())
                .collect(Collectors.joining(","));
        members.setValue(value);
        ServiceConfigUtils.generateClusterVariable(clusterId, SERVICE_NAME, membersName, value);
    }
}
