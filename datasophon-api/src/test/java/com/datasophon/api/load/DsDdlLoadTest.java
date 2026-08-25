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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import cn.hutool.crypto.SecureUtil;

/** 验证 DS 分发插件与平台托管对象存储凭据。 */
class DsDdlLoadTest {

    private static final String DS_META_RELATIVE = "package/raw/meta/datacluster-physical/DS";
    private static final List<String> TASK_PLUGINS = List.of("shell", "spark", "flink", "flink-stream", "sql");
    private static final List<String> TASK_PLUGIN_ROLES = List.of("ApiServer", "MasterServer", "WorkerServer");

    @Test
    void taskPluginsAreDistributedToAllRequiredRoles() throws Exception {
        File metaDir = locateMetaDir();
        JSONObject ddl = loadDdl(metaDir);
        Map<String, JSONObject> roles = ddl.getJSONArray("roles").stream()
                .map(JSONObject.class::cast)
                .collect(Collectors.toMap(role -> role.getString("name"), Function.identity()));

        for (String roleName : TASK_PLUGIN_ROLES) {
            JSONObject role = roles.get(roleName);
            assertNotNull(role, "缺少 DS 角色: " + roleName);
            String serverDir = switch (roleName) {
                case "ApiServer" -> "api-server";
                case "MasterServer" -> "master-server";
                case "WorkerServer" -> "worker-server";
                default -> throw new IllegalArgumentException(roleName);
            };
            JSONArray hooks = role.getJSONArray("hooks");
            for (String plugin : TASK_PLUGINS) {
                String jarName = "dolphinscheduler-task-" + plugin + "-3.4.1.jar";
                JSONObject hook = findDownloadHook(hooks, serverDir + "/libs/" + jarName);
                assertEquals("plugin/" + jarName, hook.getJSONObject("params").getString("from"));
                File jar = new File(metaDir, "plugin/" + jarName);
                assertTrue(jar.isFile(), "插件文件缺失: " + jar.getAbsolutePath());
                assertEquals(SecureUtil.md5(jar), hook.getJSONObject("params").getString("md5"));
            }
        }
    }

    @Test
    void objectStorageCredentialsArePlatformManaged() throws Exception {
        JSONObject ddl = loadDdl(locateMetaDir());
        JSONArray parameters = ddl.getJSONArray("parameters");

        assertManagedCredential(parameters, "aws.s3.access.key.id", "${ROOT.Rustfs.access_key}");
        assertManagedCredential(parameters, "aws.s3.access.key.secret", "${ROOT.Rustfs.secret_key}");

        ddl.getJSONArray("roles").stream()
                .map(JSONObject.class::cast)
                .flatMap(role -> role.getJSONArray("hooks").stream())
                .map(JSONObject.class::cast)
                .filter(hook -> "s3Sync".equals(hook.getString("action")))
                .map(hook -> hook.getJSONObject("params"))
                .forEach(params -> {
                    assertEquals("${ROOT.Rustfs.access_key}", params.getString("accessKey"));
                    assertEquals("${ROOT.Rustfs.secret_key}", params.getString("secretKey"));
                });
    }

    @Test
    void apiTokenIsOptionalPlatformOnlyConfig() throws Exception {
        JSONObject ddl = loadDdl(locateMetaDir());
        JSONObject apiToken = ddl.getJSONArray("parameters").stream()
                .map(JSONObject.class::cast)
                .filter(parameter -> "apiToken".equals(parameter.getString("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("parameter not found: apiToken"));

        assertFalse(apiToken.getBooleanValue("required"));
        assertEquals("", apiToken.getString("defaultValue"));
        assertFalse(apiToken.getBooleanValue("register"));
        assertTrue(apiToken.getString("description").contains("不会下发"));
        assertFalse(ddl.getJSONObject("configWriter").toJSONString().contains("apiToken"));
    }

    private static void assertManagedCredential(JSONArray parameters, String name, String expectedDefault) {
        JSONObject parameter = parameters.stream()
                .map(JSONObject.class::cast)
                .filter(candidate -> name.equals(candidate.getString("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("parameter not found: " + name));
        assertEquals(expectedDefault, parameter.getString("defaultValue"));
        assertTrue(parameter.getBooleanValue("hidden"), name + " 不应作为 DS 独立凭据开放编辑");
        assertFalse(parameter.getBooleanValue("configurableInWizard"), name + " 应由平台对象存储配置投影");
    }

    private static JSONObject findDownloadHook(JSONArray hooks, String target) {
        return hooks.stream()
                .map(JSONObject.class::cast)
                .filter(hook -> "POST_INSTALL".equals(hook.getString("type")))
                .filter(hook -> "download".equals(hook.getString("action")))
                .filter(hook -> target.equals(hook.getJSONObject("params").getString("to")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("download hook not found: " + target));
    }

    private static JSONObject loadDdl(File metaDir) throws Exception {
        File ddl = new File(metaDir, "service_ddl.json");
        assertTrue(ddl.isFile(), "service_ddl.json 必须存在: " + ddl.getAbsolutePath());
        return JSONObject.parseObject(Files.readString(ddl.toPath(), StandardCharsets.UTF_8));
    }

    private static File locateMetaDir() {
        File current = new File(System.getProperty("user.dir"));
        File candidate = current.toPath().resolve(DS_META_RELATIVE).normalize().toFile();
        if (candidate.isDirectory()) {
            return candidate;
        }
        return current.toPath().resolve("../").resolve(DS_META_RELATIVE).normalize().toFile();
    }
}
