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

package com.datasophon.api.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.datasophon.common.Constants;
import com.datasophon.common.model.ArchInfo;
import com.datasophon.common.model.ServiceRoleInfo;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ServiceCommandUtilsTest {

    static {
        System.setProperty("commonPropertiesLocation", Path.of("..", "conf", "api.properties").toAbsolutePath().toString());
    }

    @Test
    void installHomeUsesHostArchitectureWithoutReadingUnsupportedRoleGetter() {
        ArchInfo archInfo = new ArchInfo();
        archInfo.setDecompressPackageName("otelcol-contrib_0.156.0");
        ServiceRoleInfo role = new ServiceRoleInfo();
        role.setHostname("ddh-01");
        role.setArchInfoMap(Map.of("x86_64", archInfo));

        assertThat(ServiceCommandUtils.resolveInstallHome(role, "x86_64"))
                .isEqualTo(Constants.INSTALL_PATH + "/otelcol-contrib_0.156.0");
    }
}
