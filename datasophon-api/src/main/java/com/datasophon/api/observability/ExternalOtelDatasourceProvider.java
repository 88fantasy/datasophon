/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.observability;

import java.util.Optional;

/**
 * OTel 查询侧使用的外部 Doris 数据源来源。
 *
 * <p>接管集群的 Doris 不由本平台安装，角色实例表里查不到，只能用接管时登记的外部地址。
 * 抽出本接口是为了让 {@link OtelDorisReaderFactory} 不必知道「接管登记表」这类概念——
 * 查询侧只关心「有没有一个可连的外部 Doris」，「哪种集群才有」是 k8s 接管侧的策略。
 *
 * <p>返回值里没有库名：OTel 数据由离线安装包内的 collector 写入，库名恒为 {@code otel}，
 * 查询侧同样按全限定表名硬编码。此前 {@code doris_database} 列一路铺到前端却无人消费，已删除，
 * 新增实现时不要再把库名放回来。
 */
public interface ExternalOtelDatasourceProvider {

    /** 无登记（或该集群不适用外部数据源）时返回 {@link Optional#empty()}，由调用方回落到角色实例查询。 */
    Optional<ExternalDatasource> find(Integer clusterId);

    /** 外部 Doris FE 的连接地址。账号固定为 otel_reader，密码走 {@link OtelCredentialService}。 */
    record ExternalDatasource(String host, String port) {
    }
}
