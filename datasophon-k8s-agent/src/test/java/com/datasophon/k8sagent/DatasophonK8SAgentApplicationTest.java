package com.datasophon.k8sagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class for K8sAgentApplication.
 */
@SpringBootTest
class DatasophonK8SAgentApplicationTest {

    @Test
    void contextLoads() {
        // Verify that the Spring application context loads successfully
        DatasophonK8sAgentApplication application = new DatasophonK8sAgentApplication();
        assertNotNull(application);
    }
}
