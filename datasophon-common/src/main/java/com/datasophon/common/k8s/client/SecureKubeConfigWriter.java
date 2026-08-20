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

package com.datasophon.common.k8s.client;

import com.datasophon.common.k8s.config.ClientOptions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;

/** 把 K8s 凭据写入受限临时 kubeconfig，避免凭据出现在 argv 和日志中。 */
final class SecureKubeConfigWriter {

    private static final String NAME = "datasophon";

    private SecureKubeConfigWriter() {
    }

    static String write(ClientOptions options, File tempDir, String certificateAuthority) {
        File config = new File(tempDir, "kubeConfig.yaml");
        String content = StrUtil.isNotBlank(options.getKubeConfig())
                ? options.getKubeConfig()
                : generatedContent(options, certificateAuthority);
        FileUtil.writeString(content, config, StandardCharsets.UTF_8);
        if (!System.getProperty("os.name").toLowerCase().contains("window")) {
            try {
                Files.setPosixFilePermissions(config.toPath(), PosixFilePermissions.fromString("rw-------"));
            } catch (IOException e) {
                throw new IllegalStateException("设置 kubeconfig 文件权限失败", e);
            }
        }
        return config.getAbsolutePath();
    }

    private static String generatedContent(ClientOptions options, String certificateAuthority) {
        Map<String, Object> cluster = new LinkedHashMap<>();
        cluster.put("server", options.getServerName());
        if (StrUtil.isNotBlank(certificateAuthority)) {
            cluster.put("certificate-authority", certificateAuthority);
        } else {
            cluster.put("insecure-skip-tls-verify", true);
        }

        Map<String, Object> user = new LinkedHashMap<>();
        if (StrUtil.isNotBlank(options.getToken())) {
            user.put("token", options.getToken());
        } else {
            user.put("username", options.getUsername());
            user.put("password", options.getPassword());
        }

        Map<String, Object> context = Map.of("cluster", NAME, "user", NAME);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("apiVersion", "v1");
        root.put("kind", "Config");
        root.put("clusters", List.of(Map.of("name", NAME, "cluster", cluster)));
        root.put("users", List.of(Map.of("name", NAME, "user", user)));
        root.put("contexts", List.of(Map.of("name", NAME, "context", context)));
        root.put("current-context", NAME);
        return new Yaml().dump(root);
    }
}
