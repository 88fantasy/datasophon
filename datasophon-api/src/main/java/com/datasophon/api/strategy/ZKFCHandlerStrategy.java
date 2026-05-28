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
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.common.model.ServiceRoleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class ZKFCHandlerStrategy implements ServiceRoleStrategy {

  private static final Logger logger = LoggerFactory.getLogger(ZKFCHandlerStrategy.class);

  @Override
  public void handler(Integer clusterId, List<String> hosts, String serviceName) {
    if (hosts.size() == 2) {
      ProcessUtils.generateClusterVariable(clusterId, serviceName, "ZKFC1", hosts.get(0));
      ProcessUtils.generateClusterVariable(clusterId, serviceName, "ZKFC2", hosts.get(1));
    }
  }


  @Override
  public void handlerServiceRoleInfo(ServiceRoleInfo serviceRoleInfo, String hostname) {
    String zkfc2 = GlobalVariables.getValueByService(serviceRoleInfo.getClusterId(), serviceRoleInfo.getServiceName(),"ZKFC2");
    if (hostname.equals(zkfc2)) {
      logger.info("set to slave zkfc");
      serviceRoleInfo.setSlave(true);
      serviceRoleInfo.setSortNum(6);
    }
  }

}
