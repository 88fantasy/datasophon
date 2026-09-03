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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.service.doris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.datasophon.api.enums.Status;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DorisServiceInstanceValidatorTest {

    private ClusterServiceInstanceService service;
    private DorisServiceInstanceValidator validator;

    @BeforeEach
    void setUp() {
        service = mock(ClusterServiceInstanceService.class);
        validator = new DorisServiceInstanceValidator(service);
    }

    @Test
    void returnsInstanceWhenClusterAndDorisTypeMatch() {
        ClusterServiceInstanceEntity instance = instance(7, "doris");
        when(service.getById(44)).thenReturn(instance);

        assertThat(validator.requireDorisInstance(7, 44)).isSameAs(instance);
    }

    @Test
    void rejectsMissingInstance() {
        assertBadRequest(validator, 7, 44);
    }

    @Test
    void rejectsInstanceFromAnotherCluster() {
        when(service.getById(44)).thenReturn(instance(8, "DORIS"));

        assertBadRequest(validator, 7, 44);
    }

    @Test
    void rejectsNonDorisService() {
        when(service.getById(44)).thenReturn(instance(7, "HDFS"));

        assertBadRequest(validator, 7, 44);
    }

    private static void assertBadRequest(
                                         DorisServiceInstanceValidator validator, Integer clusterId, Integer instanceId) {
        assertThatThrownBy(() -> validator.requireDorisInstance(clusterId, instanceId))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo(Status.INSTANCE_MISMATCH.getMsg());
                });
    }

    private static ClusterServiceInstanceEntity instance(Integer clusterId, String serviceName) {
        ClusterServiceInstanceEntity instance = new ClusterServiceInstanceEntity();
        instance.setClusterId(clusterId);
        instance.setServiceName(serviceName);
        return instance;
    }
}
