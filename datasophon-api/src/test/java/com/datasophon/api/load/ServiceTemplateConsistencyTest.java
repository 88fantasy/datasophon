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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 防止配置模板与 service_ddl.json 漂移：每个 service_ddl.json 里 generators.templateName 引用的模板，
 * 必须能在同服务目录的 templates/ 下找到，或者属于 FreemakerUtils 按 configFormat 硬编码选取的
 * 引擎级格式模板（不属于任何单个服务，留在 worker classpath）。
 *
 * <p>本测试是"配置模板从 worker 迁移到元数据目录"改造的长期防护——新增服务如果漏放 templates/，
 * 会在这里直接失败，而不是等到部署现场触发 TemplateNotFoundException。
 */
class ServiceTemplateConsistencyTest {

    private static final String META_ROOT_RELATIVE = "package/raw/meta/datacluster-physical";

    /** FreemakerUtils.determinateTplName() 按 configFormat 硬编码选取，不属于任何单个服务。 */
    private static final Set<String> ENGINE_LEVEL_TEMPLATES =
            Set.of("xml.ftl", "properties.ftl", "properties2.ftl", "properties3.ftl");

    private static final Pattern TEMPLATE_NAME_PATTERN =
            Pattern.compile("\"templateName\"\\s*:\\s*\"([^\"]+)\"");

    @Test
    void everyDdlReferencedTemplateResolvesUnderServiceTemplatesOrEngineWhitelist() throws IOException {
        File metaRoot = locateRepoFile(META_ROOT_RELATIVE);
        assertTrue(metaRoot.isDirectory(), "meta root must exist: " + metaRoot.getAbsolutePath());

        File[] serviceDirs = metaRoot.listFiles(File::isDirectory);
        assertNotNull(serviceDirs, "meta root must contain service directories");

        List<String> missing = new ArrayList<>();
        for (File serviceDir : serviceDirs) {
            File ddl = new File(serviceDir, "service_ddl.json");
            if (!ddl.isFile()) {
                continue;
            }
            String content = Files.readString(ddl.toPath(), StandardCharsets.UTF_8);
            Matcher matcher = TEMPLATE_NAME_PATTERN.matcher(content);
            while (matcher.find()) {
                String templateName = matcher.group(1);
                if (ENGINE_LEVEL_TEMPLATES.contains(templateName)) {
                    continue;
                }
                File expected = new File(new File(serviceDir, "templates"), templateName);
                if (!expected.isFile()) {
                    missing.add(serviceDir.getName() + "/templates/" + templateName);
                }
            }
        }

        assertTrue(missing.isEmpty(),
                () -> "以下 templateName 在 service_ddl.json 中被引用，但 templates/ 目录下缺失（新增/改名服务时忘记放模板）："
                        + missing);
    }

    /**
     * 稳健地定位仓库文件：测试通过 {@code -pl datasophon-api} 运行时 user.dir 是模块目录，向上一级即仓库根；
     * 也兼容从仓库根直接运行的场景。写法与 {@code OtelCollectorDdlLoadTest} 一致。
     */
    private File locateRepoFile(String relative) {
        File candidate = new File(System.getProperty("user.dir")).toPath()
                .resolve("../")
                .resolve(relative)
                .normalize()
                .toFile();
        if (candidate.exists()) {
            return candidate;
        }
        candidate = new File(System.getProperty("user.dir"), relative);
        if (candidate.exists()) {
            return candidate;
        }
        return new File("../" + relative).getAbsoluteFile();
    }
}
