package com.datasophon.common.k8s.client;

import com.datasophon.common.k8s.exception.DockerException;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;
import lombok.Data;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * docker 命令封装客户端
 */
@Data
public class DockerClient  {

    private final String dockerPath;


    public static String detectDockerPath() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("window")) {
            ExecResult result = ShellUtils.exec(null, Arrays.asList("command", "-v", "docker"), -1);
            if (result.isSuccess()) {
                return result.getExecOut().trim();
            }
        }
        return "docker";
    }

    public DockerClient() {
        this.dockerPath = detectDockerPath();
    }

    /**
     * 执行 docker 命令的基础方法
     *
     * @param subCommandParts docker 子命令及其参数（不包含 docker 路径本身）
     * @return 执行结果
     */
    private ExecResult execute(List<String> subCommandParts, int timeoutSeconds) {
        List<String> commandParts = new ArrayList<>();
        commandParts.add(dockerPath);

        commandParts.addAll(subCommandParts);

        return ShellUtils.execWithBash(null, commandParts, timeoutSeconds);
    }

    /**
     * 执行 docker 命令并返回输出
     *
     * @param subCommandParts docker 子命令及其参数
     * @return 命令输出
     */
    public String executeToString(List<String> subCommandParts, int timeoutSeconds) {
        ExecResult result = execute(subCommandParts, timeoutSeconds);
        if (!result.isSuccess()) {
            throw new DockerException("docker 命令执行失败：" + result.getErrorTraceMessage());
        }
        return result.getExecOut();
    }

    /**
     * 通过 stdin 传递输入执行 docker 命令
     *
     * @param subCommandParts docker 子命令及其参数
     * @param stdinInput 要通过 stdin 传递的输入内容
     * @return 执行结果
     */
    private ExecResult executeWithStdin(List<String> subCommandParts, String stdinInput, int timeoutSeconds) {
        List<String> commandParts = new ArrayList<>();
        commandParts.add(dockerPath);
        commandParts.addAll(subCommandParts);

        return ShellUtils.execWithStdin(null, commandParts, stdinInput, timeoutSeconds);
    }

    /**
     * 加载镜像包
     *
     * @param file 镜像 tar 包文件
     */
    public String load(File file) {
        List<String> args = Arrays.asList("load", "-i", file.getAbsolutePath());
        ExecResult result = execute(args, -1);
        if (!result.isSuccess()) {
            throw new DockerException(String.format("加载镜像%s失败，%s", file.getName(), result.getErrorTraceMessage()));
        }
        String hint = "Loaded image:";
        if (result.getExecOut().startsWith(hint)) {
            return result.getExecOut().substring(hint.length());
        }
        throw new DockerException(String.format("加载镜像%s失败，%s", file.getName(), result.getExecOut()));
    }

    /**
     * 为镜像打标签
     *
     * @param imageId 源镜像（全路径：tag）
     * @param newImageId 目标镜像名（全路径：tag）
     */
    public void tagImage(String imageId, String newImageId) {
        List<String> args = Arrays.asList("tag", imageId, newImageId);
        ExecResult result = execute(args, 30);
        if (!result.isSuccess()) {
            throw new DockerException(String.format("为镜像%s打标签%s失败，%s", imageId, newImageId, result.getErrorTraceMessage()));
        }
    }

    /**
     * 删除镜像标签
     *
     * @param imageId 镜像 ID 或标签
     */
    public void removeImage(String imageId) {
        List<String> args = Arrays.asList("rmi", imageId);
        ExecResult result = execute(args, 30);
        if (!result.isSuccess()) {
            throw new DockerException(String.format("删除镜像%s失败，%s", imageId, result.getErrorTraceMessage()));
        }
    }

    /**
     * 推送镜像到仓库
     *
     * @param imageId 镜像 ID 或标签（全路径）
     */
    public void push(String imageId) {
        List<String> args = Arrays.asList("push", imageId);
        ExecResult result = execute(args, -1);
        if (!result.isSuccess()) {
            throw new DockerException(String.format("推送镜像%s失败，%s", imageId, result.getErrorTraceMessage()));
        }
    }

    /**
     * 登录到 Docker  registry
     *
     * @param registry registry 地址
     * @param username 用户名
     * @param password 密码
     */
    public void login(String registry, String username, String password) {
        // 使用 --password-stdin 方式传递密码，避免密码出现在命令行参数中
        List<String> args = Arrays.asList(
                "login",
                registry,
                "-u", username,
                "--password-stdin"
        );
        ExecResult result = executeWithStdin(args, password, 30);
        if (!result.isSuccess()) {
            throw new DockerException(String.format("登录 registry%s失败，%s", registry, result.getErrorTraceMessage()));
        }
    }

    /**
     * 从 registry 登出
     *
     * @param registry registry 地址
     */
    public void logout(String registry) {
        List<String> args = Arrays.asList("logout", registry);
        ExecResult result = execute(args, 30);
        if (!result.isSuccess()) {
            throw new DockerException(String.format("登出 registry%s失败，%s", registry, result.getErrorTraceMessage()));
        }
    }

    /**
     * 删除 Docker manifest
     *
     * @param name manifest 名称
     * @param ignoreErrorIfAbsent 如果 manifest 不存在是否忽略错误
     */
    public void rmManifest(String name, boolean ignoreErrorIfAbsent) {
        List<String> args = new ArrayList<>();
        args.add("manifest");
        args.add("rm");
        args.add(name);
        ExecResult result = execute(args, 30);
        if (!result.isSuccess()) {
            if (ignoreErrorIfAbsent) {
                // 忽略错误
                return;
            }
            throw new DockerException(String.format("删除 manifest %s 失败，%s", name, result.getErrorTraceMessage()));
        }
    }

    /**
     * 创建 Docker manifest
     *
     * @param name manifest 名称
     * @param tags 要添加到 manifest 的镜像 tag 列表
     * @param insecure 是否使用不安全连接
     */
    public void createManifest(String name, List<String> tags, boolean insecure) {
        List<String> args = new ArrayList<>();
        args.add("manifest");
        args.add("create");
        args.add(name);
        for (String tag : tags) {
            args.add("--amend");
            args.add(tag);
        }
        if (insecure) {
            args.add("--insecure");
        }
        ExecResult result = execute(args, 30);
        if (!result.isSuccess()) {
            throw new DockerException(String.format("创建 manifest %s 失败，%s", name, result.getErrorTraceMessage()));
        }
    }

    /**
     * 标注 Docker manifest（指定架构和操作系统）
     *
     * @param name manifest 名称
     * @param tag 镜像 tag
     * @param arch 架构（如 amd64, arm64）
     * @param os 操作系统（如 linux, windows）
     */
    public void annotateManifest(String name, String tag, String arch, String os) {
        List<String> args = new ArrayList<>();
        args.add("manifest");
        args.add("annotate");
        args.add(name);
        args.add(tag);
        args.add("--arch");
        args.add(arch);
        args.add("--os");
        args.add(os);
        ExecResult result = execute(args, 30);
        if (!result.isSuccess()) {
            throw new DockerException(String.format("标注 manifest %s 的镜像%s失败，%s", name, tag, result.getErrorTraceMessage()));
        }
    }

    /**
     * 推送 Docker manifest 到 registry
     *
     * @param name manifest 名称
     * @param insecure 是否使用不安全连接
     */
    public void pushManifest(String name, boolean insecure) {
        List<String> args = new ArrayList<>();
        args.add("manifest");
        args.add("push");
        args.add(name);
        if (insecure) {
            args.add("--insecure");
        }
        ExecResult result = execute(args, -1);
        if (!result.isSuccess()) {
            throw new DockerException(String.format("推送 manifest %s 失败，%s", name, result.getErrorTraceMessage()));
        }
    }


}
