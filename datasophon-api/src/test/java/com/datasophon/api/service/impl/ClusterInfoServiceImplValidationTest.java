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

package com.datasophon.api.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ManageMode;

import org.junit.jupiter.api.Test;

class ClusterInfoServiceImplValidationTest {

    @Test
    void serviceRejectsImportedPhysicalCluster() {
        ClusterInfoEntity cluster = new ClusterInfoEntity();
        cluster.setArchType(ClusterArchType.physical);
        cluster.setManageMode(ManageMode.IMPORTED);

        assertThatThrownBy(() -> new ClusterInfoServiceImpl().saveCluster(cluster))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("仅支持 K8s");
    }
}
