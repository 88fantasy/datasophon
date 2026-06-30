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

import static org.assertj.core.api.Assertions.assertThat;

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterVariableService;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OtelDorisReaderFactoryTest {
    
    @Test
    void reusesPoolForSameConnectionSettings() {
        OtelCredentialService credentialService = new OtelCredentialService(null);
        OtelDorisReaderFactory factory = new OtelDorisReaderFactory(
                proxy(ClusterServiceRoleInstanceService.class),
                proxy(ClusterVariableService.class),
                credentialService);
        ReflectionTestUtils.setField(factory, "fallbackHost", "127.0.0.1");
        ReflectionTestUtils.setField(factory, "fallbackPort", "9030");
        ReflectionTestUtils.setField(factory, "fallbackPassword", "secret");
        
        factory.create(7);
        factory.create(7);
        
        assertThat(factory.poolSizeForTest()).isEqualTo(1);
    }
    
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> null);
    }
}
