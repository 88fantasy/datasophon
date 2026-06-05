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

package com.datasophon.api.exceptions;

import com.datasophon.api.enums.Status;
import com.datasophon.common.utils.Result;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Exception Handler
 */
@ControllerAdvice
@ResponseBody
public class ApiExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);
    
    @ExceptionHandler(Exception.class)
    public Result exceptionHandler(Exception e, HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod hm) {
            ApiException ce = hm.getMethodAnnotation(ApiException.class);
            if (ce != null) {
                Status st = ce.value();
                logger.error(st.getMsg(), e);
                return Result.error(st.getCode(), st.getMsg());
            }
        }
        String message = e instanceof NullPointerException ? "对象空指针" : e.getMessage();
        logger.error(e.getMessage(), e);
        return Result.error(Status.INTERNAL_SERVER_ERROR_ARGS.getCode(), message);
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public Result constraintViolationException(ConstraintViolationException e) {
        Set<String> set = e.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessageTemplate)
                .collect(Collectors.toSet());
        return Result.error(String.join(",", set));
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result exceptionHandler(MethodArgumentTypeMismatchException e) {
        return Result.error("参数类型错不匹配：" + e.getMessage());
    }
    
    /**
     * business exception
     */
    @ExceptionHandler(BusinessException.class)
    public Result businessExceptionHandler(BusinessException e) {
        logger.error(e.getMessage(), e);
        return Result.error(e.getMessage());
    }
    
    @ExceptionHandler(BusinessHintException.class)
    public Result businessHintException(BusinessHintException e) {
        return Result.error(e.getMessage());
    }
}
