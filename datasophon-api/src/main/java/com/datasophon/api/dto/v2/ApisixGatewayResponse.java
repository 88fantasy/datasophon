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

package com.datasophon.api.dto.v2;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/** GET /v2/.../apisix/gateway 响应体：网关配置 YAML + 托管段预览 + 节点列表。 */
@Data
@AllArgsConstructor
public class ApisixGatewayResponse {

    /** 用户可编辑段（upstreams/routes/global_rules），非空时为持久化值，空时为向导参数拼出的初始值。 */
    private String gatewayYaml;

    /** 模板固定输出的托管段（plugin_metadata + #END），供前端拼出「最终 apisix.yaml」只读预览。 */
    private String managedSuffix;

    private List<ApisixGatewayRole> roles;
}
