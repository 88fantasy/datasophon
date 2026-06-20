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

package com.datasophon.api.observability;

import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.dao.entity.ClusterVariable;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Service;

@Service
public class OtelCredentialService {

    static final String SERVICE_NAME = "OTELCOLLECTOR";
    static final String COLLECTOR_PASSWORD = "dorisCollectorPassword";
    static final String READER_PASSWORD = "dorisReaderPassword";

    private final ClusterVariableService variableService;
    private final SecureRandom random = new SecureRandom();

    public OtelCredentialService(ClusterVariableService variableService) {
        this.variableService = variableService;
    }

    public synchronized OtelCredentials getOrCreate(Integer clusterId) {
        String collector = getOrCreate(clusterId, COLLECTOR_PASSWORD);
        String reader = getOrCreate(clusterId, READER_PASSWORD);
        return new OtelCredentials(collector, reader);
    }

    private String getOrCreate(Integer clusterId, String name) {
        ClusterVariable existing = variableService.getVariableByVariableName(clusterId, SERVICE_NAME, name);
        if (existing != null) {
            GlobalVariables.putValue(clusterId, SERVICE_NAME, name, existing.getVariableValue());
            return existing.getVariableValue();
        }
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ClusterVariable variable = new ClusterVariable();
        variable.setClusterId(clusterId);
        variable.setServiceName(SERVICE_NAME);
        variable.setVariableName(name);
        variable.setVariableValue(password);
        variableService.save(variable);
        GlobalVariables.putValue(clusterId, SERVICE_NAME, name, password);
        return password;
    }
}
