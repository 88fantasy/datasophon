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

package com.datasophon.api.observability;

import com.datasophon.api.master.transport.WorkerCallAdapter;
import com.datasophon.common.command.GenerateServiceConfigCommand;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.ExecResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** OTELCOLLECTOR 配置下发主干：按节点重生成配置(otelcol.yaml + otelcol.env)→ 推送 → 重启。 */
@Service
public class OtelCollectorConfigService {
    
    private static final Logger log = LoggerFactory.getLogger(OtelCollectorConfigService.class);
    
    static final String SERVICE_NAME = "OTELCOLLECTOR";
    static final String ROLE_NAME = "OtelCollector";
    
    private final WorkerCallAdapter workerCallAdapter;
    
    public OtelCollectorConfigService(WorkerCallAdapter workerCallAdapter) {
        this.workerCallAdapter = workerCallAdapter;
    }
    
    /**
     * 为指定节点构建 OTELCOLLECTOR 的 GenerateServiceConfigCommand，含 otelcol.yaml 和 otelcol.env 双 generator。
     */
    public GenerateServiceConfigCommand buildConfigCommand(
                                                           Integer clusterId, String hostname, Map<String, String> params) {
        Map<Generators, List<ServiceConfig>> fileMap = new HashMap<>();
        fileMap.put(generator("otelcol.yaml", "otelcol.ftl"), toConfigs(params));
        fileMap.put(generator("otelcol.env", "otelcol-env.ftl"), toConfigs(params));
        
        GenerateServiceConfigCommand cmd = new GenerateServiceConfigCommand();
        cmd.setClusterId(clusterId);
        cmd.setServiceName(SERVICE_NAME);
        cmd.setServiceRoleName(ROLE_NAME);
        cmd.setCofigFileMap(fileMap);
        return cmd;
    }
    
    /**
     * 下发配置并重启节点上的 otelcol：先 configure，成功后才 restart；configure 失败则短路返回。
     */
    public ExecResult pushNodeConfig(
                                     Integer clusterId, String hostname, Map<String, String> params) {
        GenerateServiceConfigCommand cfg = buildConfigCommand(clusterId, hostname, params);
        ExecResult configured = workerCallAdapter.configureServiceRole(hostname, cfg);
        if (configured == null || !configured.getExecResult()) {
            log.warn("otelcol configure failed on {}, skip restart", hostname);
            return configured != null ? configured : ExecResult.error("configure returned null");
        }
        ServiceRoleOperateCommand op = new ServiceRoleOperateCommand();
        op.setServiceName(SERVICE_NAME);
        op.setServiceRoleName(ROLE_NAME);
        op.setCommandType(CommandType.RESTART_SERVICE);
        return workerCallAdapter.restartServiceRole(hostname, op);
    }
    
    private static Generators generator(String filename, String template) {
        Generators g = new Generators();
        g.setFilename(filename);
        g.setOutputDirectory("config");
        g.setConfigFormat("custom");
        g.setTemplateName(template);
        return g;
    }
    
    private static List<ServiceConfig> toConfigs(Map<String, String> params) {
        List<ServiceConfig> list = new ArrayList<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            ServiceConfig sc = new ServiceConfig();
            sc.setName(e.getKey());
            sc.setValue(e.getValue());
            sc.setConfigType("map");
            sc.setRequired(true);
            sc.setEnabled(true);
            list.add(sc);
        }
        return list;
    }
}
