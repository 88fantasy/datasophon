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
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.api.utils.PackageUtils;
import com.datasophon.common.command.GenerateServiceConfigCommand;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleRunner;
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
    static final String LOCAL_SCRAPE_JOBS_YAML = "localScrapeJobsYaml";
    static final String NODE_HOSTNAME = "nodeHostname";
    
    private final WorkerCallAdapter workerCallAdapter;
    private final ServiceInstallService installService;
    private final OtelScrapeConfigBuilder scrapeConfigBuilder;
    
    public OtelCollectorConfigService(WorkerCallAdapter workerCallAdapter,
                                      ServiceInstallService installService,
                                      OtelScrapeConfigBuilder scrapeConfigBuilder) {
        this.workerCallAdapter = workerCallAdapter;
        this.installService = installService;
        this.scrapeConfigBuilder = scrapeConfigBuilder;
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
        cmd.setDecompressPackageName(decompressPackageName());
        return cmd;
    }

    /**
     * Worker 侧按 decompressPackageName 拼出配置文件真实写入路径(见 PkgInstallPathUtils.getInstallHome)，
     * 缺失时会写到 install.path 下字面量 "null" 目录，不落到 otelcol 实际运行的安装目录。
     * PackageUtils 是 LoadServiceMeta 加载阶段为每个服务(不分 arch)填入的代表性解压目录名缓存，
     * 与其他服务共用同一份已验证的解析结果，不重复实现按架构解析逻辑。
     */
    private static String decompressPackageName() {
        return PackageUtils.getServiceDcPackageName(OtelSchema.FRAMEWORK, SERVICE_NAME);
    }
    
    /**
     * 下发配置并重启节点上的 otelcol：先 configure，成功后才 restart；configure 失败则短路返回。
     */
    public ExecResult pushNodeConfig(
                                     Integer clusterId, String hostname, Map<String, String> params) {
        Map<String, String> effectiveParams = effectiveParams(clusterId, hostname, params);
        GenerateServiceConfigCommand cfg = buildConfigCommand(clusterId, hostname, effectiveParams);
        ExecResult configured = workerCallAdapter.configureServiceRole(hostname, cfg);
        if (configured == null || !configured.getExecResult()) {
            log.warn("otelcol configure failed on {}, skip restart", hostname);
            return configured != null ? configured : ExecResult.error("configure returned null");
        }
        ServiceRoleOperateCommand op = new ServiceRoleOperateCommand();
        op.setServiceName(SERVICE_NAME);
        op.setServiceRoleName(ROLE_NAME);
        op.setCommandType(CommandType.RESTART_SERVICE);
        op.setRestartRunner(restartRunner());
        return workerCallAdapter.restartServiceRole(hostname, op);
    }

    /**
     * service_ddl.json 里 control.sh 的 stopRunner(600s) + startRunner(60s)
     * 之和，对应 control.sh restart 内部 stop; sleep 2s; start 的完整耗时。
     */
    private static ServiceRoleRunner restartRunner() {
        ServiceRoleRunner runner = new ServiceRoleRunner();
        runner.setProgram("control.sh");
        runner.setArgs(List.of("restart"));
        runner.setTimeout("660");
        return runner;
    }
    
    private Map<String, String> effectiveParams(Integer clusterId, String hostname, Map<String, String> params) {
        Map<String, String> effective = serviceParams(clusterId);
        if (params != null) {
            effective.putAll(params);
        }
        if (!effective.containsKey(LOCAL_SCRAPE_JOBS_YAML)) {
            effective.put(LOCAL_SCRAPE_JOBS_YAML, scrapeConfigBuilder.build(clusterId, hostname));
        }
        effective.put(NODE_HOSTNAME, hostname);
        return effective;
    }
    
    private Map<String, String> serviceParams(Integer clusterId) {
        Map<String, String> params = new HashMap<>();
        try {
            List<ServiceConfig> configs = installService.getServiceConfigOption(clusterId, SERVICE_NAME);
            for (ServiceConfig config : configs) {
                if (config.getName() != null && config.getValue() != null) {
                    params.put(config.getName(), String.valueOf(config.getValue()));
                }
            }
        } catch (RuntimeException e) {
            log.warn("Failed to load {} service config for cluster {}: {}", SERVICE_NAME, clusterId, e.getMessage());
        }
        return params;
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
