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

package com.datasophon.api.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Prometheus 查询代理配置。
 *
 * <p>对应 {@code application.yml} 中的 {@code datasophon.prometheus} 节点：
 * <pre>
 * datasophon:
 *   prometheus:
 *     url: http://192.168.2.122:9090
 *     timeout-ms: 10000
 * </pre>
 *
 * <p>可通过环境变量 {@code DDH_PROMETHEUS_URL} 覆盖地址，适合 Docker/K8s 部署场景。
 */
@Configuration
@ConfigurationProperties(prefix = "datasophon.prometheus")
public class PrometheusProxyProperties {
    
    /** Prometheus HTTP 地址（无尾部斜杠），可通过 DDH_PROMETHEUS_URL 覆盖。 */
    private String url = "http://192.168.2.122:9090";
    
    /** 请求超时（毫秒）。 */
    private int timeoutMs = 10000;
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public int getTimeoutMs() {
        return timeoutMs;
    }
    
    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
