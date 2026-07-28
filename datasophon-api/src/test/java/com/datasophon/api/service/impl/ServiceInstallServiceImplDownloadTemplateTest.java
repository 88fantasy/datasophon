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

package com.datasophon.api.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceInstanceRoleGroupService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleGroupConfigService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.api.service.FrameInfoService;
import com.datasophon.api.service.FrameServiceRoleService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.cmd.ClusterServiceCommandService;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.utils.nexus.NexusFacade;
import com.datasophon.common.utils.nexus.client.CommonNexusClient;
import com.datasophon.common.utils.nexus.client.RawRepoClient;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 覆盖 downloadTemplate 迁移后的三级回退：meta 存储命中 / 回退历史扁平路径 / 两者皆无返回 404。
 * 详见配置模板从 worker 迁移到元数据目录的实施计划第 4 阶段。
 */
class ServiceInstallServiceImplDownloadTemplateTest {

    private final ServiceInstallServiceImpl service = new ServiceInstallServiceImpl(
            mock(ClusterInfoService.class),
            mock(FrameInfoService.class),
            mock(FrameServiceService.class),
            mock(FrameServiceRoleService.class),
            mock(ClusterServiceCommandService.class),
            mock(ClusterServiceInstanceService.class),
            mock(ClusterVariableService.class),
            mock(ClusterServiceInstanceRoleGroupService.class),
            mock(ClusterServiceRoleGroupConfigService.class),
            mock(ClusterServiceRoleInstanceService.class));

    @Test
    void hitsMetaStorageAndSkipsFlatFallback() throws Exception {
        MetaStorage metaStorage = mock(MetaStorage.class);
        doAnswer(invocation -> {
            MetaStorage.OutputStreamSupplier supplier = invocation.getArgument(2);
            try (OutputStream out = supplier.get()) {
                out.write("meta-content".getBytes(StandardCharsets.UTF_8));
            }
            return null;
        }).when(metaStorage).downResource(any(), anyString(), any());

        try (MockedStatic<StorageUtils> storageUtils = mockStatic(StorageUtils.class)) {
            storageUtils.when(StorageUtils::getMetaStorage).thenReturn(metaStorage);

            MockHttpServletResponse response = new MockHttpServletResponse();
            service.downloadTemplate("datacluster-physical", "APISIX", "apisix-config.ftl", response);

            assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("meta-content");
            assertThat(response.getStatus()).isEqualTo(200);
            verify(metaStorage).downResource(
                    argThat(item -> "datacluster-physical".equals(item.getFramework())
                            && "APISIX".equals(item.getServiceName())
                            && MetaStorage.PHYSICAL.equals(item.getType())),
                    eq("templates/apisix-config.ftl"),
                    any());
        }
    }

    @Test
    void fallsBackToFlatPathWhenMetaStorageMisses() throws Exception {
        MetaStorage metaStorage = mock(MetaStorage.class);
        doThrow(new FileNotFoundException("not found in meta"))
                .when(metaStorage).downResource(any(), anyString(), any());

        RawRepoClient rawRepoClient = mock(RawRepoClient.class);
        when(rawRepoClient.getNexusRawObjectUrl("/template/legacy.ftl"))
                .thenReturn("http://nexus/repository/raw/template/legacy.ftl");
        CommonNexusClient commonClient = mock(CommonNexusClient.class);
        doAnswer(invocation -> {
            OutputStream out = invocation.getArgument(1);
            out.write("flat-content".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(commonClient).download(eq("http://nexus/repository/raw/template/legacy.ftl"), any());

        try (MockedStatic<StorageUtils> storageUtils = mockStatic(StorageUtils.class);
                MockedStatic<NexusFacade> nexusFacade = mockStatic(NexusFacade.class)) {
            storageUtils.when(StorageUtils::getMetaStorage).thenReturn(metaStorage);
            nexusFacade.when(NexusFacade::getRawRepoClient).thenReturn(rawRepoClient);
            nexusFacade.when(NexusFacade::getCommonClient).thenReturn(commonClient);

            MockHttpServletResponse response = new MockHttpServletResponse();
            service.downloadTemplate("datacluster-physical", "APISIX", "legacy.ftl", response);

            assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("flat-content");
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void returns404WhenNeitherMetaStorageNorFlatPathHasTheTemplate() throws Exception {
        MetaStorage metaStorage = mock(MetaStorage.class);
        doThrow(new FileNotFoundException("not found in meta"))
                .when(metaStorage).downResource(any(), anyString(), any());

        RawRepoClient rawRepoClient = mock(RawRepoClient.class);
        when(rawRepoClient.getNexusRawObjectUrl(anyString())).thenReturn("http://nexus/repository/raw/template/missing.ftl");
        CommonNexusClient commonClient = mock(CommonNexusClient.class);
        doThrow(new FileNotFoundException("not found in flat path"))
                .when(commonClient).download(anyString(), any());

        try (MockedStatic<StorageUtils> storageUtils = mockStatic(StorageUtils.class);
                MockedStatic<NexusFacade> nexusFacade = mockStatic(NexusFacade.class)) {
            storageUtils.when(StorageUtils::getMetaStorage).thenReturn(metaStorage);
            nexusFacade.when(NexusFacade::getRawRepoClient).thenReturn(rawRepoClient);
            nexusFacade.when(NexusFacade::getCommonClient).thenReturn(commonClient);

            MockHttpServletResponse response = new MockHttpServletResponse();
            service.downloadTemplate("datacluster-physical", "APISIX", "missing.ftl", response);

            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.getContentAsByteArray()).isEmpty();
        }
    }
}
