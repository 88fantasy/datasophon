package com.datasophon.k8sagent.aop;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.exceptions.ValidateException;
import com.datasophon.common.utils.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestControllerAdvice
@Slf4j
public final class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Object defaultHandler(Exception exception, HttpServletRequest request) {
        boolean isParameterError =
                exception instanceof BindException
                || exception instanceof ValidateException
                || exception instanceof HttpMessageNotReadableException
                || exception instanceof IllegalArgumentException;
        if (isParameterError) {
            log.warn("request {} error, ", request.getRequestURI(), exception);
        } else {
            log.error("request {} error, ", request.getRequestURI(), exception);
        }


        int code = isParameterError ? 400 : 500;
        String message = exception.getMessage();
        if (exception instanceof NullPointerException) {
            message = "对象空值";
        } else if (exception instanceof MethodArgumentNotValidException) {
            MethodArgumentNotValidException methodArgumentNotValidException = (MethodArgumentNotValidException) exception;
            List<ObjectError> allErrors = methodArgumentNotValidException.getBindingResult().getAllErrors();
            if (CollUtil.isNotEmpty(allErrors)) {
                String split = "<===>";
                StringBuilder stringBuilder = new StringBuilder();

                for (int i = 0; i < allErrors.size(); ++i) {
                    stringBuilder.append(allErrors.get(i).getDefaultMessage());
                    if (i != allErrors.size() - 1) {
                        stringBuilder.append(split);
                    }
                }

                message = stringBuilder.toString();
            }
        } else if (exception instanceof BindException) {
            BindException bindException = (BindException) exception;
            message = bindException.getAllErrors().get(0).getDefaultMessage();
        } else if (exception instanceof HttpMessageNotReadableException) {
            message = "框架内部类型转换失败";
        } else if (exception instanceof ValidateException || exception instanceof IllegalArgumentException) {
            message = exception.getMessage();
        }

        return Result.error(code, message);
    }

}
