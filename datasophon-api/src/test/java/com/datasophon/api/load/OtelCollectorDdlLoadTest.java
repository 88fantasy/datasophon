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

/** 验证 OTELCOLLECTOR service_ddl.json 结构与关键字段(Task 4 — Phase A1)。 */
class OtelCollectorDdlLoadTest {
    
    private static final String DDL_RELATIVE =
            "package/raw/meta/datacluster-physical/OTELCOLLECTOR/service_ddl.json";
    
    /**
     * 稳健地定位 service_ddl.json：测试通过 {@code -pl datasophon-api} 运行时 user.dir 是模块目录，
     * 向上一级即仓库根；也兼容从仓库根直接运行的场景。
     */
    private File locateDdl() {
        // 优先：从 user.dir 向上一级（模块 → 仓库根）
        File candidate = new File(System.getProperty("user.dir")).toPath()
                .resolve("../")
                .resolve(DDL_RELATIVE)
                .normalize()
                .toFile();
        if (candidate.exists()) {
            return candidate;
        }
        // 备选：user.dir 本身就是仓库根
        candidate = new File(System.getProperty("user.dir"), DDL_RELATIVE);
        if (candidate.exists()) {
            return candidate;
        }
        // 最后兜底：../package/... 相对于当前目录
        return new File("../" + DDL_RELATIVE).getAbsoluteFile();
    }
    
    @Test
    void ddl_is_valid_and_declares_per_node_worker_role() throws Exception {
        File ddl = locateDdl();
        assertTrue(ddl.exists(), "service_ddl.json 必须存在: " + ddl.getAbsolutePath());
        
        String content = new String(Files.readAllBytes(ddl.toPath()), StandardCharsets.UTF_8);
        JSONObject json = JSONObject.parseObject(content);
        
        // 服务名
        assertEquals("OTELCOLLECTOR", json.getString("name"));
        
        // 角色断言
        JSONArray roles = json.getJSONArray("roles");
        JSONObject role = roles.getJSONObject(0);
        assertEquals("OtelCollector", role.getString("name"));
        assertEquals("worker", role.getString("roleType"));
        assertEquals("1+", role.getString("cardinality"));
        assertEquals(Integer.valueOf(8888), role.getInteger("jmxPort"));
        
        // configWriter 指向 otelcol.ftl
        String templateName = json.getJSONObject("configWriter")
                .getJSONArray("generators")
                .getJSONObject(0)
                .getString("templateName");
        assertEquals("otelcol.ftl", templateName);
        
        // POST_INSTALL hook 下载 control.sh
        JSONObject hook = role.getJSONArray("hooks").getJSONObject(0);
        assertEquals("download", hook.getString("action"));
    }
}
