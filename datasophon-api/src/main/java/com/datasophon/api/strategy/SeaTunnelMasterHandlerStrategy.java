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

import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.utils.ServiceConfigUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SeaTunnelMasterHandlerStrategy implements ServiceRoleStrategy {

    private static final Logger logger = LoggerFactory.getLogger(SeaTunnelMasterHandlerStrategy.class);
    private static final String DEFAULT_HAZELCAST_PORT = "5801";

    @Override
    public void handler(Integer clusterId, List<String> hosts, String serviceName) {
        if (!hosts.isEmpty()) {
            String configuredPort = GlobalVariables.getValueByService(clusterId, serviceName, "masterHazelcastPort");
            if (StringUtils.isBlank(configuredPort)) {
                logger.warn("Missing SeaTunnel master Hazelcast port for cluster {}; using default {}",
                        clusterId, DEFAULT_HAZELCAST_PORT);
            }
            String port = StringUtils.defaultIfBlank(configuredPort, DEFAULT_HAZELCAST_PORT);

            String masterMembers = hosts.stream()
                    .map(host -> host + ":" + port)
                    .collect(Collectors.joining(","));
            ServiceConfigUtils.generateClusterVariable(clusterId, serviceName, "masterMembers", masterMembers);
            String clusterCode = ServiceConfigUtils.getClusterInfo(clusterId).getClusterCode();
            ServiceConfigUtils.generateClusterVariable(clusterId, serviceName, "clusterName",
                    "seatunnel-" + clusterCode);
        }
    }
}
