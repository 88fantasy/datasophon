package com.datasophon.common.k8s.client;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.common.k8s.dto.UpgradeParams;
import com.datasophon.common.k8s.exception.HelmException;
import com.datasophon.common.k8s.vo.helm.HelmReleaseVO;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Helm 命令封装客户端
 */
@Slf4j
@Data
public class HelmClient implements AutoCloseable{

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
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("window")) {
            ExecResult result = ShellUtils.exec(null, Collections.singletonList("command -v helm"), -1);
            if (result.isSuccess()) {
                return result.getExecOut().trim();
            }
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
    public ExecResult execute(List<String> subCommandParts, int timeoutSeconds) {
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
        subCommandParts.add("-o");
        subCommandParts.add("json");
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
    public ExecResult executeWithResult(List<String> subCommandParts, int timeoutSeconds) throws HelmException {
        ExecResult result = execute(subCommandParts, timeoutSeconds);
        if (!result.isSuccess()) {
            throw new HelmException("helm 命令执行失败：" + result.getErrorTraceMessage());
        }
        return result;
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
        ExecResult result = executeWithResult(command, params.getTimeoutSeconds());
        try {
            return mapper.readValue(result.getExecOut(), HelmReleaseVO.class);
        } catch (Exception e) {
            throw new HelmException("解析 helm upgrade 响应失败：" + e.getMessage());
        }
    }




    @Override
    public void close() {
        if (tempDir != null) {
//            FIXME　
//            FileUtil.del(tempDir);
        }
    }
}