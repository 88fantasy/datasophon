package com.datasophon.common.k8s.client;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.common.k8s.exception.HelmifyException;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.ShellUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * helmify 命令封装客户端
 * 将 Kubernetes YAML 资源转换为 Helm Chart
 */
@Slf4j
@Data
public class HelmifyClient implements AutoCloseable {

    private final String helmifyPath;
    private final File tempDir;

    public static String detectHelmifyPath() {
        String path = System.getProperty("helmify.install_path");
        if (StrUtil.isNotBlank(path)) {
            return path;
        }
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("window")) {
            ExecResult result = ShellUtils.exec(null, Arrays.asList("command", "-v", "helmify"), -1);
            if (result.isSuccess()) {
                return result.getExecOut().trim();
            }
        }
        return "helmify";
    }

    public HelmifyClient() {
        this.helmifyPath = detectHelmifyPath();
        this.tempDir = PathUtils.getTmpDir("helmify/" + RandomUtil.randomString(12));
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("window")) {
            ShellUtils.execWithBash(null, Arrays.asList("chmod", "-R", "0700", tempDir.getAbsolutePath()), -1);
        }
    }

    /**
     * 执行 helmify 命令的基础方法
     *
     * @param subCommandParts helmify 子命令及其参数（不包含 helmify 路径本身）
     * @return 执行结果
     */
    private ExecResult execute(List<String> subCommandParts, int timeoutSeconds) {
        List<String> commandParts = new java.util.ArrayList<>();
        commandParts.add(helmifyPath);
        commandParts.addAll(subCommandParts);

        return ShellUtils.execWithBash(tempDir.getAbsolutePath(), commandParts, timeoutSeconds);
    }

    /**
     * 将 Kubernetes YAML 文件转换为 Helm Chart 并打包
     *
     * @param chartName   Chart 名称
     * @param yamlPath    YAML 文件或目录路径
     * @param version     Chart 版本号
     * @return 生成的 tar 包文件绝对路径
     * @throws HelmifyException 执行失败时抛出异常
     */
    public String createChart(String chartName, String version, String yamlPath) throws HelmifyException {
        if (StrUtil.isBlank(chartName)) {
            throw new IllegalArgumentException("chartName 不能为空");
        }
        if (StrUtil.isBlank(yamlPath)) {
            throw new IllegalArgumentException("yamlPath 不能为空");
        }
        if (StrUtil.isBlank(version)) {
            throw new IllegalArgumentException("version 不能为空");
        }
        File yamlFile = new File(yamlPath);
        if (!yamlFile.exists()) {
            throw new HelmifyException("YAML 文件或目录不存在：" + yamlPath);
        }
        // 1. 创建临时目录
        File chartTempDir = new File(tempDir, chartName);
        try {
            // 2. 执行 helmify 命令生成 Chart
            List<String> args = Arrays.asList(
                    "-f", yamlPath
            );
            ExecResult result = execute(args, 60);
            if (!result.isSuccess()) {
                throw new HelmifyException("helmify 命令执行失败：" + result.getErrorTraceMessage());
            }

            // 3. 修改 Chart.yaml 中的 version
            File chartYaml = new File(chartTempDir, "Chart.yaml");
            if (!chartYaml.exists()) {
                throw new HelmifyException("Chart.yaml 未生成：" + chartYaml.getAbsolutePath());
            }

            modifyChartVersion(chartYaml, version);

            // 4. 打包 Chart 为 tar 文件
            File targetDirectory = PathUtils.getTmpDir("helmify-tar/" + RandomUtil.randomString(12));
            String tarFileName = chartName + "-" + version + ".tgz";
            File tarFile = new File(targetDirectory, tarFileName);

            packageChart(chartTempDir, tarFile);
            log.info("Helm Chart 打包成功：{}", tarFile.getAbsolutePath());
            return tarFile.getAbsolutePath();
        } catch (Exception e) {
            throw new HelmifyException("转换并打包 Helm Chart 失败：" + e.getMessage(), e);
        } finally {
            FileUtil.del(chartTempDir);
        }
    }

    /**
     * 修改 Chart.yaml 中的 version 字段
     *
     * @param chartYamlFile Chart.yaml 文件
     * @param targetVersion 目标版本号
     */
    private void modifyChartVersion(File chartYamlFile, String targetVersion) throws HelmifyException {
        try {
            String content = FileUtil.readString(chartYamlFile, StandardCharsets.UTF_8);

            // 使用正则替换 appVersion 字段
            String updatedContent = content.replaceAll("(?m)^appVersion:\\s*.*$", "appVersion: " + targetVersion);

            FileUtil.writeString(updatedContent, chartYamlFile, StandardCharsets.UTF_8);
            log.debug("已更新 Chart.yaml version 为：{}", targetVersion);
        } catch (Exception e) {
            throw new HelmifyException("修改 Chart.yaml version 失败：" + e.getMessage(), e);
        }
    }

    /**
     * 将 Chart 目录打包为 tar.gz 文件
     *
     * @param chartDir Chart 目录
     * @param tarFile  输出的 tar 文件
     */
    private void packageChart(File chartDir, File tarFile) throws HelmifyException {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("window")) {
            // Windows 系统，使用 PowerShell 或 tar 命令
            try {
                // 尝试使用 tar 命令（Windows 10+ 自带）
                List<String> command = Arrays.asList(
                        "tar", "-czf", tarFile.getAbsolutePath(), "-C",
                        chartDir.getParentFile().getAbsolutePath(),
                        chartDir.getName()
                );
                ExecResult result = ShellUtils.execWithBash(null, command, 60);
                if (!result.isSuccess()) {
                    throw new HelmifyException("tar 打包失败：" + result.getErrorTraceMessage());
                }
            } catch (Exception e) {
                throw new HelmifyException("Windows 下打包失败：" + e.getMessage(), e);
            }
        } else {
            // Linux/Mac 系统
            try {
                List<String> command = Arrays.asList(
                        "tar", "-czf", tarFile.getAbsolutePath(), "-C",
                        chartDir.getParentFile().getAbsolutePath(),
                        chartDir.getName()
                );
                ExecResult result = ShellUtils.execWithBash(null, command, 60);
                if (!result.isSuccess()) {
                    throw new HelmifyException("tar 打包失败：" + result.getErrorTraceMessage());
                }
            } catch (Exception e) {
                throw new HelmifyException("打包失败：" + e.getMessage(), e);
            }
        }
    }

    @Override
    public void close() {
        if (tempDir != null) {
            FileUtil.del(tempDir);
        }
    }
}
