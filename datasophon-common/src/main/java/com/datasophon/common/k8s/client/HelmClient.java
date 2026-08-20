package com.datasophon.common.k8s.client;

import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.common.k8s.dto.UninstallParams;
import com.datasophon.common.k8s.dto.UpgradeParams;
import com.datasophon.common.k8s.exception.HelmException;
import com.datasophon.common.k8s.vo.helm.HelmHistoryVO;
import com.datasophon.common.k8s.vo.helm.HelmReleaseListItemVO;
import com.datasophon.common.k8s.vo.helm.HelmReleaseVO;
import com.datasophon.common.k8s.vo.helm.HelmStatusVO;
import com.datasophon.common.lang.VisibleForTesting;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Helm 命令封装客户端
 */
@Slf4j
@Data
public class HelmClient implements AutoCloseable {

    private final String helmPath;

    private final String kubeConfig;
    private final String token;
    private final String username;
    private final String password;
    private final String serverCert;
    private final String serverName;

    private final File tempDir;

    private final ObjectMapper mapper;

    public static String detectHelmPath() {
        String path = PropertyUtils.getString("helm.install_path");
        if (StrUtil.isNotBlank(path)) {
            return path;
        }
        return "helm";
    }

    public HelmClient(ClientOptions options) {
        this.helmPath = detectHelmPath();
        this.tempDir = PathUtils.getTmpDir("sensitive/" + RandomUtil.randomString(12));
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("window")) {
            ShellUtils.exec(null, Arrays.asList("chmod", "-R", "0700", tempDir.getAbsolutePath()), -1);
        }

        if (StrUtil.isNotBlank(options.getServerCert())) {
            File cert = new File(tempDir, "ca.cert");
            Base64.decodeToFile(options.getServerCert(), cert);
            this.serverCert = cert.getAbsolutePath();
        } else {
            this.serverCert = null;
        }
        this.token = options.getToken();
        this.username = options.getUsername();
        this.password = options.getPassword();
        this.serverName = options.getServerName();
        this.kubeConfig = SecureKubeConfigWriter.write(options, tempDir, serverCert);

        JsonMapper.Builder builder = JsonMapper.builder();
        builder.defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
        builder.defaultLocale(Locale.CHINA);
        builder.defaultTimeZone(TimeZone.getTimeZone("GMT+8"));

        builder.disable(MapperFeature.DEFAULT_VIEW_INCLUSION);
        builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        builder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        builder.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
        mapper = builder.build();
    }

    /**
     * 执行 helm 命令的基础方法
     *
     * @param subCommandParts helm 子命令及其参数（不包含 helm 路径本身）
     * @return 执行结果
     */
    @VisibleForTesting
    ExecResult execute(List<String> subCommandParts, int timeoutSeconds) {
        List<String> commandParts = new ArrayList<>();
        commandParts.add(helmPath);

        // SecureKubeConfigWriter 始终生成受限临时配置；认证信息不得回退到 argv。
        commandParts.add("--kubeconfig");
        commandParts.add(kubeConfig);
        commandParts.addAll(subCommandParts);

        // 参数必须直接交给 ProcessBuilder，不能再经 bash -c 拼接；release、namespace 等值
        // 可能来自 HTTP 请求，shell 拼接会把分号、命令替换等内容解释成额外命令。
        return ShellUtils.exec(null, commandParts, timeoutSeconds);
    }

    /**
     * 执行 helm 命令并返回执行结果
     *
     * @param subCommandParts helm 子命令及其参数
     * @return 执行结果
     * @throws HelmException 命令执行失败
     */
    @VisibleForTesting
    ExecResult executeForJsonResult(List<String> subCommandParts, int timeoutSeconds, boolean checkError) throws HelmException {
        List<String> commandParts = new ArrayList<>(subCommandParts);
        commandParts.add("-o");
        commandParts.add("json");
        ExecResult result = execute(commandParts, timeoutSeconds);
        if (checkError && !result.isSuccess()) {
            throw new HelmException("helm 命令执行失败：" + result.getErrorTraceMessage());
        }
        return result;
    }

    @VisibleForTesting
    ExecResult executeForJsonResult(List<String> subCommandParts, int timeoutSeconds) throws HelmException {
        return executeForJsonResult(subCommandParts, timeoutSeconds, true);
    }

    private <T> T convert(ExecResult result, Class<T> clazz) {
        try {
            String content = result.getExecOut();
            if (StrUtil.isBlank(content)) {
                content = "{}";
            }
            return mapper.readValue(content, clazz);
        } catch (Exception e) {
            throw new HelmException("解析 helm 响应失败：" + e.getMessage() + "。响应体：\n" + result.getExecOut());
        }
    }

    private <T> List<T> convertList(ExecResult result, Class<T> clazz) {
        try {
            String content = result.getExecOut();
            if (StrUtil.isBlank(content)) {
                content = "[]";
            }
            return mapper.readValue(content, mapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            throw new HelmException("解析 helm 响应失败：" + e.getMessage() + "。响应体：\n" + result.getExecOut());
        }
    }

    /**
     * 升级 Helm release
     *
     * @param params upgrade 参数
     * @return Helm Release VO
     * @throws HelmException 命令执行失败
     */
    public HelmReleaseVO upgrade(UpgradeParams params) throws HelmException {
        if (StrUtil.isBlank(params.getReleaseName())) {
            throw new HelmException("releaseName 不能为空");
        }
        if (StrUtil.isBlank(params.getChartPath())) {
            throw new HelmException("chartPath 不能为空");
        }

        List<String> command = buildUpgradeCommand(params);
        log.info("执行 helm upgrade: release={}, chart={}", params.getReleaseName(), params.getChartPath());
        ExecResult result = executeForJsonResult(command, params.getTimeoutSeconds() + 1);
        return convert(result, HelmReleaseVO.class);
    }

    /**
     * 构建 helm upgrade 命令参数
     *
     * @param params upgrade 参数
     * @return 命令参数列表
     */
    private List<String> buildUpgradeCommand(UpgradeParams params) {
        List<String> args = new ArrayList<>();
        args.add("upgrade");
        args.add(params.getReleaseName());
        args.add(params.getChartPath());

        // 添加 values 文件
        if (params.getValuesFiles() != null) {
            for (String valuesFile : params.getValuesFiles()) {
                args.add("--values");
                args.add(valuesFile);
            }
        }

        // 添加 set 参数
        if (params.getSetValues() != null) {
            for (String setValue : params.getSetValues()) {
                args.add("--set");
                args.add(setValue);
            }
        }

        // 添加 set-file 参数
        if (params.getSetFileValues() != null) {
            for (String setFileValue : params.getSetFileValues()) {
                args.add("--set-file");
                args.add(setFileValue);
            }
        }

        // 命名空间
        if (StrUtil.isNotBlank(params.getNamespace())) {
            args.add("--namespace");
            args.add(params.getNamespace());
        }

        // 超时
        args.add("--timeout");
        args.add(params.getTimeoutSeconds() + "s");

        // install 选项
        if (params.isInstall()) {
            args.add("--install");
        }

        // wait
        if (params.isWait()) {
            args.add("--wait");
        }
        if (params.isWaitForJob()) {
            args.add("--wait-for-jobs");
        }

        // description
        if (StrUtil.isNotBlank(params.getDescription())) {
            args.add("--description");
            args.add(params.getDescription());
        }

        return args;
    }

    /**
     * 列出 release。
     *
     * <p>{@code helm list -o json} 返回的是扁平结构（见 {@link HelmReleaseListItemVO}），
     * 与 {@code helm upgrade} 返回的 {@link HelmReleaseVO} 结构不同，不可混用。
     *
     * <p>不传 {@code --all}：该 flag 在 helm v4 已移除，且默认输出已覆盖
     * 接管扫描关心的 deployed 状态。
     *
     * @param namespace 命名空间；为空则跨全部命名空间（{@code -A}）
     * @param filter    按 status 过滤（如 deployed / failed）；为空则不过滤
     * @return release 列表
     * @throws HelmException 命令执行失败
     */
    public List<HelmReleaseListItemVO> list(String namespace, String filter) throws HelmException {
        List<String> args = new ArrayList<>();
        args.add("list");

        if (StrUtil.isNotBlank(namespace)) {
            args.add("--namespace");
            args.add(namespace);
        } else {
            args.add("-A");
        }

        ExecResult result = executeForJsonResult(args, 30);
        List<HelmReleaseListItemVO> releases = convertList(result, HelmReleaseListItemVO.class);
        if (StrUtil.isBlank(filter)) {
            return releases;
        }
        List<HelmReleaseListItemVO> filtered = new ArrayList<>();
        for (HelmReleaseListItemVO release : releases) {
            if (filter.equalsIgnoreCase(release.getStatus())) {
                filtered.add(release);
            }
        }
        return filtered;
    }

    /**
     * 读取 release 的 user-supplied values（{@code helm get values -o json}）。
     *
     * <p>只返回用户覆盖过的值，不含 chart 默认值；供接管场景的只读配置展示使用。
     *
     * @param releaseName release 名称
     * @param namespace   命名空间
     * @return values 的 JSON 文本；release 无自定义 values 时返回 {@code null} 字面量文本
     * @throws HelmException 命令执行失败
     */
    public String getValues(String releaseName, String namespace) throws HelmException {
        if (StrUtil.isBlank(releaseName)) {
            throw new IllegalArgumentException("releaseName 不能为空");
        }
        List<String> args = new ArrayList<>();
        args.add("get");
        args.add("values");
        args.add(releaseName);
        if (StrUtil.isNotBlank(namespace)) {
            args.add("--namespace");
            args.add(namespace);
        }

        return executeForJsonResult(args, 30).getExecOut();
    }

    /**
     * 查询指定 release 的历史记录
     *
     * @param releaseName release 名称
     * @param namespace   命名空间
     * @return 历史记录列表（按版本号降序排列）
     * @throws HelmException 命令执行失败
     */
    public List<HelmHistoryVO> history(String releaseName, String namespace) throws HelmException {
        if (StrUtil.isBlank(releaseName)) {
            throw new IllegalArgumentException("releaseName 不能为空");
        }

        List<String> args = new ArrayList<>();
        args.add("history");
        args.add(releaseName);

        if (StrUtil.isNotBlank(namespace)) {
            args.add("--namespace");
            args.add(namespace);
        }
        ExecResult result = executeForJsonResult(args, 30, false);

        if (!result.isSuccess() && !StrUtil.trimToEmpty(result.getExecOut()).contains("not found")) {
            throw new HelmException("helm 命令执行失败：" + result.getErrorTraceMessage());
        }
        // ignore not found error
        if (!result.isSuccess()) {
            return new ArrayList<>(0);
        }
        return convertList(result, HelmHistoryVO.class);
    }

    /**
     * 查询指定 release 的状态信息
     *
     * @param releaseName release 名称
     * @param namespace   命名空间
     * @param revision    修订版本号
     * @return release 状态信息
     * @throws HelmException 命令执行失败
     */
    public HelmStatusVO status(String releaseName, String namespace, Integer revision) throws HelmException {
        List<String> args = new ArrayList<>();
        args.add("status");
        args.add(releaseName);

        if (StrUtil.isNotBlank(namespace)) {
            args.add("--namespace");
            args.add(namespace);
        }

        if (revision != null) {
            args.add("--revision");
            args.add(revision.toString());
        }

        ExecResult result = executeForJsonResult(args, 30);
        return convert(result, HelmStatusVO.class);
    }

    /**
     * 卸载 Helm release（保留历史记录）
     *
     * @param namespace   命名空间
     * @param releaseName release 名称
     * @throws HelmException 命令执行失败
     */
    public void uninstall(String namespace, String releaseName) throws HelmException {
        UninstallParams params = new UninstallParams();
        params.setNamespace(namespace);
        params.setReleaseName(releaseName);
        params.setKeepHistory(true);
        uninstall(params);
    }

    /**
     * 卸载 Helm release
     *
     * @param params 卸载参数
     * @throws HelmException 命令执行失败
     */
    public void uninstall(UninstallParams params) throws HelmException {
        if (StrUtil.isBlank(params.getReleaseName())) {
            throw new HelmException("releaseName 不能为空");
        }

        List<String> args = new ArrayList<>();
        args.add("uninstall");
        args.add(params.getReleaseName());

        // 命名空间
        if (StrUtil.isNotBlank(params.getNamespace())) {
            args.add("--namespace");
            args.add(params.getNamespace());
        }

        // 保留 release 历史记录
        if (params.isKeepHistory()) {
            args.add("--keep-history");
        }

        // 超时
        args.add("--timeout");
        args.add(params.getTimeoutSeconds() + "s");

        log.info("执行 helm uninstall: release={}, keepHistory={}", params.getReleaseName(), params.isKeepHistory());
        ExecResult result = execute(args, params.getTimeoutSeconds() + 1);

        // 如果执行失败，检查是否是因为 release 不存在
        if (!result.isSuccess()) {
            String errorMsg = result.getErrorTraceMessage();
            // 如果 release 不存在，也认为执行成功
            if (errorMsg.contains("release") && errorMsg.contains("not found")) {
                log.info("helm release 不存在，视为已成功：{}", params.getReleaseName());
                return;
            }
            throw new HelmException("helm 命令执行失败：" + errorMsg);
        }
    }

    @Override
    public void close() {
        if (tempDir != null) {
            FileUtil.del(tempDir);
        }
    }

}
