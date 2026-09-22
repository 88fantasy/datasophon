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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mockStatic;

import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.utils.ServiceConfigUtils;
import com.datasophon.dao.entity.ClusterInfoEntity;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SeaTunnelMasterHandlerStrategyTest {

    private static final Integer CLUSTER_ID = 74101;
    private static final Integer CLUSTER_ID_WITHOUT_PORT = 74103;
    private static final String SERVICE_NAME = "SEATUNNEL";

    @Test
    void buildsMasterMembersFromCustomPortAndSetsClusterName() {
        GlobalVariables.putValue(CLUSTER_ID, SERVICE_NAME, "masterHazelcastPort", "5811");
        ClusterInfoEntity clusterInfo = new ClusterInfoEntity();
        clusterInfo.setClusterCode("prod01");

        try (MockedStatic<ServiceConfigUtils> configUtils = mockStatic(ServiceConfigUtils.class)) {
            configUtils.when(() -> ServiceConfigUtils.getClusterInfo(CLUSTER_ID)).thenReturn(clusterInfo);

            new SeaTunnelMasterHandlerStrategy().handler(
                    CLUSTER_ID, List.of("master-a", "master-b"), SERVICE_NAME);

            configUtils.verify(() -> ServiceConfigUtils.generateClusterVariable(
                    CLUSTER_ID, SERVICE_NAME, "masterMembers", "master-a:5811,master-b:5811"));
            configUtils.verify(() -> ServiceConfigUtils.generateClusterVariable(
                    CLUSTER_ID, SERVICE_NAME, "clusterName", "seatunnel-prod01"));
        }
    }

    @Test
    void fallsBackToDefaultPortWhenMissingAndRegistersRoleStrategy() {
        ClusterInfoEntity clusterInfo = new ClusterInfoEntity();
        clusterInfo.setClusterCode("test01");

        try (MockedStatic<ServiceConfigUtils> configUtils = mockStatic(ServiceConfigUtils.class)) {
            configUtils.when(() -> ServiceConfigUtils.getClusterInfo(CLUSTER_ID_WITHOUT_PORT)).thenReturn(clusterInfo);

            new SeaTunnelMasterHandlerStrategy().handler(
                    CLUSTER_ID_WITHOUT_PORT, List.of("master-c"), SERVICE_NAME);

            configUtils.verify(() -> ServiceConfigUtils.generateClusterVariable(
                    CLUSTER_ID_WITHOUT_PORT, SERVICE_NAME, "masterMembers", "master-c:5801"));
            configUtils.verify(() -> ServiceConfigUtils.generateClusterVariable(
                    CLUSTER_ID_WITHOUT_PORT, SERVICE_NAME, "clusterName", "seatunnel-test01"));
        }

        assertInstanceOf(SeaTunnelMasterHandlerStrategy.class,
                ServiceRoleStrategyContext.getServiceRoleHandler("SeaTunnelMaster"));
    }
}
