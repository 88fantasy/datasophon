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


package com.datasophon.api.service;

import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleHostMapping;
import com.datasophon.common.utils.Result;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public interface ServiceInstallService {

    List<ServiceConfig> getServiceConfigOption(Integer clusterId, String serviceName);


    List<ServiceConfig> getServiceConfigFromDdl(Integer clusterId, String serviceName);

    void saveServiceConfig(Integer clusterId, String serviceName, List<ServiceConfig> configJson,
                             Integer roleGroupId);

    void saveServiceRoleHostMapping(Integer clusterId, List<ServiceRoleHostMapping> list);


    Result getServiceRoleDeployOverview(Integer clusterId);

    

    void downloadResource(String frameCode, String serviceRoleName,
                          String resource, HttpServletResponse response) throws Exception;


    void downloadTemplate(String templateName, HttpServletResponse response) throws IOException;

    Result getServiceRoleHostMapping(Integer clusterId);
    
    Result checkServiceDependency(Integer clusterId, List<Integer> serviceIds);
}
