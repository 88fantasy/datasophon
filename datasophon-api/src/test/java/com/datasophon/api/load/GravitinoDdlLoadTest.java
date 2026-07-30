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

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * 验证 GRAVITINO service_ddl.json 的血缘转发配置（L1 第 4 批 §8.0 之后、L2 开工新增）。
 *
 * <p>只做静态结构核对：配置能否真的把血缘事件转发到真实运行的 datasophon-api，
 * 需要真实 Gravitino + Spark 部署才能验证，本测试不覆盖那部分。</p>
 */
class GravitinoDdlLoadTest {

    private static final String DDL_RELATIVE = "package/raw/meta/datacluster-physical/GRAVITINO/service_ddl.json";

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

    private JSONObject loadDdl() throws Exception {
        File ddl = locateDdl();
        assertTrue(ddl.exists(), "service_ddl.json 必须存在: " + ddl.getAbsolutePath());
        String content = new String(Files.readAllBytes(ddl.toPath()), StandardCharsets.UTF_8);
        return JSONObject.parseObject(content);
    }

    @Test
    void gravitinoConfGeneratorIncludesAllLineageParams() throws Exception {
        JSONObject json = loadDdl();
        JSONObject gravitinoConfGenerator = json.getJSONObject("configWriter")
                .getJSONArray("generators")
                .getJSONObject(0);
        assertEquals("gravitino.conf", gravitinoConfGenerator.getString("filename"));
        JSONArray includeParams = gravitinoConfGenerator.getJSONArray("includeParams");

        assertTrue(includeParams.contains("gravitino.lineage.source"));
        assertTrue(includeParams.contains("gravitino.lineage.processorClass"));
        assertTrue(includeParams.contains("gravitino.lineage.sinks"));
        assertTrue(includeParams.contains("gravitino.lineage.sinkQueueCapacity"));
        assertTrue(includeParams.contains("gravitino.lineage.http.sinkClass"));
        assertTrue(includeParams.contains("gravitino.lineage.http.url"));
        assertTrue(includeParams.contains("gravitino.lineage.http.authType"));
        assertTrue(includeParams.contains("gravitino.lineage.http.apiKey"));
    }

    @Test
    void lineageParametersPinNoopProcessorAndApiKeyAuth() throws Exception {
        JSONObject json = loadDdl();
        JSONArray parameters = json.getJSONArray("parameters");

        assertEquals("org.apache.gravitino.lineage.processor.NoopProcessor",
                findParameterDefaultValue(parameters, "gravitino.lineage.processorClass"),
                "L0 现场核查已确认 Gravitino 不做 identifier 规范化，processorClass 必须显式钉死为 NoopProcessor");
        assertEquals("org.apache.gravitino.lineage.sink.LineageHttpSink",
                findParameterDefaultValue(parameters, "gravitino.lineage.http.sinkClass"));
        assertEquals("apiKey", findParameterDefaultValue(parameters, "gravitino.lineage.http.authType"),
                "血缘转发必须走鉴权，不能配成 authType=none");
    }

    private static String findParameterDefaultValue(JSONArray parameters, String name) {
        for (int i = 0; i < parameters.size(); i++) {
            JSONObject parameter = parameters.getJSONObject(i);
            if (name.equals(parameter.getString("name"))) {
                return parameter.getString("defaultValue");
            }
        }
        throw new AssertionError("parameter not found: " + name);
    }
}
