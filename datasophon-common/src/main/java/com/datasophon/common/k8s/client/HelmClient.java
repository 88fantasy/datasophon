package com.datasophon.common.k8s.client;

import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.common.k8s.dto.UninstallParams;
import com.datasophon.common.k8s.dto.UpgradeParams;
import com.datasophon.common.k8s.exception.HelmException;
import com.datasophon.common.k8s.vo.helm.HelmHistoryVO;
import com.datasophon.common.k8s.vo.helm.HelmReleaseVO;
import com.datasophon.common.k8s.vo.helm.HelmStatusVO;
import com.datasophon.common.lang.VisibleForTesting;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

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
        
        if (StrUtil.isNotBlank(options.getKubeConfig())) {
            File config = new File(tempDir, "kubeConfig.yaml");
            FileUtil.writeString(options.getKubeConfig(), config, StandardCharsets.UTF_8);
            this.kubeConfig = config.getAbsolutePath();
        } else {
            this.kubeConfig = null;
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
        
        // 添加认证参数
        if (StrUtil.isNotBlank(kubeConfig)) {
            commandParts.add("--kubeconfig");
            commandParts.add(kubeConfig);
        } else {
            if (StrUtil.isNotBlank(token)) {
                commandParts.add("--kube-token");
                commandParts.add(token);
            } else if (StrUtil.isNotBlank(username) && StrUtil.isNotBlank(password)) {
                commandParts.add("--kube-username");
                commandParts.add(username);
                commandParts.add("--kube-password");
                commandParts.add(password);
            }
            if (StrUtil.isNotBlank(serverCert)) {
                commandParts.add("--kube-ca-file");
                commandParts.add(serverCert);
            }
            if (StrUtil.isNotBlank(serverName)) {
                commandParts.add("--kube-apiserver");
                commandParts.add(serverName);
            }
        }
        commandParts.addAll(subCommandParts);
        
        return ShellUtils.execWithBash(null, commandParts, timeoutSeconds);
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
     * 列出指定 namespace 的 release 列表
     *
     * @param namespace 命名空间
     * @param filter    过滤条件（如 pending, deployed, failed 等）
     * @return release 列表
     * @throws HelmException 命令执行失败
     */
    public List<HelmReleaseVO> list(String namespace, String filter) throws HelmException {
        List<String> args = new ArrayList<>();
        args.add("list");
        
        if (StrUtil.isNotBlank(namespace)) {
            args.add("--namespace");
            args.add(namespace);
        }
        
        args.add("--all");
        
        ExecResult result = executeForJsonResult(args, 30);
        List<HelmReleaseVO> releases = convertList(result, HelmReleaseVO.class);
        List<HelmReleaseVO> filtered = new ArrayList<>();
        for (HelmReleaseVO release : releases) {
            if (release.getInfo() != null && filter.equalsIgnoreCase(release.getInfo().getStatus())) {
                filtered.add(release);
            }
        }
        return filtered;
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
            // FIXME
            // FileUtil.del(tempDir);
        }
    }
    
}