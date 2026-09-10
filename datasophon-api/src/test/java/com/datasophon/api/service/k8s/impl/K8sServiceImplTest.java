package com.datasophon.api.service.k8s.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.datasophon.api.dto.instance.K8sServiceInstanceQueryDTO;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.service.k8s.K8sClientOptionsFactory;
import com.datasophon.api.vo.k8s.K8sServiceInfo;
import com.datasophon.common.k8s.client.KubectlClient;
import com.datasophon.common.k8s.vo.k8s.K8sResourceList;
import com.datasophon.common.k8s.vo.k8s.K8sService;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.k8s.InstanceSourceKind;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class K8sServiceImplTest {

    private final K8sServiceImpl service = new K8sServiceImpl();
    private final K8sServiceInstanceVO instance = new K8sServiceInstanceVO();
    private final K8sServiceInstanceQueryDTO query = new K8sServiceInstanceQueryDTO();
    private final K8sClusterConfig config = new K8sClusterConfig();

    K8sServiceImplTest() {
        K8sServiceInstanceService instances = mock(K8sServiceInstanceService.class);
        ReflectionTestUtils.setField(service, "k8sServiceInstanceService", instances);
        ReflectionTestUtils.setField(service, "clientOptionsFactory", mock(K8sClientOptionsFactory.class));
        instance.setNamespace("prod");
        instance.setReleaseName("elasticsearch");
        instance.setServiceName("different-chart-name");
        instance.setSourceKind(InstanceSourceKind.HELM);
        query.setInstanceId(58);
        when(instances.getVoById(58)).thenReturn(Optional.of(instance));
    }

    @Test
    void includesLegacyHelmServicesAlongsideModernOnesWithDisjointSelectors() {
        String modern = "app.kubernetes.io/managed-by=Helm,app.kubernetes.io/instance=elasticsearch";
        String legacy = "app.kubernetes.io/managed-by=Helm,release=elasticsearch,!app.kubernetes.io/instance";
        try (var clients = mockConstruction(KubectlClient.class, (client, context) -> {
            when(client.getServices("prod", modern)).thenReturn(resources("modern-service"));
            when(client.getServices("prod", legacy)).thenReturn(resources("elasticsearch-master", "elasticsearch-master-headless"));
        })) {
            assertThat(service.listServices(config, query)).extracting(K8sServiceInfo::getName)
                    .containsExactly("modern-service", "elasticsearch-master", "elasticsearch-master-headless");
            KubectlClient client = clients.constructed().getFirst();
            verify(client).getServices("prod", modern);
            // Only missing instance labels qualify: dual-labeled resources cannot be duplicated or assigned across releases.
            verify(client).getServices("prod", legacy);
            verify(client).close();
            verifyNoMoreInteractions(client);
        }
    }

    @Test
    void leavesCrResourceSelectionUnchanged() {
        instance.setSourceKind(InstanceSourceKind.CR);
        try (var clients = mockConstruction(KubectlClient.class, (client, context) -> {
            when(client.getServices(eq("prod"), isNull())).thenReturn(resources("cr-service"));
        })) {
            assertThat(service.listServices(config, query)).extracting(K8sServiceInfo::getName).containsExactly("cr-service");
            KubectlClient client = clients.constructed().getFirst();
            verify(client).getServices("prod", null);
            verify(client).close();
            verifyNoMoreInteractions(client);
        }
    }

    private static K8sResourceList<K8sService> resources(String... names) {
        K8sResourceList<K8sService> result = new K8sResourceList<>();
        result.setItems(java.util.Arrays.stream(names).map(name -> {
            K8sService item = new K8sService();
            K8sService.Metadata metadata = new K8sService.Metadata();
            metadata.setName(name);
            metadata.setNamespace("prod");
            item.setMetadata(metadata);
            return item;
        }).toList());
        return result;
    }
}
