package com.datasophon.api.service.k8s;

import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.common.function.ThrowableMapper;
import com.datasophon.common.k8s.client.HelmClient;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.common.k8s.vo.helm.HelmReleaseListItemVO;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import java.util.List;

import org.springframework.stereotype.Service;

import cn.hutool.core.bean.BeanUtil;

/**
 * 只读读取目标集群的 Helm release 信息。
 *
 * <p>服务于两个场景：接管扫描（按 chart 名匹配框架服务定义）与接管实例的只读配置展示。
 * 本类**不做任何写操作**。
 */
@Service
public class HelmReleaseReader {

    /** helm list 的状态过滤值，接管只关心已成功部署的 release。 */
    private static final String STATUS_DEPLOYED = "deployed";

    /**
     * 列出集群内全部 namespace 下处于 deployed 状态的 release。
     *
     * @param config 目标集群连接配置
     * @return release 列表，按 helm 返回顺序
     */
    public List<HelmReleaseListItemVO> listDeployed(K8sClusterConfig config) {
        return exec(config, client -> client.list(null, STATUS_DEPLOYED), "获取 Helm release 列表");
    }

    /**
     * 读取指定 release 的 user-supplied values。
     *
     * @param config      目标集群连接配置
     * @param releaseName release 名称
     * @param namespace   release 所在命名空间
     * @return values 的 JSON 文本
     */
    public String getValues(K8sClusterConfig config, String releaseName, String namespace) {
        return exec(config, client -> client.getValues(releaseName, namespace),
                String.format("获取 Helm release %s 的配置", releaseName));
    }

    private <T> T exec(K8sClusterConfig config, ThrowableMapper<HelmClient, T> action, String actionHint) {
        try (HelmClient client = new HelmClient(newOptions(config))) {
            return action.accept(client);
        } catch (Exception e) {
            throw new BusinessException(String.format("%s 失败，%s", actionHint, e.getMessage()), e);
        }
    }

    private ClientOptions newOptions(K8sClusterConfig config) {
        ClientOptions options = BeanUtil.toBean(config, ClientOptions.class);
        options.setServerName(config.getServerHost());
        return options;
    }
}
