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

package com.datasophon.api.controller.internal;

import com.datasophon.api.load.LoadServiceMeta;
import com.datasophon.api.load.MetaReloadResult;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 元数据管理相关的内部端点（供内部系统或脚本调用）。
 *
 * <p>此控制器不继承 {@link com.datasophon.api.controller.ApiController}，路径为
 * {@code /ddh/internal/meta/**} 而非 {@code /ddh/api/internal/meta/**}。登录/CSRF
 * 拦截器仅覆盖 {@code /ddh/api/**}，{@code basicValidRequestInterceptor} 显式排除
 * {@code /internal/**}，故这些端点不在拦截范围内。
 *
 * <p>该端点使用 {@code X-Internal-Token} 共享 Token 鉴权。Token 未配置时端点默认拒绝访问。
 */
@RestController
@RequestMapping("/internal/meta")
public class InternalMetaController {

    private final LoadServiceMeta loadServiceMeta;
    private final String internalToken;

    public InternalMetaController(
                                  LoadServiceMeta loadServiceMeta,
                                  @Value("${datasophon.internal-api.token:}") String internalToken) {
        this.loadServiceMeta = loadServiceMeta;
        this.internalToken = internalToken;
    }

    @PostMapping("/refresh")
    public InternalResponse<MetaReloadResult> refresh(
                                                      @RequestHeader(name = "X-Internal-Token", required = false) String token,
                                                      HttpServletResponse response) {
        if (!isAuthorized(token)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return InternalResponse.fail(HttpStatus.UNAUTHORIZED.value(), "内部接口鉴权失败");
        }
        return InternalResponse.ok(loadServiceMeta.reloadAllMeta());
    }

    private boolean isAuthorized(String token) {
        if (StringUtils.isBlank(internalToken) || token == null) {
            return false;
        }
        return MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }
}
