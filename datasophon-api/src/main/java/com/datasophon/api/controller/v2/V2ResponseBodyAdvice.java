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

package com.datasophon.api.controller.v2;

import com.datasophon.api.dto.ApiResponse;
import com.datasophon.common.utils.Result;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * v2 API 响应体统一包装。
 *
 * <p>仅作用于 {@code com.datasophon.api.controller.v2} 包，对旧接口无影响。
 * <ul>
 *   <li>若控制器已返回 {@link ApiResponse}，原样透传（避免双重包装）。</li>
 *   <li>若控制器返回旧的 {@link Result}（{@code code/msg/data}），转换为标准信封。</li>
 *   <li>其他返回值一律包装为 {@code success=true} 的 {@link ApiResponse}。</li>
 * </ul>
 */
@RestControllerAdvice(basePackages = "com.datasophon.api.controller.v2")
public class V2ResponseBodyAdvice implements ResponseBodyAdvice<Object> {
    
    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // 只处理非 null 返回值；异常路径由 V2ApiExceptionHandler 负责
        return true;
    }
    
    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        // 已经是标准信封，原样透传
        if (body instanceof ApiResponse<?>) {
            return body;
        }
        
        // 旧 Result（code/msg/data）转标准信封
        if (body instanceof Result result) {
            boolean success = Integer.valueOf(200).equals(result.getCode());
            if (success) {
                return ApiResponse.ok(result.getData());
            }
            return ApiResponse.fail(
                    result.getCode() != null ? result.getCode() : 500,
                    (String) result.get("msg"));
        }
        
        // 其他对象或 null 包装为 ok
        return ApiResponse.ok(body);
    }
}
