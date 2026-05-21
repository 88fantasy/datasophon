package com.datasophon.api.master.handler.k8s;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import com.datasophon.api.dto.instance.K8sNamespaceIdentityDTO;
import com.datasophon.api.utils.HelmValueUtils;
import com.datasophon.common.k8s.client.HelmClient;
import com.datasophon.common.k8s.dto.UpgradeParams;
import com.datasophon.common.k8s.vo.helm.HelmHistoryVO;
import com.datasophon.common.k8s.vo.helm.HelmReleaseVO;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.nexus.NexusFacade;
import com.datasophon.common.utils.nexus.vo.Assert;
import com.datasophon.common.utils.nexus.vo.Component;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class K8sAgentInstallHandler extends K8sAgentHandler {

    @Override
    public void execute(K8sClusterConfig config) {
        log.info("开始部署 K8s Agent: clusterId={}", config.getClusterId());
        try {
            ensureNamespaceExists(config);
            // 从 classpath 读取 Helm Chart 到临时目录
            File chartFile = downloadChartToTemp();

            applyHelmChart(config, chartFile);
        } catch (Exception e) {
            log.error("K8s Agent 部署失败：{}", RELEASE_NAME, e);
            throw new RuntimeException("部署 K8s Agent 失败：" + e.getMessage(), e);
        }
    }

    private void ensureNamespaceExists(K8sClusterConfig config) {
        log.info("检查并创建 Namespace，clusterId:{}, namespace:{}", config.getClusterId(), NAMESPACE);
        boolean created = k8sService.createIfAbsent(config, NAMESPACE);
        if (created) {
            log.info("已经在{}创建名空间{}", config.getServerHost(), NAMESPACE);
        }
        k8sClusterNamespaceService.createIfAbsent(new K8sNamespaceIdentityDTO(config.getClusterId(), NAMESPACE), 1);
        log.info("K8s namespace '{}' state 更新为 active, clusterId:{}", NAMESPACE, config.getClusterId());
    }

    /**
     * 从 nexus上下载 Helm Chart 到临时目录
     */
    private File downloadChartToTemp() throws IOException {
        Component component = NexusFacade.getHelmClient().getNewestComponent(HELM_CHART);
        if (component == null || CollUtil.isEmpty(component.getAssets())) {
            throw new IllegalStateException("未找到 Helm Chart: " + HELM_CHART);
        }

        Assert asset = component.getAssets().get(0);
        String downloadUrl = asset.getDownloadUrl();
        File tmpFile = PathUtils.createTmpFile("helm/" + RandomUtil.randomString(12), HELM_CHART + ".tgz");
        try (OutputStream out = Files.newOutputStream(tmpFile.toPath())) {
            NexusFacade.getCommonClient().download(downloadUrl, out);
        }

        log.info("Helm Chart 已下载到临时文件: {}", tmpFile.getAbsolutePath());
        return tmpFile;
    }


    private HelmReleaseVO applyHelmChart(K8sClusterConfig config, File chartFile) {
        log.info("应用 Helm Chart, Release 名称：{}, Namespace: {}", RELEASE_NAME, NAMESPACE);

        try (HelmClient client = buildHelmClient(config)) {
            deletePendingPreRelease(client, config);
            return doInstall(client, chartFile);
        } finally {
            FileUtil.del(chartFile);
        }
    }

    private void deletePendingPreRelease(HelmClient client, K8sClusterConfig config) {
        try {
            log.info("开始清理 pending 状态的 release 历史，releaseName={}, namespace={}", RELEASE_NAME, NAMESPACE);
            // 获取该 release 的历史记录（按版本号降序排列）
            List<HelmHistoryVO> history = client.history(RELEASE_NAME, NAMESPACE);
            if (history == null || history.isEmpty()) {
                log.info("release {} 没有找到历史记录", RELEASE_NAME);
                return;
            }
            history = new ArrayList<>(history);
            history.sort(Comparator.comparing(HelmHistoryVO::getRevision).reversed());

            boolean isPending = false;
            // 从高版本开始删除，直到遇到第一个非 pending 状态
            for (HelmHistoryVO entry : history) {
                String status = entry.getStatus();
                isPending = "pending-upgrade".equalsIgnoreCase(status) || "pending-install".equalsIgnoreCase(status);

                if (!isPending) {
                    // 遇到第一个非 pending 状态，停止删除
                    log.info("release: {}, 遇到第一个非 pending 状态：{}, 版本：{}", RELEASE_NAME, status, entry.getRevision());
                    break;
                }
            }
            if (isPending) {
                client.uninstall(NAMESPACE, RELEASE_NAME);
                log.warn("namespace {}, 存在pending 的agent, 直接卸载：{}", NAMESPACE, RELEASE_NAME);
            } else {
                log.info("namespace: {}, 没有pending的 {} agent, 无需清理", NAMESPACE, RELEASE_NAME);
            }
        } catch (Exception e) {
            log.warn("清理 pending release 历史失败：{}", e.getMessage(), e);
            // 不抛出异常，避免影响主流程
        }
    }


    private HelmReleaseVO doInstall(HelmClient client, File chartFile) {
        UpgradeParams params = new UpgradeParams();
        params.setReleaseName(RELEASE_NAME);
        params.setChartPath(chartFile.getAbsolutePath());
        params.setNamespace(NAMESPACE);

        prepareParameter(params);


        log.info("使用 Helm 开始安装 {}", RELEASE_NAME);
        HelmReleaseVO result = client.upgrade(params);
        log.info("Helm upgrade 完成，状态：{}", result.getInfo().getStatus());


        if (!result.getInfo().getStatus().equalsIgnoreCase("deployed")) {
            throw new IllegalStateException(String.format("安装 %s 失败，原因：%s", RELEASE_NAME, result.getInfo().getNotes()));
        }

        log.info("K8s Agent 部署成功：{}, 版本：{}", RELEASE_NAME, result.getVersion());
        return result;
    }

    /**
     * 生成helm的运行时变量
     *
     * @param params
     */
    private void prepareParameter(UpgradeParams params) {
        // 读取 master 的 common.properties，通过 --set-file 传入 Helm Chart
        String path = PropertyUtils.getFunctionalPropertyFile().getAbsolutePath();
        path = path.replaceAll("\\\\", "/");
        params.setSetFileValues(Collections.singletonList("config.commonProperties=" + path));

        Map<String, String> helmValues = HelmValueUtils.getExtraValues();
        Map<String, String> extraValues = new HashMap<>(helmValues);
        extraValues.put("image.registry", helmValues.get("nexus.imageRegistry"));
        List<String> extraValueList = new ArrayList<>();
        extraValues.forEach((k, v) -> extraValueList.add(k + "=" + v));
        params.setSetValues(extraValueList);
    }
}
