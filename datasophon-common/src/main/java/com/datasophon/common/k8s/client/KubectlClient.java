package com.datasophon.common.k8s.client;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.common.k8s.exception.KubectlException;
import com.datasophon.common.k8s.vo.k8s.K8sConfigMap;
import com.datasophon.common.k8s.vo.k8s.K8sDeployment;
import com.datasophon.common.k8s.vo.k8s.K8sIngress;
import com.datasophon.common.k8s.vo.k8s.K8sNamespace;
import com.datasophon.common.k8s.vo.k8s.K8sNode;
import com.datasophon.common.k8s.vo.k8s.K8sPod;
import com.datasophon.common.k8s.vo.k8s.K8sResourceList;
import com.datasophon.common.k8s.vo.k8s.K8sService;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import lombok.Data;

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
 * kubectl 命令封装客户端
 */
@Data
public class KubectlClient implements AutoCloseable {

    private final ObjectMapper mapper;

    private final String kubectlPath;
    private final String kubeConfig;
    private final String token;
    private final String username;
    private final String password;
    private final String serverCert;
    private final String serverName;

    private final File tempDir;


    public static String detectKubectlPath() {
        String path = PropertyUtils.getString("kubectl.install_path");
        if (StrUtil.isNotBlank(path)) {
            return path;
        }
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("window")) {
            ExecResult result = ShellUtils.exec(null, Arrays.asList("command", "-v", "kubectl"), -1);
            if (result.isSuccess()) {
                return result.getExecOut().trim();
            }
        }
        return "kubectl";
    }

    public KubectlClient(ClientOptions options) {
        this.kubectlPath = detectKubectlPath();
        this.tempDir = PathUtils.getTmpDir("sensitive/" + RandomUtil.randomString(12));
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("window")) {
            ShellUtils.exec(null, Arrays.asList("chmod", "-R",  "0700", tempDir.getAbsolutePath()), -1);
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
     * 执行 kubectl 命令的基础方法
     *
     * @param subCommandParts kubectl 子命令及其参数（不包含 kubectl 路径本身）
     * @return 执行结果
     */
    public ExecResult execute(List<String> subCommandParts, int timeoutSeconds) {
        List<String> commandParts = new ArrayList<>();
        commandParts.add(kubectlPath);

        // 认证优先级：kubeConfig > token > username/password
        if (StrUtil.isNotBlank(kubeConfig)) {
            commandParts.add("--kubeconfig");
//            路径可能存在空格
            commandParts.add(String.format("%s", kubeConfig));
        } else {
            if (StrUtil.isNotBlank(token)) {
                commandParts.add("--token");
                commandParts.add(token);
            } else {
                commandParts.add("--username");
                commandParts.add(username);
                commandParts.add("--password");
                commandParts.add(password);
            }
            // 如果有证书，添加证书支持
            if (StrUtil.isNotBlank(serverCert)) {
                commandParts.add("--certificate-authority");
                commandParts.add(String.format("%s", serverCert));
            } else {
                commandParts.add("--insecure-skip-tls-verify=true");
            }
            commandParts.add("--server");
            commandParts.add(serverName);
        }
        commandParts.addAll(subCommandParts);

        return ShellUtils.execWithBash(null, commandParts, timeoutSeconds);
    }


    /**
     * 执行 kubectl 命令并返回 JSON 解析结果
     *
     * @param subCommandParts kubectl 子命令及其参数
     * @return JSON 根节点
     * @throws KubectlException JSON 解析失败
     */
    public String executeToJson(List<String> subCommandParts, int timeoutSeconds) throws KubectlException {
        List<String> args = new ArrayList<>(subCommandParts);
        args.add("-o");
        args.add("json");

        ExecResult result = execute(args, timeoutSeconds);
        if (!result.isSuccess()) {
            throw new KubectlException("kubectl 命令执行失败：" + result.getErrorTraceMessage());
        }

        return result.getExecOut();
    }

    /**
     * 获取集群版本信息
     *
     * @return 版本字符串
     */
    public String getVersion() throws KubectlException {
        ExecResult result = execute(Collections.singletonList("version"), 5);
        if (!result.isSuccess()) {
            throw new KubectlException("获取 K8s 版本失败：" + result.getExecOut());
        }

        String output = result.getExecOut().trim();
        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            if (line.contains("Server Version:")) {
                return line.split(":")[1].trim();
            }
        }
        throw new KubectlException("无法解析 K8s 版本信息：" + output);
    }

    /**
     * 获取节点列表
     *
     * @return 节点列表
     */
    public K8sResourceList<K8sNode> getNodes() throws KubectlException {
        String jsonNode = executeToJson(Arrays.asList("get", "nodes"), 30);
        return parseResourceList(jsonNode, K8sNode.class);
    }

    /**
     * 获取命名空间列表
     *
     * @return 命名空间列表
     */
    public K8sResourceList<K8sNamespace> getNamespaces() throws KubectlException {
        String jsonNode = executeToJson(Arrays.asList("get", "namespaces"), 30);
        return parseResourceList(jsonNode, K8sNamespace.class);
    }

    /**
     * 获取指定命名空间的 Pods
     *
     * @param namespace     命名空间
     * @param labelSelector 标签选择器
     * @return Pod 列表
     */
    public K8sResourceList<K8sPod> getPods(String namespace, String labelSelector) throws KubectlException {
        List<String> args = new ArrayList<>(Arrays.asList("get", "pods", "-n", namespace));
        if (StrUtil.isNotBlank(labelSelector)) {
            args.add("-l");
            args.add(labelSelector);
        }
        String jsonNode = executeToJson(args, 30);
        return parseResourceList(jsonNode, K8sPod.class);
    }

    /**
     * 获取指定命名空间的 Deployments
     *
     * @param namespace     命名空间
     * @param labelSelector 标签选择器
     * @return Deployment 列表
     */
    public K8sResourceList<K8sDeployment> getDeployments(String namespace, String labelSelector) throws KubectlException {
        List<String> args = new ArrayList<>(Arrays.asList("get", "deployments", "-n", namespace));
        if (StrUtil.isNotBlank(labelSelector)) {
            args.add("-l");
            args.add(labelSelector);
        }
        String jsonNode = executeToJson(args, 30);
        return parseResourceList(jsonNode, K8sDeployment.class);
    }

    /**
     * 获取指定命名空间的 Services
     *
     * @param namespace     命名空间
     * @param labelSelector 标签选择器
     * @return Service 列表
     */
    public K8sResourceList<K8sService> getServices(String namespace, String labelSelector) throws KubectlException {
        List<String> args = new ArrayList<>(Arrays.asList("get", "services", "-n", namespace));
        if (StrUtil.isNotBlank(labelSelector)) {
            args.add("-l");
            args.add(labelSelector);
        }
        String jsonNode = executeToJson(args, 30);
        return parseResourceList(jsonNode, K8sService.class);
    }

    /**
     * 获取指定命名空间的 Ingresses
     *
     * @param namespace     命名空间
     * @param labelSelector 标签选择器
     * @return Ingress 列表
     */
    public K8sResourceList<K8sIngress> getIngresses(String namespace, String labelSelector) throws KubectlException {
        List<String> args = new ArrayList<>(Arrays.asList("get", "ingresses", "-n", namespace, "--api-version", "networking.k8s.io/v1"));
        if (StrUtil.isNotBlank(labelSelector)) {
            args.add("-l");
            args.add(labelSelector);
        }
        String jsonNode = executeToJson(args, 30);
        return parseResourceList(jsonNode, K8sIngress.class);
    }

    /**
     * 获取指定命名空间的 ConfigMaps
     *
     * @param namespace     命名空间
     * @param labelSelector 标签选择器
     * @return ConfigMap 列表
     */
    public K8sResourceList<K8sConfigMap> getConfigMaps(String namespace, String labelSelector) throws KubectlException {
        List<String> args = new ArrayList<>(Arrays.asList("get", "configmaps", "-n", namespace));
        if (StrUtil.isNotBlank(labelSelector)) {
            args.add("-l");
            args.add(labelSelector);
        }
        String jsonNode = executeToJson(args, 30);
        return parseResourceList(jsonNode, K8sConfigMap.class);
    }

    /**
     * 解析 K8s 资源列表 JSON
     */
    private <T> K8sResourceList<T> parseResourceList(String content, Class<T> resourceClass) throws KubectlException {
        TypeFactory tf = mapper.getTypeFactory();
        JavaType type = tf.constructParametricType(K8sResourceList.class, resourceClass);
        try {
            return mapper.readValue(content, type);
        } catch (JsonProcessingException e) {
            throw new KubectlException(String.format("解析结果错误失败，%s", e.getMessage()), e);
        }
    }

    /**
     * 检查资源是否存在
     *
     * @param namespace     命名空间
     * @param resourceType  资源类型
     * @param labelSelector 标签选择器
     * @return 是否存在
     */
    public boolean hasResources(String namespace, String resourceType, String labelSelector) throws KubectlException {
        List<String> args = new ArrayList<>(Arrays.asList("get", resourceType, "-n", namespace));
        if (StrUtil.isNotBlank(labelSelector)) {
            args.add("-l");
            args.add(labelSelector);
        }
        args.add("-o");
        args.add("jsonpath={.items[*].metadata.name}");

        ExecResult result = execute(args, 30);
        if (!result.isSuccess()) {
            return false;
        }
        String output = result.getExecOut().trim();
        return StrUtil.isNotBlank(output);
    }

    @Override
    public void close() {
        if (tempDir != null) {
//            FIXME　
//            FileUtil.del(tempDir);
        }
    }
}
