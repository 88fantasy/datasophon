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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * 验证 SPARK3 service_ddl.json 的 OpenLineage 监听器配置（L2 开工新增）。
 *
 * <p>只做静态结构核对：`spark.extraListeners`/`spark.openlineage.transport.*` 是否写对，
 * 一个真实 Spark SQL 作业能否产出正确的血缘边，需要真实 SPARK3 + Gravitino 部署才能验证，
 * 本测试不覆盖那部分。</p>
 */
class Spark3DdlLoadTest {

    private static final String DDL_RELATIVE = "package/raw/meta/datacluster-physical/SPARK3/service_ddl.json";

    private File locateDdl() {
        File candidate = new File(System.getProperty("user.dir")).toPath()
                .resolve("../")
                .resolve(DDL_RELATIVE)
                .normalize()
                .toFile();
        if (candidate.exists()) {
            return candidate;
        }
        candidate = new File(System.getProperty("user.dir"), DDL_RELATIVE);
        if (candidate.exists()) {
            return candidate;
        }
        return new File("../" + DDL_RELATIVE).getAbsoluteFile();
    }

    @Test
    void sparkDefaultsConfDeclaresOpenLineageListenerAndHttpTransportToGravitino() throws Exception {
        File ddl = locateDdl();
        assertTrue(ddl.exists(), "service_ddl.json 必须存在: " + ddl.getAbsolutePath());
        String content = new String(Files.readAllBytes(ddl.toPath()), StandardCharsets.UTF_8);
        JSONObject json = JSONObject.parseObject(content);

        JSONObject sparkDefaults = findParameter(json.getJSONArray("parameters"), "custom.spark.defaults.conf");
        Map<String, String> entries = flatten(sparkDefaults.getJSONArray("defaultValue"));

        assertEquals("io.openlineage.spark.agent.OpenLineageSparkListener", entries.get("spark.extraListeners"));
        assertEquals("http", entries.get("spark.openlineage.transport.type"));
        assertEquals("http://${ROOT.GRAVITINO.__hostIp__}:${ROOT.GRAVITINO.__port__}",
                entries.get("spark.openlineage.transport.url"),
                "血缘事件送 Gravitino（2026-07-30 会话决策：先经 Gravitino 转发），"
                        + "url 只能放 base origin：openlineage-java 的 HttpTransport.getUri() 只要"
                        + "transport.endpoint 为空就会用 URIBuilder.setPath() 把路径整体替换成硬编码默认值"
                        + " /api/v1/lineage，url 里带的路径段会被直接丢弃（2026-08-04 沙箱实测坐实，此前"
                        + "误把整段 /api/lineage 塞进 url 导致真实请求 404）");
        assertEquals("/api/lineage", entries.get("spark.openlineage.transport.endpoint"),
                "真正的请求路径必须由 transport.endpoint 显式声明，不能指望 transport.url 里的路径生效");
    }

    private static JSONObject findParameter(JSONArray parameters, String name) {
        for (int i = 0; i < parameters.size(); i++) {
            JSONObject parameter = parameters.getJSONObject(i);
            if (name.equals(parameter.getString("name"))) {
                return parameter;
            }
        }
        throw new AssertionError("parameter not found: " + name);
    }

    private static Map<String, String> flatten(JSONArray multipleWithKeyEntries) {
        Map<String, String> flattened = new HashMap<>();
        for (int i = 0; i < multipleWithKeyEntries.size(); i++) {
            JSONObject entry = multipleWithKeyEntries.getJSONObject(i);
            for (String key : entry.keySet()) {
                flattened.put(key, entry.getString(key));
            }
        }
        return flattened;
    }
}
