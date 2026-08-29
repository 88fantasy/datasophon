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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/** 验证 GRAVITINO service_ddl.json 的独立 MySQL 血缘存储配置。 */
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
        assertTrue(includeParams.contains("gravitino.lineage.storage.enabled"));
        assertTrue(includeParams.contains("gravitino.lineage.storage.jdbcUrl"));
        assertTrue(includeParams.contains("gravitino.lineage.storage.jdbcPassword"));
        assertTrue(includeParams.contains("gravitino.lineage.storage.cacheSyncIntervalSecs"));
        assertTrue(includeParams.contains("gravitino.lineage.storage.staleThresholdSecs"));
        assertTrue(includeParams.contains("gravitino.lineage.storage.sourceLaggingThresholdSecs"));
    }

    @Test
    void lineageParametersUseLocalSnapshotAndIndependentStorage() throws Exception {
        JSONObject json = loadDdl();
        JSONArray parameters = json.getJSONArray("parameters");

        assertEquals("1.3.1-SNAPSHOT", json.getString("version"));
        assertEquals("org.apache.gravitino.lineage.processor.NoopProcessor",
                findParameterDefaultValue(parameters, "gravitino.lineage.processorClass"),
                "processorClass 继续显式钉死为 NoopProcessor");
        assertEquals("log", findParameterDefaultValue(parameters, "gravitino.lineage.sinks"));
        assertEquals("true", findParameterDefaultValue(parameters, "gravitino.lineage.storage.enabled"));
        assertEquals("jdbc:mysql://${ROOT.Mysql.mysqlHostPort}/gravitino_lineage_1"
                + "?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true",
                findParameterDefaultValue(parameters, "gravitino.lineage.storage.jdbcUrl"));
        assertTrue(parameters.stream()
                .map(JSONObject.class::cast)
                .noneMatch(parameter -> parameter.getString("name").startsWith("gravitino.lineage.http.")));
    }

    @Test
    void externalRunKeysIsOptionalAndDisabledByDefault() throws Exception {
        JSONArray parameters = loadDdl().getJSONArray("parameters");
        JSONObject externalRunKeys = findParameter(parameters, "gravitino.lineage.storage.externalRunKeys");

        assertFalse(externalRunKeys.getBooleanValue("required"));
        assertEquals("", externalRunKeys.getString("defaultValue"));
    }

    private static String findParameterDefaultValue(JSONArray parameters, String name) {
        return findParameter(parameters, name).getString("defaultValue");
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
}
