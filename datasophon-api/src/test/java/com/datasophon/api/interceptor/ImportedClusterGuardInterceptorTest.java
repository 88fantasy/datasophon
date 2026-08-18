package com.datasophon.api.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.security.ImportedReadOnly;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.enums.ManageMode;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

class ImportedClusterGuardInterceptorTest {

    private final ClusterInfoService clusterInfoService = mock(ClusterInfoService.class);
    private final ImportedClusterGuardInterceptor interceptor =
            new ImportedClusterGuardInterceptor(clusterInfoService);

    @Test
    @DisplayName("接管集群上的写接口被拒，错误信息带集群名与动作")
    void rejectsWriteOnImportedCluster() {
        givenCluster(7, ManageMode.IMPORTED);

        assertThatThrownBy(() -> interceptor.preHandle(
                requestFor(7), new MockHttpServletResponse(), handler("write")))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("接管模式")
                .hasMessageContaining("修改服务配置");
    }

    @Test
    @DisplayName("普通集群不受影响")
    void allowsWriteOnManagedCluster() {
        givenCluster(7, ManageMode.MANAGED);

        assertThat(interceptor.preHandle(
                requestFor(7), new MockHttpServletResponse(), handler("write"))).isTrue();
    }

    @Test
    @DisplayName("manageMode 为空的历史集群按普通集群放行，不影响存量数据")
    void allowsWriteWhenManageModeIsNull() {
        givenCluster(7, null);

        assertThat(interceptor.preHandle(
                requestFor(7), new MockHttpServletResponse(), handler("write"))).isTrue();
    }

    @Test
    @DisplayName("没有注解的接口即便在接管集群上也放行")
    void ignoresUnannotatedHandler() {
        givenCluster(7, ManageMode.IMPORTED);

        assertThat(interceptor.preHandle(
                requestFor(7), new MockHttpServletResponse(), handler("read"))).isTrue();
    }

    @Test
    @DisplayName("路径上没有 clusterId 时放行，交由 service 层自行校验")
    void ignoresRequestWithoutClusterId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of());

        assertThat(interceptor.preHandle(
                request, new MockHttpServletResponse(), handler("write"))).isTrue();
    }

    private void givenCluster(int clusterId, ManageMode mode) {
        ClusterInfoEntity cluster = new ClusterInfoEntity();
        cluster.setId(clusterId);
        cluster.setClusterName("bjsy");
        cluster.setManageMode(mode);
        when(clusterInfoService.getById(clusterId)).thenReturn(cluster);
    }

    private MockHttpServletRequest requestFor(int clusterId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("clusterId", String.valueOf(clusterId)));
        return request;
    }

    private HandlerMethod handler(String methodName) {
        try {
            return new HandlerMethod(new StubController(),
                    StubController.class.getMethod(methodName));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 只为拿到带/不带注解的 HandlerMethod，不参与真实请求处理。 */
    static class StubController {

        @ImportedReadOnly("修改服务配置")
        public void write() {
        }

        public void read() {
        }
    }
}
