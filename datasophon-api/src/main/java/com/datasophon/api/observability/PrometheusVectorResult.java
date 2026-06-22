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

import java.util.List;
import java.util.Map;

/**
 * Prometheus instant query 响应体，与前端 {@code promql.ts} 的 {@code PrometheusVector} 对齐。
 *
 * <p>序列化形状：
 * <pre>{"resultType":"vector","result":[{"metric":{...},"value":[ts,"val"]}]}</pre>
 *
 * <p>{@code value[0]} 是 Unix 秒（long），{@code value[1]} 是数值字符串。
 */
public record PrometheusVectorResult(
        String resultType,
        List<VectorSample> result) {

    public static PrometheusVectorResult of(List<VectorSample> result) {
        return new PrometheusVectorResult("vector", result);
    }

    /**
     * 单个样本。{@code value} 是 {@code [long, String]} 的混型数组，Jackson 序列化为
     * {@code [1234567890, "42.5"]}，符合 Prometheus wire format。
     */
    public record VectorSample(Map<String, String> metric, Object[] value) {}
}
