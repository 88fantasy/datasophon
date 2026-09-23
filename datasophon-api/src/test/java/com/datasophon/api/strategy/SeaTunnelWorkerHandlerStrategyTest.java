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

package com.datasophon.api.strategy;

import static org.mockito.Mockito.mockStatic;

import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.utils.ServiceConfigUtils;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SeaTunnelWorkerHandlerStrategyTest {

    private static final Integer CLUSTER_ID = 74102;
    private static final Integer CLUSTER_ID_WITHOUT_PORT = 74104;
    private static final String SERVICE_NAME = "SEATUNNEL";

    @Test
    void buildsWorkerMembersFromCustomPort() {
        GlobalVariables.putValue(CLUSTER_ID, SERVICE_NAME, "workerHazelcastPort", "5822");

        try (MockedStatic<ServiceConfigUtils> configUtils = mockStatic(ServiceConfigUtils.class)) {
            new SeaTunnelWorkerHandlerStrategy().handler(
                    CLUSTER_ID, List.of("worker-a", "worker-b"), SERVICE_NAME);

            configUtils.verify(() -> ServiceConfigUtils.generateClusterVariable(
                    CLUSTER_ID, SERVICE_NAME, "workerMembers", "worker-a:5822,worker-b:5822"));
        }
    }

    @Test
    void fallsBackToDefaultPortWhenMissingAndRegistersRoleStrategy() {
        try (MockedStatic<ServiceConfigUtils> configUtils = mockStatic(ServiceConfigUtils.class)) {
            new SeaTunnelWorkerHandlerStrategy().handler(
                    CLUSTER_ID_WITHOUT_PORT, List.of("worker-c"), SERVICE_NAME);

            configUtils.verify(() -> ServiceConfigUtils.generateClusterVariable(
                    CLUSTER_ID_WITHOUT_PORT, SERVICE_NAME, "workerMembers", "worker-c:5802"));
        }

        org.junit.jupiter.api.Assertions.assertInstanceOf(SeaTunnelWorkerHandlerStrategy.class,
                ServiceRoleStrategyContext.getServiceRoleHandler("SeaTunnelWorker"));
    }
}
