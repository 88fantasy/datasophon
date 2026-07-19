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

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.ClusterVariable;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

@Component
public class DefaultDorisDataSourceFactory implements DorisDataSourceFactory {

    private final ClusterServiceRoleInstanceService roleService;
    private final ClusterVariableService variableService;

    public DefaultDorisDataSourceFactory(ClusterServiceRoleInstanceService roleService,
                                         ClusterVariableService variableService) {
        this.roleService = roleService;
        this.variableService = variableService;
    }

    @Override
    public DataSource create(Integer clusterId) {
        List<ClusterServiceRoleInstanceEntity> frontends = roleService
                .getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, "DorisFE")
                .stream()
                .filter(role -> ServiceRoleState.RUNNING.equals(role.getServiceRoleState()))
                .toList();
        if (frontends.isEmpty()) {
            throw new IllegalStateException("No running DorisFE for cluster " + clusterId);
        }
        String port = variable(clusterId, "query_port", "9030");
        String password = requireValue(variable(clusterId, "root_password", null),
                "Doris root_password is not configured for cluster " + clusterId);
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://" + frontends.get(0).getHostname() + ":" + port
                + "/?useUnicode=true&characterEncoding=utf8&useSSL=false");
        dataSource.setUsername("root");
        dataSource.setPassword(password);
        return dataSource;
    }

    private String variable(Integer clusterId, String name, String defaultValue) {
        ClusterVariable value = variableService.getVariableByVariableName(clusterId, "DORIS", name);
        return value == null ? defaultValue : value.getVariableValue();
    }

    static String requireValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value;
    }
}
