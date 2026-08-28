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

package com.datasophon.api.ds;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Optional;

/**
 * SSOT：哪些 DS 配置项由平台托管（不接受用户输入，值来自其他服务的凭据/变量），
 * 供配置合并（{@code ServiceConfigUtils}）与 DDL 刷新（{@code DdlMetaServiceImpl}）两条路径共用，
 * 避免各自维护一份清单导致悄悄失步。
 *
 * <p>清单与取值语义必须一起共用：只共用清单、各自实现取值，两条路径在「全局变量缺失」时的行为
 * 会分叉（写 null / 写空串 / 保留占位符字面量），所以解析统一走 {@link #resolve}。
 */
public final class DsManagedConfig {

    public static final String SERVICE_NAME = "DS";

    public static final Map<String, String> OBJECT_STORAGE_CREDENTIALS = Map.of(
            "aws.s3.access.key.id", "${ROOT.Rustfs.access_key}",
            "aws.s3.access.key.secret", "${ROOT.Rustfs.secret_key}");

    /**
     * Resolves one platform-managed config value from the cluster's global variables.
     *
     * @return 解析出的非空值；全局变量缺失或为空时返回 {@link Optional#empty()}，
     *         调用方必须保留配置原值——{@code conf/api.properties} 里 {@code rustfs.secret_key}
     *         默认就是空值，无条件覆盖会把运维填好的凭据静默刷成空串。
     */
    public static Optional<String> resolve(String configName, Map<String, String> globalVariables) {
        String placeholder = OBJECT_STORAGE_CREDENTIALS.get(configName);
        if (placeholder == null || globalVariables == null) {
            return Optional.empty();
        }
        String resolved = globalVariables.get(placeholder);
        return StringUtils.isBlank(resolved) ? Optional.empty() : Optional.of(resolved);
    }

    private DsManagedConfig() {
    }
}
