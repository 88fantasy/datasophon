package com.datasophon.api.service.agent;

import com.datasophon.api.service.PropertiesPathUtils;
import com.datasophon.api.service.agent.impl.K8sAgentDeployServiceImpl;
import com.datasophon.common.k8s.client.HelmClient;
import com.datasophon.common.k8s.dto.UninstallParams;
import com.datasophon.common.k8s.dto.UpgradeParams;
import com.datasophon.common.k8s.vo.helm.HelmReleaseVO;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.nexus.NexusFacade;
import com.datasophon.common.utils.nexus.client.CommonNexusClient;
import com.datasophon.common.utils.nexus.client.HelmRepoClient;
import com.datasophon.common.utils.nexus.vo.Assert;
import com.datasophon.common.utils.nexus.vo.Component;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * K8sAgentDeployServiceImpl 单元测试
 *
 * @author zhanghuangbin
 */
@ExtendWith(MockitoExtension.class)
class K8sAgentDeployServiceTest {

    private K8sAgentDeployServiceImpl service;

    @TempDir
    File tempDir;

    @BeforeEach
    public void init() {
        PropertiesPathUtils.resetPropertyFile();
        service = new K8sAgentDeployServiceImpl();
    }

    private K8sClusterConfig buildConfig() {
        K8sClusterConfig config = new K8sClusterConfig();
        config.setClusterId(1);
        config.setKubeConfig("apiVersion: v1\nclusters: []");
        config.setServerHost("https://k8s-api:6443");
        config.setToken("test-token");
        config.setUsername("admin");
        config.setPassword("admin123");
        config.setServerCert(null);
        return config;
    }

    @Test
    void deployAgent_should_succeed_when_helmUpgradeReturnsDeployed() throws Exception {
        K8sClusterConfig config = buildConfig();

        HelmReleaseVO releaseVO = new HelmReleaseVO();
        releaseVO.setName("datasophon-k8s-agent");
        releaseVO.setVersion(1);
        HelmReleaseVO.Info info = new HelmReleaseVO.Info();
        info.setStatus("deployed");
        releaseVO.setInfo(info);

        HelmRepoClient mockHelmRepoClient = mock(HelmRepoClient.class);
        CommonNexusClient mockCommonClient = mock(CommonNexusClient.class);

        // 构建 Nexus 返回的 Component
        Assert asset = new Assert();
        asset.setDownloadUrl("http://nexus:8081/repository/helm/datasophon-k8s-agent-1.0.0.tgz");
        Component component = new Component();
        component.setName("datasophon-k8s-agent");
        component.setVersion("1.0.0");
        component.setAssets(Collections.singletonList(asset));

        when(mockHelmRepoClient.getNewestComponent("datasophon-k8s-agent")).thenReturn(component);
        doNothing().when(mockCommonClient).download(anyString(), any(OutputStream.class));

        File tmpFile = new File(tempDir, "datasophon-k8s-agent.tgz");
        tmpFile.createNewFile();

        try (MockedStatic<NexusFacade> nexusMock = mockStatic(NexusFacade.class);
             MockedStatic<PathUtils> pathUtilsMock = mockStatic(PathUtils.class);
             MockedConstruction<HelmClient> helmClientMock = mockConstruction(HelmClient.class,
                     (mock, context) -> {
                         when(mock.upgrade(any(UpgradeParams.class))).thenReturn(releaseVO);
                     })) {

            nexusMock.when(NexusFacade::getHelmClient).thenReturn(mockHelmRepoClient);
            nexusMock.when(NexusFacade::getCommonClient).thenReturn(mockCommonClient);
            pathUtilsMock.when(() -> PathUtils.createTmpFile(anyString(), anyString())).thenReturn(tmpFile);

            // 执行
            service.deployAgent(config);

            // 验证
            verify(mockHelmRepoClient).getNewestComponent("datasophon-k8s-agent");
            verify(mockCommonClient).download(eq("http://nexus:8081/repository/helm/datasophon-k8s-agent-1.0.0.tgz"), any(OutputStream.class));
            assertEquals(1, helmClientMock.constructed().size());
            HelmClient constructedClient = helmClientMock.constructed().get(0);
            verify(constructedClient).upgrade(any(UpgradeParams.class));
        }
    }

    @Test
    void deployAgent_should_throwException_when_chartNotFound() throws Exception {
        K8sClusterConfig config = buildConfig();

        HelmRepoClient mockHelmRepoClient = mock(HelmRepoClient.class);
        when(mockHelmRepoClient.getNewestComponent("datasophon-k8s-agent")).thenReturn(null);

        try (MockedStatic<NexusFacade> nexusMock = mockStatic(NexusFacade.class)) {
            nexusMock.when(NexusFacade::getHelmClient).thenReturn(mockHelmRepoClient);

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> service.deployAgent(config));
            assertTrue(exception.getMessage().contains("部署 K8s Agent 失败"));
        }
    }

    @Test
    void deployAgent_should_throwException_when_chartAssetsEmpty() throws Exception {
        K8sClusterConfig config = buildConfig();

        HelmRepoClient mockHelmRepoClient = mock(HelmRepoClient.class);
        Component component = new Component();
        component.setName("datasophon-k8s-agent");
        component.setAssets(Collections.emptyList());
        when(mockHelmRepoClient.getNewestComponent("datasophon-k8s-agent")).thenReturn(component);

        try (MockedStatic<NexusFacade> nexusMock = mockStatic(NexusFacade.class)) {
            nexusMock.when(NexusFacade::getHelmClient).thenReturn(mockHelmRepoClient);

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> service.deployAgent(config));
            assertTrue(exception.getMessage().contains("部署 K8s Agent 失败"));
        }
    }

    @Test
    void deployAgent_should_throwException_when_helmUpgradeStatusNotDeployed() throws Exception {
        K8sClusterConfig config = buildConfig();

        HelmReleaseVO releaseVO = new HelmReleaseVO();
        releaseVO.setName("datasophon-k8s-agent");
        releaseVO.setVersion(1);
        HelmReleaseVO.Info info = new HelmReleaseVO.Info();
        info.setStatus("failed");
        info.setNotes("安装超时");
        releaseVO.setInfo(info);

        HelmRepoClient mockHelmRepoClient = mock(HelmRepoClient.class);
        CommonNexusClient mockCommonClient = mock(CommonNexusClient.class);

        Assert asset = new Assert();
        asset.setDownloadUrl("http://nexus:8081/repository/helm/chart.tgz");
        Component component = new Component();
        component.setName("datasophon-k8s-agent");
        component.setVersion("1.0.0");
        component.setAssets(Collections.singletonList(asset));

        when(mockHelmRepoClient.getNewestComponent("datasophon-k8s-agent")).thenReturn(component);
        doNothing().when(mockCommonClient).download(anyString(), any(OutputStream.class));

        File tmpFile = new File(tempDir, "datasophon-k8s-agent.tgz");
        tmpFile.createNewFile();

        try (MockedStatic<NexusFacade> nexusMock = mockStatic(NexusFacade.class);
             MockedStatic<PathUtils> pathUtilsMock = mockStatic(PathUtils.class);
             MockedConstruction<HelmClient> helmClientMock = mockConstruction(HelmClient.class,
                     (mock, context) -> {
                         when(mock.upgrade(any(UpgradeParams.class))).thenReturn(releaseVO);
                     })) {

            nexusMock.when(NexusFacade::getHelmClient).thenReturn(mockHelmRepoClient);
            nexusMock.when(NexusFacade::getCommonClient).thenReturn(mockCommonClient);
            pathUtilsMock.when(() -> PathUtils.createTmpFile(anyString(), anyString())).thenReturn(tmpFile);

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> service.deployAgent(config));
            assertTrue(exception.getMessage().contains("部署 K8s Agent 失败"));
        }
    }

    @Test
    void deployAgent_should_throwException_when_downloadFails() throws Exception {
        K8sClusterConfig config = buildConfig();

        HelmRepoClient mockHelmRepoClient = mock(HelmRepoClient.class);
        CommonNexusClient mockCommonClient = mock(CommonNexusClient.class);

        Assert asset = new Assert();
        asset.setDownloadUrl("http://nexus:8081/repository/helm/chart.tgz");
        Component component = new Component();
        component.setName("datasophon-k8s-agent");
        component.setVersion("1.0.0");
        component.setAssets(Collections.singletonList(asset));

        when(mockHelmRepoClient.getNewestComponent("datasophon-k8s-agent")).thenReturn(component);

        File tmpFile = new File(tempDir, "datasophon-k8s-agent.tgz");
        tmpFile.createNewFile();

        doThrow(new IOException("网络连接失败")).when(mockCommonClient)
                .download(anyString(), any(OutputStream.class));

        try (MockedStatic<NexusFacade> nexusMock = mockStatic(NexusFacade.class);
             MockedStatic<PathUtils> pathUtilsMock = mockStatic(PathUtils.class)) {

            nexusMock.when(NexusFacade::getHelmClient).thenReturn(mockHelmRepoClient);
            nexusMock.when(NexusFacade::getCommonClient).thenReturn(mockCommonClient);
            pathUtilsMock.when(() -> PathUtils.createTmpFile(anyString(), anyString())).thenReturn(tmpFile);

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> service.deployAgent(config));
            assertTrue(exception.getMessage().contains("部署 K8s Agent 失败"));
        }
    }

    @Test
    void undeployAgent_should_succeed_when_uninstallSuccess() {
        K8sClusterConfig config = buildConfig();

        try (MockedConstruction<HelmClient> helmClientMock = mockConstruction(HelmClient.class,
                (mock, context) -> {
                    doNothing().when(mock).uninstall(any(UninstallParams.class));
                })) {

            service.undeployAgent(config);

            assertEquals(1, helmClientMock.constructed().size());
            HelmClient constructedClient = helmClientMock.constructed().get(0);
            verify(constructedClient).uninstall(any(UninstallParams.class));
        }
    }

    @Test
    void undeployAgent_should_throwException_when_uninstallFails() {
        K8sClusterConfig config = buildConfig();

        try (MockedConstruction<HelmClient> helmClientMock = mockConstruction(HelmClient.class,
                (mock, context) -> {
                    doThrow(new RuntimeException("helm uninstall 失败"))
                            .when(mock).uninstall(any(UninstallParams.class));
                })) {

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> service.undeployAgent(config));
            assertTrue(exception.getMessage().contains("卸载 K8s Agent 失败"));
        }
    }

    @Test
    void undeployAgent_should_passCorrectParams_when_called() {
        K8sClusterConfig config = buildConfig();

        try (MockedConstruction<HelmClient> helmClientMock = mockConstruction(HelmClient.class,
                (mock, context) -> {
                    doAnswer(invocation -> {
                        UninstallParams params = invocation.getArgument(0);
                        assertEquals("datasophon-k8s-agent", params.getReleaseName());
                        assertEquals("vos", params.getNamespace());
                        assertFalse(params.isKeepHistory());
                        return null;
                    }).when(mock).uninstall(any(UninstallParams.class));
                })) {

            service.undeployAgent(config);

            // 验证 HelmClient 构造时传入了正确的 ClientOptions
            assertEquals(1, helmClientMock.constructed().size());
        }
    }

    @Test
    void namespace_constant_should_be_vos() {
        assertEquals("vos", K8sAgentDeployService.NAMESPACE);
    }
}
