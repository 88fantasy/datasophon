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

package com.datasophon.api.interceptor;

import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.security.ImportedReadOnly;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.enums.ManageMode;

import java.util.Map;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 接管集群写操作门禁：带 {@link ImportedReadOnly} 的接口，若目标集群是接管模式则直接拒绝。
 *
 * <p>放在拦截器而不是切面，是因为它需要 URI 模板变量里的 {@code clusterId}，
 * 而这个值在 MVC 层现成可取；项目现有的鉴权链也都是拦截器，风格一致。
 */
@Component
public class ImportedClusterGuardInterceptor implements HandlerInterceptor {

    private static final String CLUSTER_ID = "clusterId";

    private final ClusterInfoService clusterInfoService;

    public ImportedClusterGuardInterceptor(ClusterInfoService clusterInfoService) {
        this.clusterInfoService = clusterInfoService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        ImportedReadOnly annotation = handlerMethod.getMethodAnnotation(ImportedReadOnly.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(ImportedReadOnly.class);
        }
        if (annotation == null) {
            return true;
        }

        Integer clusterId = resolveClusterId(request);
        if (clusterId == null) {
            return true;
        }
        ClusterInfoEntity cluster = clusterInfoService.getById(clusterId);
        if (cluster == null || !ManageMode.IMPORTED.equals(cluster.getManageMode())) {
            return true;
        }
        throw new BusinessHintException(
                String.format("集群「%s」是接管模式，只提供只读监控，不能%s。"
                        + "如需变更请在目标集群自行操作。",
                        cluster.getClusterName(), annotation.value()));
    }

    private Integer resolveClusterId(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> variables)) {
            return null;
        }
        Object raw = variables.get(CLUSTER_ID);
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(raw.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
