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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/** 验证 OTELCOLLECTOR service_ddl.json 结构与关键字段(Task 4 — Phase A1)。 */
class OtelCollectorDdlLoadTest {
    
    private static final String DDL_RELATIVE =
            "package/raw/meta/datacluster-physical/OTELCOLLECTOR/service_ddl.json";
    
    private static final String README_RELATIVE =
            "deploy/observability/otelcol/README.md";
    
    /**
     * 稳健地定位 service_ddl.json：测试通过 {@code -pl datasophon-api} 运行时 user.dir 是模块目录，
     * 向上一级即仓库根；也兼容从仓库根直接运行的场景。
     */
    private File locateRepoFile(String relative) {
        // 优先：从 user.dir 向上一级（模块 → 仓库根）
        File candidate = new File(System.getProperty("user.dir")).toPath()
                .resolve("../")
                .resolve(relative)
                .normalize()
                .toFile();
        if (candidate.exists()) {
            return candidate;
        }
        // 备选：user.dir 本身就是仓库根
        candidate = new File(System.getProperty("user.dir"), relative);
        if (candidate.exists()) {
            return candidate;
        }
        // 最后兜底
        return new File("../" + relative).getAbsoluteFile();
    }
    
    private File locateDdl() {
        return locateRepoFile(DDL_RELATIVE);
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
        assertEquals("otelSelfMetricsPort", role.getString("jmxPortParam"));
        
        // configWriter 指向 otelcol.ftl
        JSONObject yamlGenerator = json.getJSONObject("configWriter")
                .getJSONArray("generators")
                .getJSONObject(0);
        String templateName = yamlGenerator.getString("templateName");
        assertEquals("otelcol.ftl", templateName);
        assertTrue(yamlGenerator.getJSONArray("includeParams").contains("nodeHostname"),
                "otelcol.ftl 使用 nodeHostname，首次安装 DDL includeParams 必须传入该变量");
        
        // POST_INSTALL hook 下载 control.sh
        JSONObject hook = role.getJSONArray("hooks").getJSONObject(0);
        assertEquals("download", hook.getString("action"));
    }
    
    /**
     * H1 — 预检：DDL 声明的每个 arch packageName 必须在 vendoring README 中出现，
     * 且 README 为每个包记录了一个 32 位 md5（防止 DDL 与 vendoring 文档漂移）。
     */
    @Test
    void ddl_package_names_are_documented_in_vendoring_readme_with_md5() throws Exception {
        File ddl = locateDdl();
        assertTrue(ddl.exists(), "service_ddl.json 必须存在: " + ddl.getAbsolutePath());
        
        File readme = locateRepoFile(README_RELATIVE);
        assertTrue(readme.exists(), "vendoring README 必须存在: " + readme.getAbsolutePath());
        
        String ddlContent = new String(Files.readAllBytes(ddl.toPath()), StandardCharsets.UTF_8);
        String readmeContent = new String(Files.readAllBytes(readme.toPath()), StandardCharsets.UTF_8);
        
        JSONObject json = JSONObject.parseObject(ddlContent);
        JSONObject arch = json.getJSONObject("arch");
        assertTrue(arch != null && !arch.isEmpty(), "arch 字段必须存在且非空");
        
        // 正则匹配 README 中每行 `| packageName | ... | <32位md5> |` 的格式
        Pattern md5Pattern = Pattern.compile("[a-f0-9]{32}");
        
        List<String> missing = new ArrayList<>();
        for (String archKey : arch.keySet()) {
            JSONObject archEntry = arch.getJSONObject(archKey);
            String packageName = archEntry.getString("packageName");
            assertTrue(packageName != null && !packageName.isEmpty(),
                    "arch." + archKey + ".packageName 不能为空");
            
            // 检查 README 包含该 packageName
            assertTrue(readmeContent.contains(packageName),
                    "README 未记录包: " + packageName + " (arch=" + archKey + ")");
            
            // 检查同一行或相邻表格行包含 32 位 md5
            boolean hasMd5 = false;
            for (String line : readmeContent.split("\n")) {
                if (line.contains(packageName)) {
                    Matcher m = md5Pattern.matcher(line);
                    if (m.find()) {
                        hasMd5 = true;
                        break;
                    }
                }
            }
            if (!hasMd5) {
                missing.add(packageName);
            }
        }
        assertTrue(missing.isEmpty(),
                "以下包在 README 中缺少 32 位 md5 记录: " + missing);
    }
}
