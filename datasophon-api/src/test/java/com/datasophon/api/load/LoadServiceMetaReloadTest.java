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

package com.datasophon.api.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.api.service.FrameInfoService;
import com.datasophon.api.service.ddl.DdlMetaService;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.storage.vo.ServiceMetaItem;
import com.datasophon.dao.entity.FrameInfoEntity;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

class LoadServiceMetaReloadTest {

    private final ClusterInfoService clusterInfoService = mock(ClusterInfoService.class);

    private final FrameInfoService frameInfoService = mock(FrameInfoService.class);

    private final DdlMetaService ddlMetaService = mock(DdlMetaService.class);

    private LoadServiceMeta loadServiceMeta;

    @BeforeEach
    void setUp() {
        loadServiceMeta = new LoadServiceMeta();
        ReflectionTestUtils.setField(loadServiceMeta, "clusterInfoService", clusterInfoService);
        ReflectionTestUtils.setField(loadServiceMeta, "variableService", mock(ClusterVariableService.class));
        ReflectionTestUtils.setField(loadServiceMeta, "frameInfoService", frameInfoService);
        ReflectionTestUtils.setField(loadServiceMeta, "ddlMetaService", ddlMetaService);
        when(clusterInfoService.list()).thenReturn(List.of());
    }

    @Test
    void reloadAllMeta_returnsCountsForPhysicalAndK8sServices() throws Exception {
        MetaStorage metaStorage = mock(MetaStorage.class);
        ServiceMetaItem physical = item("BIGDATA", "HDFS");
        ServiceMetaItem k8s = item("BIGDATA", "SPARK");
        FrameInfoEntity frameInfo = new FrameInfoEntity();
        when(metaStorage.listService(MetaStorage.PHYSICAL)).thenReturn(List.of(physical));
        when(metaStorage.listService(MetaStorage.K8S)).thenReturn(List.of(k8s));
        when(metaStorage.getServiceDdL(physical)).thenReturn("physical ddl");
        when(metaStorage.getServiceDdL(k8s)).thenReturn("k8s ddl");
        when(frameInfoService.saveFrameIfAbsent("BIGDATA")).thenReturn(frameInfo);

        try (MockedStatic<StorageUtils> storageUtils = mockStatic(StorageUtils.class)) {
            storageUtils.when(StorageUtils::getMetaStorage).thenReturn(metaStorage);
            MetaReloadResult result = loadServiceMeta.reloadAllMeta();

            assertThat(result.getPhysicalTotal()).isEqualTo(1);
            assertThat(result.getPhysicalLoaded()).isEqualTo(1);
            assertThat(result.getK8sTotal()).isEqualTo(1);
            assertThat(result.getK8sLoaded()).isEqualTo(1);
            assertThat(result.getErrors()).isEmpty();
            assertThat(result.isMetaStorageAvailable()).isTrue();
        }
    }

    @Test
    void reloadAllMeta_recordsInvalidDdlAndContinuesLoadingOtherServices() throws Exception {
        MetaStorage metaStorage = mock(MetaStorage.class);
        ServiceMetaItem invalid = item("BIGDATA", "INVALID");
        ServiceMetaItem valid = item("BIGDATA", "HDFS");
        when(metaStorage.listService(MetaStorage.PHYSICAL)).thenReturn(List.of(invalid, valid));
        when(metaStorage.listService(MetaStorage.K8S)).thenReturn(List.of());
        when(metaStorage.getServiceDdL(invalid)).thenReturn("invalid ddl");
        when(metaStorage.getServiceDdL(valid)).thenReturn("valid ddl");
        when(frameInfoService.saveFrameIfAbsent("BIGDATA")).thenReturn(new FrameInfoEntity());
        doThrow(new IllegalArgumentException("invalid ddl"))
                .when(ddlMetaService)
                .loadServicePhysicalDdl(anyList(), any(), eq("INVALID"), any());

        try (MockedStatic<StorageUtils> storageUtils = mockStatic(StorageUtils.class)) {
            storageUtils.when(StorageUtils::getMetaStorage).thenReturn(metaStorage);
            MetaReloadResult result = loadServiceMeta.reloadAllMeta();

            assertThat(result.getPhysicalTotal()).isEqualTo(2);
            assertThat(result.getPhysicalLoaded()).isEqualTo(1);
            assertThat(result.getErrors()).containsExactly("BIGDATA/INVALID: invalid ddl");
            verify(ddlMetaService).loadServicePhysicalDdl(anyList(), any(), eq("HDFS"), eq("valid ddl"));
        }
    }

    @Test
    void reloadAllMeta_returnsSkippedResultWhenMetaStorageIsUnavailable() {
        try (MockedStatic<StorageUtils> storageUtils = mockStatic(StorageUtils.class)) {
            storageUtils.when(StorageUtils::getMetaStorage)
                    .thenThrow(new IllegalStateException("no enabled storage"));
            MetaReloadResult result = loadServiceMeta.reloadAllMeta();

            assertThat(result.isMetaStorageAvailable()).isFalse();
            assertThat(result.getPhysicalTotal()).isZero();
            assertThat(result.getK8sTotal()).isZero();
            assertThat(result.getErrors()).isEmpty();
        }
    }

    private ServiceMetaItem item(String framework, String serviceName) {
        ServiceMetaItem item = new ServiceMetaItem();
        item.setFramework(framework);
        item.setServiceName(serviceName);
        return item;
    }
}
