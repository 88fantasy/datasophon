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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datasophon.api.load.ServiceRoleJmxMap;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.List;

import org.junit.jupiter.api.Test;

class OtelScrapeConfigBuilderTest {
    
    private final ClusterServiceRoleInstanceService roleService = mock(ClusterServiceRoleInstanceService.class);
    private final ClusterInfoService clusterInfoService = mock(ClusterInfoService.class);
    private final OtelScrapeConfigBuilder builder =
            new OtelScrapeConfigBuilder(roleService, clusterInfoService);
    
    @Test
    void buildsLocalScrapeJobsForRunningRolesAndNodeExporter() {
        givenClusterFrame("FRAME_A");
        ServiceRoleJmxMap.put("FRAME_A_HDFS_NameNode", "9101");
        ServiceRoleJmxMap.put("FRAME_A_HDFS_DataNode", "9102");
        when(roleService.getServiceRoleListByHostnameAndClusterId("worker-1", 7))
                .thenReturn(List.of(
                        role("HDFS", "NameNode", ServiceRoleState.RUNNING),
                        role("HDFS", "DataNode", ServiceRoleState.RUNNING)));
        
        String yaml = builder.build(7, "worker-1");
        
        assertThat(yaml).contains("    - job_name: 'NameNode'");
        assertThat(yaml).contains("      metrics_path: '/metrics'");
        assertThat(yaml).contains("        - targets: ['127.0.0.1:9101']");
        assertThat(yaml).contains("          labels: {job: 'NameNode', instance: 'worker-1:9101'}");
        assertThat(yaml).contains("    - job_name: 'DataNode'");
        assertThat(yaml).contains("        - targets: ['127.0.0.1:9102']");
        assertThat(yaml).contains("    - job_name: 'node'");
        assertThat(yaml).contains("        - targets: ['127.0.0.1:9100']");
    }
    
    @Test
    void skipsStoppedRolesAndRolesWithoutRegisteredPort() {
        givenClusterFrame("FRAME_B");
        ServiceRoleJmxMap.put("FRAME_B_HDFS_NameNode", "9201");
        when(roleService.getServiceRoleListByHostnameAndClusterId("worker-2", 8))
                .thenReturn(List.of(
                        role("HDFS", "NameNode", ServiceRoleState.STOP),
                        role("HDFS", "DataNode", ServiceRoleState.RUNNING)));
        
        String yaml = builder.build(8, "worker-2");
        
        assertThat(yaml).doesNotContain("NameNode");
        assertThat(yaml).doesNotContain("DataNode");
        assertThat(yaml).contains("job_name: 'node'");
    }
    
    @Test
    void appliesMetricsPathOverridesAndDorisGroupLabels() {
        givenClusterFrame("FRAME_C");
        ServiceRoleJmxMap.put("FRAME_C_DOLPHINSCHEDULER_ApiServer", "12345");
        ServiceRoleJmxMap.put("FRAME_C_NACOS_NacosServer", "8848");
        ServiceRoleJmxMap.put("FRAME_C_APISIX_Apisix", "9091");
        ServiceRoleJmxMap.put("FRAME_C_MINIO_Minio", "9000");
        ServiceRoleJmxMap.put("FRAME_C_DORIS_DorisFE", "8030");
        ServiceRoleJmxMap.put("FRAME_C_DORIS_DorisBE", "8040");
        when(roleService.getServiceRoleListByHostnameAndClusterId("worker-3", 9))
                .thenReturn(List.of(
                        role("DOLPHINSCHEDULER", "ApiServer", ServiceRoleState.RUNNING),
                        role("NACOS", "NacosServer", ServiceRoleState.RUNNING),
                        role("APISIX", "Apisix", ServiceRoleState.RUNNING),
                        role("MINIO", "Minio", ServiceRoleState.RUNNING),
                        role("DORIS", "DorisFE", ServiceRoleState.RUNNING),
                        role("DORIS", "DorisBE", ServiceRoleState.RUNNING)));
        
        String yaml = builder.build(9, "worker-3");
        
        assertThat(yaml).contains("job_name: 'ApiServer'");
        assertThat(yaml).contains("metrics_path: '/dolphinscheduler/actuator/prometheus'");
        assertThat(yaml).contains("job_name: 'NacosServer'");
        assertThat(yaml).contains("metrics_path: '/nacos/actuator/prometheus'");
        assertThat(yaml).contains("job_name: 'Apisix'");
        assertThat(yaml).contains("metrics_path: '/apisix/prometheus/metrics'");
        assertThat(yaml).contains("job_name: 'Minio'");
        assertThat(yaml).contains("metrics_path: '/minio/v2/metrics/cluster'");
        assertThat(yaml).contains("labels: {job: 'DorisFE', instance: 'worker-3:8030', group: 'fe'}");
        assertThat(yaml).contains("labels: {job: 'DorisBE', instance: 'worker-3:8040', group: 'be'}");
    }
    
    @Test
    void emptyRoleListStillGeneratesNodeExporterJob() {
        givenClusterFrame("FRAME_D");
        when(roleService.getServiceRoleListByHostnameAndClusterId("worker-4", 10))
                .thenReturn(List.of());
        
        String yaml = builder.build(10, "worker-4");
        
        assertThat(yaml).contains("job_name: 'node'");
        assertThat(yaml).contains("targets: ['127.0.0.1:9100']");
    }
    
    private void givenClusterFrame(String frame) {
        ClusterInfoEntity cluster = new ClusterInfoEntity();
        cluster.setClusterFrame(frame);
        when(clusterInfoService.getById(org.mockito.ArgumentMatchers.anyInt())).thenReturn(cluster);
    }
    
    private static ClusterServiceRoleInstanceEntity role(String serviceName, String roleName, ServiceRoleState state) {
        ClusterServiceRoleInstanceEntity role = new ClusterServiceRoleInstanceEntity();
        role.setServiceName(serviceName);
        role.setServiceRoleName(roleName);
        role.setServiceRoleState(state);
        return role;
    }
}
