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

package com.datasophon.api.service.k8s;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.k8s.K8sAuthType;

import org.junit.jupiter.api.Test;

import cn.hutool.core.bean.BeanUtil;

/**
 * 锁定 {@link K8sClusterConfig} → {@link ClientOptions} 的凭据类型传递契约。
 *
 * <p>{@code K8sServiceImpl.newOptions} 与 {@code HelmReleaseReader} 都靠
 * {@code BeanUtil.toBean} 做转换，依赖 Hutool 把 {@link K8sAuthType} 枚举隐式转成
 * {@code ClientOptions.type}（String）。{@code SecureKubeConfigWriter.resolveContent}
 * 再按这个字符串决定用哪个凭据字段——切换认证方式后不至于误用库中残留的旧凭据。
 *
 * <p>这条链路全程没有编译期约束：枚举改名、Hutool 换转换策略、或有人给
 * {@code K8sAuthType} 加上 {@code toString()} 覆写，都会让 type 静默变成 null 或
 * 对不上的字面量，从而无声退回"按非空猜测"的旧行为。所以在此固化。
 */
class ClientOptionsTypeMappingTest {

    @Test
    void beanUtilCarriesAuthTypeIntoClientOptionsAsEnumName() {
        for (K8sAuthType type : K8sAuthType.values()) {
            K8sClusterConfig config = new K8sClusterConfig();
            config.setType(type);

            ClientOptions options = BeanUtil.toBean(config, ClientOptions.class);

            assertEquals(type.name(), options.getType(),
                    String.format("K8sAuthType.%s 未能通过 BeanUtil.toBean 传递到 ClientOptions.type，"
                            + "SecureKubeConfigWriter 会退回按非空猜测凭据", type));
        }
    }

    /**
     * {@code SecureKubeConfigWriter} 里的 TYPE_* 常量是私有的字面量副本，
     * 与本枚举没有编译期绑定，这里正面锁住取值。
     */
    @Test
    void authTypeNamesMatchSecureKubeConfigWriterConstants() {
        assertEquals("config_file", K8sAuthType.config_file.name());
        assertEquals("token", K8sAuthType.token.name());
        assertEquals("password", K8sAuthType.password.name());
    }
}
