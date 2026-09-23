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

package com.datasophon.api.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import cn.hutool.crypto.SecureUtil;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * SEATUNNEL 服务元数据的可渲染性校验。
 *
 * <p>平台只把 {@code configWriter.generators[].includeParams} 中**存在于 parameters** 的参数塞进模板
 * 数据（{@code DdlMetaServiceImpl#buildConfigFileMap} 按 {@code map.containsKey} 严格过滤，
 * {@code FreemakerUtils#renderCustomConfigFormat} 只读 {@code configType=map} 的配置项）。
 * 因此角色策略生成的集群变量必须像 ELASTICSEARCH 的 {@code discovery.seed_hosts} 那样，
 * 经由某个 DDL 参数的 defaultValue 占位符引进来——直接把变量名写进 includeParams 会被静默丢弃，
 * 模板渲染时抛 {@code InvalidReferenceException}，整个服务的配置下发失败。
 *
 * <p>这里复刻那条链路（includeParams 过滤 → defaultValue 回填 → 占位符替换 → FreeMarker 渲染），
 * 用只含平台真实会提供的变量去渲染全部模板，而不是手工喂一份齐全的变量表。
 */
class SeaTunnelDdlLoadTest {

    private static final String META_RELATIVE = "package/raw/meta/datacluster-physical/SEATUNNEL";

    /** {@code FreemakerUtils#renderCustomConfigFormat} 无条件注入的内置变量。 */
    private static final Map<String, Object> BUILTIN_VARS =
            Map.of("ip", "192.168.10.131", "host", "ddh-01", "itemList", List.of());

    /** 角色策略 {@code generateClusterVariable} 写入 GlobalVariables 的集群变量。 */
    private static final Map<String, String> CLUSTER_VARIABLES = Map.of(
            "${SEATUNNEL.clusterName}", "seatunnel-cluster1",
            "${SEATUNNEL.masterMembers}", "ddh-02:5801,ddh-03:5801",
            "${SEATUNNEL.workerMembers}", "ddh-03:5802,ddh-04:5802,ddh-05:5802",
            "${ROOT.Rustfs.__hostIp__}", "192.168.10.131",
            "${ROOT.Rustfs.__port__}", "9040",
            "${ROOT.Rustfs.access_key}", "test-ak",
            "${ROOT.Rustfs.secret_key}", "test-sk",
            "${GRAVITINO.GravitinoServer.__hostIp__}", "192.168.10.132",
            "${GRAVITINO.gravitino.server.webserver.httpPort}", "8090");

    @Test
    void everyIncludeParamIsDeclaredAsParameter() throws Exception {
        JSONObject ddl = loadDdl();
        Set<String> declared = parameterNames(ddl);

        Map<String, List<String>> undeclared = new LinkedHashMap<>();
        for (JSONObject generator : generators(ddl)) {
            List<String> missing = generator.getJSONArray("includeParams").toJavaList(String.class).stream()
                    .filter(name -> !declared.contains(name))
                    .toList();
            if (!missing.isEmpty()) {
                undeclared.put(generator.getString("filename"), missing);
            }
        }

        assertTrue(undeclared.isEmpty(),
                () -> "includeParams 引用了 parameters 中不存在的名字，这些变量不会进入模板数据，"
                        + "渲染时会抛 InvalidReferenceException：" + undeclared);
    }

    @Test
    void allTemplatesRenderIntoValidYamlWithOnlyPlatformSuppliedVariables() throws Exception {
        JSONObject ddl = loadDdl();
        Map<String, String> rendered = renderAll(ddl);

        assertEquals(8, rendered.size(), "generator 数量变化时请同步核对本用例的断言");

        // http.port 按角色分流：master 版拿不到 workerHttpPort，走 <#else> 用 masterHttpPort
        assertEquals("18088", httpPort(rendered.get("seatunnel-master.yaml")));
        assertEquals("18089", httpPort(rendered.get("seatunnel-worker.yaml")));
        assertEquals("18088", httpPort(rendered.get("seatunnel.yaml")), "客户端配置与 master 版一致");

        for (Map.Entry<String, String> entry : rendered.entrySet()) {
            if (entry.getKey().endsWith(".yaml")) {
                assertNotNull(new Yaml().load(entry.getValue()), entry.getKey() + " 不是合法 YAML");
            }
        }
    }

    @Test
    void hazelcastTemplatesResolveClusterVariablesGeneratedByRoleStrategies() throws Exception {
        Map<String, String> rendered = renderAll(loadDdl());

        String master = rendered.get("hazelcast-master.yaml");
        assertTrue(master.contains("cluster-name: seatunnel-cluster1"), master);
        assertTrue(master.contains("- ddh-02:5801") && master.contains("- ddh-03:5801"), "master 成员缺失");
        assertTrue(master.contains("- ddh-03:5802") && master.contains("- ddh-05:5802"), "worker 成员缺失");
        assertTrue(master.contains("fs.s3a.endpoint: http://192.168.10.131:9040"), "S3 endpoint 未渲染");
        assertTrue(master.contains("map-store"), "master 必须配 IMap map-store");

        String worker = rendered.get("hazelcast-worker.yaml");
        assertTrue(worker.contains("- ddh-02:5801") && worker.contains("- ddh-03:5802"), "worker 成员表不完整");
        assertFalse(worker.contains("map-store"), "worker 不配 map-store");
        assertTrue(worker.contains("port: 5802"), worker);

        // 客户端只连 master，不能把 worker 当成连接入口
        String client = rendered.get("hazelcast-client.yaml");
        assertTrue(client.contains("- ddh-02:5801"), client);
        assertFalse(client.contains("5802"), "client cluster-members 不应包含 worker");
    }

    @Test
    void jvmOptionsTemplatesParameterizeHeapAndDirectMemory() throws Exception {
        Map<String, String> rendered = renderAll(loadDdl());

        assertTrue(rendered.get("jvm_master_options").contains("-Xms2g"), "master 堆默认 2g");
        assertTrue(rendered.get("jvm_worker_options").contains("-Xmx4g"), "worker 堆默认 4g");
        assertTrue(rendered.get("jvm_worker_options").contains("-XX:MaxDirectMemorySize=1g"), "direct 内存默认 1g");
    }

    @Test
    void lineageUrlResolvesToGravitinoWhenInstalled() throws Exception {
        assertTrue(renderAll(loadDdl()).get("seatunnel.yaml")
                .contains("url: http://192.168.10.132:8090/api/lineage"));
    }

    /** 实机复现：token 默认为空时渲染出 {@code auth_token: }（YAML null），SeaTunnel master 启动即 Fatal。 */
    @Test
    void emptyLineageTokenOmitsAuthTokenEntry() throws Exception {
        for (String file : List.of("seatunnel-master.yaml", "seatunnel-worker.yaml", "seatunnel.yaml")) {
            Map<?, ?> openlineage = (Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) new Yaml()
                    .load(renderAll(loadDdl()).get(file))).get("seatunnel")).get("engine");
            openlineage = (Map<?, ?>) openlineage.get("openlineage");
            assertFalse(openlineage.containsKey("auth_token"), file + " 不应输出空的 auth_token");
        }
    }

    /** DDL 不硬依赖 GRAVITINO/Rustfs：未安装时占位符留在默认值里，必须显式失败而不是把字面量写进配置。 */
    @Test
    void unresolvedGravitinoOrRustfsPlaceholderFailsRendering() throws Exception {
        Map<String, String> noGravitino = new HashMap<>(CLUSTER_VARIABLES);
        noGravitino.remove("${GRAVITINO.GravitinoServer.__hostIp__}");
        TemplateException lineage = assertThrows(TemplateException.class, () -> renderAll(loadDdl(), noGravitino));
        assertTrue(lineage.getMessage().contains("lineageUrl is required"));

        Map<String, String> noRustfs = new HashMap<>(CLUSTER_VARIABLES);
        noRustfs.remove("${ROOT.Rustfs.__port__}");
        TemplateException s3 = assertThrows(TemplateException.class, () -> renderAll(loadDdl(), noRustfs));
        assertTrue(s3.getMessage().contains("s3Endpoint is unresolved"));
    }

    @Test
    void controlScriptHookMd5MatchesTheScriptOnDisk() throws Exception {
        File script = new File(metaDir(), "script/control.sh");
        assertTrue(script.isFile(), "control.sh 缺失: " + script.getAbsolutePath());
        String actual = SecureUtil.md5(script);

        List<String> declared = loadDdl().getJSONArray("roles").stream()
                .map(JSONObject.class::cast)
                .flatMap(role -> role.getJSONArray("hooks").stream().map(JSONObject.class::cast))
                .filter(hook -> "download".equals(hook.getString("action")))
                .map(hook -> hook.getJSONObject("params"))
                .filter(params -> "control.sh".equals(params.getString("to")))
                .map(params -> params.getString("md5"))
                .toList();

        assertEquals(2, declared.size(), "Master 与 Worker 各应有一个 control.sh 下发钩子");
        declared.forEach(md5 -> assertEquals(actual, md5, "改了 control.sh 就要回填 DDL 里的 md5"));
    }

    /** 复刻 includeParams 过滤 → defaultValue 回填 → 占位符替换 → FreeMarker 渲染。 */
    private Map<String, String> renderAll(JSONObject ddl) throws Exception {
        return renderAll(ddl, CLUSTER_VARIABLES);
    }

    private Map<String, String> renderAll(JSONObject ddl, Map<String, String> variables) throws Exception {
        Map<String, JSONObject> parameters = ddl.getJSONArray("parameters").stream()
                .map(JSONObject.class::cast)
                .collect(Collectors.toMap(p -> p.getString("name"), p -> p, (a, b) -> a, LinkedHashMap::new));

        Configuration config = new Configuration(Configuration.getVersion());
        config.setDirectoryForTemplateLoading(new File(metaDir(), "templates"));

        Map<String, String> rendered = new LinkedHashMap<>();
        for (JSONObject generator : generators(ddl)) {
            Map<String, Object> data = new HashMap<>(BUILTIN_VARS);
            for (String name : generator.getJSONArray("includeParams").toJavaList(String.class)) {
                JSONObject parameter = parameters.get(name);
                // DdlMetaServiceImpl#buildConfigFileMap: 不在 parameters 里的名字直接丢弃
                if (parameter == null || !"map".equals(parameter.getString("configType"))) {
                    continue;
                }
                // ServiceInstallServiceImpl#applyDefaultValues: value 为空时回落 defaultValue
                Object value = parameter.get("value") != null ? parameter.get("value") : parameter.get("defaultValue");
                data.put(name, resolvePlaceholders(String.valueOf(value), variables));
            }
            Template template = config.getTemplate(generator.getString("templateName"));
            StringWriter out = new StringWriter();
            template.process(data, out);
            rendered.put(generator.getString("filename"), out.toString());
        }
        return rendered;
    }

    private static String resolvePlaceholders(String value, Map<String, String> variables) {
        String resolved = value;
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            resolved = resolved.replace(variable.getKey(), variable.getValue());
        }
        return resolved;
    }

    private static String httpPort(String seatunnelYaml) {
        Object http = ((Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) new Yaml().load(seatunnelYaml))
                .get("seatunnel")).get("engine")).get("http");
        return String.valueOf(((Map<?, ?>) http).get("port"));
    }

    private static Set<String> parameterNames(JSONObject ddl) {
        return ddl.getJSONArray("parameters").stream()
                .map(JSONObject.class::cast)
                .map(parameter -> parameter.getString("name"))
                .collect(Collectors.toSet());
    }

    private static List<JSONObject> generators(JSONObject ddl) {
        JSONArray generators = ddl.getJSONObject("configWriter").getJSONArray("generators");
        return generators.stream().map(JSONObject.class::cast).toList();
    }

    private JSONObject loadDdl() throws Exception {
        File ddl = new File(metaDir(), "service_ddl.json");
        assertTrue(ddl.isFile(), "DDL 缺失: " + ddl.getAbsolutePath());
        return JSONObject.parseObject(Files.readString(ddl.toPath(), StandardCharsets.UTF_8));
    }

    /** 与 {@link DsDdlLoadTest} 一致：兼容从模块目录和仓库根两种 user.dir 运行。 */
    private File metaDir() {
        File candidate = new File(System.getProperty("user.dir")).toPath()
                .resolve("../")
                .resolve(META_RELATIVE)
                .normalize()
                .toFile();
        if (candidate.isDirectory()) {
            return candidate;
        }
        candidate = new File(System.getProperty("user.dir"), META_RELATIVE);
        assertTrue(candidate.isDirectory(), "找不到 SEATUNNEL 元数据目录: " + candidate.getAbsolutePath());
        return candidate;
    }
}
