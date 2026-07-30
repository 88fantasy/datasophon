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
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.exceptions.BusinessHintException;

import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * v2 API 异常处理器，仅作用于 {@code com.datasophon.api.controller.v2} 包。
 *
 * <p>优先级高于全局 {@code ApiExceptionHandler}（@Order(1)），异常转 ant-design-pro
 * 标准信封 {@link ApiResponse}（{@code success=false, showType=2}）。旧接口的异常
 * 仍由全局 Advice 处理，不受影响。
 */
@Order(1)
@RestControllerAdvice(basePackages = "com.datasophon.api.controller.v2")
public class V2ApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(V2ApiExceptionHandler.class);

    /**
     * {@link org.springframework.web.server.ResponseStatusException} 以及 Spring 内建的绑定异常
     * （如缺失必填 {@code @RequestParam} 抛出的 {@code MissingServletRequestParameterException}）
     * 分别继承自不同的基类，但都实现 {@link ErrorResponse} 接口——按 {@code instanceof} 判断而不是
     * 枚举具体异常类型，否则 Spring 未来新增实现该接口的异常类型时会再次悄悄漏掉。
     *
     * <p>这个分支必须存在：缺了它时响应体会正确标成 {@code success=false}，但 HTTP 状态码仍是
     * 默认的 200，调用方（含 Gravitino 的 http sink）会把失败误判成成功而不重试/不告警。
     * 已用真实 Spring 上下文验证过这个缺陷（503/400 全部退化成 200）。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        if (e instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            logger.error("v2 API error response exception: {}", e.getMessage(), e);
            return ResponseEntity.status(status).body(ApiResponse.fail(status, errorResponse.getBody().getDetail()));
        }
        String message = e instanceof NullPointerException ? "对象空指针" : e.getMessage();
        logger.error("v2 API exception: {}", message, e);
        return ResponseEntity.status(500).body(ApiResponse.fail(500, message));
    }

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        logger.error("v2 business exception: {}", e.getMessage(), e);
        return ApiResponse.fail(500, e.getMessage());
    }

    @ExceptionHandler(BusinessHintException.class)
    public ApiResponse<Void> handleBusinessHintException(BusinessHintException e) {
        return ApiResponse.fail(400, e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException e) {
        Set<String> messages = e.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessageTemplate)
                .collect(Collectors.toSet());
        return ApiResponse.fail(400, String.join(", ", messages));
    }
}
