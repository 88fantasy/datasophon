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

package com.datasophon.api.service.extrepo.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.cmd.ClusterK8sServiceCommandService;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.common.enums.CommandType;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class K8SProductInstallServiceImplTest {

    @Test
    void rejectsServiceInstanceFromAnotherClusterBeforeCreatingCommands() {
        K8sServiceInstanceService instanceService = mock(K8sServiceInstanceService.class);
        FrameK8sServiceService frameService = mock(FrameK8sServiceService.class);
        ClusterK8sServiceCommandService commandService = mock(ClusterK8sServiceCommandService.class);
        K8sServiceInstanceVO foreign = new K8sServiceInstanceVO();
        foreign.setId(11);
        foreign.setClusterId(8);
        when(instanceService.listByIds(List.of(11))).thenReturn(List.of(foreign));

        K8SProductInstallServiceImpl service = new K8SProductInstallServiceImpl();
        ReflectionTestUtils.setField(service, "k8sServiceInstanceService", instanceService);
        ReflectionTestUtils.setField(service, "frameK8sServiceService", frameService);
        ReflectionTestUtils.setField(service, "k8sServiceCommandService", commandService);

        assertThatThrownBy(() -> service.generateAndExecSrvInstCmd(
                7, CommandType.STOP_SERVICE, List.of(11)))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("不属于当前集群");

        verifyNoInteractions(frameService, commandService);
    }
}
