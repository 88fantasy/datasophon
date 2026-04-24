package com.datasophon.api.service.agent;

import com.datasophon.api.DataSophonApplicationTestLauncher;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * K8sAgentDeployServiceImpl 单元测试
 *
 * @author zhanghuangbin
 */
@SpringBootTest(classes = DataSophonApplicationTestLauncher.class)
public class K8sAgentDeployServiceInjectTest {


    @Autowired
    private K8sAgentDeployService k8sAgentDeployService;


    @Autowired
    private K8sClusterConfigService k8sClusterConfigService;



    @Test
    public void testDeploy() {
        K8sClusterConfig config = k8sClusterConfigService.getInitConfig(7);
        k8sAgentDeployService.deployAgent(config);
    }

}
