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

import com.datasophon.api.utils.ServiceConfigUtils;

import java.util.List;

public class ElasticSearchHandlerStrategy implements ServiceRoleStrategy {
    
    @Override
    public void handler(Integer clusterId, List<String> hosts, String serviceName) {
        
        if (!hosts.isEmpty()) {
            ServiceConfigUtils.generateClusterVariable(clusterId, serviceName, "initMasterNodes",
                    String.join(",", hosts));
            String join = String.join(":9300,", hosts);
            String seedHosts = join + ":9300";
            ServiceConfigUtils.generateClusterVariable(clusterId, serviceName, "seedHosts", seedHosts);
            
            String elasticSearchHostPorts = String.join(":9200,", hosts) + ":9200";
            ServiceConfigUtils.generateClusterVariable(clusterId, serviceName, "elasticSearchHostPorts", elasticSearchHostPorts);
            
            ServiceConfigUtils.generateClusterVariable(clusterId, serviceName, "elasticSearchHost",
                    hosts.get(0));
        }
    }
    
}
