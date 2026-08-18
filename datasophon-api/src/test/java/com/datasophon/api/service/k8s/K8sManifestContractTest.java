package com.datasophon.api.service.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import com.datasophon.common.model.k8s.K8sArtifact;
import com.datasophon.common.model.k8s.K8sOperatorArtifact;
import com.datasophon.common.model.k8s.K8sServiceInfo;
import com.datasophon.common.utils.YamlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * 「数衍平台 K8s」框架清单包的契约测试。
 *
 * <p>用与 {@code DdlMetaServiceImpl.loadServiceK8sDdl} 相同的解析与校验方式，
 * 确保清单在导入前就能发现格式错误，而不是等上传到 Nexus 后才在启动日志里报错。
 */
class K8sManifestContractTest {

    /** 清单目录，相对仓库根。测试工作目录是 datasophon-api，故上溯一级。 */
    private static final Path MANIFEST_DIR = Paths.get("..", "package", "raw", "meta", "datacluster-k8s");

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("每份 manifest.yaml 都能解析，且通过 K8sServiceInfo 的必填校验")
    void everyManifestParsesAndValidates() throws IOException {
        List<Path> manifests = manifests();
        assertThat(manifests).as("清单目录不应为空").isNotEmpty();

        List<String> failures = new ArrayList<>();
        for (Path manifest : manifests) {
            String content = Files.readString(manifest, StandardCharsets.UTF_8);
            K8sServiceInfo info;
            try {
                info = YamlUtils.parseYaml(content, K8sServiceInfo.class);
            } catch (Exception e) {
                failures.add(manifest + " 解析失败：" + e.getMessage());
                continue;
            }
            Set<ConstraintViolation<K8sServiceInfo>> violations = validator.validate(info);
            for (ConstraintViolation<K8sServiceInfo> violation : violations) {
                failures.add(manifest + " " + violation.getMessage());
            }
            // 与 loadServiceK8sDdl 一致：artifact 至少支持 helm/yaml/operator 三种部署方式之一
            boolean hasArtifact = info.getArtifact() != null
                    && (isNotBlank(info.getArtifact().getHelm()) || isNotBlank(info.getArtifact().getYaml())
                            || info.getArtifact().getOperator() != null);
            if (!hasArtifact) {
                failures.add(manifest + " artifact 未声明 helm、yaml 或 operator");
            }
        }
        assertThat(failures).isEmpty();
    }

    @Test
    @DisplayName("只声明 kind=operator、无 helm/yaml 的 manifest 通过 artifact 校验")
    void operatorOnlyArtifact_passesValidation() {
        // 覆盖"下一个 operator 服务完全不由 datasophon 安装，只做接管扫描"的场景
        // （本次 Doris 因保留原有 yaml 字段不会触发这个分支，此处单独构造校验通用性）。
        K8sArtifact artifact = new K8sArtifact();
        artifact.setKind(K8sArtifact.KIND_OPERATOR);
        K8sOperatorArtifact operator = new K8sOperatorArtifact();
        operator.setGroup("example.io");
        operator.setVersion("v1");
        operator.setPlural("examples");
        artifact.setOperator(operator);

        K8sServiceInfo info = new K8sServiceInfo();
        info.setName("example-operator-service");
        info.setVersion("1.0.0");
        info.setType("MIDDLEWARE");
        info.setArtifact(artifact);

        Set<ConstraintViolation<K8sServiceInfo>> violations = validator.validate(info);
        assertThat(violations).isEmpty();

        boolean hasArtifact = info.getArtifact() != null
                && (isNotBlank(info.getArtifact().getHelm()) || isNotBlank(info.getArtifact().getYaml())
                        || info.getArtifact().getOperator() != null);
        assertThat(hasArtifact).as("只有 operator 也应视为已声明部署方式，不应报错").isTrue();
    }

    @Test
    @DisplayName("Doris manifest 的 operator 块解析出正确的 GVK 与角色探测正则")
    void dorisManifest_operatorBlockParsesCorrectly() throws IOException {
        Path dorisManifest = MANIFEST_DIR.resolve("doris").resolve("manifest.yaml");
        K8sServiceInfo info = YamlUtils.parseYaml(
                Files.readString(dorisManifest, StandardCharsets.UTF_8), K8sServiceInfo.class);

        K8sArtifact artifact = info.getArtifact();
        assertThat(artifact.getKind()).isEqualTo(K8sArtifact.KIND_OPERATOR);
        // 安装路径不变：kind=operator 与原有 yaml 字段共存
        assertThat(artifact.getYaml()).isEqualTo("ddc-cluster.yaml");

        K8sOperatorArtifact operator = artifact.getOperator();
        assertThat(operator.getGroup()).isEqualTo("disaggregated.cluster.doris.com");
        assertThat(operator.getVersion()).isEqualTo("v1");
        assertThat(operator.getKind()).isEqualTo("DorisDisaggregatedCluster");
        assertThat(operator.getPlural()).isEqualTo("dorisdisaggregatedclusters");
        assertThat(operator.getMonitorProfile()).isEqualTo("doris-disaggregated");

        assertThat(operator.getRoles()).hasSize(2);
        assertThat(operator.getRoles().get(0).getName()).isEqualTo("fe");
        assertThat(operator.getRoles().get(0).getJobPattern()).isEqualTo("-fe$");
        assertThat(operator.getRoles().get(1).getName()).isEqualTo("compute");
        // YAML 单引号字符串不转义，\d 应原样保留（不是被吞成 d）
        assertThat(operator.getRoles().get(1).getJobPattern()).isEqualTo("-cg\\d+$");

        // 正则本身要能正确编译并按预期匹配 job 名，而不仅仅是字符串相等
        assertThat("doris-disaggregated-cluster-fe".matches(".*" + operator.getRoles().get(0).getJobPattern()))
                .isTrue();
        assertThat("doris-disaggregated-cluster-cg1".matches(".*" + operator.getRoles().get(1).getJobPattern()))
                .isTrue();
        assertThat("doris-disaggregated-cluster-cg12".matches(".*" + operator.getRoles().get(1).getJobPattern()))
                .isTrue();
        assertThat("doris-disaggregated-cluster-ms".matches(".*" + operator.getRoles().get(1).getJobPattern()))
                .isFalse();
    }

    @Test
    @DisplayName("服务名与所在目录名一致，且分类取值合法")
    void serviceNameMatchesDirectoryAndTypeIsValid() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Path manifest : manifests()) {
            K8sServiceInfo info = YamlUtils.parseYaml(
                    Files.readString(manifest, StandardCharsets.UTF_8), K8sServiceInfo.class);
            String dirName = manifest.getParent().getFileName().toString();
            // 目录名即 Nexus 上的 serviceName，必须与清单内 name 一致，否则匹配会错位
            if (!dirName.equals(info.getName())) {
                failures.add(manifest + " 目录名 " + dirName + " 与 name " + info.getName() + " 不一致");
            }
            if (!List.of("ENVIRONMENT", "MIDDLEWARE", "APPLICATION").contains(info.getType())) {
                failures.add(manifest + " type 取值非法：" + info.getType());
            }
        }
        assertThat(failures).isEmpty();
    }

    /**
     * 目标集群 {@code helm list -A} 的全部 11 条 deployed release 的 chart 字段
     * （2026-08-17 实测，见任务清单 §9.2）。
     */
    private static final List<String> TARGET_CLUSTER_CHARTS = List.of(
            "apisix-2.12.5",
            "cert-manager-v1.20.3",
            "dolphinscheduler-helm-3.2.2",
            "elasticsearch-8.5.1",
            "fdb-operator-0.3.0",
            "juicefs-csi-driver-0.31.4",
            "kyuubi-0.1.0",
            "nacos-operator-0.1.0",
            "opentelemetry-operator-0.114.1",
            "redis-cluster-13.0.4",
            "zookeeper-13.8.7");

    @Test
    @DisplayName("清单覆盖目标集群全部 11 条 Helm release，无一落入 pending")
    void coversEveryReleaseOfTargetCluster() throws IOException {
        List<String> declaredNames = new ArrayList<>();
        for (Path manifest : manifests()) {
            declaredNames.add(YamlUtils.parseYaml(
                    Files.readString(manifest, StandardCharsets.UTF_8), K8sServiceInfo.class).getName());
        }

        List<String> uncovered = new ArrayList<>();
        for (String chart : TARGET_CLUSTER_CHARTS) {
            // 与 K8sTakeoverScanService 一致：按最后一个连字符切出 chart 名
            String chartName = chart.substring(0, chart.lastIndexOf('-'));
            if (!declaredNames.contains(chartName)) {
                uncovered.add(chart + " → " + chartName);
            }
        }
        assertThat(uncovered).as("这些 release 会落入 pending 需人工绑定").isEmpty();
    }

    private List<Path> manifests() throws IOException {
        if (!Files.isDirectory(MANIFEST_DIR)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(MANIFEST_DIR)) {
            return paths.filter(p -> p.getFileName().toString().equals("manifest.yaml")).sorted().toList();
        }
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
