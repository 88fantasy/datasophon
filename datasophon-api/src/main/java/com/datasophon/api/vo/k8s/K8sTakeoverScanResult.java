package com.datasophon.api.vo.k8s;

import com.datasophon.dao.enums.k8s.InstanceSourceKind;

import java.util.List;

/**
 * 接管扫描结果。
 *
 * @param matched    已自动匹配到框架服务定义的 release，可直接登记
 * @param pending    未匹配上的 release，需人工从框架服务目录中指定绑定关系
 * @param missing    已登记但对应 release 已不在集群中的接管实例（重扫时的对账结果）
 * @param failedCrds 本次 CR 扫描失败的 CRD 标识（{@code plural.group}）；非空代表 CR 扫描不完整——
 *                   {@code missing} 里不会包含任何本应由这些失败 CRD 覆盖的 CR 实例（避免误报失联），
 *                   前端应据此展示「部分 CR 未扫描完整」的降级提示，而不是静默当成扫描正常完成
 */
public record K8sTakeoverScanResult(List<ScannedRelease> matched,
                                    List<ScannedRelease> pending,
                                    List<MissingInstance> missing,
                                    List<String> failedCrds) {

    /**
     * 扫描到的单个 Helm release。
     *
     * @param releaseName      Helm release 名（注意与 chartName 不一定相同）
     * @param namespace        所在命名空间
     * @param chart            chart 原始字段，如 {@code apisix-2.12.5}
     * @param chartName        从 chart 切出的名称
     * @param chartVersion     从 chart 切出的版本
     * @param frameServiceId   匹配到的框架服务定义 ID；pending 中为 null
     * @param frameServiceName 匹配到的框架服务名；pending 中为 null
     * @param catalog          服务分类 ENVIRONMENT / MIDDLEWARE / APPLICATION；pending 中为 null
     * @param registered       该 release 是否已经登记过，供前端默认不重复勾选
     * @param sourceKind       来源类型 HELM=Helm release CR=operator 自定义资源
     */
    public record ScannedRelease(String releaseName,
                                 String namespace,
                                 String chart,
                                 String chartName,
                                 String chartVersion,
                                 Integer frameServiceId,
                                 String frameServiceName,
                                 String catalog,
                                 boolean registered,
                                 InstanceSourceKind sourceKind) {
    }

    /**
     * 已登记但在集群中已找不到对应 release 的接管实例。
     *
     * <p>只报告不自动删除——release 可能只是被临时卸载待重装，替用户决定删登记太武断。
     *
     * @param instanceId  平台侧实例 ID，供前端跳到「取消接管」
     * @param releaseName 登记时记录的 release 名
     * @param namespace   登记时记录的命名空间
     * @param serviceName 绑定的框架服务名
     */
    public record MissingInstance(Integer instanceId,
                                  String releaseName,
                                  String namespace,
                                  String serviceName) {
    }
}
