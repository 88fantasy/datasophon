package com.datasophon.common.k8s.client;

import cn.hutool.core.util.StrUtil;
import com.datasophon.common.k8s.dto.ChartInstallParameter;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Helm 命令封装客户端
 */
@Data
public class HelmClient {

    private final String helmPath;
    private final String username;
    private final String password;

    public static String detectHelmPath() {
        String path = PropertyUtils.getString("helm.install_path");
        if (StrUtil.isNotBlank(path)) {
            return path;
        }
        ExecResult result = ShellUtils.exec(null, Collections.singletonList("command -v helm"), -1);
        if (result.isSuccess()) {
            return result.getExecOut();
        }
        return null;
    }


    /**
     * 执行 Helm 命令的基础方法
     *
     * @param subCommandParts Helm 子命令及其参数（不包含 helm 路径本身）
     * @return 执行结果
     */
    public ExecResult execute(String workPath, List<String> subCommandParts, int timeoutSeconds)  {
        // 构建完整命令参数列表
        List<String> commandParts = new ArrayList<>();
        commandParts.add(helmPath);          // 添加 helm 可执行文件路径
        commandParts.addAll(subCommandParts);

        return ShellUtils.execWithBash(workPath, commandParts, timeoutSeconds);
    }


    /**
     * 安装或升级一个 release（helm upgrade --install）
     *
     * @return 执行结果
     */
    public ExecResult upgradeInstall(String workspace, ChartInstallParameter parameter, int timeout) {
        List<String> args = new ArrayList<>();
        args.add("upgrade");
        args.add("--install");
        args.add(parameter.getReleaseName());
        args.add(parameter.getReleaseName());
        args.add(parameter.getChart());
        args.add("--namespace");
        args.add(parameter.getNamespace());

        if (parameter.getValuesFiles() != null) {
            for (String file : parameter.getValuesFiles()) {
                args.add("--values");
                args.add(file);
            }
        }
        if (parameter.getSetValues() != null) {
            for (String set : parameter.getSetValues()) {
                args.add("--set");
                args.add(set);
            }
        }

        if (parameter.getExtraParameters() != null) {
            args.addAll(parameter.getExtraParameters());
        }

        return execute(workspace, args, timeout);
    }

    /**
     * 卸载 release
     *
     * @param releaseName release 名称
     * @param namespace   命名空间
     */
    public ExecResult uninstall(String releaseName, String namespace, int timeout) {
        List<String> args = Arrays.asList("uninstall", releaseName, "--namespace", namespace);
        return execute(null, args, timeout);
    }

    /**
     * 列出 releases
     *
     * @param namespace 命名空间（如果为 null 则列出所有命名空间）
     * @return 执行结果（stdout 包含列表信息）
     */
    public ExecResult list(String namespace) {
        List<String> args = new ArrayList<>();
        args.add("list");
        if (namespace != null) {
            args.add("--namespace");
            args.add(namespace);
        } else {
            args.add("--all-namespaces");
        }
        return execute(null, args, -1);
    }

    /**
     * 获取 release 状态
     *
     * @param releaseName release 名称
     * @param namespace   命名空间
     */
    public ExecResult status(String releaseName, String namespace) {
        List<String> args = Arrays.asList("status", releaseName, "--namespace", namespace);
        return execute(null, args, -1);
    }

    /**
     * 获取 release 的 values
     *
     * @param releaseName release 名称
     * @param namespace   命名空间
     */
    public ExecResult getValues(String releaseName, String namespace) {
        List<String> args = Arrays.asList("get", "values", releaseName, "--namespace", namespace);
        return execute(null, args, -1);
    }

    /**
     * 测试 release（helm test）
     *
     * @param releaseName release 名称
     * @param namespace   命名空间
     */
    public ExecResult test(String releaseName, String namespace) {
        List<String> args = Arrays.asList("test", releaseName, "--namespace", namespace);
        return execute(null, args, -1);
    }

    /**
     * 添加 Helm 仓库
     *
     * @param repoName 仓库名称
     * @param repoUrl  仓库 URL
     */
    public ExecResult repoAdd(String repoName, String repoUrl) {
        List<String> args = new ArrayList<>();
        args.add("repo");
        args.add("add");
        args.add(repoName);
        args.add(repoUrl);
        if (username != null) {
            args.add("--username");
            args.add(username);
            args.add("--password");
            args.add(password);
        }
        return execute(null, args, -1);
    }

    /**
     * 更新 Helm 仓库
     */
    public ExecResult repoUpdate(int timeout) {
        List<String> args = new ArrayList<>();
        args.add("repo");
        args.add("update");
        if (username != null) {
            args.add("--username");
            args.add(username);
            args.add("--password");
            args.add(password);
        }
        return execute(null, args, timeout);
    }

    /**
     * 拉取 chart（helm pull）
     *
     * @param chartRef chart 引用（如 repo/chart）
     * @param destDir  目标目录
     */
    public ExecResult pull(String chartRef, String destDir) {
        List<String> args = Arrays.asList("pull", chartRef, "--destination", destDir);
        return execute(null, args, -1);
    }



}