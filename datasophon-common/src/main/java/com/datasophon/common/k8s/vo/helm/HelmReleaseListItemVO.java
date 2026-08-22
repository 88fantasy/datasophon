package com.datasophon.common.k8s.vo.helm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * {@code helm list -o json} 的单条输出。
 *
 * <p>注意与 {@link HelmReleaseVO} 区分：后者是 {@code helm upgrade -o json} 返回的完整
 * release 对象（带 {@code info} 嵌套），而 {@code helm list} 返回的是扁平结构，
 * 两者不可互换。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HelmReleaseListItemVO {

    private String name;

    private String namespace;

    private String revision;

    private String updated;

    /** deployed / failed / pending-install / superseded 等 */
    private String status;

    /** chart 名与版本的组合，如 {@code apisix-2.12.5} */
    private String chart;

    @JsonProperty("app_version")
    private String appVersion;

    /**
     * 从 {@link #chart} 中截出 chart 名，如 {@code apisix-2.12.5} → {@code apisix}。
     *
     * <p>按最后一个 {@code -} 切分，可正确处理 {@code dolphinscheduler-helm-3.2.2}
     * 与 {@code cert-manager-v1.20.3} 这类名字本身含连字符的情况。
     */
    public String chartName() {
        int idx = lastSeparator();
        return idx < 0 ? chart : chart.substring(0, idx);
    }

    /**
     * 从 {@link #chart} 中截出 chart 版本，如 {@code apisix-2.12.5} → {@code 2.12.5}。
     */
    public String chartVersion() {
        int idx = lastSeparator();
        return idx < 0 ? null : chart.substring(idx + 1);
    }

    private int lastSeparator() {
        return chart == null ? -1 : chart.lastIndexOf('-');
    }
}
