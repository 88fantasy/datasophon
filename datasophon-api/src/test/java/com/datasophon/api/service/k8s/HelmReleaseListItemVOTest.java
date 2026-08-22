package com.datasophon.api.service.k8s;

import com.datasophon.common.k8s.vo.helm.HelmReleaseListItemVO;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link HelmReleaseListItemVO} 反序列化与 chart 名解析测试。
 *
 * <p>fixture 取自真实集群 {@code helm list -A -o json} 的输出，覆盖了
 * chart 名本身含连字符、版本带 {@code v} 前缀等实际会遇到的形态。
 *
 * <p><b>为什么被测类在 common 而测试在 api</b>：父 pom 的 surefire 硬编码
 * {@code skipTests=true}，只有 datasophon-api / datasophon-worker 覆盖成
 * {@code ${skipTests}}，因此 datasophon-common 下的测试从不执行。放在这里
 * 才有真实的回归保护。
 */
class HelmReleaseListItemVOTest {

    /** 真实输出片段（helm v4.1.3，2026-08-17 采集）。 */
    private static final String REAL_OUTPUT = "["
            + "{\"name\":\"apisix\",\"namespace\":\"apisix\",\"revision\":\"1\","
            + "\"updated\":\"2026-04-23 13:34:45.082864 +0800 CST\",\"status\":\"deployed\","
            + "\"chart\":\"apisix-2.12.5\",\"app_version\":\"3.14.1\"},"
            + "{\"name\":\"cert-manager\",\"namespace\":\"cert-manager\",\"revision\":\"2\","
            + "\"updated\":\"2026-07-08 14:52:21.763869 +0800 CST\",\"status\":\"deployed\","
            + "\"chart\":\"cert-manager-v1.20.3\",\"app_version\":\"v1.20.3\"},"
            + "{\"name\":\"dolphinscheduler\",\"namespace\":\"prod\",\"revision\":\"4\","
            + "\"updated\":\"2026-07-08 15:31:21.481045 +0800 CST\",\"status\":\"deployed\","
            + "\"chart\":\"dolphinscheduler-helm-3.2.2\",\"app_version\":\"3.2.2\"}"
            + "]";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("能反序列化 helm list 的扁平结构，含 app_version 下划线字段")
    void shouldDeserializeFlatListOutput() throws Exception {
        List<HelmReleaseListItemVO> releases = mapper.readValue(REAL_OUTPUT,
                mapper.getTypeFactory().constructCollectionType(List.class, HelmReleaseListItemVO.class));

        Assertions.assertEquals(3, releases.size());

        HelmReleaseListItemVO apisix = releases.get(0);
        Assertions.assertEquals("apisix", apisix.getName());
        Assertions.assertEquals("apisix", apisix.getNamespace());
        Assertions.assertEquals("1", apisix.getRevision());
        Assertions.assertEquals("deployed", apisix.getStatus());
        Assertions.assertEquals("apisix-2.12.5", apisix.getChart());
        Assertions.assertEquals("3.14.1", apisix.getAppVersion());
    }

    @Test
    @DisplayName("chart 名解析：按最后一个连字符切分，兼容名字含连字符与 v 前缀版本")
    void shouldSplitChartNameAndVersion() throws Exception {
        List<HelmReleaseListItemVO> releases = mapper.readValue(REAL_OUTPUT,
                mapper.getTypeFactory().constructCollectionType(List.class, HelmReleaseListItemVO.class));

        Assertions.assertEquals("apisix", releases.get(0).chartName());
        Assertions.assertEquals("2.12.5", releases.get(0).chartVersion());

        // 名字自带连字符 + 版本带 v 前缀
        Assertions.assertEquals("cert-manager", releases.get(1).chartName());
        Assertions.assertEquals("v1.20.3", releases.get(1).chartVersion());

        // 名字含两段连字符
        Assertions.assertEquals("dolphinscheduler-helm", releases.get(2).chartName());
        Assertions.assertEquals("3.2.2", releases.get(2).chartVersion());
    }

    @Test
    @DisplayName("chart 字段缺失或无连字符时不抛异常")
    void shouldTolerateMalformedChart() {
        HelmReleaseListItemVO nullChart = new HelmReleaseListItemVO();
        Assertions.assertNull(nullChart.chartName());
        Assertions.assertNull(nullChart.chartVersion());

        HelmReleaseListItemVO noSeparator = new HelmReleaseListItemVO();
        noSeparator.setChart("mychart");
        Assertions.assertEquals("mychart", noSeparator.chartName());
        Assertions.assertNull(noSeparator.chartVersion());
    }
}
