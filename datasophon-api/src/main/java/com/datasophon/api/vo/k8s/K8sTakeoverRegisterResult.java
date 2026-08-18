package com.datasophon.api.vo.k8s;

/**
 * 单个服务的接管登记结果。
 *
 * @param instanceId  登记后的服务实例 ID
 * @param releaseName Helm release 名
 * @param namespace   所在命名空间
 * @param metricsJob  探测到的 OTel job，逗号分隔；未接入采集时为 null
 * @param scraped     是否已接入采集。false 时前端应提示该服务的看板无数据，
 *                    并引导补 ServiceMonitor
 */
public record K8sTakeoverRegisterResult(Integer instanceId,
                                        String releaseName,
                                        String namespace,
                                        String metricsJob,
                                        boolean scraped) {
}
