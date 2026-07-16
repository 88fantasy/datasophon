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

import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.exceptions.BusinessHintException;

import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * 内部系统对接端点的异常处理器。
 */
@Order(1)
@RestControllerAdvice(basePackages = "com.datasophon.api.controller.internal")
public class InternalApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(InternalApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public InternalResponse<Void> handleException(Exception e) {
        String message = e instanceof NullPointerException ? "对象空指针" : e.getMessage();
        logger.error("internal API exception: {}", message, e);
        return InternalResponse.fail(500, message);
    }

    @ExceptionHandler(BusinessException.class)
    public InternalResponse<Void> handleBusinessException(BusinessException e) {
        logger.error("internal business exception: {}", e.getMessage(), e);
        return InternalResponse.fail(500, e.getMessage());
    }

    @ExceptionHandler(BusinessHintException.class)
    public InternalResponse<Void> handleBusinessHintException(BusinessHintException e) {
        return InternalResponse.fail(400, e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public InternalResponse<Void> handleConstraintViolation(ConstraintViolationException e) {
        Set<String> messages = e.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessageTemplate)
                .collect(Collectors.toSet());
        return InternalResponse.fail(400, String.join(", ", messages));
    }
}
